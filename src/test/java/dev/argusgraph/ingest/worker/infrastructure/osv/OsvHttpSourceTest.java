package dev.argusgraph.ingest.worker.infrastructure.osv;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the zip-streaming core with an in-memory archive shaped like an OSV
 * ecosystem dump (one JSON file per advisory). Non-JSON entries must be skipped.
 * The HTTP layer above it is deliberately untested here — the integration test covers
 * wiring, and we never hit the live OSV bucket from tests.
 */
class OsvHttpSourceTest {

	@Test
	void streamsEachJsonZipEntryAsOneDocument() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("GHSA-aaaa.json"));
			zip.write("{\"id\":\"GHSA-aaaa\"}".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("GHSA-bbbb.json"));
			zip.write("{\"id\":\"GHSA-bbbb\"}".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("README.txt"));
			zip.write("not an advisory".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}

		List<String> documents = new ArrayList<>();
		OsvHttpSource.streamZipDocuments(new ByteArrayInputStream(bytes.toByteArray()), documents::add);

		assertThat(documents).containsExactly("{\"id\":\"GHSA-aaaa\"}", "{\"id\":\"GHSA-bbbb\"}");
	}

}
