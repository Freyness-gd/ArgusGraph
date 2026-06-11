package dev.argusgraph.inference.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.argusgraph.inference.InferenceAPI;

/** Inference operations. Reachable at {@code /api/v1/inference/...} via the global prefix. */
@RestController
@RequestMapping("/inference")
@RequiredArgsConstructor
@Tag(name = "Inference", description = "Derived knowledge — transitive vulnerability exposure.")
public class InferenceController {

	private final InferenceAPI inference;

	@PostMapping("/recompute")
	@Operation(summary = "Rebuild all derived edges (delete + re-derive across the whole graph)")
	public RecomputeResponse recompute() {
		return new RecomputeResponse(this.inference.recomputeAll());
	}

	/** Result of a full recompute. */
	public record RecomputeResponse(long edgesWritten) {
	}

}
