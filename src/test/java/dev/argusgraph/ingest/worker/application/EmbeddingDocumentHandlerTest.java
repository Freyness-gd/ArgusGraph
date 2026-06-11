package dev.argusgraph.ingest.worker.application;

import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import dev.argusgraph.graph.GraphAPI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The embedding handler is the consume-side seam of the embedding queue: JSON payload
 * in, model call + GraphAPI attach out. Malformed JSON must propagate — that's what
 * drives the listener's retry-then-dead-letter path in production.
 */
class EmbeddingDocumentHandlerTest {

	private static final float[] VECTOR = new float[] { 0.1f, 0.2f, 0.3f };

	private final TextEmbeddingModel model = mock(TextEmbeddingModel.class);

	private final GraphAPI graph = mock(GraphAPI.class);

	private final EmbeddingDocumentHandler handler = new EmbeddingDocumentHandler(new JsonMapper(), this.model,
			this.graph);

	@Test
	void embedsTheTextAndAttachesTheVector() {
		when(this.model.embed("Remote code injection in Log4j")).thenReturn(VECTOR);

		this.handler.handle("""
				{"vulnerabilityId":"GHSA-jfh8-c2jp-5v3q","text":"Remote code injection in Log4j"}
				""");

		verify(this.graph).attachEmbedding("GHSA-jfh8-c2jp-5v3q", VECTOR);
	}

	@Test
	void malformedPayloadPropagatesAndNothingIsAttached() {
		assertThatThrownBy(() -> this.handler.handle("not json")).isInstanceOf(JacksonException.class);
		verifyNoInteractions(this.model, this.graph);
	}

}
