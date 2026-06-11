package dev.argusgraph.ingest.worker.application;

/**
 * Port: read a queue's current message count. Returns {@code null} when the broker
 * cannot answer (unreachable, queue not declared yet) — status reporting must degrade,
 * never fail.
 */
public interface QueueDepthProbe {

	Integer depth(String queueName);

}
