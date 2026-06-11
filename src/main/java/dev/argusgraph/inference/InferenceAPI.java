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

	/** Re-derive every rule across the whole graph (delete derived edges, rebuild). Returns edges written. */
	long recomputeAll();

	/** Run derivation scoped to the given source package-version purls (incremental). Returns edges written. */
	long runFor(Set<String> sourcePurls);

	/** Transitive vulnerability exposure for each of the given package-version purls. */
	List<TransitiveHit> transitiveExposure(Collection<String> purls);

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
