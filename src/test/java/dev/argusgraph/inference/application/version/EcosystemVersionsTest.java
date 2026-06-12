package dev.argusgraph.inference.application.version;

import java.util.Comparator;
import java.util.Optional;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EcosystemVersionsTest {

	@Test
	void semverOrdersNumericallyAndPrereleaseBeforeRelease() {
		Comparator<String> c = SemverComparator.INSTANCE;
		assertThat(c.compare("1.2.0", "1.10.0")).isNegative();   // numeric, not lexical
		assertThat(c.compare("1.0.0-alpha", "1.0.0")).isNegative();
		assertThat(c.compare("1.0.0-alpha.1", "1.0.0-alpha.2")).isNegative();
		assertThat(c.compare("1.0.0-alpha", "1.0.0-alpha.1")).isNegative(); // fewer ids < more
		assertThat(c.compare("2.0.0", "2.0.0")).isZero();
	}

	@Test
	void selectsSemverForSemverRangeType() {
		assertThat(EcosystemVersions.comparatorFor("SEMVER", "npm")).isPresent();
		assertThat(EcosystemVersions.comparatorFor("SEMVER", "anything")).isPresent();
	}

	@Test
	void selectsMavenForEcosystemMavenAndOrdersQualifiers() {
		Optional<Comparator<String>> maven = EcosystemVersions.comparatorFor("ECOSYSTEM", "maven");
		assertThat(maven).isPresent();
		assertThat(maven.get().compare("1.0-alpha", "1.0")).isNegative();
		// ComparableVersion is the source of truth for Maven qualifier ordering; the comparator
		// must agree with it (it treats a ".RELEASE" qualifier as equal to the bare version).
		assertThat(sign(maven.get().compare("1.0.0.RELEASE", "1.0.0")))
				.isEqualTo(sign(new ComparableVersion("1.0.0.RELEASE").compareTo(new ComparableVersion("1.0.0"))));
	}

	private static int sign(int value) {
		return Integer.compare(value, 0);
	}

	@Test
	void unsupportedEcosystemAndGitYieldEmpty() {
		assertThat(EcosystemVersions.comparatorFor("ECOSYSTEM", "pypi")).isEmpty();
		assertThat(EcosystemVersions.comparatorFor("GIT", "maven")).isEmpty();
		assertThat(EcosystemVersions.comparatorFor("ECOSYSTEM", null)).isEmpty();
	}
}
