package dev.argusgraph.graph.application;

import java.util.List;

/**
 * Read model: one package version with its direct (depth-1) neighbourhood — outgoing
 * dependency edges and incoming vulnerability edges. Deeper traversals (transitive
 * exposure) are the job of the future inference module.
 */
public record PackageVersionDetails(String purl, String packagePurl, String version, List<Dependency> dependencies,
		List<AffectingVulnerability> vulnerabilities) {

	/** Outgoing {@code DEPENDS_ON} edge. */
	public record Dependency(String purl, String scope) {
	}

	/** Incoming {@code AFFECTS} edge. */
	public record AffectingVulnerability(String id, String severity, Double cvssScore) {
	}

}
