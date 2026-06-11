package dev.argusgraph.inference.infrastructure.rules;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRule;
import dev.argusgraph.inference.application.InferenceScope;

/**
 * R1 — transitive exposure. {@code AFFECTS(v, target) ∧ DEPENDS_ON+(source, target)
 * ⇒ TRANSITIVELY_AFFECTED(v, source)} with the shortest dependency depth.
 */
@Component
public class R1TransitiveExposure implements InferenceRule {

    private static final int VERSION = 1;

    private final InferenceRepository repository;

    public R1TransitiveExposure(InferenceRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "R1";
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public long apply(InferenceScope scope) {
        return this.repository.writeR1Transitive(scope.sourcePurls(), VERSION);
    }

}
