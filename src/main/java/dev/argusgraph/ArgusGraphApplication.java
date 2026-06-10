package dev.argusgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>
 * {@link Modulith} marks this package ({@code dev.argusgraph}) as the base of a
 * modular monolith: every direct sub-package ({@code graph}, {@code ingest}, ...) is a
 * Spring Modulith application module whose boundaries are verified by
 * {@code ModulithTests}.
 */
@Modulith
@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class ArgusGraphApplication {

	public static void main(String[] args) {
		SpringApplication.run(ArgusGraphApplication.class, args);
	}

}
