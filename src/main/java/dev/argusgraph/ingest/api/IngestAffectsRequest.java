package dev.argusgraph.ingest.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Record that a vulnerability affects a package version (both are upserted). */
@Schema(name = "IngestAffectsRequest")
public record IngestAffectsRequest(
		@NotBlank(message = "vulnerabilityId is required") @Schema(example = "CVE-2021-44228") String vulnerabilityId,
		@NotBlank(message = "purl is required") @Schema(
				example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String purl) {
}
