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

	/** Page through derived TRANSITIVELY_AFFECTED edges (vuln → exposed package-version). */
	DerivedPage findDerivedEdges(String q, int page, int size);

	/** The shortest dependency chain from an exposed package-version to a version the vuln directly AFFECTS. */
	ExposureChain exposureChain(String vulnId, String exposedPurl);

	/** Run severity imputation over unscored embedded vulns; returns count + duration. */
	ImputeResult imputeSeverity();

	/** Leave-one-out accuracy of the embedding severity predictor. */
	EvalResult evaluateSeverity();

	/** The rule pipeline in execution order (enabled and disabled). */
	List<RuleView> rules();

	/** Enable or disable a rule by name. */
	void setRuleEnabled(String name, boolean enabled);

	/**
	 * Reorder the rule pipeline; {@code orderedNames} must be a permutation of the current rule names.
	 * Order is not stratum-validated — running a rule before the rule it depends on changes the result
	 * (intentional: the pipeline is user-orderable).
	 */
	void reorderRules(List<String> orderedNames);

	/**
	 * Full rebuild via the ordered rule pipeline (delete derived, then run enabled rules in order).
	 * Returns the aggregate run metrics plus the per-rule edge breakdown.
	 */
	RuleRunResult runRules();

	/** A rule in the pipeline: identity, stratum/recursion metadata, and whether it currently runs. */
	record RuleView(String name, int version, int stratum, boolean recursive, boolean enabled, String description,
			String cypher) {
	}

	/** Edges one rule created during a run. */
	record RuleOutput(String rule, long edgesCreated) {
	}

	/** A rule-pipeline run: the aggregate metrics + the per-rule breakdown. */
	record RuleRunResult(RunResult run, List<RuleOutput> ruleOutputs) {
	}

	/** Metrics for one recompute run. */
	record RunResult(String engine, long durationMs, int rounds, long queryCount, long edgesDerived, long timestamp) {
	}

	/** Event published by other modules when new DEPENDS_ON edges land for a set of purls. */
	record DependenciesLinked(Long projectId, Set<String> purls) {
	}

	/** Transitive exposure for one source purl. */
	record TransitiveHit(String purl, List<TransitiveVuln> vulnerabilities) {
	}

	/** One page of derived edges. */
	record DerivedPage(List<DerivedEdge> items, int page, int size, long total) {
	}

	/** A derived exposure edge: a vulnerability transitively reaching an exposed package-version. */
	record DerivedEdge(String vulnId, String severity, Double cvssScore, String summary, String exposedPurl,
			int depth, String inferredBy) {
	}

	/** The DEPENDS_ON path from the exposed version (first) to the directly-affected version (last). */
	record ExposureChain(String vulnId, List<String> path, String affectedPurl) {
	}

	/** One vulnerability reaching a source purl transitively, with the shortest dependency depth. */
	record TransitiveVuln(String id, String severity, Double cvssScore, String summary, int depth) {
	}

	/** Result of an imputation run. */
	record ImputeResult(long predicted, long durationMs) {
	}

	/** Leave-one-out evaluation: mean absolute error on CVSS, label-band accuracy, sample size. */
	record EvalResult(long n, double mae, double labelAccuracy, long durationMs) {
	}

}
