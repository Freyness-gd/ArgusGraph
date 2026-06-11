package dev.argusgraph.ingest.worker.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.ingest.worker.application.IngestJobRegistry;
import dev.argusgraph.ingest.worker.application.IngestRouting;
import dev.argusgraph.ingest.worker.application.OsvFetchJob;
import dev.argusgraph.ingest.worker.application.QueueDepthProbe;

/**
 * Manual trigger for source-fetch jobs. Returns 202 immediately; the fetch runs async
 * and feeds the ingest queue. Job status and queue depths are served by {@code GET /status}.
 * The worker executor is deliberately tiny (1 thread + 4 queued); when it is saturated
 * the trigger answers 429.
 */
@RestController
@RequestMapping("/ingest/jobs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ingest Jobs", description = "Asynchronous source-fetch jobs feeding the ingest queue.")
public class IngestJobController {

	private static final List<String> STATUS_QUEUES = List.of(IngestRouting.OSV_QUEUE,
			IngestRouting.OSV_DEAD_LETTER_QUEUE, IngestRouting.EMBEDDING_QUEUE,
			IngestRouting.EMBEDDING_DEAD_LETTER_QUEUE);

	private final OsvFetchJob osvFetchJob;

	private final IngestJobRegistry registry;

	private final QueueDepthProbe queueDepths;

	@PostMapping("/osv")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Fetch one OSV ecosystem dump and queue every document for ingestion")
	public void startOsvFetch(@RequestParam @NotBlank(message = "ecosystem is required") @Parameter(
			description = "OSV ecosystem name (dump directory in the OSV bucket)", example = "Maven") String ecosystem) {
		this.osvFetchJob.runOsv(ecosystem);
	}

	@GetMapping("/status")
	@Operation(summary = "Fetch-job history (newest first) and live queue depths")
	public JobStatusResponse status() {
		List<JobStatusResponse.JobResponse> jobs = this.registry.snapshot()
			.stream()
			.map(JobStatusResponse.JobResponse::from)
			.toList();
		List<JobStatusResponse.QueueResponse> queues = STATUS_QUEUES.stream()
			.map(name -> new JobStatusResponse.QueueResponse(name, this.queueDepths.depth(name)))
			.toList();
		return new JobStatusResponse(jobs, queues);
	}

	/** The async submit happens on the caller thread — a full executor queue surfaces here. */
	@ExceptionHandler(TaskRejectedException.class)
	ProblemDetail handleWorkerSaturated(TaskRejectedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Fetch worker is busy: a job is already running and the queue is full. Retry later.");
		problem.setTitle("Ingest worker busy");
		return problem;
	}

}
