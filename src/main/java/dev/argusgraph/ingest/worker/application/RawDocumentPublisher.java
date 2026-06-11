package dev.argusgraph.ingest.worker.application;

/**
 * PORT — outbound: publish one raw source document to the ingest message broker under a
 * routing key (see {@link IngestRouting}). Implemented by the AMQP adapter; swapping
 * brokers touches only the adapter, never this layer.
 *
 * <p>
 * Wire contract: documents travel as UTF-8 {@code text/plain} messages. Publishers in
 * other stacks must set a {@code text/*} content type — the default converter hands the
 * consumer a {@code byte[]} (and the message dead-letters) for anything else.
 */
public interface RawDocumentPublisher {

	void publish(String routingKey, String rawDocument);

}
