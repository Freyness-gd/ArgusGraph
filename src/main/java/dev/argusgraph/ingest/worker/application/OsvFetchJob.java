package dev.argusgraph.ingest.worker.application;

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
 *
 * <p>
 * Job progress is tracked in the {@link IngestJobRegistry} for the dashboard's status endpoint.
 */
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
@Slf4j
public class OsvFetchJob {

	private final OsvSource source;

	private final RawDocumentPublisher publisher;

	private final IngestJobRegistry registry;

	@Async("ingestWorkerExecutor")
	public void runOsv(String ecosystem) {
		// Registered only once the executor actually starts us — queued submits stay invisible (no QUEUED state in v1).
		IngestJobRegistry.JobRecord job = this.registry.start(ecosystem);
		log.info("OSV fetch started: ecosystem={}", ecosystem);
		try {
			this.source.fetchEcosystem(ecosystem, document -> {
				this.publisher.publish(IngestRouting.OSV_ROUTING_KEY, document);
				job.incrementPublished();
			});
			job.complete();
			log.info("OSV fetch finished: ecosystem={} documentsPublished={}", ecosystem, job.documentsPublished());
		}
		catch (RuntimeException ex) {
			job.fail(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
			log.error("OSV fetch failed: ecosystem={} documentsPublished={}", ecosystem, job.documentsPublished(), ex);
		}
		catch (Error err) {
			// OOME from a hostile/oversized dump must not leave the job stuck RUNNING.
			job.fail(err.toString());
			throw err;
		}
	}

}
