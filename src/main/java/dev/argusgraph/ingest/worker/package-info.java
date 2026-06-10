/**
 * Async ingestion workers: fetch raw documents from external sources, publish them to
 * RabbitMQ, then consume, transform, and persist through the ingest module's
 * {@code IngestService}. Nested inside the ingest module — internal, not a new Modulith
 * module. AMQP and HTTP specifics live only under {@code infrastructure}; the
 * application layer talks to ports.
 */
package dev.argusgraph.ingest.worker;
