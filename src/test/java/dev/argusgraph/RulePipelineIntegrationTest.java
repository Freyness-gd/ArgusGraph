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
 * Slice 4 R-Task 4: the pluggable rule pipeline end to end. Lists the default pipeline,
 * proves an all-enabled {@code run-rules} derives transitive exposure, that disabling R2
 * (the AFFECTS materialiser) zeroes that exposure out, that a reorder persists, and that a
 * non-permutation order is rejected with 409 CONFLICT.
 *
 * <p>
 * The pipeline reasons over the whole graph, so the test is made hermetic: {@code @BeforeEach}
 * clears the seeded node labels (the shared Testcontainers Neo4j is cached across integration
 * test classes) and restores the default rule order/enablement, so counts are deterministic
 * regardless of suite order. Seeding mirrors {@code InferenceIntegrationTest}'s depth-1 shape:
 * a CRITICAL OSV advisory over a version range (introduced 0, fixed 3.0.0) so R2 is the sole
 * AFFECTS writer, plus an SBOM whose direct dependency reaches the vulnerable package-version
 * through DEPENDS_ON.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class RulePipelineIntegrationTest {

	private static final String CRITICAL_VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	private static final String MARKER = "dev.argusgraph.rulepipe";

	private static final String SBOM = """
			{"bomFormat":"CycloneDX","specVersion":"1.5",
			 "metadata":{"component":{"bom-ref":"root","name":"rulepipe-demo"}},
			 "components":[
			   {"bom-ref":"a","purl":"pkg:maven/%1$s/a@1.0.0"},
			   {"bom-ref":"vuln","purl":"pkg:maven/%1$s/vuln@2.0.0"}
			 ],
			 "dependencies":[
			   {"ref":"root","dependsOn":["a"]},
			   {"ref":"a","dependsOn":["vuln"]}
			 ]}
			""".formatted(MARKER);

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private Driver driver;

	// The shared Testcontainers Neo4j is cached across integration test classes and the rule
	// pipeline reasons over the whole graph, so clear the seeded node labels (which drops their
	// derived edges with them) and restore the default rule order/enablement. This makes the
	// transitive-exposure counts this test asserts on deterministic regardless of suite order.
	@BeforeEach
	void resetGraphAndPipeline() {
		try (var session = this.driver.session()) {
			session.run("MATCH (n) WHERE n:Vulnerability OR n:PackageVersion OR n:Package OR n:Project "
					+ "DETACH DELETE n").consume();
		}
		// Default order is [R2, R1-base, R1-step]; restore it and re-enable every rule in case a
		// prior test in this class left the in-memory registry mutated.
		this.rest.exchange("/api/v1/inference/rules/order", HttpMethod.POST,
				new HttpEntity<>(List.of("R2", "R1-base", "R1-step")), ruleListType());
		for (String name : List.of("R2", "R1-base", "R1-step")) {
			this.rest.postForEntity("/api/v1/inference/rules/" + name + "/enabled?enabled=true", null, Object.class);
		}
	}

	@Test
	void transitiveEndpointReturnsExposureForDependentPurl() {
		seedExposure();

		// Derive transitive edges first.
		assertThat(runRules().getStatusCode()).isEqualTo(HttpStatus.OK);

		// a@1.0.0 depends on vuln@2.0.0 — it is the transitively-exposed package-version (depth≥1).
		String dependentPurl = "pkg:maven/" + MARKER + "/a@1.0.0";
		ResponseEntity<List<Map<String, Object>>> response = this.rest.exchange(
				"/api/v1/inference/transitive?purls=" + dependentPurl, HttpMethod.GET, null,
				new ParameterizedTypeReference<List<Map<String, Object>>>() {
				});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> hits = response.getBody();
		assertThat(hits).isNotNull().isNotEmpty();

		// The hit for the dependent purl must contain the seeded advisory.
		@SuppressWarnings("unchecked")
		Map<String, Object> hit = hits.stream()
			.filter(h -> dependentPurl.equals(h.get("purl")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("No hit for purl: " + dependentPurl));

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> vulns = (List<Map<String, Object>>) hit.get("vulnerabilities");
		assertThat(vulns).isNotEmpty();

		Map<String, Object> vuln = vulns.stream()
			.filter(v -> "ARGUS-RULEPIPE-1".equals(v.get("id")))
			.findFirst()
			.orElseThrow(() -> new AssertionError("Seeded vuln ARGUS-RULEPIPE-1 not in hits"));

		assertThat(vuln.get("severity")).isNotNull();
		assertThat(((Number) vuln.get("depth")).intValue()).isGreaterThanOrEqualTo(1);
	}

	@Test
	void listsDefaultPipelineInExecutionOrderAllEnabled() {
		List<Map<String, Object>> rules = rules();

		assertThat(rules).hasSize(3);
		assertThat(rules).extracting(r -> r.get("name")).containsExactly("R2", "R1-base", "R1-step");
		assertThat(rules).allSatisfy(r -> assertThat(r.get("enabled")).isEqualTo(true));
	}

	@Test
	void allEnabledRunDerivesTransitiveExposure() {
		seedExposure();

		ResponseEntity<Map<String, Object>> run = runRules();
		assertThat(run.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(run.getBody()).containsEntry("engine", "rules");

		// R2 materialised AFFECTS, R1 derived the transitive hop: exposure now exists.
		assertThat(transitiveCount()).isGreaterThan(0L);
	}

	@Test
	void disablingR2RemovesTransitiveExposure() {
		seedExposure();

		// Sanity: with the full pipeline, exposure is present.
		assertThat(runRules().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(transitiveCount()).isGreaterThan(0L);

		// Disable R2 (the AFFECTS materialiser) — the returned listing must reflect it.
		List<Map<String, Object>> afterDisable = setRuleEnabled("R2", false);
		assertThat(rule(afterDisable, "R2")).containsEntry("enabled", false);

		// run-rules deletes derived + R2 AFFECTS, then runs only the enabled rules. With no R2
		// there is no AFFECTS, so R1 derives nothing: exposure must be empty.
		assertThat(runRules().getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(transitiveCount()).isZero();

		// Re-enable R2 to restore the default pipeline for any following test.
		List<Map<String, Object>> afterEnable = setRuleEnabled("R2", true);
		assertThat(rule(afterEnable, "R2")).containsEntry("enabled", true);
	}

	@Test
	void reorderPersistsAcrossReads() {
		List<String> target = List.of("R1-step", "R1-base", "R2");

		List<Map<String, Object>> reordered = this.rest
			.exchange("/api/v1/inference/rules/order", HttpMethod.POST, new HttpEntity<>(target), ruleListType())
			.getBody();
		assertThat(reordered).extracting(r -> r.get("name")).containsExactlyElementsOf(target);

		// A follow-up GET confirms the new order is persisted in the registry, not just echoed.
		assertThat(rules()).extracting(r -> r.get("name")).containsExactlyElementsOf(target);
	}

	@Test
	void nonPermutationOrderIsRejectedWithConflict() {
		// ["R2"] drops two rules, so it is not a permutation of the current names: the registry
		// throws BusinessRuleException, mapped by GlobalExceptionHandler to 409 CONFLICT.
		ResponseEntity<Map<String, Object>> response = this.rest.exchange("/api/v1/inference/rules/order",
				HttpMethod.POST, new HttpEntity<>(List.of("R2")),
				new ParameterizedTypeReference<Map<String, Object>>() {
				});

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
	}

	// --- seeding (mirrors InferenceIntegrationTest's depth-1 shape) --------------------------

	@SuppressWarnings("unchecked")
	private void seedExposure() {
		// vuln@2.0.0 exists and is hit by one CRITICAL advisory. The advisory carries a RANGE
		// (introduced 0, fixed 3.0.0) and no enumerated versions on purpose: enumerated versions
		// would write a direct, R2-independent AFFECTS edge at ingest time, but a range makes R2
		// the *sole* materialiser of AFFECTS for vuln@2.0.0 — so disabling R2 truly removes it.
		this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", "pkg:maven/" + MARKER + "/vuln@2.0.0"), Map.class);
		Map<String, Object> osv = Map.of("id", "ARGUS-RULEPIPE-1", "modified", "2026-01-01T00:00:00Z", "published",
				"2026-01-01T00:00:00Z", "summary", "rule pipeline marker advisory", "severity",
				List.of(Map.of("type", "CVSS_V3", "score", CRITICAL_VECTOR)), "affected",
				List.of(Map.of("package",
						Map.of("ecosystem", "Maven", "name", MARKER + ":vuln", "purl",
								"pkg:maven/" + MARKER + "/vuln"),
						"ranges", List.of(Map.of("type", "ECOSYSTEM", "events",
								List.of(Map.of("introduced", "0"), Map.of("fixed", "3.0.0")))))));
		assertThat(this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);

		// Import the SBOM: a@1.0.0 -DEPENDS_ON-> vuln@2.0.0. The import's async derivation runs R1
		// project-scoped only — R2 (and thus AFFECTS from ranges) materialises only on a full
		// recompute, so no exposure exists until this test's synchronous whole-graph run-rules.
		// That means there is no async writer to race the run-rules calls below.
		assertThat(this.rest.postForEntity("/api/v1/projects", sbomEntity(SBOM), Map.class).getStatusCode())
			.isEqualTo(HttpStatus.CREATED);
	}

	// --- helpers ----------------------------------------------------------------------------

	private List<Map<String, Object>> rules() {
		return this.rest.exchange("/api/v1/inference/rules", HttpMethod.GET, null, ruleListType()).getBody();
	}

	private List<Map<String, Object>> setRuleEnabled(String name, boolean enabled) {
		return this.rest
			.exchange("/api/v1/inference/rules/" + name + "/enabled?enabled=" + enabled, HttpMethod.POST, null,
					ruleListType())
			.getBody();
	}

	private ResponseEntity<Map<String, Object>> runRules() {
		return this.rest.exchange("/api/v1/inference/run-rules", HttpMethod.POST, null,
				new ParameterizedTypeReference<Map<String, Object>>() {
				});
	}

	private Map<String, Object> rule(List<Map<String, Object>> rules, String name) {
		return rules.stream().filter(r -> name.equals(r.get("name"))).findFirst().orElseThrow();
	}

	/** Direct driver count of derived transitive-exposure edges across the whole graph. */
	private long transitiveCount() {
		try (var session = this.driver.session()) {
			return session.run(
					"MATCH (:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(:PackageVersion) RETURN count(t) AS c")
				.single().get("c").asLong();
		}
	}

	private static ParameterizedTypeReference<List<Map<String, Object>>> ruleListType() {
		return new ParameterizedTypeReference<List<Map<String, Object>>>() {
		};
	}

	private HttpEntity<String> sbomEntity(String body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return new HttpEntity<>(body, headers);
	}

}
