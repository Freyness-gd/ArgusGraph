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
		List<R2Hit> hits = this.repository.r2Candidates()
			.stream()
			.filter(c -> this.evaluator.evaluate(c.rangesJson(), c.purlType(), c.version())
					== OsvRangeEvaluator.Verdict.AFFECTED)
			.map(c -> new R2Hit(c.vulnId(), c.purl()))
			.toList();
		return hits.isEmpty() ? 0 : this.repository.writeR2Affects(hits);
	}

}
