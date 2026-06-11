package dev.argusgraph.ingest.worker.application;

/**
 * PORT — outbound: turn one text into an embedding vector. Implemented by the in-process
 * ONNX adapter; swapping models (Ollama, hosted API) touches only the adapter — and the
 * vector index dimensions in the graph module's schema initializer.
 */
public interface TextEmbeddingModel {

	float[] embed(String text);

	/** Vector dimensionality — must match the graph's vector index. */
	int dimensions();

}
