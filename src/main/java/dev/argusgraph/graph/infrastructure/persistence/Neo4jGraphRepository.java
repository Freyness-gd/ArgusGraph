package dev.argusgraph.graph.infrastructure.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.graph.PackageVersion;
import dev.argusgraph.graph.Vulnerability;
import dev.argusgraph.graph.application.GraphRepository;
import dev.argusgraph.graph.application.GraphStats;
import dev.argusgraph.graph.application.Neighbourhood;
import dev.argusgraph.graph.application.PackageDetails;
import dev.argusgraph.graph.application.PackageHits;
import dev.argusgraph.graph.application.PackagePage;
import dev.argusgraph.graph.application.PackageVersionDetails;
import dev.argusgraph.graph.application.VulnerabilityPage;

/**
 * Cypher adapter for the {@link GraphRepository} port. Deliberately uses
 * {@link Neo4jClient} with hand-written {@code MERGE} statements instead of SDN
 * {@code @Node} mapping: upserts stay explicitly idempotent, there is no accidental
 * deep-fetch of a cyclic dependency graph, and the statements evolve naturally into
 * {@code UNWIND}-batched bulk ingestion when the scraper workers arrive.
 *
 * <p>
 * Nested vulnerability lists (severities, references) are flattened into parallel array
 * properties — Neo4j properties cannot hold maps. Raw OSV ranges travel as a verbatim
 * JSON string on the {@code AFFECTS_PACKAGE} relationship for the future inference
 * engine.
 */
@Component
@RequiredArgsConstructor
class Neo4jGraphRepository implements GraphRepository {

	private static final String UPSERT_PACKAGE_VERSION = """
			MERGE (p:Package {purl: $packagePurl})
			  ON CREATE SET p.type = $type, p.namespace = $namespace, p.name = $name
			MERGE (v:PackageVersion {purl: $purl})
			  ON CREATE SET v.version = $version
			MERGE (p)-[:HAS_VERSION]->(v)
			""";

	private static final String UPSERT_PACKAGE = """
			MERGE (p:Package {purl: $packagePurl})
			  ON CREATE SET p.type = $type, p.namespace = $namespace, p.name = $name
			""";

	private static final String UPSERT_VULNERABILITY = """
			MERGE (v:Vulnerability {id: $id})
			SET v.modified = coalesce($modified, v.modified),
			    v.published = coalesce($published, v.published),
			    v.withdrawn = coalesce($withdrawn, v.withdrawn),
			    v.summary = coalesce($summary, v.summary),
			    v.details = coalesce($details, v.details),
			    v.aliases = coalesce($aliases, v.aliases),
			    v.related = coalesce($related, v.related),
			    v.upstream = coalesce($upstream, v.upstream),
			    v.severityTypes = coalesce($severityTypes, v.severityTypes),
			    v.severityVectors = coalesce($severityVectors, v.severityVectors),
			    v.referenceTypes = coalesce($referenceTypes, v.referenceTypes),
			    v.referenceUrls = coalesce($referenceUrls, v.referenceUrls),
			    v.severity = coalesce($severity, v.severity),
			    v.cvssScore = coalesce($cvssScore, v.cvssScore),
			    v.embedding = coalesce($embedding, v.embedding)
			""";

	private static final String LINK_DEPENDENCY = """
			MATCH (from:PackageVersion {purl: $fromPurl})
			MATCH (to:PackageVersion {purl: $toPurl})
			MERGE (from)-[d:DEPENDS_ON]->(to)
			SET d.scope = coalesce($scope, d.scope)
			""";

	private static final String LINK_AFFECTS = """
			MERGE (v:Vulnerability {id: $id})
			WITH v
			MATCH (pv:PackageVersion {purl: $purl})
			MERGE (v)-[:AFFECTS]->(pv)
			""";

	private static final String LINK_AFFECTS_PACKAGE = """
			MERGE (v:Vulnerability {id: $id})
			WITH v
			MATCH (p:Package {purl: $packagePurl})
			MERGE (v)-[r:AFFECTS_PACKAGE]->(p)
			SET r.ranges = coalesce($ranges, r.ranges)
			""";

	private static final String ATTACH_EMBEDDING = """
			MATCH (v:Vulnerability {id: $id})
			SET v.embedding = $embedding
			""";

	private static final String FIND_PACKAGE_VERSION = """
			MATCH (p:Package)-[:HAS_VERSION]->(pv:PackageVersion {purl: $purl})
			OPTIONAL MATCH (pv)-[d:DEPENDS_ON]->(dep:PackageVersion)
			WITH p, pv,
			     [x IN collect(DISTINCT {purl: dep.purl, scope: d.scope}) WHERE x.purl IS NOT NULL] AS dependencies
			OPTIONAL MATCH (vuln:Vulnerability)-[:AFFECTS]->(pv)
			RETURN pv.purl AS purl,
			       p.purl AS packagePurl,
			       pv.version AS version,
			       dependencies,
			       [x IN collect(DISTINCT {id: vuln.id, severity: vuln.severity, cvssScore: vuln.cvssScore,
			                               summary: vuln.summary})
			        WHERE x.id IS NOT NULL] AS vulnerabilities
			""";

	private static final String NEIGHBOURHOOD = """
			MATCH (pv:PackageVersion {purl: $purl})
			OPTIONAL MATCH (pv)-[:DEPENDS_ON]->(dep:PackageVersion)
			OPTIONAL MATCH (dependent:PackageVersion)-[:DEPENDS_ON]->(pv)
			OPTIONAL MATCH (av:Vulnerability)-[:AFFECTS]->(pv)
			OPTIONAL MATCH (tv:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(pv)
			RETURN pv.purl AS center, pv.version AS version,
			  [x IN collect(DISTINCT dep.purl) WHERE x IS NOT NULL] AS dependencies,
			  [x IN collect(DISTINCT dependent.purl) WHERE x IS NOT NULL] AS dependents,
			  [x IN collect(DISTINCT {id: av.id, severity: av.severity}) WHERE x.id IS NOT NULL] AS vulnerabilities,
			  [x IN collect(DISTINCT {id: tv.id, severity: tv.severity, depth: t.depth})
			   WHERE x.id IS NOT NULL] AS transitive
			""";

	private static final String FETCH_STATS = """
			RETURN COUNT { MATCH (:Package) } AS packages,
			       COUNT { MATCH (:PackageVersion) } AS packageVersions,
			       COUNT { MATCH (:Vulnerability) } AS vulnerabilities
			""";

	private static final String FETCH_SEVERITY_BUCKETS = """
			MATCH (v:Vulnerability)
			RETURN coalesce(v.severity, 'NONE') AS severity, count(v) AS n
			""";

	private static final String VULNERABILITY_FILTER = """
			MATCH (v:Vulnerability)
			WHERE ($severity IS NULL OR coalesce(v.severity, 'NONE') = $severity)
			  AND ($q IS NULL OR toLower(v.id) CONTAINS $q OR toLower(coalesce(v.summary, '')) CONTAINS $q)
			""";

	private static final String FIND_VULNERABILITIES = VULNERABILITY_FILTER + """
			RETURN v.id AS id, v.severity AS severity, v.cvssScore AS cvssScore,
			       v.summary AS summary, v.published AS published
			ORDER BY v.published IS NULL, v.published DESC, v.id ASC
			SKIP $skip LIMIT $limit
			""";

	private static final String COUNT_VULNERABILITIES = VULNERABILITY_FILTER + """
			RETURN count(v) AS total
			""";

	// Batch size is per-transaction: each batch detach-deletes this many nodes plus all
	// their relationships in one tx. OSV vulnerability nodes are high-degree, so a large
	// batch can exceed db.memory.transaction.max on an untuned community instance. 1000
	// keeps each tx small; throughput cost over 10000 is negligible for a one-off reset.
	private static final String WIPE_ALL = """
			MATCH (n)
			CALL {
			    WITH n
			    DETACH DELETE n
			} IN TRANSACTIONS OF 1000 ROWS
			""";

	private static final String TREND_BUCKETS = """
			MATCH (v:Vulnerability)
			WHERE v.published IS NOT NULL
			  AND date(v.published) >= date($from)
			  AND date(v.published) <= date($to)
			WITH date.truncate($interval, date(v.published)) AS bucket, count(v) AS n
			RETURN toString(bucket) AS bucket, n
			""";

	private static final String TOP_AFFECTED_PACKAGES = """
			MATCH (v:Vulnerability)-[:AFFECTS]->(:PackageVersion)<-[:HAS_VERSION]-(p:Package)
			RETURN p.purl AS packagePurl, count(DISTINCT v) AS vulnerabilities
			ORDER BY vulnerabilities DESC, packagePurl ASC
			LIMIT $limit
			""";

	private static final String MATCH_PACKAGE_VERSIONS = """
			UNWIND $purls AS purl
			OPTIONAL MATCH (pv:PackageVersion {purl: purl})
			OPTIONAL MATCH (v:Vulnerability)-[:AFFECTS]->(pv)
			RETURN purl,
			       pv IS NOT NULL AS known,
			       [x IN collect(DISTINCT {id: v.id, severity: v.severity, cvssScore: v.cvssScore,
			                               summary: v.summary}) WHERE x.id IS NOT NULL] AS vulnerabilities
			""";

	private static final String PACKAGE_FILTER = """
			MATCH (p:Package)
			WHERE ($q IS NULL OR toLower(p.purl) CONTAINS $q OR toLower(coalesce(p.name,'')) CONTAINS $q)
			""";

	private static final String FIND_PACKAGES = PACKAGE_FILTER + """
			OPTIONAL MATCH (p)-[:HAS_VERSION]->(pv:PackageVersion)
			WITH p, count(DISTINCT pv) AS versionCount
			OPTIONAL MATCH (p)-[:HAS_VERSION]->(pv2:PackageVersion)<-[:AFFECTS]-(v:Vulnerability)
			WITH p, versionCount, count(DISTINCT v) AS vulnerabilityCount
			RETURN p.purl AS packagePurl, p.type AS type, versionCount, vulnerabilityCount
			ORDER BY vulnerabilityCount DESC, packagePurl ASC
			SKIP $skip LIMIT $limit
			""";

	private static final String COUNT_PACKAGES = PACKAGE_FILTER + "RETURN count(p) AS total";

	private static final String FIND_PACKAGE = """
			MATCH (p:Package {purl: $purl})
			OPTIONAL MATCH (p)-[:HAS_VERSION]->(pv:PackageVersion)
			OPTIONAL MATCH (vuln:Vulnerability)-[:AFFECTS]->(pv)
			WITH p, pv,
			     [x IN collect(DISTINCT {id: vuln.id, severity: vuln.severity, cvssScore: vuln.cvssScore,
			                             summary: vuln.summary})
			      WHERE x.id IS NOT NULL] AS vulns
			WITH p, [x IN collect({purl: pv.purl, version: pv.version, vulnerabilities: vulns}) WHERE x.purl IS NOT NULL] AS versions
			RETURN p.purl AS packagePurl, p.type AS type, versions
			""";

	private final Neo4jClient neo4j;

	private final Driver driver;

	@Override
	public PackageVersion upsertPackageVersion(PackageVersion packageVersion) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("purl", packageVersion.getPurl());
		parameters.put("packagePurl", packageVersion.getPackagePurl());
		parameters.put("type", packageVersion.getType());
		parameters.put("namespace", packageVersion.getNamespace());
		parameters.put("name", packageVersion.getName());
		parameters.put("version", packageVersion.getVersion());
		this.neo4j.query(UPSERT_PACKAGE_VERSION).bindAll(parameters).run();
		return packageVersion;
	}

	@Override
	public void upsertPackage(String packagePurl, String type, String namespace, String name) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("packagePurl", packagePurl);
		parameters.put("type", type);
		parameters.put("namespace", namespace);
		parameters.put("name", name);
		this.neo4j.query(UPSERT_PACKAGE).bindAll(parameters).run();
	}

	@Override
	public Vulnerability upsertVulnerability(Vulnerability vulnerability) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("id", vulnerability.getId());
		parameters.put("modified", toOffset(vulnerability.getModified()));
		parameters.put("published", toOffset(vulnerability.getPublished()));
		parameters.put("withdrawn", toOffset(vulnerability.getWithdrawn()));
		parameters.put("summary", vulnerability.getSummary());
		parameters.put("details", vulnerability.getDetails());
		parameters.put("aliases", emptyToNull(vulnerability.getAliases()));
		parameters.put("related", emptyToNull(vulnerability.getRelated()));
		parameters.put("upstream", emptyToNull(vulnerability.getUpstream()));
		parameters.put("severityTypes",
				emptyToNull(vulnerability.getSeverities().stream().map(Vulnerability.Severity::type).toList()));
		parameters.put("severityVectors",
				emptyToNull(vulnerability.getSeverities().stream().map(Vulnerability.Severity::vector).toList()));
		parameters.put("referenceTypes",
				emptyToNull(vulnerability.getReferences().stream().map(Vulnerability.Reference::type).toList()));
		parameters.put("referenceUrls",
				emptyToNull(vulnerability.getReferences().stream().map(Vulnerability.Reference::url).toList()));
		parameters.put("severity", vulnerability.getSeverity());
		parameters.put("cvssScore", vulnerability.getCvssScore());
		parameters.put("embedding", toDoubleList(vulnerability.getEmbedding()));
		this.neo4j.query(UPSERT_VULNERABILITY).bindAll(parameters).run();
		return vulnerability;
	}

	@Override
	public void linkDependency(String fromPurl, String toPurl, String scope) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("fromPurl", fromPurl);
		parameters.put("toPurl", toPurl);
		parameters.put("scope", scope);
		this.neo4j.query(LINK_DEPENDENCY).bindAll(parameters).run();
	}

	@Override
	public void linkAffects(String vulnerabilityId, String purl) {
		this.neo4j.query(LINK_AFFECTS).bindAll(Map.of("id", vulnerabilityId, "purl", purl)).run();
	}

	@Override
	public void linkAffectsPackage(String vulnerabilityId, String packagePurl, String rangesJson) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("id", vulnerabilityId);
		parameters.put("packagePurl", packagePurl);
		parameters.put("ranges", rangesJson);
		this.neo4j.query(LINK_AFFECTS_PACKAGE).bindAll(parameters).run();
	}

	@Override
	public void attachEmbedding(String vulnerabilityId, float[] embedding) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("id", vulnerabilityId);
		parameters.put("embedding", toDoubleList(embedding));
		this.neo4j.query(ATTACH_EMBEDDING).bindAll(parameters).run();
	}

	@Override
	public Optional<PackageVersionDetails> findPackageVersion(String purl) {
		return this.neo4j.query(FIND_PACKAGE_VERSION).bindAll(Map.of("purl", purl)).fetch().one().map(this::toDetails);
	}

	@Override
	public Optional<Neighbourhood> findNeighbourhood(String purl) {
		return this.neo4j.query(NEIGHBOURHOOD).bindAll(Map.of("purl", purl)).fetch().one().map(this::toNeighbourhood);
	}

	@Override
	public GraphStats fetchStats() {
		Map<String, Object> counts = this.neo4j.query(FETCH_STATS).fetch().one().orElseThrow();
		Map<String, Long> bySeverity = new LinkedHashMap<>();
		this.neo4j.query(FETCH_SEVERITY_BUCKETS)
			.fetch()
			.all()
			.forEach(row -> bySeverity.put((String) row.get("severity"), ((Number) row.get("n")).longValue()));
		return new GraphStats(((Number) counts.get("packages")).longValue(),
				((Number) counts.get("packageVersions")).longValue(),
				((Number) counts.get("vulnerabilities")).longValue(), bySeverity);
	}

	@Override
	public VulnerabilityPage findVulnerabilities(String severity, String q, int page, int size) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("severity", severity);
		parameters.put("q", q == null ? null : q.toLowerCase(Locale.ROOT));
		parameters.put("skip", (long) page * size);
		parameters.put("limit", (long) size);
		List<VulnerabilityPage.Item> items = this.neo4j.query(FIND_VULNERABILITIES)
			.bindAll(parameters)
			.fetch()
			.all()
			.stream()
			.map(row -> new VulnerabilityPage.Item((String) row.get("id"), (String) row.get("severity"),
					toDouble(row.get("cvssScore")), (String) row.get("summary"), toInstant(row.get("published"))))
			.toList();
		long total = this.neo4j.query(COUNT_VULNERABILITIES)
			.bindAll(parameters)
			.fetchAs(Long.class)
			.one()
			.orElse(0L);
		return new VulnerabilityPage(items, page, size, total);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<GraphAPI.PurlMatch> findMatches(Collection<String> purls) {
		return this.neo4j.query(MATCH_PACKAGE_VERSIONS)
			.bindAll(Map.of("purls", List.copyOf(purls)))
			.fetch()
			.all()
			.stream()
			.map(row -> new GraphAPI.PurlMatch((String) row.get("purl"), (Boolean) row.get("known"),
					((List<Map<String, Object>>) row.get("vulnerabilities")).stream()
						.map(v -> new GraphAPI.VulnerabilityRef((String) v.get("id"), (String) v.get("severity"),
								toDouble(v.get("cvssScore")), (String) v.get("summary")))
						.toList()))
			.toList();
	}

	@Override
	public Map<LocalDate, Long> trendBuckets(LocalDate from, LocalDate to, String interval) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("from", from.toString());
		parameters.put("to", to.toString());
		parameters.put("interval", interval);
		Map<LocalDate, Long> buckets = new HashMap<>();
		this.neo4j.query(TREND_BUCKETS)
			.bindAll(parameters)
			.fetch()
			.all()
			.forEach(row -> buckets.put(LocalDate.parse((String) row.get("bucket")),
					((Number) row.get("n")).longValue()));
		return buckets;
	}

	@Override
	public List<PackageHits> topAffectedPackages(int limit) {
		return this.neo4j.query(TOP_AFFECTED_PACKAGES)
			.bindAll(Map.of("limit", limit))
			.fetch()
			.all()
			.stream()
			.map(row -> new PackageHits((String) row.get("packagePurl"),
					((Number) row.get("vulnerabilities")).longValue()))
			.toList();
	}

	@Override
	public PackagePage findPackages(String q, int page, int size) {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("q", q == null ? null : q.toLowerCase(Locale.ROOT));
		parameters.put("skip", (long) page * size);
		parameters.put("limit", (long) size);
		List<PackagePage.Item> items = this.neo4j.query(FIND_PACKAGES)
			.bindAll(parameters)
			.fetch()
			.all()
			.stream()
			.map(row -> new PackagePage.Item((String) row.get("packagePurl"), (String) row.get("type"),
					((Number) row.get("versionCount")).longValue(),
					((Number) row.get("vulnerabilityCount")).longValue()))
			.toList();
		long total = this.neo4j.query(COUNT_PACKAGES).bindAll(parameters).fetchAs(Long.class).one().orElse(0L);
		return new PackagePage(items, page, size, total);
	}

	@Override
	@SuppressWarnings("unchecked")
	public Optional<PackageDetails> findPackage(String packagePurl) {
		return this.neo4j.query(FIND_PACKAGE)
			.bindAll(Map.of("purl", packagePurl))
			.fetch()
			.one()
			.map(row -> new PackageDetails((String) row.get("packagePurl"), (String) row.get("type"),
					((List<Map<String, Object>>) row.get("versions")).stream()
						.map(this::toVersion)
						// Lexicographic order — good enough for browsing; semver-aware sort (1.9 vs 1.10) is deferred.
						.sorted(Comparator.comparing(PackageDetails.Version::version,
								Comparator.nullsLast(Comparator.naturalOrder())))
						.toList()));
	}

	@Override
	public long wipeAll() {
		// CALL { ... } IN TRANSACTIONS requires an implicit (auto-commit) transaction.
		// Neo4jClient always uses managed/explicit transactions, so the raw Driver is used here.
		// The wipe also removes Spring Modulith event-publication nodes (they live in the same
		// database) — intended for a full reset.
		try (Session session = this.driver.session()) {
			return session.run(WIPE_ALL).consume().counters().nodesDeleted();
		}
	}

	@SuppressWarnings("unchecked")
	private PackageVersionDetails toDetails(Map<String, Object> row) {
		List<Map<String, Object>> dependencies = (List<Map<String, Object>>) row.get("dependencies");
		List<Map<String, Object>> vulnerabilities = (List<Map<String, Object>>) row.get("vulnerabilities");
		return new PackageVersionDetails((String) row.get("purl"), (String) row.get("packagePurl"),
				(String) row.get("version"),
				dependencies.stream()
					.map(d -> new PackageVersionDetails.Dependency((String) d.get("purl"), (String) d.get("scope")))
					.toList(),
				vulnerabilities.stream()
					.map(v -> new PackageVersionDetails.AffectingVulnerability((String) v.get("id"),
							(String) v.get("severity"), toDouble(v.get("cvssScore")), (String) v.get("summary")))
					.toList());
	}

	@SuppressWarnings("unchecked")
	private Neighbourhood toNeighbourhood(Map<String, Object> row) {
		List<Map<String, Object>> vulnerabilities = (List<Map<String, Object>>) row.get("vulnerabilities");
		List<Map<String, Object>> transitive = (List<Map<String, Object>>) row.get("transitive");
		return new Neighbourhood((String) row.get("center"), (String) row.get("version"),
				(List<String>) row.get("dependencies"), (List<String>) row.get("dependents"),
				vulnerabilities.stream()
					.map(v -> new Neighbourhood.VulnRef((String) v.get("id"), (String) v.get("severity")))
					.toList(),
				transitive.stream()
					.map(t -> new Neighbourhood.TransitiveRef((String) t.get("id"), (String) t.get("severity"),
							((Number) t.get("depth")).intValue()))
					.toList());
	}

	@SuppressWarnings("unchecked")
	private PackageDetails.Version toVersion(Map<String, Object> row) {
		List<Map<String, Object>> vulnerabilities = (List<Map<String, Object>>) row.get("vulnerabilities");
		return new PackageDetails.Version((String) row.get("purl"), (String) row.get("version"),
				vulnerabilities.stream()
					.map(v -> new PackageVersionDetails.AffectingVulnerability((String) v.get("id"),
							(String) v.get("severity"), toDouble(v.get("cvssScore")), (String) v.get("summary")))
					.toList());
	}

	private static Object toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Double toDouble(Object value) {
		return value == null ? null : ((Number) value).doubleValue();
	}

	private static Instant toInstant(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof ZonedDateTime zoned) {
			return zoned.toInstant();
		}
		return ((java.time.OffsetDateTime) value).toInstant();
	}

	private static List<String> emptyToNull(List<String> values) {
		return values == null || values.isEmpty() ? null : values;
	}

	private static List<Double> toDoubleList(float[] values) {
		if (values == null) {
			return null;
		}
		List<Double> result = new ArrayList<>(values.length);
		for (float value : values) {
			result.add((double) value);
		}
		return result;
	}

}
