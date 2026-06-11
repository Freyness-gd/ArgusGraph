package dev.argusgraph.ingest.worker.infrastructure.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract the rest of the system relies on: 384 dimensions (must match the
 * graph's vector index), deterministic vectors (re-ingesting is idempotent), and
 * distinct texts producing distinct vectors. Runs the real bundled ONNX model — no
 * network, no Docker.
 */
class MiniLmTextEmbeddingModelTest {

	private final MiniLmTextEmbeddingModel model = new MiniLmTextEmbeddingModel();

	@Test
	void produces384DimensionalVectors() {
		float[] vector = this.model.embed("Remote code injection in Log4j");

		assertThat(vector).hasSize(384);
		assertThat(this.model.dimensions()).isEqualTo(384);
	}

	@Test
	void isDeterministicForTheSameText() {
		assertThat(this.model.embed("Log4Shell: JNDI lookup remote code execution."))
			.isEqualTo(this.model.embed("Log4Shell: JNDI lookup remote code execution."));
	}

	@Test
	void distinguishesDifferentTexts() {
		assertThat(this.model.embed("Remote code execution in a logging library"))
			.isNotEqualTo(this.model.embed("Prototype pollution in a utility library"));
	}

}
