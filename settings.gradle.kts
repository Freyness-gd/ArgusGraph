plugins {
	// Auto-provisions the JDK toolchain (BellSoft Liberica 25) if it's missing locally.
	id("org.gradle.toolchains.foojay-resolver-convention") version ("1.0.0")
}

rootProject.name = "argusgraph"

// ── Centralised repository management ─────────────────────────────────────────
// FAIL_ON_PROJECT_REPOS ensures no individual build.gradle.kts can declare its
// own repositories — all resolution goes through here. Maven Central plus the Spring
// milestone repo, which is needed for the Spring Boot 4.1.0 RC line.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		mavenCentral()
		// Required for the Spring Boot 4.1.0 RC line. Remove once you move to a GA release.
		maven("https://repo.spring.io/milestone")
	}
}
