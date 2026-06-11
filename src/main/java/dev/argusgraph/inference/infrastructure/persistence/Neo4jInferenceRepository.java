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
 * authoritative {@code AFFECTS}/{@code DEPENDS_ON} edges and owns the derived
 * {@code TRANSITIVELY_AFFECTED} edge, separable by its {@code inferredBy} provenance.
 */
@Component
@RequiredArgsConstructor
class Neo4jInferenceRepository implements InferenceRepository {

	// Scoped run binds $sourcePurls; full run passes null and the WHERE clause is skipped.
	private static final String WRITE_R1 = """
			MATCH (v:Vulnerability)-[:AFFECTS]->(target:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON*1..]->(target)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			WITH v, source, min(length(shortestPath((source)-[:DEPENDS_ON*1..]->(target)))) AS depth
			MERGE (v)-[t:TRANSITIVELY_AFFECTED]->(source)
			  SET t.depth = depth, t.inferredBy = 'R1', t.ruleVersion = $ruleVersion,
				  t.derivedAt = datetime()
			RETURN count(t) AS written
			""";

	private static final String DELETE_R1 = """
			MATCH (:Vulnerability)-[t:TRANSITIVELY_AFFECTED {inferredBy: 'R1'}]->(:PackageVersion)
			DELETE t
			RETURN count(t) AS deleted
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
	public long writeR1Transitive(Collection<String> sourcePurls, int ruleVersion) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("sourcePurls", sourcePurls == null ? null : List.copyOf(sourcePurls));
		parameters.put("ruleVersion", ruleVersion);
		return this.neo4j.query(WRITE_R1).bindAll(parameters).fetchAs(Long.class).one().orElse(0L);
	}

	@Override
	public long deleteR1() {
		return this.neo4j.query(DELETE_R1).fetchAs(Long.class).one().orElse(0L);
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

}
