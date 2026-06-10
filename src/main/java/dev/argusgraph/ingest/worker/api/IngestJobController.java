package dev.argusgraph.ingest.worker.api;

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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.ingest.worker.application.OsvFetchJob;

/**
 * Manual trigger for source-fetch jobs. Returns 202 immediately; the fetch runs async
 * and feeds the ingest queue. No job-status endpoint yet — progress is visible in the
 * application logs and the RabbitMQ management UI. The worker executor is deliberately
 * tiny (1 thread + 4 queued); when it is saturated the trigger answers 429.
 */
@RestController
@RequestMapping("/ingest/jobs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Ingest Jobs", description = "Asynchronous source-fetch jobs feeding the ingest queue.")
public class IngestJobController {

	private final OsvFetchJob osvFetchJob;

	@PostMapping("/osv")
	@ResponseStatus(HttpStatus.ACCEPTED)
	@Operation(summary = "Fetch one OSV ecosystem dump and queue every document for ingestion")
	public void startOsvFetch(@RequestParam @NotBlank(message = "ecosystem is required") @Parameter(
			description = "OSV ecosystem name (dump directory in the OSV bucket)", example = "Maven") String ecosystem) {
		this.osvFetchJob.runOsv(ecosystem);
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
