package dev.argusgraph;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up a throwaway Neo4j container for tests. {@code @ServiceConnection} auto-wires
 * Spring's Neo4j driver to it — no bolt URIs in test config. Import this into any
 * {@code @SpringBootTest} that needs the graph database.
 *
 * <p>
 * Requires a running Docker daemon. Tests that don't touch the database (e.g.
 * {@code ModulithTests}) don't import this and run without Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	Neo4jContainer<?> neo4jContainer() {
		return new Neo4jContainer<>(DockerImageName.parse("neo4j:5-community"));
	}

}
