package dev.argusgraph.ingest.worker.application;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

/**
 * Fetch side of the OSV pipeline: streams every document of one ecosystem from the
 * {@link OsvSource} and publishes each raw JSON document to the ingest exchange. Runs
 * async so the REST trigger returns immediately. Failures are logged, not propagated:
 * consuming is idempotent, so re-triggering the job after a partial run just resumes.
 *
 * <p>
 * Deliberately NOT component-scanned: registered as an explicit bean in
 * {@code IngestWorkerConfig} once both port adapters exist — keeps every commit's
 * application context loadable while the worker slice is built up task by task.
 */
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
@Slf4j
public class OsvFetchJob {

	private final OsvSource source;

	private final RawDocumentPublisher publisher;

	@Async("ingestWorkerExecutor")
	public void runOsv(String ecosystem) {
		log.info("OSV fetch started: ecosystem={}", ecosystem);
		AtomicInteger published = new AtomicInteger();
		try {
			this.source.fetchEcosystem(ecosystem, document -> {
				this.publisher.publish(IngestRouting.OSV_ROUTING_KEY, document);
				published.incrementAndGet();
			});
			log.info("OSV fetch finished: ecosystem={} documentsPublished={}", ecosystem, published.get());
		}
		catch (RuntimeException ex) {
			log.error("OSV fetch failed: ecosystem={} documentsPublished={}", ecosystem, published.get(), ex);
		}
	}

}
