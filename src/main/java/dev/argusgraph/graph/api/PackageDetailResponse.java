package dev.argusgraph.graph.api;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.api.PackageVersionResponse.VulnerabilityRefResponse;
import dev.argusgraph.graph.application.PackageDetails;

/**
 * Read response: one package with all its versions and the vulnerabilities affecting each.
 * Reuses {@link VulnerabilityRefResponse} so the vulnerability shape stays identical to the
 * version-level read response.
 */
@Schema(name = "PackageDetail")
public record PackageDetailResponse(
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core") String packagePurl,
		@Schema(example = "maven") String type, List<VersionResponse> versions) {

	public static PackageDetailResponse from(PackageDetails details) {
		return new PackageDetailResponse(details.packagePurl(), details.type(),
				details.versions().stream().map(VersionResponse::from).toList());
	}

	/** One {@code PackageVersion} under the package. */
	@Schema(name = "PackageDetailVersion")
	public record VersionResponse(
			@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String purl,
			@Schema(example = "2.14.1") String version, List<VulnerabilityRefResponse> vulnerabilities) {

		static VersionResponse from(PackageDetails.Version version) {
			return new VersionResponse(version.purl(), version.version(),
					version.vulnerabilities().stream().map(VulnerabilityRefResponse::from).toList());
		}
	}

}
