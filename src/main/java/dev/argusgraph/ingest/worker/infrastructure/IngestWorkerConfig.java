package dev.argusgraph.ingest.worker.infrastructure;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import dev.argusgraph.ingest.worker.application.IngestJobRegistry;
import dev.argusgraph.ingest.worker.application.OsvFetchJob;
import dev.argusgraph.ingest.worker.application.OsvSource;
import dev.argusgraph.ingest.worker.application.RawDocumentPublisher;

/**
 * Wires the worker slice: the executor backing {@code @Async("ingestWorkerExecutor")}
 * fetch jobs (deliberately tiny — one fetch at a time, a short queue absorbs
 * re-triggers, submits beyond that are rejected) and the explicit {@link OsvFetchJob}
 * bean. The job is registered here rather than component-scanned so the application
 * context only ever sees it together with the port adapters it needs.
 * {@code @EnableAsync} already lives on {@code ArgusGraphApplication}.
 */
@Configuration
class IngestWorkerConfig {

	@Bean(name = "ingestWorkerExecutor")
	Executor ingestWorkerExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(4);
		executor.setThreadNamePrefix("ingest-worker-");
		return executor;
	}

	@Bean
	IngestJobRegistry ingestJobRegistry() {
		return new IngestJobRegistry();
	}

	@Bean
	OsvFetchJob osvFetchJob(OsvSource source, RawDocumentPublisher publisher, IngestJobRegistry registry) {
		return new OsvFetchJob(source, publisher, registry);
	}

}
