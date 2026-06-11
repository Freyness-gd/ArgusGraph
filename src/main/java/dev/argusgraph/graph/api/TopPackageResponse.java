package dev.argusgraph.graph.api;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.application.PackageHits;

/** Read response: one package ranked by distinct affecting vulnerabilities. */
@Schema(name = "TopPackage")
public record TopPackageResponse(
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core") String packagePurl,
		@Schema(example = "12") long vulnerabilities) {

	public static TopPackageResponse from(PackageHits hits) {
		return new TopPackageResponse(hits.packagePurl(), hits.vulnerabilities());
	}

}
