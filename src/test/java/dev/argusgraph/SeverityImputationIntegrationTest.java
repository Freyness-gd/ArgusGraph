package dev.argusgraph;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;

import dev.argusgraph.graph.GraphAPI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4.4: the latent engine imputes a CRITICAL severity for an unscored advisory embedded
 * inside a CRITICAL cluster, leaves real scores untouched, and reports a sane leave-one-out
 * accuracy. Embeddings are seeded deterministically via attachEmbedding so k-NN is controlled.
 *
 * <p>
 * Geometry note: the Neo4j cosine vector index floors a dissimilar neighbour's score at 0.5
 * (it clamps negative cosine to zero before the {@code (1+cos)/2} mapping), so a far cluster
 * still carries weight 0.5 in the similarity-weighted mean. A large CRITICAL cluster (five
 * scored vulns) versus a single distant LOW vuln keeps the CRITICAL mass dominant enough for
 * the imputation to land CRITICAL and for leave-one-out to classify the cluster correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SeverityImputationIntegrationTest {

	private static final String CRIT = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H"; // 10.0
	private static final String LOW = "CVSS:3.1/AV:N/AC:H/PR:H/UI:R/S:U/C:L/I:N/A:N"; // 2.0

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private GraphAPI graph;

	@Autowired
	private Driver driver;

	// The shared Testcontainers Neo4j is cached across integration test classes, so clear any
	// vulnerabilities other tests left behind. This makes the leave-one-out count exact: the six
	// scored + one unscored vulns this test seeds are the only embedded vulns eval-severity sees.
	@BeforeEach
	void clearVulnerabilities() {
		try (var session = this.driver.session()) {
			session.run("MATCH (v:Vulnerability) DETACH DELETE v").consume();
		}
	}

	@Test
	void imputesCriticalForUnscoredVulnNearCriticalClusterAndScoresAccuracy() {
		// CRITICAL cluster (scored), embeddings all on axis 0 — mutually similar (cosine 1).
		for (int i = 1; i <= 5; i++) {
			ingestScored("ARGUS-E1-CRIT-" + i, CRIT);
			this.graph.attachEmbedding("ARGUS-E1-CRIT-" + i, axis(0, 1.0f - i * 0.001f));
		}
		// One LOW-scored vuln on a different axis — dissimilar to the CRITICAL cluster.
		ingestScored("ARGUS-E1-LOW-1", LOW);
		// axis 100: orthogonal to the CRITICAL cluster at axis 0.
		this.graph.attachEmbedding("ARGUS-E1-LOW-1", axis(100, 1.0f));
		// Unscored vuln embedded inside the CRITICAL cluster.
		ingestUnscored("ARGUS-E1-UNK");
		this.graph.attachEmbedding("ARGUS-E1-UNK", axis(0, 0.97f));

		// Precondition: the unscored vuln must carry no real score so it is an impute candidate.
		assertThat(readVuln("ARGUS-E1-UNK").get("cvssScore")).isNull();

		// Impute: the unscored vuln, surrounded by the CRITICAL cluster, gets a CRITICAL band.
		assertThat(this.rest.postForEntity("/api/v1/inference/impute-severity", null, Map.class)
			.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> predicted = readVuln("ARGUS-E1-UNK");
		assertThat(predicted.get("predictedSeverity")).isEqualTo("CRITICAL");
		assertThat(predicted.get("predictedBy")).isEqualTo("E1");
		assertThat(((Number) predicted.get("predictedCvssScore")).doubleValue()).isGreaterThanOrEqualTo(9.0);

		// Real scores untouched: a scored vuln keeps its severity and gets no predicted* props.
		Map<String, Object> realCrit = readVuln("ARGUS-E1-CRIT-1");
		assertThat(realCrit.get("severity")).isEqualTo("CRITICAL");
		assertThat(realCrit.get("predictedSeverity")).isNull();

		// Leave-one-out accuracy over the six scored vulns (five CRITICAL, one LOW).
		Map<String, Object> eval = this.rest.postForEntity("/api/v1/inference/eval-severity", null, Map.class)
			.getBody();
		assertThat(((Number) eval.get("n")).longValue()).isEqualTo(6);
		assertThat(((Number) eval.get("labelAccuracy")).doubleValue()).isGreaterThan(0.5);
		// worst case: the lone LOW (~2.0) predicted ~10 by its CRITICAL neighbours → MAE ≈ 8/6 ≈ 1.3
		assertThat(((Number) eval.get("mae")).doubleValue()).isLessThan(3.0);
	}

	@SuppressWarnings("unchecked")
	private void ingestScored(String id, String vector) {
		Map<String, Object> osv = Map.of("id", id, "modified", "2026-01-01T00:00:00Z",
				"published", "2026-01-01T00:00:00Z", "summary", id,
				"severity", List.of(Map.of("type", "CVSS_V3", "score", vector)),
				"affected", List.of());
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
	}

	@SuppressWarnings("unchecked")
	private void ingestUnscored(String id) {
		Map<String, Object> osv = Map.of("id", id, "modified", "2026-01-01T00:00:00Z",
				"published", "2026-01-01T00:00:00Z", "summary", id, "affected", List.of());
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
	}

	/** A 384-dim vector with a single non-zero axis. */
	private static float[] axis(int index, float value) {
		float[] v = new float[384];
		v[index] = value;
		return v;
	}

	private Map<String, Object> readVuln(String id) {
		try (var session = this.driver.session()) {
			return session.run("MATCH (v:Vulnerability {id: $id}) "
					+ "RETURN v.severity AS severity, v.cvssScore AS cvssScore, "
					+ "v.predictedSeverity AS predictedSeverity, "
					+ "v.predictedCvssScore AS predictedCvssScore, v.predictedBy AS predictedBy",
					Map.of("id", id)).single().asMap();
		}
	}

}
