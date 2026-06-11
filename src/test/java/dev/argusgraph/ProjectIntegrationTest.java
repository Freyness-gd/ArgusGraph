package dev.argusgraph;

import java.util.List;
import java.util.Map;

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
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Project module end to end: SBOM upload into H2, on-demand matching against a real
 * (Testcontainers) Neo4j, deletion. The H2 store is in-memory per test JVM; graph data
 * is scoped by unique marker purls/ids because the Neo4j container is shared.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class ProjectIntegrationTest {

	private static final String CRITICAL_VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	private static final String SBOM = """
			{"bomFormat":"CycloneDX","specVersion":"1.5",
			 "metadata":{"component":{"name":"demo-app"}},
			 "components":[
			   {"purl":"pkg:maven/dev.argusgraph.proj/web@1.0.0"},
			   {"purl":"pkg:npm/argus-proj-ghost@9.9.9"},
			   {"name":"component-without-purl"},
			   {"purl":"pkg:maven/dev.argusgraph.proj/versionless"}
			 ]}
			""";

	@Autowired
	private TestRestTemplate rest;

	@Test
	@SuppressWarnings("unchecked")
	void importsMatchesAndDeletesAProject() {
		// Graph side: web@1.0.0 exists and is hit by one CRITICAL advisory.
		assertThat(this.rest
			.postForEntity("/api/v1/ingest/package-versions",
					Map.of("purl", "pkg:maven/dev.argusgraph.proj/web@1.0.0"), Map.class)
			.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> osv = Map.of("id", "ARGUS-PROJ-1", "modified", "2026-01-01T00:00:00Z", "published",
				"2026-01-01T00:00:00Z", "summary", "project marker advisory", "severity",
				List.of(Map.of("type", "CVSS_V3", "score", CRITICAL_VECTOR)), "affected",
				List.of(Map.of("package",
						Map.of("ecosystem", "Maven", "name", "dev.argusgraph.proj:web", "purl",
								"pkg:maven/dev.argusgraph.proj/web"),
						"versions", List.of("1.0.0"))));
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);

		// Import: name from SBOM metadata; 2 usable purls, 2 skipped.
		ResponseEntity<Map> imported = this.rest.postForEntity("/api/v1/projects", sbomEntity(SBOM), Map.class);
		assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(imported.getBody()).containsEntry("name", "demo-app")
			.containsEntry("dependencies", 2)
			.containsEntry("skipped", 2);
		long id = ((Number) imported.getBody().get("id")).longValue();

		// List contains it.
		List<Map<String, Object>> projects = this.rest.getForObject("/api/v1/projects", List.class);
		assertThat(projects).anySatisfy(p -> {
			assertThat(p).containsEntry("name", "demo-app").containsEntry("dependencyCount", 2);
		});

		// Detail: AFFECTED first with the advisory, ghost purl UNKNOWN.
		Map<String, Object> detail = this.rest.getForObject("/api/v1/projects/" + id, Map.class);
		Map<String, Object> summary = (Map<String, Object>) detail.get("summary");
		assertThat(summary).containsEntry("dependencies", 2)
			.containsEntry("affected", 1)
			.containsEntry("clean", 0)
			.containsEntry("unknown", 1);
		Map<String, Number> bySeverity = (Map<String, Number>) summary.get("bySeverity");
		assertThat(bySeverity.get("CRITICAL").intValue()).isEqualTo(1);

		List<Map<String, Object>> deps = (List<Map<String, Object>>) detail.get("dependencies");
		assertThat(deps).hasSize(2);
		assertThat(deps.get(0)).containsEntry("purl", "pkg:maven/dev.argusgraph.proj/web@1.0.0")
			.containsEntry("verdict", "AFFECTED");
		List<Map<String, Object>> vulns = (List<Map<String, Object>>) deps.get(0).get("vulnerabilities");
		assertThat(vulns).singleElement()
			.satisfies(v -> assertThat(v).containsEntry("id", "ARGUS-PROJ-1").containsEntry("severity", "CRITICAL"));
		assertThat(deps.get(1)).containsEntry("purl", "pkg:npm/argus-proj-ghost@9.9.9")
			.containsEntry("verdict", "UNKNOWN");

		// Explicit name wins over SBOM metadata.
		ResponseEntity<Map> named = this.rest.postForEntity("/api/v1/projects?name=custom-name",
				sbomEntity(SBOM), Map.class);
		assertThat(named.getBody()).containsEntry("name", "custom-name");

		// Bad bodies → 409.
		assertThat(this.rest.postForEntity("/api/v1/projects", sbomEntity("not json"), Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CONFLICT);
		assertThat(this.rest.postForEntity("/api/v1/projects", sbomEntity("{}"), Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CONFLICT);

		// Delete → gone.
		this.rest.delete("/api/v1/projects/" + id);
		assertThat(this.rest.getForEntity("/api/v1/projects/" + id, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.NOT_FOUND);
	}

	private static HttpEntity<String> sbomEntity(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}

}
