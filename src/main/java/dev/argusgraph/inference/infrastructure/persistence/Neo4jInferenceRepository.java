package dev.argusgraph.inference.infrastructure.persistence;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

	private static final String WRITE_R1_NATIVE = """
			MATCH (v:Vulnerability)-[:AFFECTS]->(target:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON*1..]->(target)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			WITH v, source, min(length(shortestPath((source)-[:DEPENDS_ON*1..]->(target)))) AS depth
			MERGE (v)-[t:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t.depth = depth, t.inferredBy = 'R1', t.ruleVersion = 2,
			                t.derivedAt = datetime(), t._new = true
			  ON MATCH  SET t.depth = CASE WHEN depth < t.depth THEN depth ELSE t.depth END
			WITH t WHERE coalesce(t._new, false) = true
			REMOVE t._new
			RETURN count(t) AS created
			""";

	private static final String WRITE_R1_BASE_FRONTIER = """
			MATCH (v:Vulnerability)-[:AFFECTS]->(target:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON]->(target)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			MERGE (v)-[t:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t.depth = 1, t.round = 0, t.inferredBy = 'R1', t.ruleVersion = 2,
			                t.derivedAt = datetime(), t._new = true
			WITH t WHERE coalesce(t._new, false) = true
			REMOVE t._new
			RETURN count(t) AS created
			""";

	private static final String WRITE_R1_STEP_DELTA = """
			MATCH (v:Vulnerability)-[t:TRANSITIVELY_AFFECTED {round: $prevRound}]->(mid:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON]->(mid)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			WITH v, source, min(t.depth) + 1 AS depth, $prevRound + 1 AS nextRound
			MERGE (v)-[t2:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t2.depth = depth, t2.round = nextRound, t2.inferredBy = 'R1',
			                t2.ruleVersion = 2, t2.derivedAt = datetime(), t2._new = true
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

	private static final String DERIVED_FILTER = """
			MATCH (v:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(pv:PackageVersion)
			WHERE ($q IS NULL OR toLower(v.id) CONTAINS $q OR toLower(pv.purl) CONTAINS $q
			       OR toLower(coalesce(v.summary,'')) CONTAINS $q)
			""";

	private static final String FIND_DERIVED = DERIVED_FILTER + """
			RETURN v.id AS vulnId, v.severity AS severity, v.cvssScore AS cvssScore, v.summary AS summary,
			       pv.purl AS exposedPurl, t.depth AS depth, t.inferredBy AS inferredBy
			ORDER BY t.depth ASC, v.id ASC, pv.purl ASC
			SKIP $skip LIMIT $limit
			""";

	private static final String COUNT_DERIVED = DERIVED_FILTER + """
			RETURN count(t) AS total
			""";

	private static final String EXPOSURE_CHAIN = """
			MATCH (v:Vulnerability {id: $vulnId})-[:AFFECTS]->(target:PackageVersion)
			MATCH p = shortestPath((source:PackageVersion {purl: $purl})-[:DEPENDS_ON*1..]->(target))
			RETURN [n IN nodes(p) | n.purl] AS path, target.purl AS affectedPurl
			ORDER BY length(p) ASC
			LIMIT 1
			""";

	private static final String IMPUTE_CANDIDATES = """
			MATCH (v:Vulnerability) WHERE v.embedding IS NOT NULL AND v.cvssScore IS NULL
			CALL (v) {
			    CALL db.index.vector.queryNodes('vulnerability_embedding', $overfetch, v.embedding)
			        YIELD node, score
			    WHERE node <> v AND node.cvssScore IS NOT NULL
			    RETURN node, score ORDER BY score DESC
			}
			WITH v, collect({score: score, cvss: node.cvssScore})[0..$k] AS neighbours
			WHERE size(neighbours) > 0
			RETURN v.id AS vulnId, neighbours
			""";

	private static final String EVAL_CANDIDATES = """
			MATCH (v:Vulnerability) WHERE v.embedding IS NOT NULL AND v.cvssScore IS NOT NULL
			CALL (v) {
			    CALL db.index.vector.queryNodes('vulnerability_embedding', $overfetch, v.embedding)
			        YIELD node, score
			    WHERE node <> v AND node.cvssScore IS NOT NULL
			    RETURN node, score ORDER BY score DESC
			}
			WITH v, collect({score: score, cvss: node.cvssScore})[0..$k] AS neighbours
			WHERE size(neighbours) > 0
			RETURN v.id AS vulnId, v.cvssScore AS actual, neighbours
			""";

	private static final String WRITE_PREDICTIONS = """
			UNWIND $preds AS p
			MATCH (v:Vulnerability {id: p.vulnId})
			SET v.predictedCvssScore = p.cvss, v.predictedSeverity = p.severity,
			    v.predictionConfidence = p.confidence, v.predictedBy = 'E1'
			RETURN count(v) AS written
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
	public long writeR1Native(Collection<String> sourcePurls) {
		return run(WRITE_R1_NATIVE, sourcePurls);
	}

	@Override
	public long writeR1BaseFrontier(Collection<String> sourcePurls) {
		return run(WRITE_R1_BASE_FRONTIER, sourcePurls);
	}

	@Override
	public long writeR1StepDelta(Collection<String> sourcePurls, int prevRound) {
		Map<String, Object> params = new HashMap<>();
		params.put("sourcePurls", sourcePurls == null ? null : List.copyOf(sourcePurls));
		params.put("prevRound", prevRound);
		return this.neo4j.query(WRITE_R1_STEP_DELTA).bindAll(params).fetchAs(Long.class).one().orElse(0L);
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

	@Override
	public InferenceAPI.DerivedPage findDerived(String q, int page, int size) {
		Map<String, Object> parameters = new HashMap<>();
		String filter = (q == null || q.isBlank()) ? null : q.toLowerCase(Locale.ROOT);
		parameters.put("q", filter);
		parameters.put("skip", (long) page * size);
		parameters.put("limit", (long) size);
		List<InferenceAPI.DerivedEdge> items = this.neo4j.query(FIND_DERIVED)
			.bindAll(parameters)
			.fetch()
			.all()
			.stream()
			.map(row -> new InferenceAPI.DerivedEdge((String) row.get("vulnId"), (String) row.get("severity"),
					toDouble(row.get("cvssScore")), (String) row.get("summary"), (String) row.get("exposedPurl"),
					((Number) row.get("depth")).intValue(), (String) row.get("inferredBy")))
			.toList();
		Map<String, Object> countParams = new HashMap<>();
		countParams.put("q", filter);
		long total = this.neo4j.query(COUNT_DERIVED)
			.bindAll(countParams)
			.fetchAs(Long.class)
			.one()
			.orElse(0L);
		return new InferenceAPI.DerivedPage(items, page, size, total);
	}

	@Override
	@SuppressWarnings("unchecked")
	public InferenceAPI.ExposureChain findChain(String vulnId, String exposedPurl) {
		return this.neo4j.query(EXPOSURE_CHAIN)
			.bindAll(Map.of("vulnId", vulnId, "purl", exposedPurl))
			.fetch()
			.one()
			.map(row -> new InferenceAPI.ExposureChain(vulnId, (List<String>) row.get("path"),
					(String) row.get("affectedPurl")))
			.orElseGet(() -> new InferenceAPI.ExposureChain(vulnId, List.of(), null));
	}

	private static Double toDouble(Object value) {
		return value == null ? null : ((Number) value).doubleValue();
	}

	private long run(String cypher, Collection<String> sourcePurls) {
		Map<String, Object> params = new HashMap<>();
		params.put("sourcePurls", sourcePurls == null ? null : List.copyOf(sourcePurls));
		return this.neo4j.query(cypher).bindAll(params).fetchAs(Long.class).one().orElse(0L);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<InferenceRepository.NeighbourSet> imputeCandidates(int overfetch, int k) {
		return this.neo4j.query(IMPUTE_CANDIDATES)
			.bindAll(Map.of("overfetch", overfetch, "k", k))
			.fetch()
			.all()
			.stream()
			.map(row -> new InferenceRepository.NeighbourSet((String) row.get("vulnId"),
					neighbours((List<Map<String, Object>>) row.get("neighbours"))))
			.toList();
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<InferenceRepository.EvalCandidate> evalCandidates(int overfetch, int k) {
		return this.neo4j.query(EVAL_CANDIDATES)
			.bindAll(Map.of("overfetch", overfetch, "k", k))
			.fetch()
			.all()
			.stream()
			.map(row -> new InferenceRepository.EvalCandidate((String) row.get("vulnId"),
					((Number) row.get("actual")).doubleValue(),
					neighbours((List<Map<String, Object>>) row.get("neighbours"))))
			.toList();
	}

	@Override
	public long writePredictions(List<InferenceRepository.Prediction> predictions) {
		List<Map<String, Object>> rows = predictions.stream()
			.map(p -> Map.<String, Object>of("vulnId", p.vulnId(), "cvss", p.cvss(),
					"severity", p.severity(), "confidence", p.confidence()))
			.toList();
		return this.neo4j.query(WRITE_PREDICTIONS).bindAll(Map.of("preds", rows)).fetchAs(Long.class).one().orElse(0L);
	}

	private static List<InferenceRepository.Neighbour> neighbours(List<Map<String, Object>> raw) {
		return raw.stream()
			.map(n -> new InferenceRepository.Neighbour(((Number) n.get("score")).doubleValue(),
					((Number) n.get("cvss")).doubleValue()))
			.toList();
	}

}
