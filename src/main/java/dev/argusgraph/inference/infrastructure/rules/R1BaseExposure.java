package dev.argusgraph.inference.infrastructure.rules;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRule;
import dev.argusgraph.inference.application.InferenceScope;

/** R1 base case: a direct dependency on an affected package exposes the dependent at depth 1. */
@Component
public class R1BaseExposure implements InferenceRule {

	private final InferenceRepository repository;

	public R1BaseExposure(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "R1-base";
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
		return false;
	}

	@Override
	public long apply(InferenceScope scope) {
		return this.repository.writeR1Base(scope.sourcePurls());
	}

}
