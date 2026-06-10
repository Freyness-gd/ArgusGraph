package dev.argusgraph.ingest.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Upsert a package version by purl (must include a version). */
@Schema(name = "IngestPackageVersionRequest")
public record IngestPackageVersionRequest(@NotBlank(message = "purl is required") @Schema(
		example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String purl) {
}
