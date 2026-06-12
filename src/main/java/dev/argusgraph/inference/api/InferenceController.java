package dev.argusgraph.inference.api;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	@PostMapping("/impute-severity")
	@Operation(summary = "Impute CVSS severity for unscored advisories via embedding k-NN (latent engine)")
	public InferenceAPI.ImputeResult imputeSeverity() {
		return this.inference.imputeSeverity();
	}

	@PostMapping("/eval-severity")
	@Operation(summary = "Leave-one-out accuracy (MAE + label accuracy) of the embedding severity predictor")
	public InferenceAPI.EvalResult evalSeverity() {
		return this.inference.evaluateSeverity();
	}

	@GetMapping("/rules")
	@Operation(summary = "List the inference rule pipeline in execution order")
	public List<InferenceAPI.RuleView> rules() {
		return this.inference.rules();
	}

	@PostMapping("/rules/{name}/enabled")
	@Operation(summary = "Enable or disable a rule by name")
	public List<InferenceAPI.RuleView> setRuleEnabled(@PathVariable String name, @RequestParam boolean enabled) {
		this.inference.setRuleEnabled(name, enabled);
		return this.inference.rules();
	}

	@PostMapping("/rules/order")
	@Operation(summary = "Reorder the rule pipeline (body: ordered list of rule names)")
	public List<InferenceAPI.RuleView> reorderRules(@RequestBody List<String> orderedNames) {
		this.inference.reorderRules(orderedNames);
		return this.inference.rules();
	}

	@PostMapping("/run-rules")
	@Operation(summary = "Rebuild derived edges by running the rule pipeline in its configured order")
	public RunResponse runRules() {
		return RunResponse.from(this.inference.runRules());
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
