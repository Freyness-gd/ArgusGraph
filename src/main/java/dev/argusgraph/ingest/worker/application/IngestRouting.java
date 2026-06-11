package dev.argusgraph.ingest.worker.application;

/**
 * Names of the ingest messaging topology. Plain strings so the application layer and the
 * AMQP adapter share one vocabulary without the application layer importing AMQP types.
 * Adding a source = new routing key + queue (+ dead-letter queue) here, declared in
 * {@code IngestAmqpConfig}.
 */
public final class IngestRouting {

	public static final String EXCHANGE = "argus.ingest";

	public static final String DEAD_LETTER_EXCHANGE = "argus.ingest.dlx";

	public static final String OSV_ROUTING_KEY = "osv.raw";

	public static final String OSV_QUEUE = "ingest.osv";

	public static final String OSV_DEAD_LETTER_QUEUE = "ingest.osv.dlq";

	private IngestRouting() {
	}

}
