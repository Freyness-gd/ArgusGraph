# Conventions

Follow these when adding code so modules stay consistent and the boundary checks keep passing.

## Vertical slice per module

```
{module}/
├── {DomainType}.java          # domain model (root package)
├── {Module}API.java           # published contract (root package) — only if other modules need it
├── package-info.java          # @ApplicationModule + jMolecules layer annotation
├── api/                       # @RestController + request/response records
├── application/               # {Module}Service + {Module}Repository (port) + read models
└── infrastructure/            # persistence/ (adapter implementing the port)
```

## Domain types

- Plain Java. **No** Spring or Neo4j annotations. Lombok `@Getter` is fine.
- Private constructor; two static factories: `create(...)` (new instance, validates
  invariants) and `reconstitute(...)` (rebuild from storage, no re-validation).
- Business rules / invariants are methods on the domain type; they throw `BusinessRuleException`.
- `@AggregateRoot`/`@ValueObject` + `@Identity` (jMolecules) on the class / id field.
- **Identity is a natural key here** (canonical purl / advisory id) — a documented
  deviation from the template's fresh-UUID convention: purls and CVE ids are globally
  unique by construction and make cross-source merging trivial.

## Application layer

- One `{Module}Service` — `@Service`, `@org.jmolecules.ddd.annotation.Service`,
  `@RequiredArgsConstructor`, `@Transactional` (use `@Transactional(readOnly = true)` on reads).
- Each public method is a use case. The service orchestrates; it does **not** hold business rules.
- `{Module}Repository` is the domain **port**: `@org.jmolecules.ddd.annotation.Repository`,
  methods expressed in domain terms (`upsertPackageVersion(PackageVersion)`,
  `findPackageVersion` → `Optional<PackageVersionDetails>`).
- A module's `*API` is implemented by its service and returns immutable record snapshots,
  never domain objects.

## API layer

- Controller is thin: map HTTP → service call → response DTO. No logic.
- Requests/responses are **records** with springdoc `@Schema` and Jakarta validation
  (`@NotBlank`, `@DecimalMin`, ...). Validate with `@Validated` on the body.
- Response records expose a static `from(...)` factory.
- `@Tag` on the controller, `@Operation` on each endpoint.
- Purls travel as query parameters on reads (they contain slashes) and in JSON bodies on writes.

## Infrastructure layer (graph persistence)

- `Neo4j{Module}Repository` (package-private `@Component`) implements the domain port with
  `Neo4jClient` and **hand-written Cypher**: `MERGE` for all writes (idempotent upserts),
  `coalesce($param, n.prop)` so optional fields fill gaps without erasing data.
- **No SDN `@Node` entities** — avoids accidental deep-fetch of the cyclic dependency
  graph and keeps the statements one `UNWIND` away from bulk ingestion.
- Constraints/indexes live in `GraphSchemaInitializer` (startup, `IF NOT EXISTS`).

## Cross-module calls

- Import only the other module's `*API` (and its nested records) plus exported root-package
  value objects (e.g. `graph.Purl`). Add an `allow class="..."` for it in
  `import-control.xml`; keep the module's sub-packages `disallow`-ed.
- Never import another module's domain internals, service, controller, or adapter.

## Errors

- Throw `ResourceNotFoundException(Type.class, id)` for missing things (→ 404) and
  `BusinessRuleException(msg)` for invariant violations (→ 409). Across a boundary, use the
  `ResourceNotFoundException(String, id)` form to avoid importing the owning module's type.

## Style

- Tabs, 160-col lines — enforced by `.editorconfig` at the editor level (no auto-formatter).
- Checkstyle (warn-only) enforces module boundaries (ImportControl) and clean imports.
- AssertJ over JUnit assertions in tests (Checkstyle flags `org.junit...Assertions`).
