package dev.argusgraph.inference;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.jmolecules.ddd.annotation.Service;

/**
 * Published contract of the inference module. Other modules trigger derivation by
 * publishing {@link DependenciesLinked}, and read derived results through this interface.
 */
@Service
public interface InferenceAPI {

	/** Run an engine over the whole graph (delete + R2 + chosen R1 closure strategy); returns its metrics. */
	RunResult recompute(String engine);

	/** Recent benchmark runs, newest first (bounded in-memory). */
	List<RunResult> recentRuns();

	/** Run derivation scoped to the given source package-version purls (incremental). Returns edges written. */
	long runFor(Set<String> sourcePurls);

	/** Transitive vulnerability exposure for each of the given package-version purls. */
	List<TransitiveHit> transitiveExposure(Collection<String> purls);

	/** Metrics for one recompute run. */
	record RunResult(String engine, long durationMs, int rounds, long queryCount, long edgesDerived, long timestamp) {
	}

	/** Event published by other modules when new DEPENDS_ON edges land for a set of purls. */
	record DependenciesLinked(Long projectId, Set<String> purls) {
	}

	/** Transitive exposure for one source purl. */
	record TransitiveHit(String purl, List<TransitiveVuln> vulnerabilities) {
	}

	/** One vulnerability reaching a source purl transitively, with the shortest dependency depth. */
	record TransitiveVuln(String id, String severity, Double cvssScore, String summary, int depth) {
	}

}
