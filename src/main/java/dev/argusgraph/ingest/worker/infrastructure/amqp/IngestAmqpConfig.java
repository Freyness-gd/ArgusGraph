package dev.argusgraph.ingest.worker.infrastructure.amqp;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.argusgraph.ingest.worker.application.IngestRouting;

/**
 * Declares the ingest messaging topology — idempotent on every boot via RabbitAdmin.
 * One topic exchange for raw source documents, one queue per source, and a dead-letter
 * pair: after the listener's retries are exhausted (application.yaml,
 * spring.rabbitmq.listener.simple.retry) the broker routes the rejected message to the
 * DLX under the queue's name, landing it in the matching .dlq for inspection and replay.
 */
@Configuration
class IngestAmqpConfig {

	@Bean
	TopicExchange ingestExchange() {
		return ExchangeBuilder.topicExchange(IngestRouting.EXCHANGE).durable(true).build();
	}

	@Bean
	DirectExchange ingestDeadLetterExchange() {
		return ExchangeBuilder.directExchange(IngestRouting.DEAD_LETTER_EXCHANGE).durable(true).build();
	}

	@Bean
	Queue osvQueue() {
		return QueueBuilder.durable(IngestRouting.OSV_QUEUE)
			.withArgument("x-dead-letter-exchange", IngestRouting.DEAD_LETTER_EXCHANGE)
			.withArgument("x-dead-letter-routing-key", IngestRouting.OSV_QUEUE)
			.build();
	}

	@Bean
	Queue osvDeadLetterQueue() {
		return QueueBuilder.durable(IngestRouting.OSV_DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Binding osvBinding(TopicExchange ingestExchange, Queue osvQueue) {
		return BindingBuilder.bind(osvQueue).to(ingestExchange).with(IngestRouting.OSV_ROUTING_KEY);
	}

	@Bean
	Binding osvDeadLetterBinding(DirectExchange ingestDeadLetterExchange, Queue osvDeadLetterQueue) {
		return BindingBuilder.bind(osvDeadLetterQueue).to(ingestDeadLetterExchange).with(IngestRouting.OSV_QUEUE);
	}

	@Bean
	Queue embeddingQueue() {
		return QueueBuilder.durable(IngestRouting.EMBEDDING_QUEUE)
			.withArgument("x-dead-letter-exchange", IngestRouting.DEAD_LETTER_EXCHANGE)
			.withArgument("x-dead-letter-routing-key", IngestRouting.EMBEDDING_QUEUE)
			.build();
	}

	@Bean
	Queue embeddingDeadLetterQueue() {
		return QueueBuilder.durable(IngestRouting.EMBEDDING_DEAD_LETTER_QUEUE).build();
	}

	@Bean
	Binding embeddingBinding(TopicExchange ingestExchange, Queue embeddingQueue) {
		return BindingBuilder.bind(embeddingQueue).to(ingestExchange).with(IngestRouting.EMBEDDING_ROUTING_KEY);
	}

	@Bean
	Binding embeddingDeadLetterBinding(DirectExchange ingestDeadLetterExchange, Queue embeddingDeadLetterQueue) {
		return BindingBuilder.bind(embeddingDeadLetterQueue)
			.to(ingestDeadLetterExchange)
			.with(IngestRouting.EMBEDDING_QUEUE);
	}

}
