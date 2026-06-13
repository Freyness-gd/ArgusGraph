package dev.argusgraph.inference.infrastructure.rules;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRule;
import dev.argusgraph.inference.application.InferenceScope;

/** R1 base case: a direct dependency on an affected package exposes the dependent at depth 1. */
@Component
public class R1BaseExposure implements InferenceRule {

	// mirrors Neo4jInferenceRepository.WRITE_R1_BASE
	private static final String CYPHER = """
			MATCH (v:Vulnerability)-[:AFFECTS]->(target:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON]->(target)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			MERGE (v)-[t:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t.depth = 1, t.inferredBy = 'R1', t.ruleVersion = 2,
			                t.derivedAt = datetime(), t._new = true
			WITH t WHERE coalesce(t._new, false) = true
			REMOVE t._new
			RETURN count(t) AS created
			""";

	private final InferenceRepository repository;

	public R1BaseExposure(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "R1-base";
	}

	@Override
	public String description() {
		return "R1 base: a direct dependency on an affected package version exposes the dependent at depth 1 "
				+ "— AFFECTS ∧ DEPENDS_ON ⇒ TRANSITIVELY_AFFECTED {depth:1}.";
	}

	@Override
	public String cypher() {
		return CYPHER;
	}

	@Override
	public int version() {
		return 2;
	}

	@Override
	public int stratum() {
		return 1;
	}

	@Override
	public boolean recursive() {
		return false;
	}

	@Override
	public long apply(InferenceScope scope) {
		return this.repository.writeR1Base(scope.sourcePurls());
	}

}
