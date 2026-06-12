package dev.argusgraph;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 4.3: the three engines (naive / semi-naive / native) compute the IDENTICAL transitive
 * exposure on the same graph, and report distinguishing metrics (native does 0 app-side rounds).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class EngineComparisonIntegrationTest {

	private static final String VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	@Autowired
	private TestRestTemplate rest;

	@Test
	@SuppressWarnings("unchecked")
	void allEnginesProduceIdenticalExposureWithDistinctMetrics() {
		// Graph: d@1.0.0 affected; chain a -> b -> c -> d (depth 3 from a).
		this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", "pkg:maven/dev.argusgraph.eng/d@1.0.0"), Map.class);
		Map<String, Object> osv = Map.of("id", "ARGUS-ENG-1", "modified", "2026-01-01T00:00:00Z",
				"published", "2026-01-01T00:00:00Z", "summary", "engine cmp",
				"severity", List.of(Map.of("type", "CVSS_V3", "score", VECTOR)),
				"affected", List.of(Map.of(
						"package", Map.of("ecosystem", "Maven", "name", "dev.argusgraph.eng:d",
								"purl", "pkg:maven/dev.argusgraph.eng/d"),
						"versions", List.of("1.0.0"))));
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
		String sbom = """
				{"bomFormat":"CycloneDX","specVersion":"1.5",
				 "metadata":{"component":{"bom-ref":"root","name":"eng-demo"}},
				 "components":[
				   {"bom-ref":"a","purl":"pkg:maven/dev.argusgraph.eng/a@1.0.0"},
				   {"bom-ref":"b","purl":"pkg:maven/dev.argusgraph.eng/b@1.0.0"},
				   {"bom-ref":"c","purl":"pkg:maven/dev.argusgraph.eng/c@1.0.0"},
				   {"bom-ref":"d","purl":"pkg:maven/dev.argusgraph.eng/d@1.0.0"}
				 ],
				 "dependencies":[
				   {"ref":"root","dependsOn":["a"]},{"ref":"a","dependsOn":["b"]},
				   {"ref":"b","dependsOn":["c"]},{"ref":"c","dependsOn":["d"]}
				 ]}
				""";
		long id = ((Number) this.rest.postForEntity("/api/v1/projects", sbomEntity(sbom), Map.class)
				.getBody().get("id")).longValue();

		Map<String, Integer> naive = exposureAfter("naive", id);
		Map<String, Integer> semi = exposureAfter("semi-naive", id);
		Map<String, Integer> nativ = exposureAfter("native", id);

		// Equivalence: identical (dependency-purl -> min depth) for ARGUS-ENG-1 across engines.
		assertThat(naive).containsEntry("pkg:maven/dev.argusgraph.eng/a@1.0.0", 3)
			.containsEntry("pkg:maven/dev.argusgraph.eng/b@1.0.0", 2)
			.containsEntry("pkg:maven/dev.argusgraph.eng/c@1.0.0", 1);
		assertThat(semi).isEqualTo(naive);
		assertThat(nativ).isEqualTo(naive);

		// Metrics: native does 0 app-side rounds; naive iterates.
		List<Map<String, Object>> runs = this.rest.getForObject("/api/v1/inference/runs", List.class);
		Map<String, Object> latestNative = runs.stream()
			.filter(r -> "native".equals(r.get("engine"))).findFirst().orElseThrow();
		assertThat(((Number) latestNative.get("rounds")).intValue()).isZero();
		Map<String, Object> latestNaive = runs.stream()
			.filter(r -> "naive".equals(r.get("engine"))).findFirst().orElseThrow();
		assertThat(((Number) latestNaive.get("rounds")).intValue()).isGreaterThan(0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Integer> exposureAfter(String engine, long projectId) {
		this.rest.postForEntity("/api/v1/inference/recompute?engine=" + engine, null, Map.class);
		Map<String, Object> body = this.rest.getForObject("/api/v1/projects/" + projectId, Map.class);
		List<Map<String, Object>> deps = (List<Map<String, Object>>) body.get("dependencies");
		return deps.stream()
			.flatMap(d -> ((List<Map<String, Object>>) d.get("transitive")).stream()
					.filter(v -> "ARGUS-ENG-1".equals(v.get("id")))
					.map(v -> Map.entry((String) d.get("purl"), ((Number) v.get("depth")).intValue())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Math::min));
	}

	private HttpEntity<String> sbomEntity(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}

}
