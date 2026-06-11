package dev.argusgraph.ingest.worker.infrastructure.embedding;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.stereotype.Component;

import dev.argusgraph.ingest.worker.application.TextEmbeddingModel;

/**
 * In-process adapter for the embedding port: all-MiniLM-L6-v2 over ONNX Runtime,
 * bundled in the jar — no external model service, CPU inference in single-digit
 * milliseconds, 384-dimensional vectors (mirrored by the graph's vector index). Long
 * texts are truncated by the model's tokenizer (~256 tokens) — fine for advisory
 * dedup/search; revisit with chunking only if retrieval quality disappoints.
 */
@Component
class MiniLmTextEmbeddingModel implements TextEmbeddingModel {

	private final AllMiniLmL6V2EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();

	@Override
	public float[] embed(String text) {
		return this.model.embed(text).content().vector();
	}

	@Override
	public int dimensions() {
		return this.model.dimension();
	}

}
