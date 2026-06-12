package dev.argusgraph.inference.application.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.argusgraph.inference.application.InferenceRepository.Neighbour;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityImputationTest {

	@Test
	void weightedMeanFavoursTheMoreSimilarNeighbour() {
		// near neighbour CRITICAL (9.8, sim 0.95), far neighbour LOW (2.0, sim 0.05).
		SeverityImputation.PredictedScore p = SeverityImputation.predict(
				List.of(new Neighbour(0.95, 9.8), new Neighbour(0.05, 2.0)));
		assertThat(p.cvss()).isBetween(9.0, 9.8);
		assertThat(SeverityBands.of(p.cvss())).isEqualTo("CRITICAL");
		assertThat(p.confidence()).isEqualTo(0.5, org.assertj.core.api.Assertions.within(1e-9)); // mean(0.95,0.05)
	}

	@Test
	void fallsBackToPlainMeanWhenAllWeightsZero() {
		SeverityImputation.PredictedScore p = SeverityImputation.predict(
				List.of(new Neighbour(0.0, 8.0), new Neighbour(0.0, 4.0)));
		assertThat(p.cvss()).isEqualTo(6.0);
	}

	@Test
	void emptyNeighboursYieldsZeroScoreAndConfidence() {
		SeverityImputation.PredictedScore p = SeverityImputation.predict(List.of());
		assertThat(p.cvss()).isEqualTo(0.0);
		assertThat(p.confidence()).isEqualTo(0.0);
	}

}
