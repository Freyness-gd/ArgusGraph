package dev.argusgraph.ingest.worker.api;

import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ProblemDetail;

import dev.argusgraph.ingest.worker.application.IngestJobRegistry;
import dev.argusgraph.ingest.worker.application.OsvFetchJob;
import dev.argusgraph.ingest.worker.application.QueueDepthProbe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The controller is a thin trigger — worth pinning: the delegation, and the 429 mapping
 * for a saturated worker executor. Status code (202) and validation are wiring, covered
 * by the integration test.
 */
class IngestJobControllerTest {

	@Test
	void delegatesTheEcosystemToTheFetchJob() {
		OsvFetchJob job = mock(OsvFetchJob.class);

		new IngestJobController(job, new IngestJobRegistry(), mock(QueueDepthProbe.class)).startOsvFetch("Maven");

		verify(job).runOsv("Maven");
	}

	@Test
	void saturatedExecutorMapsTo429() {
		OsvFetchJob job = mock(OsvFetchJob.class);

		ProblemDetail problem = new IngestJobController(job, new IngestJobRegistry(), mock(QueueDepthProbe.class))
			.handleWorkerSaturated(new TaskRejectedException("queue full"));

		assertThat(problem.getStatus()).isEqualTo(429);
	}

}
