plugins {
	java
	jacoco
	checkstyle
	alias(libs.plugins.spring.boot)
}

group = "dev.argusgraph"
version = "0.0.1-SNAPSHOT"
description = "ArgusGraph — CVE knowledge graph for software supply chain analysis"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt())
		vendor.set(JvmVendorSpec.BELLSOFT)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

dependencies {

	// ── BOMs ──────────────────────────────────────────────────────────────────
	implementation(platform(libs.spring.boot.bom))
	implementation(platform(libs.spring.modulith.bom))
	implementation(platform(libs.jmolecules.bom))

	annotationProcessor(platform(libs.spring.boot.bom))

	testImplementation(platform(libs.spring.boot.bom))
	testImplementation(platform(libs.spring.modulith.bom))
	testImplementation(platform(libs.testcontainers.bom))

	// ── Application ─────────────────────────────────────────────────────────────
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-neo4j")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")

	// Canonical purl (package URL) parsing — natural keys of the knowledge graph.
	implementation("com.github.package-url:packageurl-java:${libs.versions.packageUrl.get()}")

	// CVSS vector parsing — derives numeric base scores from OSV severity vectors.
	implementation("us.springett:cvss-calculator:${libs.versions.cvssCalculator.get()}")

	// Modular monolith + DDD/layered architecture annotations.
	implementation("org.springframework.modulith:spring-modulith-starter-core")
	implementation("org.springframework.modulith:spring-modulith-starter-neo4j")
	implementation("org.jmolecules:jmolecules-ddd")
	implementation("org.jmolecules:jmolecules-layered-architecture")

	// ── OpenAPI / Docs ──────────────────────────────────────────────────────────
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${libs.versions.springDoc.get()}")

	// ── Enable real JWT auth (see app/infrastructure/SecurityConfig.java) ─────────
	// Uncomment to turn the open prototype security into a stateless JWT resource server.
	// implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

	// ── Compile-only ──────────────────────────────────────────────────────────────
	compileOnly("org.projectlombok:lombok")

	// ── Development only (excluded from production jar) ───────────────────────────
	developmentOnly(platform(libs.spring.boot.bom))
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// ── Annotation processors ─────────────────────────────────────────────────────
	annotationProcessor("org.projectlombok:lombok")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// ── Test ──────────────────────────────────────────────────────────────────────
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// TestRestTemplate (spring-boot-resttestclient) needs RestTemplateBuilder from this module.
	testImplementation("org.springframework.boot:spring-boot-restclient")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.modulith:spring-modulith-starter-test")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-neo4j")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	// testImplementation("org.springframework.boot:spring-boot-starter-security-test") // when security is enabled
}

// ── Dependency locking ──────────────────────────────────────────────────────────
// Produces gradle.lockfile (and settings-gradle.lockfile). Regenerate after changing
// a dependency: `./gradlew dependencies --write-locks`. No verification-metadata.xml.
dependencyLocking {
	lockAllConfigurations()
}

tasks.withType<Test> {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)

	// Mockito must run as a Java agent to avoid self-attach warnings on JDK 21+.
	val mockitoJar = configurations.testRuntimeClasspath.get()
		.resolvedConfiguration.resolvedArtifacts
		.first { it.name == "mockito-core" }.file
	jvmArgs(
		"-javaagent:$mockitoJar",
		"-XX:+EnableDynamicAgentLoading",
	)
}

// ── Checkstyle ──────────────────────────────────────────────────────────────────
checkstyle {
	toolVersion = libs.versions.checkstyle.get()
	configDirectory = file("config/checkstyle")
	// Warn only — violations appear in the build log and the HTML report, but the build
	// itself doesn't fail (a prototype keeps moving). Flip to false to make them blocking.
	isIgnoreFailures = true
	isShowViolations = true
}

// ── JaCoCo ────────────────────────────────────────────────────────────────────
tasks.jacocoTestReport {
	dependsOn(tasks.withType<Test>())
	reports {
		xml.required = true
		html.required = true
	}
}
