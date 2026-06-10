package dev.argusgraph.ingest.api;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.GraphAPI;

/** The upserted package version, echoed back with its canonical keys. */
@Schema(name = "IngestPackageVersionResponse")
public record IngestPackageVersionResponse(
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String purl,
		@Schema(example = "pkg:maven/org.apache.logging.log4j/log4j-core") String packagePurl,
		@Schema(example = "2.14.1") String version) {

	static IngestPackageVersionResponse from(GraphAPI.PackageVersionSnapshot snapshot) {
		return new IngestPackageVersionResponse(snapshot.purl(), snapshot.packagePurl(), snapshot.version());
	}
}
