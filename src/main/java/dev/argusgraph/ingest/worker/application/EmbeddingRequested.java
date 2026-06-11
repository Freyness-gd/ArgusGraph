package dev.argusgraph.ingest.worker.application;

/**
 * Wire payload of the embedding queue (JSON): which vulnerability to embed and the text
 * to embed (composed at publish time from the advisory's summary + details, so the
 * embedding worker never reads the graph back).
 */
public record EmbeddingRequested(String vulnerabilityId, String text) {
}
