package dev.argusgraph.ingest.worker.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import dev.argusgraph.ingest.api.OsvVulnerabilityRequest;
import dev.argusgraph.ingest.application.IngestService;

/**
 * Consume side of the OSV pipeline: parses one raw OSV JSON document and hands it to the
 * existing {@link IngestService} upsert flow — the worker adds transport, not mapping.
 * Parse and ingest failures propagate deliberately: the AMQP listener's retry policy
 * dead-letters the message after exhaustion, preserving the payload for replay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OsvDocumentHandler {

	private final ObjectMapper objectMapper;

	private final IngestService ingest;

	public void handle(String rawDocument) {
		OsvVulnerabilityRequest document = this.objectMapper.readValue(rawDocument, OsvVulnerabilityRequest.class);
		IngestService.IngestOsvResult result = this.ingest.ingestOsv(document);
		log.debug("OSV document ingested: id={} versionsLinked={}", result.vulnerability().id(),
				result.affectedVersionsLinked());
	}

}
