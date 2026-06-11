package dev.argusgraph.project.api;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.project.application.ProjectService;

/** Read response: outcome of an SBOM import. */
@Schema(name = "ProjectImport")
public record ProjectImportResponse(@Schema(example = "1") Long id, @Schema(example = "demo-app") String name,
		@Schema(example = "412") int dependencies, @Schema(example = "3") int skipped) {

	public static ProjectImportResponse from(ProjectService.ImportResult result) {
		return new ProjectImportResponse(result.id(), result.name(), result.dependencies(), result.skipped());
	}

}
