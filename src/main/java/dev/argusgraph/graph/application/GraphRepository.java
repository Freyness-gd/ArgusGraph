package dev.argusgraph.graph.application;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jmolecules.ddd.annotation.Repository;

import dev.argusgraph.graph.GraphAPI;
import dev.argusgraph.graph.PackageVersion;
import dev.argusgraph.graph.Vulnerability;

/**
 * Domain repository PORT — expressed in graph terms, with no Neo4j detail. The
 * application layer depends on this interface; the Cypher adapter in
 * {@code infrastructure.persistence} implements it.
 *
 * <p>
 * All writes are idempotent upserts ({@code MERGE} semantics on natural keys). Link
 * methods expect both endpoint nodes to exist — {@code GraphService} upserts them first.
 */
@Repository
public interface GraphRepository {

	/** Upsert the {@code PackageVersion} node, its parent {@code Package}, and the {@code HAS_VERSION} edge. */
	PackageVersion upsertPackageVersion(PackageVersion packageVersion);

	/** Upsert a versionless {@code Package} node. */
	void upsertPackage(String packagePurl, String type, String namespace, String name);

	/** Upsert the {@code Vulnerability} node; incoming fields refresh/fill but absent fields never erase. */
	Vulnerability upsertVulnerability(Vulnerability vulnerability);

	/** Upsert {@code from -[:DEPENDS_ON {scope}]-> to} between two existing package versions. */
	void linkDependency(String fromPurl, String toPurl, String scope);

	/** Upsert {@code vulnerability -[:AFFECTS]-> packageVersion}; creates a bare vulnerability node if missing. */
	void linkAffects(String vulnerabilityId, String purl);

	/** Upsert {@code vulnerability -[:AFFECTS_PACKAGE {ranges}]-> package} with raw OSV ranges JSON. */
	void linkAffectsPackage(String vulnerabilityId, String packagePurl, String rangesJson);

	/** Set the embedding vector on an existing vulnerability node; no-op when it doesn't exist. */
	void attachEmbedding(String vulnerabilityId, float[] embedding);

	/** A package version with its direct dependencies and directly-affecting vulnerabilities. */
	Optional<PackageVersionDetails> findPackageVersion(String purl);

	/** Whole-graph counts for the dashboard; vulnerabilities without a severity count as NONE. */
	GraphStats fetchStats();

	/**
	 * One page of vulnerabilities, newest published first (nulls last). Both filters are
	 * optional ({@code null} = off): {@code severity} matches the stored value exactly,
	 * {@code q} is matched case-insensitively against id and summary.
	 */
	VulnerabilityPage findVulnerabilities(String severity, String q, int page, int size);

	/**
	 * Delete every node and relationship in batched implicit transactions; uniqueness
	 * constraints survive. Returns the exact number of nodes deleted (driver counters).
	 * MUST be called outside an explicit transaction — {@code CALL ... IN TRANSACTIONS}
	 * only runs in implicit (auto-commit) transactions.
	 */
	long wipeAll();

	/** Match results for the given version purls — one entry per input purl. */
	List<GraphAPI.PurlMatch> findMatches(Collection<String> purls);

	/** Raw per-bucket counts of vulnerabilities published in [from, to]; gaps NOT filled. */
	Map<LocalDate, Long> trendBuckets(LocalDate from, LocalDate to, String interval);

	/** Packages ranked by distinct affecting vulnerabilities, descending; ties by purl ASC. */
	List<PackageHits> topAffectedPackages(int limit);

	/**
	 * One page of packages, most-affected first then purl ASC. The optional {@code q}
	 * ({@code null} = off) is matched case-insensitively against purl and name. Each row
	 * carries its distinct version count and distinct affecting-vulnerability count.
	 */
	PackagePage findPackages(String q, int page, int size);

	/** One package with all its versions and the vulnerabilities affecting each; empty when unknown. */
	Optional<PackageDetails> findPackage(String packagePurl);

}
