package dev.argusgraph.inference.application;

import java.util.Collection;
import java.util.List;

import dev.argusgraph.inference.InferenceAPI;

/** Persistence port for the inference module's derived edges. */
public interface InferenceRepository {

	/**
	 * Materialise R1 transitive exposure: for every {@code (v)-[:AFFECTS]->(target)} and
	 * {@code (source)-[:DEPENDS_ON*1..]->(target)}, MERGE
	 * {@code (v)-[:TRANSITIVELY_AFFECTED {depth, inferredBy, ruleVersion, derivedAt}]->(source)}.
	 * When {@code sourcePurls} is null, runs over the whole graph. Returns edges written.
	 */
	long writeR1Transitive(Collection<String> sourcePurls, int ruleVersion);

	/** Delete all R1-derived edges. Returns edges deleted. */
	long deleteR1();

	/** Transitive exposure for the given source purls, ordered by ascending dependency depth. */
	List<InferenceAPI.TransitiveHit> readTransitive(Collection<String> purls);

}
