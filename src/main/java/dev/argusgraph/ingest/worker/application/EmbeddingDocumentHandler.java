package dev.argusgraph.ingest.worker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import dev.argusgraph.graph.GraphAPI;

/**
 * Consume side of the embedding pipeline: parses one {@link EmbeddingRequested} payload,
 * computes the vector in-process, and attaches it to the vulnerability node through the
 * published {@link GraphAPI} contract. Failures propagate deliberately: the AMQP
 * listener's retry policy dead-letters the message after exhaustion. Recomputation is
 * idempotent — same text, same vector.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmbeddingDocumentHandler {

	private final ObjectMapper objectMapper;

	private final TextEmbeddingModel model;

	private final GraphAPI graph;

	public void handle(String rawRequest) {
		EmbeddingRequested request = this.objectMapper.readValue(rawRequest, EmbeddingRequested.class);
		float[] vector = this.model.embed(request.text());
		this.graph.attachEmbedding(request.vulnerabilityId(), vector);
		log.debug("Embedding attached: id={} dimensions={}", request.vulnerabilityId(), vector.length);
	}

}
