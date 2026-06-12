package dev.argusgraph;

import java.util.List;
import java.util.Map;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4.2: recursive R1 driven to fixpoint, and R2 range resolution feeding R1. An advisory
 * that gives only a Maven {@code ECOSYSTEM} range — no enumerated version — still exposes a
 * transitive dependent at depth 2 (a → b → c) after recompute. Reaching depth 2 proves the
 * engine iterated the recursive R1-step stratum past its base round (R2 ▶ R1-base ▶ R1-step).
 * Marker purls isolate the shared Neo4j container.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class InferenceEngineIntegrationTest {

	private static final String VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	@Autowired
	private TestRestTemplate rest;

	@Test
	@SuppressWarnings("unchecked")
	void resolvesMavenRangeThenExposesTransitiveDependent() {
		// A -> B -> C(vuln@1.5.0). Advisory gives only an ECOSYSTEM (Maven) RANGE [1.0,2.0), no enumerated version.
		ingestVersion("pkg:maven/dev.argusgraph.r2/c@1.5.0");
		Map<String, Object> osv = Map.of("id", "ARGUS-R2-1", "modified", "2026-01-01T00:00:00Z",
				"published", "2026-01-01T00:00:00Z", "summary", "range-only advisory",
				"severity", List.of(Map.of("type", "CVSS_V3", "score", VECTOR)),
				"affected", List.of(Map.of(
						"package", Map.of("ecosystem", "Maven", "name", "dev.argusgraph.r2:c",
								"purl", "pkg:maven/dev.argusgraph.r2/c"),
						"ranges", List.of(Map.of("type", "ECOSYSTEM",
								"events", List.of(Map.of("introduced", "1.0"), Map.of("fixed", "2.0")))))));
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
				.isEqualTo(HttpStatus.CREATED);

		// Import SBOM: a -> b -> c@1.5.0.
		String sbom = """
				{"bomFormat":"CycloneDX","specVersion":"1.5",
				 "metadata":{"component":{"bom-ref":"root","name":"r2-demo"}},
				 "components":[
				   {"bom-ref":"a","purl":"pkg:maven/dev.argusgraph.r2/a@1.0.0"},
				   {"bom-ref":"b","purl":"pkg:maven/dev.argusgraph.r2/b@1.0.0"},
				   {"bom-ref":"c","purl":"pkg:maven/dev.argusgraph.r2/c@1.5.0"}
				 ],
				 "dependencies":[
				   {"ref":"root","dependsOn":["a"]},
				   {"ref":"a","dependsOn":["b"]},
				   {"ref":"b","dependsOn":["c"]}
				 ]}
				""";
		long id = ((Number) this.rest.postForEntity("/api/v1/projects", sbomEntity(sbom), Map.class)
				.getBody().get("id")).longValue();

		// Recompute runs R2 (range -> AFFECTS c@1.5.0) then R1 (transitive). Poll the detail.
		Awaitility.await().atMost(ofSeconds(20)).untilAsserted(() -> {
			this.rest.postForEntity("/api/v1/inference/recompute", null, Map.class);
			Map<String, Object> body = this.rest.getForObject("/api/v1/projects/" + id, Map.class);
			List<Map<String, Object>> deps = (List<Map<String, Object>>) body.get("dependencies");

			// b -> c directly: depth 1 (R1-base off the R2-derived AFFECTS).
			Map<String, Object> b = deps.stream()
					.filter(d -> "pkg:maven/dev.argusgraph.r2/b@1.0.0".equals(d.get("purl")))
					.findFirst().orElseThrow();
			List<Map<String, Object>> bTransitive = (List<Map<String, Object>>) b.get("transitive");
			assertThat(bTransitive).anySatisfy(v -> {
				assertThat(v).containsEntry("id", "ARGUS-R2-1");
				assertThat(((Number) v.get("depth")).intValue()).isEqualTo(1);
			});

			// a -> b -> c: depth 2 (R1-step recursive round). Proves iteration past the base round.
			Map<String, Object> a = deps.stream()
					.filter(d -> "pkg:maven/dev.argusgraph.r2/a@1.0.0".equals(d.get("purl")))
					.findFirst().orElseThrow();
			List<Map<String, Object>> aTransitive = (List<Map<String, Object>>) a.get("transitive");
			assertThat(aTransitive).anySatisfy(v -> {
				assertThat(v).containsEntry("id", "ARGUS-R2-1");
				assertThat(((Number) v.get("depth")).intValue()).isEqualTo(2); // a -> b -> c
			});
		});
	}

	private void ingestVersion(String purl) {
		this.rest.postForEntity("/api/v1/ingest/package-versions", Map.of("purl", purl), Map.class);
	}

	private HttpEntity<String> sbomEntity(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}

}
