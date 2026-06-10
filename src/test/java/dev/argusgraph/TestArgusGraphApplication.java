package dev.argusgraph;

import org.springframework.boot.SpringApplication;

/**
 * Dev convenience entry point: runs the real application against an automatically-started
 * Neo4j Testcontainer. Run this from your IDE for a zero-setup local boot — no manual
 * {@code docker compose up}, no {@code .env}.
 */
public final class TestArgusGraphApplication {

	private TestArgusGraphApplication() {
	}

	public static void main(String[] args) {
		SpringApplication.from(ArgusGraphApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
