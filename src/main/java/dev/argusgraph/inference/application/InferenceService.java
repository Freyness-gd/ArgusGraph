package dev.argusgraph.inference.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.argusgraph.inference.InferenceAPI;

/**
 * Runs the ordered rule catalog to fixpoint and serves derived results. With a single rule
 * (R1) the loop settles in one pass; the structure is ready for R2…Rn feeding each other.
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
        this.repository.deleteR1();
        return runToFixpoint(InferenceScope.all());
    }

    @Override
    @Transactional(transactionManager = "neo4jTransactionManager")
    public long runFor(Set<String> sourcePurls) {
        if (sourcePurls == null || sourcePurls.isEmpty()) {
            return 0L;
        }
        return runToFixpoint(InferenceScope.of(sourcePurls));
    }

    @Override
    @Transactional(transactionManager = "neo4jTransactionManager", readOnly = true)
    public List<TransitiveHit> transitiveExposure(Collection<String> purls) {
        if (purls == null || purls.isEmpty()) {
            return List.of();
        }
        return this.repository.readTransitive(purls);
    }

    private long runToFixpoint(InferenceScope scope) {
        // R1 computes full transitivity in one pass (DEPENDS_ON*). Single-rule catalog ⇒ one pass.
        // When rules that feed each other are added, replace with a change-detecting loop that
        // compares derived-edge counts between passes rather than MERGE write counts.
        long total = 0;
        for (InferenceRule rule : this.rules) {
            total += rule.apply(scope);
        }
        return total;
    }

}
