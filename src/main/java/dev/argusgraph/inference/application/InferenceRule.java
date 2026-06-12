package dev.argusgraph.inference.application;

/**
 * One materialised forward-chaining rule. Rules are grouped into strata (ascending) and run
 * in order; a stratum containing a recursive rule is iterated to fixpoint. {@code apply}
 * performs ONE round and returns the number of edges CREATED (not merged), so the engine can
 * detect the fixpoint.
 */
public interface InferenceRule {

	String name();

	int version();

	/** Execution order; lower strata run first. Rules in the same stratum run together. */
	int stratum();

	/** True if the engine must iterate this rule's stratum until no edge is created. */
	boolean recursive();

	/** One round over the scope; returns edges created this round. */
	long apply(InferenceScope scope);

}
