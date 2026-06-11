package dev.argusgraph.graph.infrastructure.persistence;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

/**
 * Creates the graph's uniqueness constraints at startup ({@code IF NOT EXISTS}, so
 * re-running is free). Constraints both guard the natural keys and back the {@code MERGE}
 * upserts with an index.
 *
 * <p>
 * Prototype-grade schema management — if migrations get more involved, switch to a
 * dedicated tool (e.g. neo4j-migrations) the same way the template shipped Flyway.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class GraphSchemaInitializer implements ApplicationRunner {

	private static final List<String> CONSTRAINTS = List.of(
			"CREATE CONSTRAINT package_purl_unique IF NOT EXISTS FOR (p:Package) REQUIRE p.purl IS UNIQUE",
			"CREATE CONSTRAINT package_version_purl_unique IF NOT EXISTS FOR (v:PackageVersion) REQUIRE v.purl IS UNIQUE",
			"CREATE CONSTRAINT vulnerability_id_unique IF NOT EXISTS FOR (v:Vulnerability) REQUIRE v.id IS UNIQUE");

	/** Dimensions match the all-MiniLM-L6-v2 model computing the vectors (worker module). */
	private static final String VULNERABILITY_EMBEDDING_INDEX = """
			CREATE VECTOR INDEX vulnerability_embedding IF NOT EXISTS
			FOR (v:Vulnerability) ON (v.embedding)
			OPTIONS {indexConfig: {`vector.dimensions`: 384, `vector.similarity_function`: 'cosine'}}
			""";

	private final Neo4jClient neo4j;

	@Override
	public void run(ApplicationArguments args) {
		CONSTRAINTS.forEach(constraint -> this.neo4j.query(constraint).run());
		this.neo4j.query(VULNERABILITY_EMBEDDING_INDEX).run();
		log.info("Graph schema ensured: {} uniqueness constraints, 1 vector index.", CONSTRAINTS.size());
	}

}
