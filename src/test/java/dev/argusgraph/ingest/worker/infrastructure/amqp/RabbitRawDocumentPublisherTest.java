package dev.argusgraph.ingest.worker.infrastructure.amqp;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins the adapter's wire contract: everything goes to the argus.ingest exchange with
 * the caller's routing key and the raw JSON as the message body.
 */
class RabbitRawDocumentPublisherTest {

	@Test
	void publishesToTheIngestExchangeWithTheGivenRoutingKey() {
		RabbitTemplate rabbit = mock(RabbitTemplate.class);

		new RabbitRawDocumentPublisher(rabbit).publish("osv.raw", "{\"id\":\"GHSA-aaaa\"}");

		verify(rabbit).convertAndSend("argus.ingest", "osv.raw", "{\"id\":\"GHSA-aaaa\"}");
	}

}
