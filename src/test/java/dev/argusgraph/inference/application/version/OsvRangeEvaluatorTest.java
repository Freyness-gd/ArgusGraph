package dev.argusgraph.inference.application.version;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OsvRangeEvaluatorTest {

	private final OsvRangeEvaluator evaluator = new OsvRangeEvaluator();

	private static final String MAVEN_RANGE = """
			[{"type":"ECOSYSTEM","events":[{"introduced":"2.0"},{"fixed":"2.15.0"}]}]
			""";

	@Test
	void inRangeMavenVersionIsAffected() {
		assertThat(evaluator.evaluate(MAVEN_RANGE, "maven", "2.14.1"))
				.isEqualTo(OsvRangeEvaluator.Verdict.AFFECTED);
	}

	@Test
	void fixedVersionIsNotAffected() {
		assertThat(evaluator.evaluate(MAVEN_RANGE, "maven", "2.15.0"))
				.isEqualTo(OsvRangeEvaluator.Verdict.NOT_AFFECTED);
		assertThat(evaluator.evaluate(MAVEN_RANGE, "maven", "1.0"))
				.isEqualTo(OsvRangeEvaluator.Verdict.NOT_AFFECTED);
	}

	@Test
	void lastAffectedIsInclusive() {
		String r = """
				[{"type":"SEMVER","events":[{"introduced":"1.0.0"},{"last_affected":"1.4.0"}]}]
				""";
		assertThat(evaluator.evaluate(r, "npm", "1.4.0")).isEqualTo(OsvRangeEvaluator.Verdict.AFFECTED);
		assertThat(evaluator.evaluate(r, "npm", "1.4.1")).isEqualTo(OsvRangeEvaluator.Verdict.NOT_AFFECTED);
	}

	@Test
	void introducedZeroMeansFromBeginning() {
		String r = """
				[{"type":"SEMVER","events":[{"introduced":"0"},{"fixed":"1.0.0"}]}]
				""";
		assertThat(evaluator.evaluate(r, "npm", "0.0.1")).isEqualTo(OsvRangeEvaluator.Verdict.AFFECTED);
	}

	@Test
	void unsupportedEcosystemIsUnresolvedNotNotAffected() {
		String r = """
				[{"type":"ECOSYSTEM","events":[{"introduced":"1.0"},{"fixed":"2.0"}]}]
				""";
		assertThat(evaluator.evaluate(r, "pypi", "1.5")).isEqualTo(OsvRangeEvaluator.Verdict.UNRESOLVED);
	}

	@Test
	void gitRangeIsUnresolved() {
		String r = """
				[{"type":"GIT","events":[{"introduced":"0"}]}]
				""";
		assertThat(evaluator.evaluate(r, "maven", "1.0")).isEqualTo(OsvRangeEvaluator.Verdict.UNRESOLVED);
	}
}
