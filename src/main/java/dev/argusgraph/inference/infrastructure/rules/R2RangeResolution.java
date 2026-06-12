package dev.argusgraph.inference.infrastructure.rules;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRepository.R2Hit;
import dev.argusgraph.inference.application.InferenceRule;
import dev.argusgraph.inference.application.InferenceScope;
import dev.argusgraph.inference.application.version.OsvRangeEvaluator;

/**
 * R2 — range resolution. Materialises version-level {@code AFFECTS} from the raw OSV ranges on
 * {@code AFFECTS_PACKAGE}, using the {@link OsvRangeEvaluator} built-in predicate. Non-recursive,
 * stratum 0 (its output feeds R1).
 */
@Component
public class R2RangeResolution implements InferenceRule {

	private final InferenceRepository repository;

	private final OsvRangeEvaluator evaluator = new OsvRangeEvaluator();

	public R2RangeResolution(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "R2";
	}

	@Override
	public int version() {
		return 1;
	}

	@Override
	public int stratum() {
		return 0;
	}

	@Override
	public boolean recursive() {
		return false;
	}

	@Override
	public long apply(InferenceScope scope) {
		// R2 is advisory-driven, not project-driven: it resolves ranges across the whole graph and
		// only runs on a full recompute. A project-scoped run (an import) drives R1 alone and reads
		// the AFFECTS a prior recompute already materialised — this avoids a full-graph range scan
		// on every import and keeps scoped runs from half-updating other projects' exposure.
		if (!scope.isAll()) {
			return 0L;
		}
		List<R2Hit> hits = this.repository.r2Candidates()
			.stream()
			.filter(c -> this.evaluator.evaluate(c.rangesJson(), c.purlType(), c.version())
					== OsvRangeEvaluator.Verdict.AFFECTED)
			.map(c -> new R2Hit(c.vulnId(), c.purl()))
			.toList();
		// Always run the write (an empty UNWIND is a cheap no-op) so the orchestrator's query
		// count stays honest — R2 is two Cypher round-trips (candidate scan + write) every run.
		return this.repository.writeR2Affects(hits);
	}

}
