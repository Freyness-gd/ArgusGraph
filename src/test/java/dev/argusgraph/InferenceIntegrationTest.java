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
 * Inference slice 1 end to end: a vulnerable transitive package, an SBOM whose direct
 * dependency reaches it through DEPENDS_ON, and the resulting TRANSITIVELY_AFFECTED
 * exposure surfaced on the project detail. Marker purls keep the shared Neo4j container
 * isolated across tests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class InferenceIntegrationTest {

    private static final String CRITICAL_VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

    private static final String SBOM = """
            {"bomFormat":"CycloneDX","specVersion":"1.5",
             "metadata":{"component":{"bom-ref":"root","name":"infer-demo"}},
             "components":[
               {"bom-ref":"a","purl":"pkg:maven/dev.argusgraph.infer/a@1.0.0"},
               {"bom-ref":"vuln","purl":"pkg:maven/dev.argusgraph.infer/vuln@2.0.0"}
             ],
             "dependencies":[
               {"ref":"root","dependsOn":["a"]},
               {"ref":"a","dependsOn":["vuln"]}
             ]}
            """;

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("unchecked")
    void surfacesTransitiveExposureAfterImport() {
        // Graph: vuln@2.0.0 exists and is hit by one CRITICAL advisory.
        this.rest.postForEntity("/api/v1/ingest/package-versions",
                Map.of("purl", "pkg:maven/dev.argusgraph.infer/vuln@2.0.0"), Map.class);
        Map<String, Object> osv = Map.of("id", "ARGUS-INFER-1", "modified", "2026-01-01T00:00:00Z", "published",
                "2026-01-01T00:00:00Z", "summary", "transitive marker advisory", "severity",
                List.of(Map.of("type", "CVSS_V3", "score", CRITICAL_VECTOR)), "affected",
                List.of(Map.of("package",
                        Map.of("ecosystem", "Maven", "name", "dev.argusgraph.infer:vuln", "purl",
                                "pkg:maven/dev.argusgraph.infer/vuln"),
                        "versions", List.of("2.0.0"))));
        assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);

        // Import the SBOM: a@1.0.0 -DEPENDS_ON-> vuln@2.0.0.
        var imported = this.rest.postForEntity("/api/v1/projects", sbomEntity(SBOM), Map.class);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = ((Number) imported.getBody().get("id")).longValue();

        // R1 runs asynchronously after commit; poll the detail until a@1.0.0 shows transitive exposure.
        Awaitility.await().atMost(ofSeconds(15)).untilAsserted(() -> {
            Map<String, Object> body = this.rest.getForObject("/api/v1/projects/" + id, Map.class);
            List<Map<String, Object>> deps = (List<Map<String, Object>>) body.get("dependencies");
            Map<String, Object> a = deps.stream()
                .filter(d -> "pkg:maven/dev.argusgraph.infer/a@1.0.0".equals(d.get("purl")))
                .findFirst()
                .orElseThrow();
            List<Map<String, Object>> transitive = (List<Map<String, Object>>) a.get("transitive");
            assertThat(transitive).isNotEmpty();
            assertThat(transitive.get(0)).containsEntry("id", "ARGUS-INFER-1").containsEntry("depth", 1);
            assertThat(a.get("verdict")).isEqualTo("TRANSITIVELY_AFFECTED");
        });
    }

    private HttpEntity<String> sbomEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

}
