/**
 * Shared kernel — tiny, dependency-free types usable by every module (e.g. the common
 * exception classes mapped to HTTP problems). Declared OPEN so any module may import it
 * without tripping module-boundary verification. Keep this package small; anything with
 * real behaviour belongs in a proper module.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN,
		displayName = "Shared Kernel")
package dev.argusgraph.shared;
