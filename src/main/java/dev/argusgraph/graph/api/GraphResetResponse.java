package dev.argusgraph.graph.api;

import io.swagger.v3.oas.annotations.media.Schema;

/** Read response: result of a whole-graph wipe. */
@Schema(name = "GraphReset")
public record GraphResetResponse(@Schema(example = "12345") long nodesDeleted) {
}
