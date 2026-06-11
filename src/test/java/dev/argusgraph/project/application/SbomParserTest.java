package dev.argusgraph.project.application;

import org.junit.jupiter.api.Test;

import dev.argusgraph.shared.exception.BusinessRuleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CycloneDX extraction: canonical purls, skip accounting, name fallback, hard failures. */
class SbomParserTest {

	private final SbomParser parser = new SbomParser();

	@Test
	void extractsCanonicalVersionPurlsAndCountsSkips() {
		String sbom = """
				{"bomFormat":"CycloneDX","specVersion":"1.5",
				 "metadata":{"component":{"name":"demo-app"}},
				 "components":[
				   {"purl":"pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"},
				   {"purl":"pkg:npm/%40scope/pkg@1.0.0"},
				   {"purl":"pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"},
				   {"name":"component-without-purl"},
				   {"purl":"not a purl at all"},
				   {"purl":"pkg:maven/com.acme/versionless"}
				 ]}
				""";

		SbomParser.ParsedSbom parsed = this.parser.parse(sbom);

		assertThat(parsed.defaultName()).isEqualTo("demo-app");
		assertThat(parsed.purls()).containsExactlyInAnyOrder(
				"pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1", "pkg:npm/%40scope/pkg@1.0.0");
		// duplicate + no-purl + unparseable + versionless = 4 skipped (dupes count as skipped)
		assertThat(parsed.skipped()).isEqualTo(4);
	}

	@Test
	void missingMetadataNameIsNull() {
		SbomParser.ParsedSbom parsed = this.parser.parse("{\"components\":[]}");
		assertThat(parsed.defaultName()).isNull();
		assertThat(parsed.purls()).isEmpty();
		assertThat(parsed.skipped()).isZero();
	}

	@Test
	void rejectsNonJsonAndNonSbomBodies() {
		assertThatThrownBy(() -> this.parser.parse("definitely not json"))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("not valid JSON");
		assertThatThrownBy(() -> this.parser.parse("{\"no\":\"components\"}"))
			.isInstanceOf(BusinessRuleException.class)
			.hasMessageContaining("components");
	}

}
