package dev.argusgraph.inference.infrastructure.rules;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRule;
import dev.argusgraph.inference.application.InferenceScope;

/** R1 recursive case: depending on an already-exposed package extends exposure one hop, depth+1. */
@Component
public class R1StepExposure implements InferenceRule {

	private final InferenceRepository repository;

	public R1StepExposure(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "R1-step";
	}

	@Override
	public int version() {
		return 2;
	}

	@Override
	public int stratum() {
		return 1;
	}

	@Override
	public boolean recursive() {
		return true;
	}

	@Override
	public long apply(InferenceScope scope) {
		return this.repository.writeR1Step(scope.sourcePurls());
	}

}
