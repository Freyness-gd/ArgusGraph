package dev.argusgraph.graph.application;

import java.util.List;

import dev.argusgraph.graph.application.PackageVersionDetails.AffectingVulnerability;

/**
 * Read model: one package with all its versions, each carrying the vulnerabilities known
 * to affect it. Reuses {@link AffectingVulnerability} so the vulnerability shape stays
 * identical to the version-level read model.
 */
public record PackageDetails(String packagePurl, String type, List<Version> versions) {

	/** One {@code PackageVersion} under the package, with its incoming {@code AFFECTS} edges. */
	public record Version(String purl, String version, List<AffectingVulnerability> vulnerabilities) {
	}

}
