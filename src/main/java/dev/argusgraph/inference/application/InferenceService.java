package dev.argusgraph.inference.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.inference.InferenceAPI;
import dev.argusgraph.inference.InferenceAPI.EvalResult;
import dev.argusgraph.inference.InferenceAPI.ImputeResult;
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

	private final List<InferenceRule> rules;

	private final R2RangeResolution rangeResolution;

	private final InferenceRepository repository;

	private final InferenceRunLog runLog;

	private final SeverityImputation severityImputation;

	private final Map<String, ClosureStrategy> strategies = new LinkedHashMap<>();

	public InferenceService(List<InferenceRule> rules, R2RangeResolution rangeResolution,
			InferenceRepository repository, InferenceRunLog runLog, SeverityImputation severityImputation,
			List<ClosureStrategy> closureStrategies) {
		this.rules = rules;
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
	public ImputeResult imputeSeverity() {
		return this.severityImputation.impute();
	}

	@Override
	public EvalResult evaluateSeverity() {
		return this.severityImputation.evaluate();
	}

	private long run(InferenceScope scope) {
		TreeMap<Integer, List<InferenceRule>> byStratum = new TreeMap<>();
		for (InferenceRule rule : this.rules) {
			byStratum.computeIfAbsent(rule.stratum(), k -> new ArrayList<>()).add(rule);
		}
		long total = 0;
		for (Map.Entry<Integer, List<InferenceRule>> stratum : byStratum.entrySet()) {
			List<InferenceRule> rs = stratum.getValue();
			boolean iterate = rs.stream().anyMatch(InferenceRule::recursive);
			long round;
			do {
				round = 0;
				for (InferenceRule rule : rs) {
					round += rule.apply(scope);
				}
				total += round;
			}
			while (iterate && round > 0);
		}
		return total;
	}

}
