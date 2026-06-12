package dev.argusgraph.inference.infrastructure.strategy;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceScope;
import dev.argusgraph.inference.application.strategy.ClosureStrategy;
import dev.argusgraph.inference.application.strategy.RunMetrics;
import dev.argusgraph.inference.infrastructure.rules.R1BaseExposure;
import dev.argusgraph.inference.infrastructure.rules.R1StepExposure;

/** Naive forward chaining: base once, then re-apply the step over ALL edges each round until none new. */
@Component
public class NaiveClosure implements ClosureStrategy {

	private final R1BaseExposure base;

	private final R1StepExposure step;

	public NaiveClosure(R1BaseExposure base, R1StepExposure step) {
		this.base = base;
		this.step = step;
	}

	@Override
	public String name() {
		return "naive";
	}

	@Override
	public long computeClosure(InferenceScope scope, RunMetrics metrics) {
		long total = this.base.apply(scope);
		metrics.query();
		long created;
		do {
			created = this.step.apply(scope);
			metrics.query();
			metrics.round();
			total += created;
		}
		while (created > 0);
		return total;
	}

}
