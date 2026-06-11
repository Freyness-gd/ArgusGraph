package dev.argusgraph.ingest.worker.api;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import dev.argusgraph.ingest.worker.application.IngestJobRegistry;

/** Read response: fetch-job history (newest first) plus live queue depths. */
@Schema(name = "JobStatus")
public record JobStatusResponse(List<JobResponse> jobs, List<QueueResponse> queues) {

	/** One fetch job, running or finished. */
	@Schema(name = "Job")
	public record JobResponse(@Schema(example = "3") long id, @Schema(example = "Maven") String ecosystem,
			@Schema(example = "RUNNING") String state, @Schema(example = "12408") int documentsPublished,
			Instant startedAt, Instant finishedAt, @Schema(example = "404 from OSV bucket") String error) {

		public static JobResponse from(IngestJobRegistry.JobView view) {
			return new JobResponse(view.id(), view.ecosystem(), view.state().name(), view.documentsPublished(),
					view.startedAt(), view.finishedAt(), view.error());
		}

	}

	/** One queue and its current depth; {@code messages} is null when the broker cannot say. */
	@Schema(name = "QueueDepth")
	public record QueueResponse(@Schema(example = "ingest.osv") String name,
			@Schema(example = "8211", nullable = true, description = "Current message count; null when the broker cannot answer (not the same as 0).") Integer messages) {
	}

}
