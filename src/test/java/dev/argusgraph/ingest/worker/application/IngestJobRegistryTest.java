package dev.argusgraph.ingest.worker.application;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure in-memory behaviour: ring-buffer trim, ordering, state transitions, live counter. */
class IngestJobRegistryTest {

	@Test
	void keepsTheNewestTwentyJobsNewestFirst() {
		IngestJobRegistry registry = new IngestJobRegistry();
		for (int i = 1; i <= 25; i++) {
			registry.start("eco-" + i);
		}

		List<IngestJobRegistry.JobView> snapshot = registry.snapshot();
		assertThat(snapshot).hasSize(20);
		assertThat(snapshot.get(0).ecosystem()).isEqualTo("eco-25");
		assertThat(snapshot.get(19).ecosystem()).isEqualTo("eco-6");
	}

	@Test
	void transitionsAndCounterShowUpInSnapshots() {
		IngestJobRegistry registry = new IngestJobRegistry();
		IngestJobRegistry.JobRecord job = registry.start("npm");
		job.incrementPublished();
		job.incrementPublished();

		IngestJobRegistry.JobView running = registry.snapshot().get(0);
		assertThat(running.state()).isEqualTo(IngestJobRegistry.State.RUNNING);
		assertThat(running.documentsPublished()).isEqualTo(2);
		assertThat(running.finishedAt()).isNull();
		assertThat(running.error()).isNull();

		job.fail("boom");
		IngestJobRegistry.JobView failed = registry.snapshot().get(0);
		assertThat(failed.state()).isEqualTo(IngestJobRegistry.State.FAILED);
		assertThat(failed.error()).isEqualTo("boom");
		assertThat(failed.finishedAt()).isNotNull();

		IngestJobRegistry.JobRecord second = registry.start("maven");
		second.complete();
		IngestJobRegistry.JobView completed = registry.snapshot().get(0);
		assertThat(completed.state()).isEqualTo(IngestJobRegistry.State.COMPLETED);
		assertThat(completed.finishedAt()).isNotNull();
	}

}
