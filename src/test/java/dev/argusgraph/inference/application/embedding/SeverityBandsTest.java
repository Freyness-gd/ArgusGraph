package dev.argusgraph.inference.application.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityBandsTest {

	@Test
	void mapsScoresToCvssBands() {
		assertThat(SeverityBands.of(9.8)).isEqualTo("CRITICAL");
		assertThat(SeverityBands.of(9.0)).isEqualTo("CRITICAL");
		assertThat(SeverityBands.of(7.5)).isEqualTo("HIGH");
		assertThat(SeverityBands.of(7.0)).isEqualTo("HIGH");
		assertThat(SeverityBands.of(4.0)).isEqualTo("MEDIUM");
		assertThat(SeverityBands.of(0.1)).isEqualTo("LOW");
		assertThat(SeverityBands.of(0.0)).isEqualTo("NONE");
	}
}
