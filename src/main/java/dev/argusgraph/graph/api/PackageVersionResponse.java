package dev.argusgraph.graph.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.application.PackageVersionDetails;

/**
 * Read response: a package version with its direct dependencies and the vulnerabilities
 * known to affect it.
 */
@Schema(name = "PackageVersion")
public record PackageVersionResponse(
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String purl,
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core") String packagePurl,
		@Schema(example = "2.14.1") String version, List<DependencyResponse> dependencies,
		List<VulnerabilityRefResponse> vulnerabilities) {

	public static PackageVersionResponse from(PackageVersionDetails details) {
		return new PackageVersionResponse(details.purl(), details.packagePurl(), details.version(),
				details.dependencies().stream().map(DependencyResponse::from).toList(),
				details.vulnerabilities().stream().map(VulnerabilityRefResponse::from).toList());
	}

	/** Outgoing {@code DEPENDS_ON} edge. */
	@Schema(name = "Dependency")
	public record DependencyResponse(@Schema(example = "pkg:maven/com.acme/lib@1.2.3") String purl,
			@Schema(example = "compile") String scope) {

		static DependencyResponse from(PackageVersionDetails.Dependency dependency) {
			return new DependencyResponse(dependency.purl(), dependency.scope());
		}
	}

	/** Incoming {@code AFFECTS} edge. */
	@Schema(name = "VulnerabilityRef")
	public record VulnerabilityRefResponse(@Schema(example = "CVE-2021-44228") String id,
			@Schema(example = "CRITICAL") String severity, @Schema(example = "10.0") Double cvssScore,
			@Schema(example = "Remote code injection in Log4j") String summary) {

		static VulnerabilityRefResponse from(PackageVersionDetails.AffectingVulnerability vulnerability) {
			return new VulnerabilityRefResponse(vulnerability.id(), vulnerability.severity(),
					vulnerability.cvssScore(), vulnerability.summary());
		}
	}

}
