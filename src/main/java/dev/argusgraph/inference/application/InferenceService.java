package dev.argusgraph.inference.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.inference.InferenceAPI;
import dev.argusgraph.inference.InferenceAPI.EvalResult;
import dev.argusgraph.inference.InferenceAPI.ImputeResult;
import dev.argusgraph.inference.InferenceAPI.RuleOutput;
import dev.argusgraph.inference.InferenceAPI.RuleRunResult;
import dev.argusgraph.inference.InferenceAPI.RuleView;
import dev.argusgraph.inference.application.embedding.SeverityImputation;
import dev.argusgraph.inference.application.strategy.ClosureStrategy;
import dev.argusgraph.inference.application.strategy.RunMetrics;
import dev.argusgraph.inference.infrastructure.rules.R2RangeResolution;
import dev.argusgraph.shared.exception.BusinessRuleException;

/**
 * Inference orchestrator. {@code recompute(engine)} runs a benchmarkable full rebuild: delete
 * derived edges, resolve ranges (R2), then materialise the R1 closure with the chosen
 * {@link ClosureStrategy}, recording metrics. {@code runFor} keeps the import-time scoped naive
 * path (stratified rule loop), unchanged from slice 4.2.
 */
@Service
@org.jmolecules.ddd.annotation.Service
public class InferenceService implements InferenceAPI {

	private static final String DEFAULT_ENGINE = "naive";

	private final RuleRegistry ruleRegistry;

	private final R2RangeResolution rangeResolution;

	private final InferenceRepository repository;

	private final InferenceRunLog runLog;

	private final SeverityImputation severityImputation;

	private final Map<String, ClosureStrategy> strategies = new LinkedHashMap<>();

	public InferenceService(RuleRegistry ruleRegistry, R2RangeResolution rangeResolution,
			InferenceRepository repository, InferenceRunLog runLog, SeverityImputation severityImputation,
			List<ClosureStrategy> closureStrategies) {
		this.ruleRegistry = ruleRegistry;
		this.rangeResolution = rangeResolution;
		this.repository = repository;
		this.runLog = runLog;
		this.severityImputation = severityImputation;
		for (ClosureStrategy strategy : closureStrategies) {
			this.strategies.put(strategy.name(), strategy);
		}
	}

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager")
	public RunResult recompute(String engine) {
		String resolved = (engine == null || engine.isBlank()) ? DEFAULT_ENGINE : engine;
		ClosureStrategy strategy = this.strategies.get(resolved);
		if (strategy == null) {
			throw new BusinessRuleException("Unknown inference engine: " + engine
					+ " (available: " + this.strategies.keySet() + ")");
		}
		RunMetrics metrics = new RunMetrics(resolved);
		this.repository.deleteTransitive();
		metrics.query();
		this.repository.deleteR2Affects();
		metrics.query();
		long r2 = this.rangeResolution.apply(InferenceScope.all());
		metrics.query(); // r2Candidates fetch
		metrics.query(); // writeR2Affects
		metrics.derived(r2);
		metrics.derived(strategy.computeClosure(InferenceScope.all(), metrics));
		RunResult result = metrics.finish();
		this.runLog.record(result);
		return result;
	}

	@Override
	public List<RunResult> recentRuns() {
		return this.runLog.recent();
	}

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager")
	public long runFor(Set<String> sourcePurls) {
		if (sourcePurls == null || sourcePurls.isEmpty()) {
			return 0L;
		}
		// Import-scoped run drives all strata; R2's AFFECTS write is idempotent across imports.
		return run(InferenceScope.of(sourcePurls));
	}

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager", readOnly = true)
	public List<TransitiveHit> transitiveExposure(Collection<String> purls) {
		if (purls == null || purls.isEmpty()) {
			return List.of();
		}
		return this.repository.readTransitive(purls);
	}

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager", readOnly = true)
	public InferenceAPI.DerivedPage findDerivedEdges(String q, int page, int size) {
		int safePage = Math.max(0, page);
		int safeSize = Math.min(100, Math.max(1, size));
		return this.repository.findDerived(q, safePage, safeSize);
	}

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager", readOnly = true)
	public InferenceAPI.ExposureChain exposureChain(String vulnId, String exposedPurl) {
		return this.repository.findChain(vulnId, exposedPurl);
	}

	@Override
	public ImputeResult imputeSeverity() {
		return this.severityImputation.impute();
	}

	@Override
	public EvalResult evaluateSeverity() {
		return this.severityImputation.evaluate();
	}

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager")
	public RuleRunResult runRules() {
		RunMetrics metrics = new RunMetrics("rules");
		this.repository.deleteTransitive();
		metrics.query();
		this.repository.deleteR2Affects();
		metrics.query();
		List<RuleOutput> outputs = new ArrayList<>();
		metrics.derived(applyRules(InferenceScope.all(), metrics, outputs));
		RunResult result = metrics.finish();
		this.runLog.record(result);
		return new RuleRunResult(result, outputs);
	}

	@Override
	public List<RuleView> rules() {
		return this.ruleRegistry.entries()
			.stream()
			.map(e -> new RuleView(e.rule().name(), e.rule().version(), e.rule().stratum(), e.rule().recursive(),
					e.enabled(), e.rule().description(), e.rule().cypher()))
			.toList();
	}

	@Override
	public void setRuleEnabled(String name, boolean enabled) {
		this.ruleRegistry.setEnabled(name, enabled);
	}

	@Override
	public void reorderRules(List<String> orderedNames) {
		this.ruleRegistry.reorder(orderedNames);
	}

	private long run(InferenceScope scope) {
		return applyRules(scope, null, null);
	}

	/** Run each enabled rule in registry order; a recursive rule iterates until it creates no edges. */
	private long applyRules(InferenceScope scope, RunMetrics metrics, List<RuleOutput> outputs) {
		long total = 0;
		for (InferenceRule rule : this.ruleRegistry.enabledInOrder()) {
			long created = 0;
			boolean recursive = rule.recursive();
			long round;
			do {
				round = rule.apply(scope);
				created += round;
				if (metrics != null) {
					metrics.round();
					metrics.query();
				}
			}
			while (recursive && round > 0);
			total += created;
			if (outputs != null) {
				outputs.add(new RuleOutput(rule.name(), created));
			}
		}
		return total;
	}

}
