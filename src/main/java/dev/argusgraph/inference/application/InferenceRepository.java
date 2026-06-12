package dev.argusgraph.inference.application;

import java.util.Collection;
import java.util.List;

import dev.argusgraph.inference.InferenceAPI;

/** Persistence port for the inference module's derived edges. */
public interface InferenceRepository {

	/** R1 base: AFFECTS(v,target) ∧ DEPENDS_ON(source,target) ⇒ TRANSITIVELY_AFFECTED depth 1. Returns created. */
	long writeR1Base(Collection<String> sourcePurls);

	/** R1 step: TRANSITIVELY_AFFECTED(v,mid,d) ∧ DEPENDS_ON(source,mid) ⇒ depth d+1. Returns created this round. */
	long writeR1Step(Collection<String> sourcePurls);

	/** Candidates for R2: every (vuln, package-version, ranges) reachable via AFFECTS_PACKAGE + HAS_VERSION. */
	List<R2Candidate> r2Candidates();

	/** R2 write: MERGE AFFECTS{inferredBy:'R2'} for the given (vulnId, purl) hits. Returns created. */
	long writeR2Affects(List<R2Hit> hits);

	/** Delete all R2-derived AFFECTS. Returns deleted. */
	long deleteR2Affects();

	/** Delete all TRANSITIVELY_AFFECTED. Returns deleted. */
	long deleteTransitive();

	/** Transitive exposure for the given source purls, by ascending depth. */
	List<InferenceAPI.TransitiveHit> readTransitive(Collection<String> purls);

	/** One R2 candidate: a versioned package under a vuln's AFFECTS_PACKAGE ranges. */
	record R2Candidate(String vulnId, String purl, String version, String purlType, String rangesJson) {
	}

	/** A resolved R2 hit to materialise. */
	record R2Hit(String vulnId, String purl) {
	}

}
