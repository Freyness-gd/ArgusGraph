package dev.argusgraph.ingest.worker.infrastructure.amqp;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import dev.argusgraph.ingest.worker.application.IngestRouting;
import dev.argusgraph.ingest.worker.application.RawDocumentPublisher;

/**
 * AMQP adapter for the publish port: raw JSON documents go to the ingest topic exchange
 * as plain text messages under the caller's routing key.
 */
@Component
@RequiredArgsConstructor
class RabbitRawDocumentPublisher implements RawDocumentPublisher {

	private final RabbitTemplate rabbit;

	@Override
	public void publish(String routingKey, String rawDocument) {
		this.rabbit.convertAndSend(IngestRouting.EXCHANGE, routingKey, rawDocument);
	}

}
