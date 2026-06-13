package dev.argusgraph.graph.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.application.Neighbourhood;

/**
 * Read response: a package version's direct graph neighbourhood — its dependencies,
 * dependents, directly-affecting vulnerabilities, and the vulnerabilities that reach it
 * transitively.
 */
@Schema(name = "Neighbourhood")
public record NeighbourhoodResponse(
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String center,
		@Schema(example = "2.14.1") String version, List<String> dependencies, List<String> dependents,
		List<VulnRefResponse> vulnerabilities, List<TransitiveRefResponse> transitive) {

	public static NeighbourhoodResponse from(Neighbourhood neighbourhood) {
		return new NeighbourhoodResponse(neighbourhood.center(), neighbourhood.version(),
				neighbourhood.dependencies(), neighbourhood.dependents(),
				neighbourhood.vulnerabilities().stream().map(VulnRefResponse::from).toList(),
				neighbourhood.transitive().stream().map(TransitiveRefResponse::from).toList());
	}

	/** A directly-affecting vulnerability. */
	@Schema(name = "NeighbourhoodVulnRef")
	public record VulnRefResponse(@Schema(example = "CVE-2021-44228") String id,
			@Schema(example = "CRITICAL") String severity) {

		static VulnRefResponse from(Neighbourhood.VulnRef vuln) {
			return new VulnRefResponse(vuln.id(), vuln.severity());
		}
	}

	/** A transitively-reaching vulnerability with its depth. */
	@Schema(name = "NeighbourhoodTransitiveRef")
	public record TransitiveRefResponse(@Schema(example = "CVE-2021-44228") String id,
			@Schema(example = "CRITICAL") String severity, @Schema(example = "1") int depth) {

		static TransitiveRefResponse from(Neighbourhood.TransitiveRef transitive) {
			return new TransitiveRefResponse(transitive.id(), transitive.severity(), transitive.depth());
		}
	}

}
