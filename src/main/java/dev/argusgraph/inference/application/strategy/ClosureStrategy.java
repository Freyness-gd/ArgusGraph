package dev.argusgraph.inference.application.strategy;

import dev.argusgraph.inference.application.InferenceScope;

/** A way to compute the R1 transitive-exposure closure. Implementations produce identical edges. */
public interface ClosureStrategy {

	/** Engine id used in the API/UI (e.g. "naive", "semi-naive", "native"). */
	String name();

	/** Materialise TRANSITIVELY_AFFECTED over the scope; record rounds/queries into metrics; return edges created. */
	long computeClosure(InferenceScope scope, RunMetrics metrics);

}
