package dev.argusgraph.graph;

import org.junit.jupiter.api.Test;

import dev.argusgraph.shared.exception.BusinessRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The purl value object is the natural key of the whole graph — these tests pin down the
 * canonicalisation rules: version split, qualifier/subpath dropping, and rejection of
 * malformed input.
 */
class PurlTest {

	@Test
	void parsesMavenPurlWithVersion() {
		Purl purl = Purl.parse("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1");

		assertThat(purl.type()).isEqualTo("maven");
		assertThat(purl.namespace()).isEqualTo("org.apache.logging.log4j");
		assertThat(purl.name()).isEqualTo("log4j-core");
		assertThat(purl.version()).isEqualTo("2.14.1");
		assertThat(purl.hasVersion()).isTrue();
		assertThat(purl.versionKey()).isEqualTo("pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1");
		assertThat(purl.packageKey()).isEqualTo("pkg:maven/org.apache.logging.log4j/log4j-core");
	}

	@Test
	void parsesNamespacelessPurl() {
		Purl purl = Purl.parse("pkg:npm/lodash@4.17.21");

		assertThat(purl.namespace()).isNull();
		assertThat(purl.packageKey()).isEqualTo("pkg:npm/lodash");
		assertThat(purl.versionKey()).isEqualTo("pkg:npm/lodash@4.17.21");
	}

	@Test
	void dropsQualifiersAndSubpathFromKeys() {
		Purl purl = Purl.parse("pkg:maven/com.acme/lib@1.0.0?type=jar&classifier=sources#src/main");

		assertThat(purl.versionKey()).isEqualTo("pkg:maven/com.acme/lib@1.0.0");
		assertThat(purl.packageKey()).isEqualTo("pkg:maven/com.acme/lib");
	}

	@Test
	void versionlessPurlHasNoVersionKey() {
		Purl purl = Purl.parse("pkg:maven/com.acme/lib");

		assertThat(purl.hasVersion()).isFalse();
		assertThat(purl.packageKey()).isEqualTo("pkg:maven/com.acme/lib");
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(purl::versionKey);
	}

	@Test
	void rejectsMalformedAndBlankInput() {
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> Purl.parse("not a purl"));
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> Purl.parse(""));
		assertThatExceptionOfType(BusinessRuleException.class).isThrownBy(() -> Purl.parse(null));
	}

}
