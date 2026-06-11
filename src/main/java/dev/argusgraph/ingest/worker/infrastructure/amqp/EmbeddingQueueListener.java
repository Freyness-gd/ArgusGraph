package dev.argusgraph.ingest.worker.infrastructure.amqp;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import dev.argusgraph.ingest.worker.application.EmbeddingDocumentHandler;
import dev.argusgraph.ingest.worker.application.IngestRouting;

/**
 * AMQP edge of the embedding pipeline: one {@code EmbeddingRequested} JSON payload per
 * message, delegated straight to the handler. Exceptions propagate into the container's
 * retry policy; exhausted retries dead-letter the message (see IngestAmqpConfig).
 */
@Component
@RequiredArgsConstructor
class EmbeddingQueueListener {

	private final EmbeddingDocumentHandler handler;

	@RabbitListener(queues = IngestRouting.EMBEDDING_QUEUE)
	void onEmbeddingRequested(String rawRequest) {
		this.handler.handle(rawRequest);
	}

}
