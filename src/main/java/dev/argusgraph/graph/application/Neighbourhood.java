package dev.argusgraph.graph.application;

import java.util.List;

/** A package-version's direct graph neighbourhood: deps, dependents, and (direct + transitive) vulns. */
public record Neighbourhood(String center, String version, List<String> dependencies, List<String> dependents,
		List<VulnRef> vulnerabilities, List<TransitiveRef> transitive) {

	/** A directly-affecting vulnerability. */
	public record VulnRef(String id, String severity) {
	}

	/** A transitively-reaching vulnerability with its depth. */
	public record TransitiveRef(String id, String severity, int depth) {
	}
}
