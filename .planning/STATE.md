# Project State

## Project Reference

See: .planning/PROJECT.md

**Core value:** Transitive vulnerability detection as a graph problem — dependencies +
advisories in one version-level Neo4j knowledge graph.
**Current focus:** Phase 2 (data import) in progress — worker pipeline (RabbitMQ) and
text embeddings live. Next: bulk import of the OSV dump (user downloads the GCS bucket
with a separate program), then UNWIND bulk path if per-doc throughput disappoints.

## Current Position

Phase: 2 (Data import) — OSV format + worker pipeline + embeddings shipped
Plan: in progress
Status: REST trigger → RabbitMQ → graph; embedding queue attaches 384-dim MiniLM
vectors (summary+details) to Vulnerability nodes; vector index live. 43/43 tests green
Last activity: 2026-06-11 — embedding pipeline (separate queue, in-JVM ONNX MiniLM,
GraphAPI.attachEmbedding, vulnerability_embedding vector index)

Progress: [████░░░░░░] ~40%

## Accumulated Context

### Decisions

- Adopted the DDD Spring Boot Template: modular monolith, Spring Modulith + jMolecules,
  open security, RFC 9457 errors. See `.planning/codebase/`.
- Renamed to ArgusGraph, base package `dev.argusgraph`.
- Neo4j only (Postgres/JPA/Flyway removed); `spring-modulith-starter-neo4j` keeps the
  event publication registry in Neo4j.
- Graph model A (version-level): vulnerability edges attach to `PackageVersion` nodes —
  transitive checks are pure path queries, no version-range math at query time.
- Natural keys: canonical purl (qualifiers/subpath dropped) / advisory id. Deviation from
  the template's UUID convention, documented in the domain types.
- Persistence via `Neo4jClient` + explicit `MERGE` Cypher behind the `GraphRepository`
  port — no SDN `@Node` mapping. Upserts only fill gaps (`coalesce`), never erase data.
- OSV schema is THE vulnerability input format (replaced the flat DTO). Severity = raw
  CVSS vectors + derived score/label (V4>V3>V2 via cvss-calculator). Enumerated versions
  → `AFFECTS` edges; raw ranges JSON → `AFFECTS_PACKAGE` edges (future inference engine
  consumes them). Unknown ecosystems fall back to `pkg:generic` — no advisory is lost.
- Boot 4 uses Jackson 3 (`tools.jackson`) — inject that `ObjectMapper`, not
  `com.fasterxml`; Jackson exceptions are unchecked now.
- Design specs live in local `docs/` (gitignored — specs are never committed); research
  notes (embeddings, inference engine, reading list) in local
  `docs/research/EMBEDDINGS-AND-INFERENCE.md`.

### Pending Todos

- Phase 2 next: bulk import path for the OSV dump — stream local dump files (user
  downloads the GCS bucket separately), UNWIND-batched Cypher instead of per-doc REST.
- Inference engine starter plan lives in `docs/research/EMBEDDINGS-AND-INFERENCE.md`:
  materialised Cypher rules with provenance (R1 transitive exposure, R2 range
  resolution, R3 alias merge, R4 withdrawn retraction), fixpoint loop, Modulith events
  for incremental recompute.
- Boot 4 note: `TestRestTemplate` lives in `org.springframework.boot.resttestclient`
  and needs `spring-boot-restclient` on the test classpath; there is no context path —
  the `/api/v1` prefix is applied per-controller by `WebConfig`, so tests must spell
  the full path (e.g. `/api/v1/ingest/...`). Actuator is at root: `/actuator/health`.

### Blockers/Concerns

- Spring Boot/Modulith on 4.1.0-RC1 / 2.1.0-RC1 — move to GA when released (drop the
  Spring milestone repo in `settings.gradle.kts`).

## Session Continuity

Last session: 2026-06-10 — Phase 1 complete; Phase 2 started: OSV-schema ingestion
(single + batch endpoints), CVSS derivation, AFFECTS_PACKAGE range edges; research doc
on embeddings/inference written.
Stopped at: OSV ingestion committed (14d96d4). Waiting on user's OSV bucket download for
the bulk import step.
Resume file: None
