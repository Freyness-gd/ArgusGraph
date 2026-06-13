package dev.argusgraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Package read endpoints against a real (Testcontainers) Neo4j. The container is shared
 * across test classes, so every assertion is scoped to a unique text marker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class PackagesIntegrationTest {

	private static final String CRITICAL_VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	private static final String MARKER = "pkgread";

	private static final String PACKAGE_PURL = "pkg:maven/dev.argusgraph.%s/lib".formatted(MARKER);

	private static final String V1 = PACKAGE_PURL + "@1.0.0";

	private static final String V2 = PACKAGE_PURL + "@2.0.0";

	private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
	};

	@Autowired
	private TestRestTemplate rest;

	@Test
	@SuppressWarnings("unchecked")
	void packageListReportsDistinctVersionAndVulnerabilityCounts() {
		ingestPackageVersion(V1);
		ingestPackageVersion(V2);
		ingestVulnerability("ARGUS-PKGREAD-CRIT", "pkgread marker critical", CRITICAL_VECTOR,
				"2026-01-01T00:00:00Z");
		linkAffects("ARGUS-PKGREAD-CRIT", V1);

		Map<String, Object> page = getMap("/api/v1/graph/packages?q=" + MARKER);
		assertThat(longOf(page, "total")).isEqualTo(1);
		assertThat(intOf(page, "page")).isZero();
		assertThat(intOf(page, "size")).isEqualTo(25);

		List<Map<String, Object>> items = (List<Map<String, Object>>) page.get("items");
		assertThat(items).singleElement().satisfies(row -> {
			assertThat(row).containsEntry("packagePurl", PACKAGE_PURL).containsEntry("type", "maven");
			assertThat(longOf(row, "versionCount")).isEqualTo(2);
			assertThat(longOf(row, "vulnerabilityCount")).isEqualTo(1);
		});

		// Paging params are honoured: size 1, page 1 holds no rows for a single-package marker.
		Map<String, Object> page2 = getMap("/api/v1/graph/packages?q=" + MARKER + "&page=1&size=1");
		assertThat(intOf(page2, "page")).isEqualTo(1);
		assertThat(intOf(page2, "size")).isEqualTo(1);
		assertThat(longOf(page2, "total")).isEqualTo(1);
		assertThat((List<Map<String, Object>>) page2.get("items")).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	void packageDetailListsVersionsWithEnrichedVulnerabilities() {
		ingestPackageVersion(V1);
		ingestPackageVersion(V2);
		ingestVulnerability("ARGUS-PKGREAD-CRIT", "pkgread marker critical", CRITICAL_VECTOR,
				"2026-01-01T00:00:00Z");
		linkAffects("ARGUS-PKGREAD-CRIT", V1);

		Map<String, Object> detail = getMap("/api/v1/graph/packages/detail?purl=" + PACKAGE_PURL);
		assertThat(detail).containsEntry("packagePurl", PACKAGE_PURL).containsEntry("type", "maven");

		List<Map<String, Object>> versions = (List<Map<String, Object>>) detail.get("versions");
		assertThat(versions).extracting(v -> v.get("version")).containsExactly("1.0.0", "2.0.0");

		Map<String, Object> affected = versions.stream()
			.filter(v -> "1.0.0".equals(v.get("version")))
			.findFirst()
			.orElseThrow();
		List<Map<String, Object>> affectedVulns = (List<Map<String, Object>>) affected.get("vulnerabilities");
		assertThat(affectedVulns).singleElement().satisfies(vuln -> {
			assertThat(vuln).containsEntry("id", "ARGUS-PKGREAD-CRIT")
				.containsEntry("severity", "CRITICAL")
				.containsEntry("cvssScore", 10.0);
			assertThat(vuln.get("summary")).isNotNull();
			assertThat((String) vuln.get("summary")).isEqualTo("pkgread marker critical");
		});

		Map<String, Object> clean = versions.stream()
			.filter(v -> "2.0.0".equals(v.get("version")))
			.findFirst()
			.orElseThrow();
		assertThat((List<Map<String, Object>>) clean.get("vulnerabilities")).isEmpty();

		// Unknown purl → 404 (ResourceNotFoundException maps to NOT_FOUND, same as getPackageVersion).
		ResponseEntity<Map<String, Object>> ghost = this.rest.exchange(
				"/api/v1/graph/packages/detail?purl=pkg:maven/dev.argusgraph.%s/ghost".formatted(MARKER),
				HttpMethod.GET, null, MAP);
		assertThat(ghost.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@SuppressWarnings("unchecked")
	void packageVersionRefsCarrySummary() {
		ingestPackageVersion(V1);
		ingestVulnerability("ARGUS-PKGREAD-CRIT", "pkgread marker critical", CRITICAL_VECTOR,
				"2026-01-01T00:00:00Z");
		linkAffects("ARGUS-PKGREAD-CRIT", V1);

		Map<String, Object> version = getMap("/api/v1/graph/package-versions?purl=" + V1);
		List<Map<String, Object>> vulns = (List<Map<String, Object>>) version.get("vulnerabilities");
		assertThat(vulns).singleElement().satisfies(vuln -> {
			assertThat(vuln).containsEntry("id", "ARGUS-PKGREAD-CRIT");
			assertThat(vuln.get("summary")).isNotNull();
			assertThat((String) vuln.get("summary")).isEqualTo("pkgread marker critical");
		});
	}

	private Map<String, Object> getMap(String uri) {
		ResponseEntity<Map<String, Object>> response = this.rest.exchange(uri, HttpMethod.GET, null, MAP);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		return response.getBody();
	}

	private void linkAffects(String vulnerabilityId, String purl) {
		assertThat(this.rest
			.postForEntity("/api/v1/ingest/affects", Map.of("vulnerabilityId", vulnerabilityId, "purl", purl),
					Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	private void ingestPackageVersion(String purl) {
		ResponseEntity<Map> response = this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", purl), Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	/** Minimal OSV document; {@code cvssVector == null} leaves the severity bucket at NONE. */
	private void ingestVulnerability(String id, String summary, String cvssVector, String published) {
		Map<String, Object> osv = new HashMap<>();
		osv.put("id", id);
		osv.put("modified", "2026-01-01T00:00:00Z");
		if (published != null) {
			osv.put("published", published);
		}
		osv.put("summary", summary);
		if (cvssVector != null) {
			osv.put("severity", List.of(Map.of("type", "CVSS_V3", "score", cvssVector)));
		}
		ResponseEntity<Map> response = this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
	}

	private static long longOf(Map<String, Object> body, String key) {
		return ((Number) body.get(key)).longValue();
	}

	private static int intOf(Map<String, Object> body, String key) {
		return ((Number) body.get(key)).intValue();
	}

}
