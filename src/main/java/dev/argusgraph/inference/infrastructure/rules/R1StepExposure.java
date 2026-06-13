package dev.argusgraph.inference.infrastructure.rules;

import org.springframework.stereotype.Component;

import dev.argusgraph.inference.application.InferenceRepository;
import dev.argusgraph.inference.application.InferenceRule;
import dev.argusgraph.inference.application.InferenceScope;

/** R1 recursive case: depending on an already-exposed package extends exposure one hop, depth+1. */
@Component
public class R1StepExposure implements InferenceRule {

	// mirrors Neo4jInferenceRepository.WRITE_R1_STEP
	private static final String CYPHER = """
			MATCH (v:Vulnerability)-[t:TRANSITIVELY_AFFECTED]->(mid:PackageVersion)
			MATCH (source:PackageVersion)-[:DEPENDS_ON]->(mid)
			WHERE $sourcePurls IS NULL OR source.purl IN $sourcePurls
			WITH v, source, min(t.depth) + 1 AS depth
			MERGE (v)-[t2:TRANSITIVELY_AFFECTED]->(source)
			  ON CREATE SET t2.depth = depth, t2.inferredBy = 'R1', t2.ruleVersion = 2,
			                t2.derivedAt = datetime(), t2._new = true
			  ON MATCH  SET t2.depth = CASE WHEN depth < t2.depth THEN depth ELSE t2.depth END
			WITH t2 WHERE coalesce(t2._new, false) = true
			REMOVE t2._new
			RETURN count(t2) AS created
			""";

	private final InferenceRepository repository;

	public R1StepExposure(InferenceRepository repository) {
		this.repository = repository;
	}

	@Override
	public String name() {
		return "R1-step";
	}

	@Override
	public String description() {
		return "R1 step (recursive): depending on an already-exposed version extends exposure one hop "
				+ "— TRANSITIVELY_AFFECTED ∧ DEPENDS_ON ⇒ TRANSITIVELY_AFFECTED {depth+1}; iterated to fixpoint.";
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
		return true;
	}

	@Override
	public long apply(InferenceScope scope) {
		return this.repository.writeR1Step(scope.sourcePurls());
	}

}
