package dev.argusgraph;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifies the modular-monolith boundaries: no module reaches into another module's
 * {@code api}/{@code application}/{@code infrastructure} internals — cross-module access
 * must go through a published {@code *API} contract. Fails the build if a boundary is
 * violated. Pure JUnit, no Spring context or database needed, so it runs fast.
 *
 * <p>
 * Run {@code ApplicationModules.of(...).verify()} after adding a module to confirm it's
 * wired cleanly.
 */
class ModulithTests {

	@Test
	void verifiesModuleBoundaries() {
		ApplicationModules.of(ArgusGraphApplication.class).verify();
	}

}
