package dev.argusgraph.ingest.worker.infrastructure.amqp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.stereotype.Component;

import dev.argusgraph.ingest.worker.application.QueueDepthProbe;

/**
 * Reads queue depths through {@link AmqpAdmin}. Returns {@code null} instead of
 * throwing when the broker is unreachable or the queue does not exist — the status
 * endpoint stays 200 with partial data.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class AmqpQueueDepthProbe implements QueueDepthProbe {

	private final AmqpAdmin amqp;

	@Override
	public Integer depth(String queueName) {
		try {
			QueueInformation info = this.amqp.getQueueInfo(queueName);
			return info == null ? null : (int) info.getMessageCount();
		}
		catch (RuntimeException ex) {
			log.debug("Queue depth probe failed for {}: {}", queueName, ex.getMessage());
			return null;
		}
	}

}
