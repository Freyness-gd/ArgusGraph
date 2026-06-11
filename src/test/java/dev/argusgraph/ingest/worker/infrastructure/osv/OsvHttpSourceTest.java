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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Test
	void truncatedZipThrowsIoException() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("GHSA-aaaa.json"));
			// Large payload so that cutting at byte 50 lands inside the compressed data stream,
			// guaranteeing an "Unexpected end of ZLIB input stream" IOException rather than a
			// silent null return (ZipInputStream ignores the central directory on truncation).
			String payload = "{\"id\":\"GHSA-aaaa\",\"data\":\"" + "x".repeat(200) + "\"}";
			zip.write(payload.getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		byte[] truncated = java.util.Arrays.copyOf(bytes.toByteArray(), 50);

		List<String> documents = new ArrayList<>();
		assertThatThrownBy(() -> OsvHttpSource.streamZipDocuments(new ByteArrayInputStream(truncated), documents::add))
			.isInstanceOf(IOException.class);
		assertThat(documents).isEmpty();
	}

}
