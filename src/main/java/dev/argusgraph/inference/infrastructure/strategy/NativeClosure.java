package dev.argusgraph.inference.infrastructure.strategy;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceScope;
import dev.argusgraph.inference.application.strategy.ClosureStrategy;
import dev.argusgraph.inference.application.strategy.RunMetrics;

/** Native: let Neo4j compute the whole closure with a single variable-length DEPENDS_ON* match. */
@Component
public class NativeClosure implements ClosureStrategy {

	private final InferenceRepository repository;

	public NativeClosure(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "native";
	}

	@Override
	public long computeClosure(InferenceScope scope, RunMetrics metrics) {
		long created = this.repository.writeR1Native(scope.sourcePurls());
		metrics.query();
		return created;
	}

}
