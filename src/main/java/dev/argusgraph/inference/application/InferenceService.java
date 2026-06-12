package dev.argusgraph.inference.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.inference.InferenceAPI;

/**
 * Stratified naive forward-chaining engine. Rules run by ascending stratum; a stratum with a
 * recursive rule is iterated until a full round creates nothing new (the fixpoint / chase).
 * With R2 (stratum 0) feeding R1 (stratum 1) this is correct without a global cycle.
 */
@Service
@org.jmolecules.ddd.annotation.Service
@RequiredArgsConstructor
public class InferenceService implements InferenceAPI {

	private final List<InferenceRule> rules;

	private final InferenceRepository repository;

	@Override
	@Transactional(transactionManager = "neo4jTransactionManager")
	public long recomputeAll() {
		this.repository.deleteTransitive();
		this.repository.deleteR2Affects();
		return run(InferenceScope.all());
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
