package dev.argusgraph.project.api;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.project.application.ProjectService;

/** Read response: one project in the list. */
@Schema(name = "ProjectSummary")
public record ProjectSummaryResponse(@Schema(example = "1") Long id, @Schema(example = "demo-app") String name,
		Instant createdAt, @Schema(example = "412") int dependencyCount) {

	public static ProjectSummaryResponse from(ProjectService.ProjectSummary summary) {
		return new ProjectSummaryResponse(summary.id(), summary.name(), summary.createdAt(),
				summary.dependencyCount());
	}

}
