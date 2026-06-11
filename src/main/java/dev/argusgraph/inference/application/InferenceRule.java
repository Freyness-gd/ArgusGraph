package dev.argusgraph.inference.application;

/**
 * One materialised forward-chaining rule: a Cypher MATCH…MERGE that writes provenance-tagged
 * derived edges, idempotently. Rules are ordered and run to fixpoint by the engine.
 */
public interface InferenceRule {

	/** Stable identifier stored as {@code inferredBy} provenance (e.g. {@code "R1"}). */
	String name();

	/** Bump when the rule's logic changes, stored as {@code ruleVersion} provenance. */
	int version();

	/** Apply the rule over the scope; return the number of derived edges written. */
	long apply(InferenceScope scope);

}
