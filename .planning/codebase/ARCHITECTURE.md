# Architecture

A **Spring Boot modular monolith** built with Domain-Driven Design, enforced by Spring
Modulith and jMolecules, persisting a knowledge graph in Neo4j.

## Module model

The application base package is `dev.argusgraph` (`@Modulith` on `ArgusGraphApplication`).
Every **direct sub-package** is a Spring Modulith application module:

| Module    | Role                                                                  |
|-----------|-----------------------------------------------------------------------|
| `app`     | Cross-cutting bootstrap: security, error handling, OpenAPI, config.   |
| `shared`  | OPEN shared kernel — tiny dependency-free types (common exceptions).  |
| `graph`   | KG core: domain types, `GraphAPI` contract, Cypher persistence, read API. Depends on no other module. |
| `ingest`  | Input adapters: typed REST today, scraper workers (OSV/NVD/GHSA/deps.dev) later. Calls `graph` via `GraphAPI` only. |

## The graph model (model A — version-level)

```
(:Package {purl, type, namespace, name})─[:HAS_VERSION]→(:PackageVersion {purl, version})
(:PackageVersion)─[:DEPENDS_ON {scope}]→(:PackageVersion)
(:Vulnerability {id, source, summary, severity, cvssScore, published, aliases})─[:AFFECTS]→(:PackageVersion)
```

- **Natural keys**: canonical purl without version (`Package`), with version
  (`PackageVersion`), advisory id (`Vulnerability`). Qualifiers/subpath are dropped so all
  sources merge onto the same nodes. Uniqueness constraints are created at startup.
- **Idempotent upserts**: every write is `MERGE`; optional vulnerability fields only fill
  gaps (`coalesce`), never erase stored data. Sources can be replayed safely.
- **Why version-level**: transitive exposure is a pure path query — no semver-range math
  at query time. Matches the resolved-version data OSV/deps.dev provide.
- `embedding` is a placeholder property for the future embedding pipeline.

## The boundary rule

A module exposes exactly one thing to other modules: its **published `*API` interface**
(plus root-package value objects like `Purl`). Everything under a module's `api/`,
`application/`, and `infrastructure/` packages is private.

`ingest` → `graph` is the reference example: `IngestService` calls `GraphAPI` methods and
works only with its nested records. It never imports `GraphService`, the domain types'
internals, or the Cypher adapter.

Boundaries are enforced **twice**:

1. **Compile time** — Checkstyle `import-control.xml` (`allow`/`disallow` per module).
2. **Test time** — `ModulithTests` calls `ApplicationModules.of(...).verify()`.

## Layering (within the graph module)

```
HTTP → api (GraphController, response DTOs)
         → application (GraphService = use cases, GraphRepository PORT, read models)
              → domain (Purl, PackageVersion, Vulnerability: invariants & normalisation)
              → infrastructure (Neo4jGraphRepository ADAPTER: Neo4jClient + MERGE Cypher,
                                GraphSchemaInitializer: startup constraints)
```

- The **domain types** are plain Java — no Spring/Neo4j annotations. Validation and
  normalisation (purl canonicalisation, severity upper-casing, CVSS range) live here.
- The **repository port** (`application/GraphRepository`) is expressed in graph terms.
- The **adapter** (`infrastructure/persistence/Neo4jGraphRepository`) owns all Cypher.
  Swapping/extending persistence touches only this package.

jMolecules annotations (`@AggregateRoot`, `@ValueObject`, `@Repository`, `@DomainLayer`,
`@Service`) make the intended roles explicit.

## Error handling

`app/api/exception/GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to RFC 9457
`ProblemDetail` (`application/problem+json`):

| Exception                   | HTTP | Source                                        |
|-----------------------------|------|-----------------------------------------------|
| `ResourceNotFoundException` | 404  | `shared.exception` (unknown purl on read)     |
| `BusinessRuleException`     | 409  | `shared.exception` (malformed purl, bad CVSS) |
| `MethodArgumentNotValid`    | 400  | Bean Validation on DTOs                       |

Add an `@ExceptionHandler` method per new failure shape — there is no exception hierarchy.

## Security

Open by default (`SecurityConfig` permits all requests, stateless, CSRF off). Spring Security
is on the classpath so locking down later is a one-file change. A commented JWT resource-server
chain is included inline.
