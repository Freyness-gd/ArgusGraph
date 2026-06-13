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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P5-T1: the neighbourhood read endpoint against a real (Testcontainers) Neo4j. Seeds a
 * depth-1 exposure mirroring {@code RulePipelineIntegrationTest.seedExposure()}: a CRITICAL
 * RANGE advisory over package B (so R2 is the sole AFFECTS materialiser) plus an SBOM where
 * A DEPENDS_ON B. After a synchronous whole-graph {@code run-rules}, B is directly affected
 * and A is transitively affected, so the endpoint can be asserted from both ends.
 *
 * <p>
 * The shared Testcontainers Neo4j is cached across integration test classes and the rule
 * pipeline reasons over the whole graph, so {@code @BeforeEach} clears the seeded node labels
 * to keep the run-rules derivation deterministic regardless of suite order.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class NeighbourhoodIntegrationTest {

	private static final String CRITICAL_VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	private static final String MARKER = "dev.argusgraph.neighbourhood";

	private static final String VULN_ID = "ARGUS-NEIGHBOURHOOD-1";

	private static final String PURL_A = "pkg:maven/" + MARKER + "/a@1.0.0";

	private static final String PURL_B = "pkg:maven/" + MARKER + "/b@2.0.0";

	private static final String SBOM = """
			{"bomFormat":"CycloneDX","specVersion":"1.5",
			 "metadata":{"component":{"bom-ref":"root","name":"neighbourhood-demo"}},
			 "components":[
			   {"bom-ref":"a","purl":"pkg:maven/%1$s/a@1.0.0"},
			   {"bom-ref":"b","purl":"pkg:maven/%1$s/b@2.0.0"}
			 ],
			 "dependencies":[
			   {"ref":"root","dependsOn":["a"]},
			   {"ref":"a","dependsOn":["b"]}
			 ]}
			""".formatted(MARKER);

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private Driver driver;

	@BeforeEach
	void resetGraph() {
		try (var session = this.driver.session()) {
			session.run("MATCH (n) WHERE n:Vulnerability OR n:PackageVersion OR n:Package OR n:Project "
					+ "DETACH DELETE n").consume();
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void neighbourhoodReadsBothEndsOfTheExposure() {
		seedExposure();
		// Derive AFFECTS (R2) and the transitive hop (R1) over the whole graph.
		assertThat(this.rest.exchange("/api/v1/inference/run-rules", HttpMethod.POST, null,
				new ParameterizedTypeReference<Map<String, Object>>() {
				}).getStatusCode()).isEqualTo(HttpStatus.OK);

		// B is the directly-affected version: it has A as a dependent and the seeded vuln among its vulns.
		Map<String, Object> b = neighbourhood(PURL_B);
		assertThat(b.get("center")).isEqualTo(PURL_B);
		assertThat((List<String>) b.get("dependents")).contains(PURL_A);
		assertThat(((List<Map<String, Object>>) b.get("vulnerabilities"))).extracting(v -> v.get("id"))
			.contains(VULN_ID);

		// A depends on B and is transitively reached by the vuln at depth >= 1.
		Map<String, Object> a = neighbourhood(PURL_A);
		assertThat(a.get("center")).isEqualTo(PURL_A);
		assertThat((List<String>) a.get("dependencies")).contains(PURL_B);
		List<Map<String, Object>> transitive = (List<Map<String, Object>>) a.get("transitive");
		Map<String, Object> hit = transitive.stream()
			.filter(t -> VULN_ID.equals(t.get("id")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Seeded vuln " + VULN_ID + " not in A's transitive vulns"));
		assertThat(((Number) hit.get("depth")).intValue()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void unknownPurlIs404() {
		ResponseEntity<Map<String, Object>> response = this.rest.exchange(
				"/api/v1/graph/neighbourhood?purl=pkg:maven/" + MARKER + "/ghost@9.9.9", HttpMethod.GET, null,
				new ParameterizedTypeReference<Map<String, Object>>() {
				});
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private Map<String, Object> neighbourhood(String purl) {
		ResponseEntity<Map<String, Object>> response = this.rest.exchange(
				"/api/v1/graph/neighbourhood?purl=" + purl, HttpMethod.GET, null,
				new ParameterizedTypeReference<Map<String, Object>>() {
				});
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).isNotNull();
		return response.getBody();
	}

	// --- seeding (mirrors RulePipelineIntegrationTest.seedExposure) --------------------------

	@SuppressWarnings("unchecked")
	private void seedExposure() {
		// b@2.0.0 exists and is hit by one CRITICAL advisory carrying a RANGE (introduced 0, fixed
		// 3.0.0) and no enumerated versions: a range makes R2 the sole materialiser of AFFECTS for
		// b@2.0.0, so the direct AFFECTS edge appears only after the whole-graph run-rules below.
		this.rest.postForEntity("/api/v1/ingest/package-versions", Map.of("purl", PURL_B), Map.class);
		Map<String, Object> osv = Map.of("id", VULN_ID, "modified", "2026-01-01T00:00:00Z", "published",
				"2026-01-01T00:00:00Z", "summary", "neighbourhood marker advisory", "severity",
				List.of(Map.of("type", "CVSS_V3", "score", CRITICAL_VECTOR)), "affected",
				List.of(Map.of("package",
						Map.of("ecosystem", "Maven", "name", MARKER + ":b", "purl", "pkg:maven/" + MARKER + "/b"),
						"ranges", List.of(Map.of("type", "ECOSYSTEM", "events",
								List.of(Map.of("introduced", "0"), Map.of("fixed", "3.0.0")))))));
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);

		// Import the SBOM: a@1.0.0 -DEPENDS_ON-> b@2.0.0.
		assertThat(this.rest.postForEntity("/api/v1/projects", sbomEntity(SBOM), Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
	}

	private HttpEntity<String> sbomEntity(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}

}
