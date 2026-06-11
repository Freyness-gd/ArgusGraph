package dev.argusgraph.ingest.worker.infrastructure.osv;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.argusgraph.ingest.worker.application.OsvSource;

/**
 * HTTP adapter for the OSV source port: streams an ecosystem dump
 * ({@code <base>/<ecosystem>/all.zip}, one JSON document per zip entry) straight off the
 * response body — nothing is written to disk and only one document is in memory at a
 * time. The base URL is configurable so tests never touch the live bucket.
 *
 * <p>
 * Timeouts guard the single worker thread: 30 s to connect, 10 min to response headers.
 * A stall mid-stream is not bounded by these — acceptable for now, the job is manually
 * re-triggerable and consumption is idempotent.
 */
@Component
@Slf4j
class OsvHttpSource implements OsvSource {

	private final HttpClient http = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(30))
		.build();

	private final String baseUrl;

	OsvHttpSource(@Value("${argus.ingest.osv.base-url:https://osv-vulnerabilities.storage.googleapis.com}") String baseUrl) {
		this.baseUrl = baseUrl;
	}

	@Override
	public void fetchEcosystem(String ecosystem, Consumer<String> onDocument) {
		// Ecosystem names may contain spaces (e.g. "Rocky Linux") — encode just those.
		URI uri = URI.create(this.baseUrl + "/" + ecosystem.replace(" ", "%20") + "/all.zip");
		log.info("Downloading OSV dump: {}", uri);
		HttpRequest request = HttpRequest.newBuilder(uri).GET().timeout(Duration.ofMinutes(10)).build();
		try {
			HttpResponse<InputStream> response = this.http.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("OSV dump fetch failed: HTTP " + response.statusCode() + " for " + uri);
			}
			try (InputStream body = response.body()) {
				streamZipDocuments(body, onDocument);
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException("OSV dump fetch failed for " + uri, ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("OSV dump fetch interrupted for " + uri, ex);
		}
	}

	/** One {@code .json} zip entry = one OSV document. Package-visible for the unit test. */
	static void streamZipDocuments(InputStream in, Consumer<String> onDocument) throws IOException {
		try (ZipInputStream zip = new ZipInputStream(in)) {
			for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				if (!entry.isDirectory() && entry.getName().endsWith(".json")) {
					onDocument.accept(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
				}
			}
		}
	}

}
