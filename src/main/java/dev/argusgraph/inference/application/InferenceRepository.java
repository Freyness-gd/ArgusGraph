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

	/** Native closure: materialise TRANSITIVELY_AFFECTED in one DEPENDS_ON* path-match query. Returns created. */
	long writeR1Native(Collection<String> sourcePurls);

	/** Semi-naive base: create the round-0 frontier (depth 1, round 0). Returns created. */
	long writeR1BaseFrontier(Collection<String> sourcePurls);

	/** Semi-naive step: extend only from edges created in {@code prevRound}; tag new edges round prevRound+1. Returns created. */
	long writeR1StepDelta(Collection<String> sourcePurls, int prevRound);

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

	/** Paged derived TRANSITIVELY_AFFECTED edges; optional case-insensitive filter on vuln id / exposed purl / summary. */
	InferenceAPI.DerivedPage findDerived(String q, int page, int size);

	/** Shortest DEPENDS_ON path from {@code exposedPurl} to a version the vuln AFFECTS; empty path if none. */
	InferenceAPI.ExposureChain findChain(String vulnId, String exposedPurl);

	/** One R2 candidate: a versioned package under a vuln's AFFECTS_PACKAGE ranges. */
	record R2Candidate(String vulnId, String purl, String version, String purlType, String rangesJson) {
	}

	/** A resolved R2 hit to materialise. */
	record R2Hit(String vulnId, String purl) {
	}

	/** Unscored embedded vulns with their k nearest SCORED neighbours (for imputation). */
	List<NeighbourSet> imputeCandidates(int overfetch, int k);

	/** Scored embedded vulns with their actual score + k nearest OTHER scored neighbours (leave-one-out). */
	List<EvalCandidate> evalCandidates(int overfetch, int k);

	/** Write imputed predictions onto the vulnerability nodes. Returns count written. */
	long writePredictions(List<Prediction> predictions);

	/** One unscored vuln and its scored neighbours. */
	record NeighbourSet(String vulnId, List<Neighbour> neighbours) {
	}

	/** One scored vuln (leave-one-out target) and its other scored neighbours. */
	record EvalCandidate(String vulnId, double actual, List<Neighbour> neighbours) {
	}

	/** A scored neighbour: similarity score from the vector index + its CVSS base score. */
	record Neighbour(double score, double cvss) {
	}

	/** A prediction to persist. */
	record Prediction(String vulnId, double cvss, String severity, double confidence) {
	}

}
