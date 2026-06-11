# Testing

| Test                          | Scope                                       | Needs Docker? |
|-------------------------------|---------------------------------------------|---------------|
| `ModulithTests`               | Module-boundary verification                | No            |
| `graph/PurlTest`              | Purl canonicalisation rules (pure unit)     | No            |
| `IngestGraphIntegrationTest`  | End-to-end HTTP over real Neo4j             | Yes           |
| `IngestWorkerIntegrationTest` | Worker E2E: POST /ingest/jobs/osv → RabbitMQ → Neo4j | Yes  |

## Module verification — `ModulithTests`

`ApplicationModules.of(ArgusGraphApplication.class).verify()` fails if any module reaches
into another module's internals. Pure JUnit, no Spring context, no DB — fast, and the first
thing to run after adding or rewiring a module.

## Integration tests — Testcontainers

`TestcontainersConfiguration` provides a `Neo4jContainer` and a `RabbitMQContainer`, both
wired via Spring Boot's `@ServiceConnection` (no URIs in test config). Import it into any
`@SpringBootTest`:

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class SomethingIntegrationTest { ... }
```

`IngestGraphIntegrationTest` is the worked example — the Log4Shell walkthrough: ingest
log4j-core 2.14.1, an app depending on it, and CVE-2021-44228 affecting it; read both back
through the graph endpoint. It also pins **idempotency** (re-ingesting creates no duplicate
nodes/edges, asserted with raw Cypher counts via `Neo4jClient`) and the **error contract**
(400 validation / 404 unknown / 409 malformed purl).

### Boot 4 gotchas (learned here)

- `TestRestTemplate` import is `org.springframework.boot.resttestclient.TestRestTemplate`,
  requires `@AutoConfigureTestRestTemplate` and `spring-boot-restclient` on the test
  classpath.
- Its base URL **already includes the `/api/v1` context path** — request `/ingest/...`,
  not `/api/v1/ingest/...`.

## Run the app against a throwaway DB

`TestArgusGraphApplication` (in `src/test`) runs the real application with the
Testcontainers Neo4j attached — use it from the IDE for a zero-setup local boot.

## Conventions

- **AssertJ** with static imports (`assertThat(...)`); Checkstyle enforces static AssertJ and
  bans JUnit `Assertions`.
- Coverage via JaCoCo: `./gradlew test` → report at `build/reports/jacoco/test/html/index.html`.
- Prefer fast unit tests on domain types (pure Java, no Spring) for rules like purl
  canonicalisation; reserve `@SpringBootTest` + Testcontainers for wiring and persistence.

## Commands

```bash
./gradlew test                          # everything (integration test needs Docker)
./gradlew test --tests ModulithTests    # boundaries only, no Docker
```
