package dev.argusgraph.ingest.worker.application;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The fetch job is a pure orchestration loop over two ports — both faked here as
 * lambdas: every document the source emits must be published under the OSV routing key,
 * and a source failure must not propagate (the job logs and stops; re-trigger resumes).
 */
class OsvFetchJobTest {

	@Test
	void publishesEveryFetchedDocumentUnderTheOsvRoutingKey() {
		List<String> published = new ArrayList<>();
		OsvSource source = (ecosystem, onDocument) -> {
			onDocument.accept("{\"id\":\"GHSA-aaaa\"}");
			onDocument.accept("{\"id\":\"GHSA-bbbb\"}");
		};
		RawDocumentPublisher publisher = (routingKey, document) -> published.add(routingKey + "|" + document);

		new OsvFetchJob(source, publisher, new IngestJobRegistry()).runOsv("Maven");

		assertThat(published).containsExactly("osv.raw|{\"id\":\"GHSA-aaaa\"}", "osv.raw|{\"id\":\"GHSA-bbbb\"}");
	}

	@Test
	void sourceFailureIsLoggedNotPropagated() {
		OsvSource source = (ecosystem, onDocument) -> {
			throw new IllegalStateException("OSV unreachable");
		};
		RawDocumentPublisher publisher = (routingKey, document) -> {
		};

		assertThatCode(() -> new OsvFetchJob(source, publisher, new IngestJobRegistry()).runOsv("Maven")).doesNotThrowAnyException();
	}

	@Test
	void publisherFailureMidStreamStopsTheJobWithoutPropagating() {
		List<String> published = new ArrayList<>();
		OsvSource source = (ecosystem, onDocument) -> {
			onDocument.accept("{\"id\":\"GHSA-aaaa\"}");
			onDocument.accept("{\"id\":\"GHSA-bbbb\"}");
		};
		RawDocumentPublisher publisher = (routingKey, document) -> {
			if (!published.isEmpty()) {
				throw new IllegalStateException("broker hiccup");
			}
			published.add(document);
		};

		assertThatCode(() -> new OsvFetchJob(source, publisher, new IngestJobRegistry()).runOsv("Maven")).doesNotThrowAnyException();
		assertThat(published).containsExactly("{\"id\":\"GHSA-aaaa\"}");
	}

}
