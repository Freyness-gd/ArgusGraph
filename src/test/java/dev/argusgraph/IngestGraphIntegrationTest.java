package dev.argusgraph;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end flow against a real (Testcontainers) Neo4j: OSV documents and dependency
 * links go in through the ingest module's REST API and come back out as a graph through
 * the graph module's read endpoint. Walks the Log4Shell example with the real OSV
 * advisory shape (GHSA-jfh8-c2jp-5v3q): enumerated versions become AFFECTS edges, raw
 * ranges land on AFFECTS_PACKAGE, severity is derived from the CVSS vector. Also pins
 * idempotency (re-ingesting creates no duplicate nodes or edges) and the error contract
 * (400 validation / 404 unknown / 409 malformed purl).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class IngestGraphIntegrationTest {

	private static final String APP_PURL = "pkg:maven/dev.argusgraph/demo-app@1.0.0";

	private static final String LOG4J_PURL = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1";

	private static final String ADVISORY_ID = "GHSA-jfh8-c2jp-5v3q";

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private Neo4jClient neo4j;

	@Test
	@SuppressWarnings("unchecked")
	void ingestsOsvDocumentAndReadsItBackAsAGraph() {
		// Two package versions.
		ResponseEntity<Map> log4j = this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", LOG4J_PURL), Map.class);
		assertThat(log4j.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(log4j.getBody()).containsEntry("purl", LOG4J_PURL)
			.containsEntry("packagePurl", "pkg:maven/org.apache.logging.log4j/log4j-core")
			.containsEntry("version", "2.14.1");

		ResponseEntity<Map> app = this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", APP_PURL), Map.class);
		assertThat(app.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		// app -[:DEPENDS_ON]-> log4j
		ResponseEntity<Void> dependency = this.rest.postForEntity("/api/v1/ingest/dependencies",
				Map.of("fromPurl", APP_PURL, "toPurl", LOG4J_PURL, "scope", "compile"), Void.class);
		assertThat(dependency.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// The Log4Shell advisory as a (trimmed) OSV document: enumerated version 2.14.1
		// plus an ECOSYSTEM range. Severity must be derived from the CVSS v3.1 vector.
		Map<String, Object> osv = Map.of("schema_version", "1.6.0", "id", ADVISORY_ID, "modified",
				"2024-03-15T12:00:00Z", "published", "2021-12-10T10:15:00Z", "aliases", List.of("CVE-2021-44228"),
				"summary", "Remote code injection in Log4j", "severity",
				List.of(Map.of("type", "CVSS_V3", "score", "CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H")),
				"affected",
				List.of(Map.of("package",
						Map.of("ecosystem", "Maven", "name", "org.apache.logging.log4j:log4j-core", "purl",
								"pkg:maven/org.apache.logging.log4j/log4j-core"),
						"ranges",
						List.of(Map.of("type", "ECOSYSTEM", "events",
								List.of(Map.of("introduced", "2.0-beta9"), Map.of("fixed", "2.15.0")))),
						"versions", List.of("2.14.1"))),
				"references",
				List.of(Map.of("type", "ADVISORY", "url", "https://nvd.nist.gov/vuln/detail/CVE-2021-44228")));

		ResponseEntity<Map> vulnerability = this.rest.postForEntity("/api/v1/ingest/vulnerabilities", osv, Map.class);
		assertThat(vulnerability.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(vulnerability.getBody()).containsEntry("id", ADVISORY_ID)
			.containsEntry("severity", "CRITICAL")
			.containsEntry("cvssScore", 10.0)
			.containsEntry("affectedVersionsLinked", 1)
			.containsEntry("packagesWithRanges", 1)
			.containsEntry("skippedPackages", 0);

		// Raw ranges preserved on the AFFECTS_PACKAGE edge.
		long rangeEdges = count("""
				MATCH (:Vulnerability {id: '%s'})-[r:AFFECTS_PACKAGE]->
				      (:Package {purl: 'pkg:maven/org.apache.logging.log4j/log4j-core'})
				WHERE r.ranges CONTAINS 'introduced'
				RETURN count(r)""".formatted(ADVISORY_ID));
		assertThat(rangeEdges).isEqualTo(1);

		// Read side: log4j carries the vulnerability, the app carries the dependency.
		ResponseEntity<Map> log4jView = this.rest.getForEntity("/api/v1/graph/package-versions?purl={purl}",
				Map.class, LOG4J_PURL);
		assertThat(log4jView.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> vulnerabilities = (List<Map<String, Object>>) log4jView.getBody()
			.get("vulnerabilities");
		assertThat(vulnerabilities).extracting(v -> v.get("id")).containsExactly(ADVISORY_ID);
		assertThat(vulnerabilities.get(0)).containsEntry("severity", "CRITICAL").containsEntry("cvssScore", 10.0);

		ResponseEntity<Map> appView = this.rest.getForEntity("/api/v1/graph/package-versions?purl={purl}", Map.class,
				APP_PURL);
		assertThat(appView.getStatusCode()).isEqualTo(HttpStatus.OK);
		List<Map<String, Object>> dependencies = (List<Map<String, Object>>) appView.getBody().get("dependencies");
		assertThat(dependencies).extracting(d -> d.get("purl")).containsExactly(LOG4J_PURL);
		assertThat(dependencies.get(0)).containsEntry("scope", "compile");
	}

	@Test
	void reingestingTheSameDataCreatesNoDuplicates() {
		String purl = "pkg:npm/lodash@4.17.21";
		String advisory = "GHSA-jf85-cpcp-j695";
		Map<String, Object> osv = Map.of("id", advisory, "modified", "2024-01-01T00:00:00Z", "affected",
				List.of(Map.of("package", Map.of("ecosystem", "npm", "name", "lodash"), "versions",
						List.of("4.17.21"))));

		// Batch ingest twice, the direct AFFECTS endpoint once — all idempotent MERGEs.
		for (int i = 0; i < 2; i++) {
			ResponseEntity<List> batch = this.rest.postForEntity("/api/v1/ingest/vulnerabilities/batch", List.of(osv, osv),
					List.class);
			assertThat(batch.getStatusCode()).isEqualTo(HttpStatus.CREATED);
			assertThat(batch.getBody()).hasSize(2);
		}
		this.rest.postForEntity("/api/v1/ingest/affects", Map.of("vulnerabilityId", advisory, "purl", purl), Void.class);

		assertThat(count("MATCH (p:Package {purl: 'pkg:npm/lodash'}) RETURN count(p)")).isEqualTo(1);
		assertThat(count("MATCH (v:PackageVersion {purl: 'pkg:npm/lodash@4.17.21'}) RETURN count(v)")).isEqualTo(1);
		assertThat(count("MATCH (:Vulnerability {id: '" + advisory + "'})-[a:AFFECTS]->"
				+ "(:PackageVersion {purl: 'pkg:npm/lodash@4.17.21'}) RETURN count(a)")).isEqualTo(1);
		assertThat(count("MATCH (:Package {purl: 'pkg:npm/lodash'})-[h:HAS_VERSION]->"
				+ "(:PackageVersion {purl: 'pkg:npm/lodash@4.17.21'}) RETURN count(h)")).isEqualTo(1);
	}

	@Test
	void enforcesTheErrorContract() {
		// 400 — bean validation: OSV requires id + modified.
		ResponseEntity<Map> blank = this.rest.postForEntity("/api/v1/ingest/vulnerabilities", Map.of(), Map.class);
		assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		// 409 — malformed purl violates a domain rule.
		ResponseEntity<Map> malformed = this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", "not a purl"), Map.class);
		assertThat(malformed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// 409 — a PackageVersion needs a version.
		ResponseEntity<Map> versionless = this.rest.postForEntity("/api/v1/ingest/package-versions",
				Map.of("purl", "pkg:maven/com.acme/lib"), Map.class);
		assertThat(versionless.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

		// 404 — reading a purl that was never ingested.
		ResponseEntity<Map> unknown = this.rest.getForEntity("/api/v1/graph/package-versions?purl={purl}", Map.class,
				"pkg:maven/com.acme/ghost@9.9.9");
		assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	private long count(String cypher) {
		return this.neo4j.query(cypher).fetchAs(Long.class).one().orElse(0L);
	}

}
