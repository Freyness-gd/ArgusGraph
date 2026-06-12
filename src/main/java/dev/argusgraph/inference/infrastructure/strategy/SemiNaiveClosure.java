package dev.argusgraph.inference.infrastructure.strategy;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceScope;
import dev.argusgraph.inference.application.strategy.ClosureStrategy;
import dev.argusgraph.inference.application.strategy.RunMetrics;

/** Semi-naive: each round extends ONLY from edges created in the previous round (the delta frontier). */
@Component
public class SemiNaiveClosure implements ClosureStrategy {

	private final InferenceRepository repository;

	public SemiNaiveClosure(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "semi-naive";
	}

	@Override
	public long computeClosure(InferenceScope scope, RunMetrics metrics) {
		long total = this.repository.writeR1BaseFrontier(scope.sourcePurls());
		metrics.query();
		int prevRound = 0;
		long created;
		do {
			created = this.repository.writeR1StepDelta(scope.sourcePurls(), prevRound);
			metrics.query();
			metrics.round();
			total += created;
			prevRound++;
		}
		while (created > 0);
		return total;
	}

}
