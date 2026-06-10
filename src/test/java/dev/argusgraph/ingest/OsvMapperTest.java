package dev.argusgraph.ingest;

import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import dev.argusgraph.graph.Purl;
import dev.argusgraph.ingest.api.OsvVulnerabilityRequest.OsvEvent;
import dev.argusgraph.ingest.api.OsvVulnerabilityRequest.OsvPackage;
import dev.argusgraph.ingest.api.OsvVulnerabilityRequest.OsvRange;
import dev.argusgraph.ingest.application.OsvMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the OSV package-identity → purl rules: document purl wins, ecosystem+name pairs
 * map through the ecosystem table (Maven group:artifact split, Go module paths, npm
 * scopes, release suffixes), and unknown ecosystems fall back to pkg:generic.
 */
class OsvMapperTest {

	private final OsvMapper mapper = new OsvMapper(JsonMapper.builder().build());

	@Test
	void prefersDocumentPurlOverEcosystemName() {
		Purl purl = this.mapper.toPackagePurl(new OsvPackage("Maven", "wrong:name",
				"pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1?type=jar"));

		assertThat(purl.packageKey()).isEqualTo("pkg:maven/org.apache.logging.log4j/log4j-core");
	}

	@Test
	void splitsMavenGroupArtifactNames() {
		Purl purl = this.mapper.toPackagePurl(new OsvPackage("Maven", "org.apache.logging.log4j:log4j-core", null));

		assertThat(purl.packageKey()).isEqualTo("pkg:maven/org.apache.logging.log4j/log4j-core");
	}

	@Test
	void splitsGoModulePaths() {
		Purl purl = this.mapper.toPackagePurl(new OsvPackage("Go", "github.com/gin-gonic/gin", null));

		assertThat(purl.packageKey()).isEqualTo("pkg:golang/github.com/gin-gonic/gin");
	}

	@Test
	void handlesScopedNpmNames() {
		Purl purl = this.mapper.toPackagePurl(new OsvPackage("npm", "@babel/traverse", null));

		assertThat(purl.packageKey()).isEqualTo("pkg:npm/%40babel/traverse");
	}

	@Test
	void stripsEcosystemReleaseSuffix() {
		Purl purl = this.mapper.toPackagePurl(new OsvPackage("Debian:11", "openssl", null));

		assertThat(purl.packageKey()).isEqualTo("pkg:deb/openssl");
	}

	@Test
	void fallsBackToGenericForUnknownEcosystems() {
		Purl purl = this.mapper.toPackagePurl(new OsvPackage("AlmaLinux", "kernel", null));

		assertThat(purl.packageKey()).isEqualTo("pkg:generic/almalinux/kernel");
	}

	@Test
	void returnsNullWithoutUsableIdentity() {
		assertThat(this.mapper.toPackagePurl(null)).isNull();
		assertThat(this.mapper.toPackagePurl(new OsvPackage(null, null, null))).isNull();
		assertThat(this.mapper.toPackagePurl(new OsvPackage("npm", " ", null))).isNull();
	}

	@Test
	void serialisesRangesVerbatim() {
		String json = this.mapper
			.rangesJson(List.of(new OsvRange("ECOSYSTEM", null,
					List.of(new OsvEvent("2.0-beta9", null, null, null), new OsvEvent(null, "2.15.0", null, null)))));

		assertThat(json).contains("\"introduced\":\"2.0-beta9\"").contains("\"fixed\":\"2.15.0\"");
	}

}
