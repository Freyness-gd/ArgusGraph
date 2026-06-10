package dev.argusgraph.ingest.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Record a dependency edge between two package versions (both are upserted). */
@Schema(name = "IngestDependencyRequest")
public record IngestDependencyRequest(
		@NotBlank(message = "fromPurl is required") @Schema(
				example = "pkg:maven/dev.argusgraph/demo-app@1.0.0") String fromPurl,
		@NotBlank(message = "toPurl is required") @Schema(
				example = "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1") String toPurl,
		@Schema(example = "compile", description = "Optional dependency scope.") String scope) {
}
