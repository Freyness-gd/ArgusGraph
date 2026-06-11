package dev.argusgraph.ingest.worker.application;

import java.util.function.Consumer;

/**
 * PORT — inbound data source: stream every raw OSV JSON document of one ecosystem to the
 * consumer, one document at a time (dumps are large — never materialise the whole set).
 * Implemented by the HTTP dump adapter; tests substitute a stub to drive the pipeline
 * without the network.
 */
public interface OsvSource {

	void fetchEcosystem(String ecosystem, Consumer<String> onDocument);

}
