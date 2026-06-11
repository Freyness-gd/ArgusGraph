package dev.argusgraph.graph.api;

import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.graph.application.GraphStats;

/** Read response: whole-graph counts for the dashboard. */
@Schema(name = "GraphStats")
public record GraphStatsResponse(@Schema(example = "1204") long packages,
		@Schema(example = "8911") long packageVersions, @Schema(example = "312") long vulnerabilities,
		@Schema(example = "{\"CRITICAL\": 12, \"HIGH\": 87, \"NONE\": 3}") Map<String, Long> bySeverity) {

	public static GraphStatsResponse from(GraphStats stats) {
		return new GraphStatsResponse(stats.packages(), stats.packageVersions(), stats.vulnerabilities(),
				stats.bySeverity());
	}

}
