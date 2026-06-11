package dev.argusgraph.ingest.worker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import dev.argusgraph.ingest.api.OsvVulnerabilityRequest;
import dev.argusgraph.ingest.application.IngestService;

/**
 * Consume side of the OSV pipeline: parses one raw OSV JSON document, hands it to the
 * existing {@link IngestService} upsert flow, then requests a text embedding for the
 * advisory (summary + details) on the embedding queue — the worker adds transport, not
 * mapping. Parse and ingest failures propagate deliberately: the AMQP listener's retry
 * policy dead-letters the message after exhaustion, preserving the payload for replay.
 * A failed embedding publish also propagates — the redelivered message re-ingests
 * idempotently and publishes again.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OsvDocumentHandler {

	private final ObjectMapper objectMapper;

	private final IngestService ingest;

	private final RawDocumentPublisher publisher;

	public void handle(String rawDocument) {
		OsvVulnerabilityRequest document = this.objectMapper.readValue(rawDocument, OsvVulnerabilityRequest.class);
		IngestService.IngestOsvResult result = this.ingest.ingestOsv(document);
		log.debug("OSV document ingested: id={} versionsLinked={}", result.vulnerability().id(),
				result.affectedVersionsLinked());
		requestEmbedding(document);
	}

	private void requestEmbedding(OsvVulnerabilityRequest document) {
		String text = embeddingText(document);
		if (text == null) {
			log.debug("No embeddable text on advisory {} — skipping embedding request.", document.id());
			return;
		}
		String payload = this.objectMapper.writeValueAsString(new EmbeddingRequested(document.id(), text));
		this.publisher.publish(IngestRouting.EMBEDDING_ROUTING_KEY, payload);
	}

	/** Summary + details, blank parts dropped; null when the advisory carries no text. */
	private static String embeddingText(OsvVulnerabilityRequest document) {
		String summary = blankToNull(document.summary());
		String details = blankToNull(document.details());
		if (summary == null) {
			return details;
		}
		return details == null ? summary : summary + "\n\n" + details;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
