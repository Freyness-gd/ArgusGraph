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

    @Test
    @SuppressWarnings("unchecked")
    void surfacesDepthTwoTransitiveChain() {
        // Graph: c@3.0.0 exists and is hit by one CRITICAL advisory.
        String marker = "dev.argusgraph.depth2";
        ingestCriticalAdvisory("ARGUS-DEPTH2-1", marker, "c", "3.0.0");

        // Import the SBOM: a@1.0.0 -DEPENDS_ON-> b@2.0.0 -DEPENDS_ON-> c@3.0.0.
        String sbom = """
                {"bomFormat":"CycloneDX","specVersion":"1.5",
                 "metadata":{"component":{"bom-ref":"root","name":"depth2-demo"}},
                 "components":[
                   {"bom-ref":"a","purl":"pkg:maven/%1$s/a@1.0.0"},
                   {"bom-ref":"b","purl":"pkg:maven/%1$s/b@2.0.0"},
                   {"bom-ref":"c","purl":"pkg:maven/%1$s/c@3.0.0"}
                 ],
                 "dependencies":[
                   {"ref":"root","dependsOn":["a"]},
                   {"ref":"a","dependsOn":["b"]},
                   {"ref":"b","dependsOn":["c"]}
                 ]}
                """.formatted(marker);
        long id = importProject(sbom);

        // R1 runs asynchronously after commit; poll the detail until both hops surface.
        Awaitility.await().atMost(ofSeconds(15)).untilAsserted(() -> {
            List<Map<String, Object>> deps = dependencies(id);

            // a@1.0.0 reaches the vuln through b: depth 2, verdict TRANSITIVELY_AFFECTED.
            Map<String, Object> a = dependency(deps, "pkg:maven/" + marker + "/a@1.0.0");
            List<Map<String, Object>> aTransitive = (List<Map<String, Object>>) a.get("transitive");
            assertThat(aTransitive).hasSize(1);
            assertThat(aTransitive.get(0)).containsEntry("id", "ARGUS-DEPTH2-1").containsEntry("depth", 2);
            assertThat(a.get("verdict")).isEqualTo("TRANSITIVELY_AFFECTED");

            // b@2.0.0 reaches the vuln directly: depth 1.
            Map<String, Object> b = dependency(deps, "pkg:maven/" + marker + "/b@2.0.0");
            List<Map<String, Object>> bTransitive = (List<Map<String, Object>>) b.get("transitive");
            assertThat(bTransitive).hasSize(1);
            assertThat(bTransitive.get(0)).containsEntry("id", "ARGUS-DEPTH2-1").containsEntry("depth", 1);
            assertThat(b.get("verdict")).isEqualTo("TRANSITIVELY_AFFECTED");
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void recomputeIsIdempotent() {
        // Graph + SBOM that produces transitive exposure: a@1.0.0 -DEPENDS_ON-> vuln@2.0.0.
        String marker = "dev.argusgraph.idem";
        ingestCriticalAdvisory("ARGUS-IDEM-1", marker, "vuln", "2.0.0");
        String sbom = """
                {"bomFormat":"CycloneDX","specVersion":"1.5",
                 "metadata":{"component":{"bom-ref":"root","name":"idem-demo"}},
                 "components":[
                   {"bom-ref":"a","purl":"pkg:maven/%1$s/a@1.0.0"},
                   {"bom-ref":"vuln","purl":"pkg:maven/%1$s/vuln@2.0.0"}
                 ],
                 "dependencies":[
                   {"ref":"root","dependsOn":["a"]},
                   {"ref":"a","dependsOn":["vuln"]}
                 ]}
                """.formatted(marker);
        long id = importProject(sbom);
        String directPurl = "pkg:maven/" + marker + "/a@1.0.0";

        // Wait for the import-driven derivation to land before recomputing.
        Awaitility.await()
            .atMost(ofSeconds(15))
            .untilAsserted(() -> assertThat(transitiveHits(id, directPurl)).isNotEmpty());

        recompute();
        List<Map<String, Object>> first = transitiveHits(id, directPurl);
        assertThat(first).hasSize(1);
        assertThat(first.get(0)).containsEntry("id", "ARGUS-IDEM-1").containsEntry("depth", 1);

        recompute();
        List<Map<String, Object>> second = transitiveHits(id, directPurl);

        // Identical hit list: same ids, same depths, same size — no duplicates from re-derivation.
        assertThat(second).hasSameSizeAs(first);
        assertThat(idDepthPairs(second)).isEqualTo(idDepthPairs(first));
    }

    @Test
    @SuppressWarnings("unchecked")
    void recomputeEndpointReportsEdgesWritten() {
        // Import a project with transitive exposure so the whole-graph recompute writes at least one edge.
        String marker = "dev.argusgraph.recompute";
        ingestCriticalAdvisory("ARGUS-RECOMP-1", marker, "vuln", "2.0.0");
        String sbom = """
                {"bomFormat":"CycloneDX","specVersion":"1.5",
                 "metadata":{"component":{"bom-ref":"root","name":"recompute-demo"}},
                 "components":[
                   {"bom-ref":"a","purl":"pkg:maven/%1$s/a@1.0.0"},
                   {"bom-ref":"vuln","purl":"pkg:maven/%1$s/vuln@2.0.0"}
                 ],
                 "dependencies":[
                   {"ref":"root","dependsOn":["a"]},
                   {"ref":"a","dependsOn":["vuln"]}
                 ]}
                """.formatted(marker);
        long id = importProject(sbom);
        Awaitility.await()
            .atMost(ofSeconds(15))
            .untilAsserted(() -> assertThat(transitiveHits(id, "pkg:maven/" + marker + "/a@1.0.0")).isNotEmpty());

        var response = this.rest.postForEntity("/api/v1/inference/recompute", null, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Number edgesWritten = (Number) response.getBody().get("edgesWritten");
        assertThat(edgesWritten).isNotNull();
        // A whole-graph recompute with at least one exposed project must write a positive edge count.
        assertThat(edgesWritten.longValue()).isGreaterThan(0L);
    }

    /**
     * Ingest a package version and a CRITICAL OSV advisory affecting it, mirroring the OSV
     * shape the scraper workers emit (purl without version + an enumerated affected version).
     */
    @SuppressWarnings("unchecked")
    private void ingestCriticalAdvisory(String advisoryId, String marker, String name, String version) {
        this.rest.postForEntity("/api/v1/ingest/package-versions",
                Map.of("purl", "pkg:maven/" + marker + "/" + name + "@" + version), Map.class);
        Map<String, Object> osv = Map.of("id", advisoryId, "modified", "2026-01-01T00:00:00Z", "published",
                "2026-01-01T00:00:00Z", "summary", "transitive marker advisory", "severity",
                List.of(Map.of("type", "CVSS_V3", "score", CRITICAL_VECTOR)), "affected",
                List.of(Map.of("package",
                        Map.of("ecosystem", "Maven", "name", marker + ":" + name, "purl",
                                "pkg:maven/" + marker + "/" + name),
                        "versions", List.of(version))));
        assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
            .isEqualTo(HttpStatus.CREATED);
    }

    @SuppressWarnings("unchecked")
    private long importProject(String sbom) {
        var imported = this.rest.postForEntity("/api/v1/projects", sbomEntity(sbom), Map.class);
        assertThat(imported.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) imported.getBody().get("id")).longValue();
    }

    private void recompute() {
        var response = this.rest.postForEntity("/api/v1/inference/recompute", null, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> dependencies(long id) {
        Map<String, Object> body = this.rest.getForObject("/api/v1/projects/" + id, Map.class);
        return (List<Map<String, Object>>) body.get("dependencies");
    }

    private Map<String, Object> dependency(List<Map<String, Object>> deps, String purl) {
        return deps.stream().filter(d -> purl.equals(d.get("purl"))).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> transitiveHits(long id, String purl) {
        return (List<Map<String, Object>>) dependency(dependencies(id), purl).get("transitive");
    }

    private List<Map<String, Object>> idDepthPairs(List<Map<String, Object>> hits) {
        return hits.stream().map(h -> Map.of("id", h.get("id"), "depth", h.get("depth"))).toList();
    }

    private HttpEntity<String> sbomEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

}
