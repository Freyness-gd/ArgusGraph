# Stack

Spring Boot/Modulith track the 4.1.0 RC line, so `settings.gradle.kts` enables the Spring
milestone repository (everything else is on Maven Central). Versions are pinned in
`gradle/libs.versions.toml`; dependency locking is on (`gradle.lockfile`).

| Concern        | Choice                          | Version    | Notes                                            |
|----------------|---------------------------------|------------|--------------------------------------------------|
| Language       | Java                            | 25         | BellSoft Liberica toolchain (Foojay auto-provision) |
| Framework      | Spring Boot                     | 4.1.0-RC1  | Spring Framework 7.1 (Spring milestone repo)     |
| Modularity     | Spring Modulith                 | 2.1.0-RC1  | starter-core + starter-neo4j (Neo4j event registry) |
| DDD/layering   | jMolecules                      | 2025.0.2   | ddd + layered-architecture annotations           |
| Web            | Spring Web MVC                  | (Boot BOM) | `spring-boot-starter-webmvc`                     |
| API docs       | springdoc-openapi               | 3.0.3      | 3.x line targets Spring Boot 4 / Spring 7        |
| Graph store    | Neo4j 5 Community + Spring Data Neo4j | (Boot BOM) | `Neo4jClient` + explicit Cypher; no SDN `@Node` mapping |
| Messaging      | RabbitMQ 4 (AMQP 0-9-1), Spring AMQP | (Boot BOM) | topic exchange `argus.ingest`, DLX/DLQ, async source-ingestion pipeline |
| Purl parsing   | packageurl-java                 | 1.5.0      | canonical purl natural keys                      |
| Schema         | `GraphSchemaInitializer`        | —          | uniqueness constraints at startup (`IF NOT EXISTS`) |
| Security       | Spring Security                 | (Boot BOM) | open by default; oauth2-resource-server commented |
| Boilerplate    | Lombok                          | (Boot BOM) | `lombok.config` present                          |
| Validation     | Jakarta Bean Validation         | (Boot BOM) | `spring-boot-starter-validation`                 |
| Testing        | JUnit 5, AssertJ, Testcontainers, Spring Modulith test | (BOMs) | Neo4j + RabbitMQ containers via `@ServiceConnection` |
| Build          | Gradle (Kotlin DSL)             | 9.4.0      | version catalog + `dependencyLocking`            |
| Code quality   | Checkstyle (ImportControl)      | 13.0.0     | **non-blocking** (warn only), JaCoCo coverage    |

## Notable choices

- **No `io.spring.dependency-management` plugin** — versions come from imported `platform()`
  BOMs (`spring-boot-dependencies`, `spring-modulith-bom`, `jmolecules-bom`, `testcontainers-bom`).
- **No SDN `@Node` mapping** — the graph adapter uses `Neo4jClient` with hand-written
  `MERGE` Cypher: idempotent upserts, no accidental deep-fetch of the cyclic dependency
  graph, ready for `UNWIND` bulk ingest.
- **Boot 4 module split gotcha** — `TestRestTemplate` lives in
  `org.springframework.boot.resttestclient` and needs `spring-boot-restclient` on the
  test classpath (`RestTemplateBuilder`).
- **Checkstyle is the only linter** — warn-only; whitespace/indent is left to `.editorconfig`.
- **No verification-metadata.xml** — dependency *locking* only (`gradle.lockfile`).

## Regenerating the lockfile

After changing any dependency: `./gradlew dependencies --write-locks`.
