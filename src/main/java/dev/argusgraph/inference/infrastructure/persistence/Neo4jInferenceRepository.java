package dev.argusgraph.inference.infrastructure.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import dev.argusgraph.inference.InferenceAPI;
import dev.argusgraph.inference.application.InferenceRepository;

/**
 * Cypher adapter for the inference module's derived edges. Reads the graph module's
 * authoritative {@code AFFECTS}/{@code DEPENDS_ON} edges, owns the derived
 * {@code TRANSITIVELY_AFFECTED} edge and the R2-derived {@code AFFECTS {inferredBy:'R2'}}
 * edge, both separable by their {@code inferredBy} provenance. R1 is materialised one hop at a
 * time (base + step) so the engine drives the transitive closure to fixpoint itself.
 */
@Component
@RequiredArgsConstructor
class Neo4jInferenceRepository implements InferenceRepository {

	private static final String R2_CANDIDATES = """
			MATCH (v:Vulnerability)-[ap:AFFECTS_PACKAGE]->(p:Package)-[:HAS_VERSION]->(pv:PackageVersion)
			WHERE ap.ranges IS NOT NULL
			RETURN v.id AS vulnId, pv.purl AS purl, pv.version AS version,
			       p.type AS purlType, ap.ranges AS rangesJson
			""";

	private static final String WRITE_R2 = """
			UNWIND $hits AS hit
			MATCH (v:Vulnerability {id: hit.vulnId})
			MATCH (pv:PackageVersion {purl: hit.purl})
			MERGE (v)-[a:AFFECTS]->(pv)
			  ON CREATE SET a.inferredBy = 'R2', a.derivedAt = datetime(), a._new = true
			WITH a WHERE coalesce(a._new, false) = true
			REMOVE a._new
			RETURN count(a) AS created
			""";

	private static final String DELETE_R2_AFFECTS = """
			MATCH (:Vulnerability)-[a:AFFECTS {inferredBy: 'R2'}]->(:PackageVersion)
			DELETE a RETURN count(a) AS deleted
			""";

	// One hop only: a direct dependency on an affected package-version, depth 1.
	private static final String WRITE_R1_BASE = """
			MATCH (v:Vulnerability)-[:AFFECTS]->(target:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON]->(target)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			MERGE (v)-[t:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t.depth = 1, t.inferredBy = 'R1', t.ruleVersion = 2,
			                t.derivedAt = datetime(), t._new = true
			WITH t WHERE coalesce(t._new, false) = true
			REMOVE t._new
			RETURN count(t) AS created
			""";

	// One recursive hop: extend an existing exposure by a single DEPENDS_ON edge, depth+1.
	private static final String WRITE_R1_STEP = """
			MATCH (v:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(mid:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON]->(mid)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			WITH v, source, min(t.depth) + 1 AS depth
			MERGE (v)-[t2:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t2.depth = depth, t2.inferredBy = 'R1', t2.ruleVersion = 2,
			                t2.derivedAt = datetime(), t2._new = true
			  ON MATCH  SET t2.depth = CASE WHEN depth < t2.depth THEN depth ELSE t2.depth END
			WITH t2 WHERE coalesce(t2._new, false) = true
			REMOVE t2._new
			RETURN count(t2) AS created
			""";

	private static final String DELETE_TRANSITIVE = """
			MATCH (:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(:PackageVersion)
			DELETE t RETURN count(t) AS deleted
			""";

	private static final String READ_TRANSITIVE = """
			UNWIND $purls AS purl
			MATCH (pv:PackageVersion {purl: purl})
			OPTIONAL MATCH (v:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(pv)
			WITH purl, v, t ORDER BY t.depth ASC
			RETURN purl,
				   [x IN collect(DISTINCT {id: v.id, severity: v.severity, cvssScore: v.cvssScore,
										   summary: v.summary, depth: t.depth}) WHERE x.id IS NOT NULL] AS vulnerabilities
			""";

	private final Neo4jClient neo4j;

	@Override
	public long writeR1Base(Collection<String> sourcePurls) {
		return run(WRITE_R1_BASE, sourcePurls);
	}

	@Override
	public long writeR1Step(Collection<String> sourcePurls) {
		return run(WRITE_R1_STEP, sourcePurls);
	}

	@Override
	public List<InferenceRepository.R2Candidate> r2Candidates() {
		return this.neo4j.query(R2_CANDIDATES)
			.fetch()
			.all()
			.stream()
			.map(row -> new InferenceRepository.R2Candidate((String) row.get("vulnId"), (String) row.get("purl"),
					(String) row.get("version"), (String) row.get("purlType"), (String) row.get("rangesJson")))
			.toList();
	}

	@Override
	public long writeR2Affects(List<InferenceRepository.R2Hit> hits) {
		List<Map<String, Object>> rows = hits.stream()
			.map(h -> Map.<String, Object>of("vulnId", h.vulnId(), "purl", h.purl()))
			.toList();
		return this.neo4j.query(WRITE_R2).bindAll(Map.of("hits", rows)).fetchAs(Long.class).one().orElse(0L);
	}

	@Override
	public long deleteR2Affects() {
		return this.neo4j.query(DELETE_R2_AFFECTS).fetchAs(Long.class).one().orElse(0L);
	}

	@Override
	public long deleteTransitive() {
		return this.neo4j.query(DELETE_TRANSITIVE).fetchAs(Long.class).one().orElse(0L);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<InferenceAPI.TransitiveHit> readTransitive(Collection<String> purls) {
		return this.neo4j.query(READ_TRANSITIVE)
			.bindAll(Map.of("purls", List.copyOf(purls)))
			.fetch()
			.all()
			.stream()
			.map(row -> new InferenceAPI.TransitiveHit((String) row.get("purl"),
					((List<Map<String, Object>>) row.get("vulnerabilities")).stream()
						.map(v -> new InferenceAPI.TransitiveVuln((String) v.get("id"), (String) v.get("severity"),
								v.get("cvssScore") == null ? null : ((Number) v.get("cvssScore")).doubleValue(),
								(String) v.get("summary"), ((Number) v.get("depth")).intValue()))
						.toList()))
			.toList();
	}

	private long run(String cypher, Collection<String> sourcePurls) {
		Map<String, Object> params = new HashMap<>();
		params.put("sourcePurls", sourcePurls == null ? null : List.copyOf(sourcePurls));
		return this.neo4j.query(cypher).bindAll(params).fetchAs(Long.class).one().orElse(0L);
	}

}
