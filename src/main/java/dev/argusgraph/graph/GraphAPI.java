package dev.argusgraph.graph;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import org.jmolecules.ddd.annotation.Service;

/**
 * Published contract of the graph module — the ONLY graph types other modules may import
 * (besides the {@link Purl} value object). The ingest module (and the future scraper
 * workers behind it) feed the knowledge graph exclusively through this interface.
 *
 * <p>
 * Every write is an idempotent upsert keyed on natural ids (canonical purl / advisory
 * id), so re-ingesting the same source data never duplicates nodes or relationships.
 * Vulnerability input is aligned to the OSV schema (https://ossf.github.io/osv-schema/).
 */
@Service
public interface GraphAPI {

	/**
	 * Upsert a {@code PackageVersion} node (and its parent {@code Package}) from a purl
	 * that must include a version.
	 */
	PackageVersionSnapshot upsertPackageVersion(String purl);

	/** Upsert a versionless {@code Package} node from a purl (any version is stripped). */
	PackageSnapshot upsertPackage(String purl);

	/**
	 * Upsert a {@code Vulnerability} node by advisory id. Optional fields only ever fill
	 * gaps or refresh values on the existing node — absent input never erases data.
	 */
	VulnerabilitySnapshot upsertVulnerability(VulnerabilityInput input);

	/**
	 * Record {@code from -[:DEPENDS_ON]-> to} between two package versions, upserting
	 * both endpoints first. {@code scope} (compile/runtime/...) is optional.
	 */
	void linkDependency(String fromPurl, String toPurl, String scope);

	/**
	 * Record {@code vulnerability -[:AFFECTS]-> packageVersion}, upserting both endpoints
	 * first.
	 */
	void linkAffects(String vulnerabilityId, String purl);

	/**
	 * Record {@code vulnerability -[:AFFECTS_PACKAGE {ranges}]-> package} with the raw
	 * OSV ranges JSON preserved verbatim for the future inference engine. Upserts both
	 * endpoints first.
	 */
	void linkAffectsPackage(String vulnerabilityId, String packagePurl, String rangesJson);

	/**
	 * Attach a text-embedding vector to an existing vulnerability node. No-op when the
	 * node does not exist (embedding requests always follow an ingest). Recomputing is
	 * idempotent — same text, same vector.
	 */
	void attachEmbedding(String vulnerabilityId, float[] embedding);

	/**
	 * Match version-level purls against the graph: which are known, and which
	 * vulnerabilities affect them. Unknown purls come back with
	 * {@code knownToGraph=false} and an empty vulnerability list. Every input purl
	 * appears exactly once in the result; order is not guaranteed.
	 */
	List<PurlMatch> matchPackageVersions(Collection<String> purls);

	/** Match result for one version-level purl. */
	record PurlMatch(String purl, boolean knownToGraph, List<VulnerabilityRef> vulnerabilities) {
	}

	/** A vulnerability affecting a matched purl. */
	record VulnerabilityRef(String id, String severity, Double cvssScore, String summary) {
	}

	/**
	 * Cross-module input for a vulnerability upsert, mirroring the OSV fields the graph
	 * persists. {@code id} and {@code modified} are required (OSV schema); everything
	 * else is optional.
	 */
	record VulnerabilityInput(String id, Instant modified, Instant published, Instant withdrawn, String summary,
			String details, List<String> aliases, List<String> related, List<String> upstream,
			List<SeverityInput> severities, List<ReferenceInput> references) {
	}

	/** A raw severity entry: CVSS type (CVSS_V2/CVSS_V3/CVSS_V4/...) and vector string. */
	record SeverityInput(String type, String vector) {
	}

	/** An external reference: OSV reference type (ADVISORY/FIX/REPORT/...) and URL. */
	record ReferenceInput(String type, String url) {
	}

	/** Immutable, cross-module view of an upserted package version. */
	record PackageVersionSnapshot(String purl, String packagePurl, String version) {
	}

	/** Immutable, cross-module view of an upserted versionless package. */
	record PackageSnapshot(String purl, String type, String namespace, String name) {
	}

	/** Immutable, cross-module view of an upserted vulnerability (derived fields included). */
	record VulnerabilitySnapshot(String id, String severity, Double cvssScore) {
	}

}
