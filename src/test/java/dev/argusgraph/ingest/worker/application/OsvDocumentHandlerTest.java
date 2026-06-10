package dev.argusgraph.ingest.worker.application;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import dev.argusgraph.ingest.api.OsvVulnerabilityRequest;
import dev.argusgraph.ingest.application.IngestService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The handler is the consume-side seam: raw OSV JSON in, IngestService call out. Uses a
 * real JsonMapper (Jackson 3 handles java.time out of the box) and a mocked
 * IngestService. Malformed JSON must propagate — that's what drives the listener's
 * retry-then-dead-letter path in production.
 */
class OsvDocumentHandlerTest {

	private final IngestService ingest = mock(IngestService.class);

	private final OsvDocumentHandler handler = new OsvDocumentHandler(new JsonMapper(), this.ingest);

	@Test
	void parsesRawOsvJsonAndDelegatesToIngestService() {
		when(this.ingest.ingestOsv(any()))
			.thenReturn(mock(IngestService.IngestOsvResult.class, RETURNS_DEEP_STUBS));

		this.handler.handle("""
				{"id":"GHSA-jfh8-c2jp-5v3q","modified":"2024-03-15T12:00:00Z",
				 "summary":"Remote code injection in Log4j"}
				""");

		ArgumentCaptor<OsvVulnerabilityRequest> captor = ArgumentCaptor.forClass(OsvVulnerabilityRequest.class);
		verify(this.ingest).ingestOsv(captor.capture());
		assertThat(captor.getValue().id()).isEqualTo("GHSA-jfh8-c2jp-5v3q");
		assertThat(captor.getValue().modified()).isEqualTo(Instant.parse("2024-03-15T12:00:00Z"));
		assertThat(captor.getValue().summary()).isEqualTo("Remote code injection in Log4j");
	}

	@Test
	void malformedJsonPropagatesAndNothingIsIngested() {
		assertThatThrownBy(() -> this.handler.handle("this is not json"))
			.isInstanceOf(JacksonException.class);
		verifyNoInteractions(this.ingest);
	}

}
