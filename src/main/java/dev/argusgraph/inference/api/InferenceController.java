package dev.argusgraph.inference.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.inference.InferenceAPI;

/** Inference operations: rebuild derived edges with a chosen engine, and read run metrics. */
@RestController
@RequestMapping("/inference")
@RequiredArgsConstructor
@Tag(name = "Inference", description = "Derived knowledge — transitive exposure, with pluggable engines.")
public class InferenceController {

	private final InferenceAPI inference;

	@PostMapping("/recompute")
	@Operation(summary = "Rebuild all derived edges with the chosen engine (naive | semi-naive | native)")
	public RunResponse recompute(@RequestParam(required = false) String engine) {
		return RunResponse.from(this.inference.recompute(engine));
	}

	@GetMapping("/runs")
	@Operation(summary = "Recent recompute runs (newest first) for engine comparison")
	public List<RunResponse> runs() {
		return this.inference.recentRuns().stream().map(RunResponse::from).toList();
	}

	/** Metrics of one recompute run. {@code edgesWritten} kept for back-compat. */
	public record RunResponse(String engine, long edgesWritten, long durationMs, int rounds, long queryCount,
			long timestamp) {

		static RunResponse from(InferenceAPI.RunResult r) {
			return new RunResponse(r.engine(), r.edgesDerived(), r.durationMs(), r.rounds(), r.queryCount(),
					r.timestamp());
		}
	}

}
