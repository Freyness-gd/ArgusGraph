package dev.argusgraph.ingest.worker.infrastructure.amqp;

import dev.argusgraph.ingest.worker.application.IngestRouting;
import dev.argusgraph.ingest.worker.application.OsvDocumentHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * AMQP edge of the consume side: one raw OSV JSON document per message, delegated
 * straight to the handler. Exceptions propagate into the container's retry policy;
 * exhausted retries dead-letter the message (see IngestAmqpConfig).
 *
 * <p>
 * Wire contract: the {@code String} parameter binds only for {@code text/*} messages
 * under the default {@code SimpleMessageConverter} — see {@code RawDocumentPublisher}.
 */
@Component
@RequiredArgsConstructor
class OsvQueueListener {

	private final OsvDocumentHandler handler;

	@RabbitListener(queues = IngestRouting.OSV_QUEUE, concurrency = "4-8")
	void onOsvDocument(String rawDocument) {
		this.handler.handle(rawDocument);
	}

}
