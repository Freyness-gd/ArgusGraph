package dev.argusgraph.inference.application;

import org.junit.jupiter.api.Test;

import dev.argusgraph.inference.InferenceAPI.RunResult;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceRunLogTest {

	@Test
	void keepsNewestFirstAndEvictsBeyondCapacity() {
		InferenceRunLog log = new InferenceRunLog();
		for (int i = 0; i < 60; i++) {
			log.record(new RunResult("naive", i, i, i, i, i));
		}
		var recent = log.recent();
		assertThat(recent).hasSize(50);
		assertThat(recent.get(0).durationMs()).isEqualTo(59); // newest first
		assertThat(recent).noneMatch(r -> r.durationMs() < 10); // oldest 10 evicted
	}

}
