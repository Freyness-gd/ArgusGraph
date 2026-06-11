package dev.argusgraph;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import dev.argusgraph.ingest.worker.application.OsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full worker pipeline against real brokers: REST trigger → fetch job (stubbed OSV
 * source — never the live bucket) → RabbitMQ → listener → IngestService → Neo4j. The
 * second test bypasses the fetch side and publishes straight to the exchange, pinning
 * the wire contract (exchange and routing-key names) a future separate worker process
 * would rely on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import({ TestcontainersConfiguration.class, IngestWorkerIntegrationTest.StubOsvSourceConfiguration.class })
class IngestWorkerIntegrationTest {

	private static final CountDownLatch RELEASE_BLOCKED_FETCHES = new CountDownLatch(1);

	private static final String TRIGGERED_DOC = """
			{"id":"GHSA-wrkr-trig-0001","modified":"2024-01-01T00:00:00Z",
			 "summary":"Worker pipeline test advisory (REST-triggered)",
			 "database_specific":{"note":"unknown OSV fields must be ignored by the real mapper"},
			 "affected":[{"package":{"ecosystem":"npm","name":"left-pad"},"versions":["1.3.0"]}]}
			""";

	private static final String DIRECT_DOC = """
			{"id":"GHSA-wrkr-dire-0002","modified":"2024-01-01T00:00:00Z",
			 "summary":"Worker pipeline test advisory (published directly)",
			 "affected":[{"package":{"ecosystem":"npm","name":"is-odd"},"versions":["3.0.1"]}]}
			""";

	@Autowired
	private TestRestTemplate rest;

	@Autowired
	private Neo4jClient neo4j;

	@Autowired
	private RabbitTemplate rabbit;

	@Test
	void restTriggerRunsTheWholePipelineIntoTheGraph() {
		ResponseEntity<Void> response = this.rest.postForEntity("/api/v1/ingest/jobs/osv?ecosystem=npm", null, Void.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(count("""
				MATCH (:Vulnerability {id: 'GHSA-wrkr-trig-0001'})-[:AFFECTS]->
				      (:PackageVersion {purl: 'pkg:npm/left-pad@1.3.0'})
				RETURN count(*)""")).isEqualTo(1));
	}

	@Test
	void rawDocumentPublishedToTheExchangeLandsInTheGraph() {
		// Literal names on purpose: this pins the wire contract for external publishers.
		this.rabbit.convertAndSend("argus.ingest", "osv.raw", DIRECT_DOC);

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(count("""
				MATCH (:Vulnerability {id: 'GHSA-wrkr-dire-0002'})-[:AFFECTS]->
				      (:PackageVersion {purl: 'pkg:npm/is-odd@3.0.1'})
				RETURN count(*)""")).isEqualTo(1));

		// The follow-up embedding hop: summary text → MiniLM vector on the node.
		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(count("""
				MATCH (v:Vulnerability {id: 'GHSA-wrkr-dire-0002'})
				WHERE v.embedding IS NOT NULL AND size(v.embedding) = 384
				RETURN count(v)""")).isEqualTo(1));
	}

	@Test
	void blankEcosystemIsRejectedWith400() {
		ResponseEntity<ProblemDetail> response = this.rest.postForEntity("/api/v1/ingest/jobs/osv?ecosystem=", null,
				ProblemDetail.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void saturatedWorkerExecutorAnswers429() {
		try {
			// 1 running + 4 queued fill the executor (see IngestWorkerConfig); the 6th must be rejected.
			for (int i = 0; i < 5; i++) {
				assertThat(this.rest.postForEntity("/api/v1/ingest/jobs/osv?ecosystem=blocking", null, Void.class)
					.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
			}

			ResponseEntity<ProblemDetail> rejected = this.rest.postForEntity("/api/v1/ingest/jobs/osv?ecosystem=blocking",
					null, ProblemDetail.class);

			assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
			assertThat(rejected.getBody()).isNotNull();
			assertThat(rejected.getBody().getTitle()).isEqualTo("Ingest worker busy");
		}
		finally {
			RELEASE_BLOCKED_FETCHES.countDown();
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void statusEndpointTracksTriggeredFetchToCompletion() {
		assertThat(this.rest.postForEntity("/api/v1/ingest/jobs/osv?ecosystem=npm", null, Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			Map<String, Object> status = this.rest.getForObject("/api/v1/ingest/jobs/status", Map.class);
			List<Map<String, Object>> jobs = (List<Map<String, Object>>) status.get("jobs");
			assertThat(jobs).anySatisfy(job -> {
				assertThat(job).containsEntry("ecosystem", "npm").containsEntry("state", "COMPLETED");
				assertThat(((Number) job.get("documentsPublished")).intValue()).isGreaterThan(0);
			});
			List<Map<String, Object>> queues = (List<Map<String, Object>>) status.get("queues");
			assertThat(queues).extracting(q -> q.get("name"))
				.containsExactly("ingest.osv", "ingest.osv.dlq", "ingest.embedding", "ingest.embedding.dlq");
		});
	}

	@Test
	@SuppressWarnings("unchecked")
	void statusEndpointReportsFailedFetch() {
		assertThat(this.rest.postForEntity("/api/v1/ingest/jobs/osv?ecosystem=explode", null, Void.class)
			.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

		await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
			Map<String, Object> status = this.rest.getForObject("/api/v1/ingest/jobs/status", Map.class);
			List<Map<String, Object>> jobs = (List<Map<String, Object>>) status.get("jobs");
			assertThat(jobs).anySatisfy(job -> {
				assertThat(job).containsEntry("ecosystem", "explode").containsEntry("state", "FAILED");
				assertThat((String) job.get("error")).isNotBlank();
			});
		});
	}

	private long count(String cypher) {
		return this.neo4j.query(cypher).fetchAs(Long.class).one().orElse(0L);
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubOsvSourceConfiguration {

		@Bean
		@Primary
		OsvSource stubOsvSource() {
			return (ecosystem, onDocument) -> {
				// "blocking" parks the single worker thread until the 429 test releases it.
				if ("blocking".equals(ecosystem)) {
					try {
						RELEASE_BLOCKED_FETCHES.await();
					}
					catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
					return;
				}
				// "explode" exercises the FAILED job path of the status endpoint.
				if ("explode".equals(ecosystem)) {
					throw new IllegalStateException("stub OSV source exploded");
				}
				onDocument.accept(TRIGGERED_DOC);
			};
		}

	}

}
