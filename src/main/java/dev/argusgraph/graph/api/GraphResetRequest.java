package dev.argusgraph.graph.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Confirmation body for the destructive graph reset. Requiring a JSON body (instead of
 * a bare POST) forces a CORS preflight, so a foreign webpage cannot fire-and-forget a
 * wipe against a locally running instance.
 */
@Schema(name = "GraphResetRequest")
public record GraphResetRequest(@Schema(example = "WIPE") String confirm) {
}
