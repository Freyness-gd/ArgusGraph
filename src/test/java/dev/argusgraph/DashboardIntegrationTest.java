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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dashboard read endpoints against a real (Testcontainers) Neo4j. The container is
 * shared across test classes, so stats assertions are deltas against a before-snapshot
 * and list assertions are scoped to a unique text marker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class DashboardIntegrationTest {

	private static final String CRITICAL_VECTOR = "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H";

	@Autowired
	private TestRestTemplate rest;

	@Test
	@SuppressWarnings("unchecked")
	void statsCountNodesAndSeverityBuckets() {
		Map<String, Object> before = this.rest.getForObject("/api/v1/graph/stats", Map.class);

		ingestPackageVersion("pkg:maven/dev.argusgraph.stats/web@1.0.0");
		ingestPackageVersion("pkg:maven/dev.argusgraph.stats/web@1.1.0");
		ingestVulnerability("ARGUS-STATS-CRIT", "stats marker critical", CRITICAL_VECTOR,
				"2026-01-01T00:00:00Z");
		ingestVulnerability("ARGUS-STATS-NONE", "stats marker none", null, "2026-01-02T00:00:00Z");

		Map<String, Object> after = this.rest.getForObject("/api/v1/graph/stats", Map.class);

		assertThat(longOf(after, "packages") - longOf(before, "packages")).isEqualTo(1);
		assertThat(longOf(after, "packageVersions") - longOf(before, "packageVersions")).isEqualTo(2);
		assertThat(longOf(after, "vulnerabilities") - longOf(before, "vulnerabilities")).isEqualTo(2);

		Map<String, Number> beforeBuckets = (Map<String, Number>) before.get("bySeverity");
		Map<String, Number> afterBuckets = (Map<String, Number>) after.get("bySeverity");
		assertThat(bucket(afterBuckets, "CRITICAL") - bucket(beforeBuckets, "CRITICAL")).isEqualTo(1);
		assertThat(bucket(afterBuckets, "NONE") - bucket(beforeBuckets, "NONE")).isEqualTo(1);
	}

	@Test
	@SuppressWarnings("unchecked")
	void vulnerabilityListFiltersSearchesAndPages() {
		ingestVulnerability("ARGUS-LIST-A", "browse-marker alpha", CRITICAL_VECTOR, "2026-03-01T00:00:00Z");
		ingestVulnerability("ARGUS-LIST-B", "browse-marker beta", null, "2026-02-01T00:00:00Z");
		ingestVulnerability("ARGUS-LIST-C", "browse-marker gamma", null, "2026-01-01T00:00:00Z");

		// q scopes everything to this test's data; newest published first.
		Map<String, Object> all = this.rest.getForObject("/api/v1/graph/vulnerabilities?q=browse-marker",
				Map.class);
		assertThat(longOf(all, "total")).isEqualTo(3);
		List<Map<String, Object>> items = (List<Map<String, Object>>) all.get("items");
		assertThat(items).extracting(i -> i.get("id"))
			.containsExactly("ARGUS-LIST-A", "ARGUS-LIST-B", "ARGUS-LIST-C");

		// Severity filter is case-normalised and narrows to the critical advisory.
		Map<String, Object> critical = this.rest
			.getForObject("/api/v1/graph/vulnerabilities?q=browse-marker&severity=critical", Map.class);
		assertThat(longOf(critical, "total")).isEqualTo(1);
		assertThat(((List<Map<String, Object>>) critical.get("items")).get(0))
			.containsEntry("id", "ARGUS-LIST-A")
			.containsEntry("severity", "CRITICAL")
			.containsEntry("cvssScore", 10.0);

		// Page 2 of size 2 holds the single remaining row.
		Map<String, Object> page2 = this.rest
			.getForObject("/api/v1/graph/vulnerabilities?q=browse-marker&page=1&size=2", Map.class);
		assertThat(longOf(page2, "total")).isEqualTo(3);
		assertThat(((List<Map<String, Object>>) page2.get("items"))).extracting(i -> i.get("id"))
			.containsExactly("ARGUS-LIST-C");
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
		osv.put("published", published);
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

	private static long bucket(Map<String, Number> buckets, String key) {
		Number value = buckets == null ? null : buckets.get(key);
		return value == null ? 0 : value.longValue();
	}

}
