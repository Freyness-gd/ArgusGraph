# Project State

## Project Reference

See: .planning/PROJECT.md

**Core value:** Transitive vulnerability detection as a graph problem — dependencies +
advisories in one version-level Neo4j knowledge graph.
**Current focus:** Phase 4 inference engine STARTED — slice 1 (R1 transitive exposure)
shipped. Next: R2 version-range resolution, then deps.dev global dependency graph (the
heavy path the user chose for ecosystem-wide reasoning). Secondary: true bulk import path
(UNWIND) for the OSV dump — current import is per-doc through the RabbitMQ worker.

## Current Position

Phase: 4 (Inference engine) — slice 1 of N shipped
Plan: Phase 4 slice 1 complete (R1). Plan/spec local in `docs/superpowers/` (gitignored).
Status: Inference engine live — SBOM import writes DEPENDS_ON edges → Modulith event →
rule R1 materialises `(:Vulnerability)-[:TRANSITIVELY_AFFECTED {depth, provenance}]->
(:PackageVersion)` → project detail shows a TRANSITIVELY_AFFECTED verdict + depth + UI card.
`POST /api/v1/inference/recompute` rebuilds derived edges. Full `clean check` green
(63 tests). Working tree: untracked `tools/` + user's RabbitMQ-tuning WIP
(`application.yaml`, `OsvQueueListener.java`) uncommitted.
Last activity: 2026-06-12 — Phase 4 slice 1: `inference` module (R1 + engine + recompute),
SBOM dependency-edge parsing, transitive exposure on the project detail (27a135b..8fa7f20)

Progress: [████████░░] ~75%

## Reconciliation (2026-06-11)

This file was stale: it claimed "stopped at OSV ingestion (14d96d4), waiting on OSV bucket
download." That commit is not in this history and ~30 commits landed since. Actual delivered
work, mapped to roadmap phases:

- **Phase 1 Skeleton** — done.
- **Phase 2 Data import** — OSV schema ingest (single + batch), CVSS derivation, AFFECTS /
  AFFECTS_PACKAGE edges, MiniLM embeddings + vector index, RabbitMQ ingest worker with
  scaled consumers (4-8, bounded prefetch). *Gap:* no UNWIND bulk path yet — import is
  per-doc via the worker.
- **Phase 3 Scraper workers** — partial: manual OSV fetch trigger, `OsvHttpSource`,
  in-memory job registry, job-status endpoint (history + live queue depths), graph reset
  (WIPE-confirmed, batched). Not yet continuous/polling.
- **Phase 4 Inference engine** — NOT started. The next major work.
- **Phase 5 SBOM + GUI** — substantially done ahead of order: Mithril SPA (vendored, hash
  routing, dark shell) with stats / browse (paged, filtered) / lookup / jobs / projects
  views + Chart.js charts; project module (H2 dual-store, CycloneDX parser, on-demand purl
  matching). *Gap:* dependency-graph / exposure visualisation depends on Phase 4.

ROADMAP.md progress table is also stale (shows phases 2/3/5 "Not started") — update it too.

## Accumulated Context

### Decisions

- Adopted the DDD Spring Boot Template: modular monolith, Spring Modulith + jMolecules,
  open security, RFC 9457 errors. See `.planning/codebase/`.
- Renamed to ArgusGraph, base package `dev.argusgraph`. Modules: app, graph, ingest,
  project, shared.
- Neo4j for the knowledge graph; `spring-modulith-starter-neo4j` keeps the event
  publication registry in Neo4j. The **project module adds a second store** — H2 via JDBC
  for imported SBOM projects — so the app is dual-store with **explicit JDBC + Neo4j
  transaction managers** (455c715); integration tests force in-memory H2 (c1b0fbd).
- Graph model A (version-level): vulnerability edges attach to `PackageVersion` nodes —
  transitive checks are pure path queries, no version-range math at query time.
- Natural keys: canonical purl (qualifiers/subpath dropped) / advisory id. Deviation from
  the template's UUID convention, documented in the domain types.
- Persistence via `Neo4jClient` + explicit `MERGE` Cypher behind the `GraphRepository`
  port — no SDN `@Node` mapping. Upserts only fill gaps (`coalesce`), never erase data.
- OSV schema is THE vulnerability input format. Severity = raw CVSS vectors + derived
  score/label (V4>V3>V2 via cvss-calculator). Enumerated versions → `AFFECTS` edges; raw
  ranges JSON → `AFFECTS_PACKAGE` edges (future inference engine consumes them). Unknown
  ecosystems fall back to `pkg:generic` — no advisory lost.
- Ingest pipeline: REST/fetch trigger → RabbitMQ raw-document queue → `OsvDocumentHandler`
  → graph; a **separate embedding queue** attaches MiniLM vectors (in-JVM ONNX) via
  `GraphAPI.attachEmbedding` to a `vulnerability_embedding` vector index.
- Dashboard: single-page app, **vendored Mithril** (no build step), hash routing, dark
  shell, served as static resources. Charts use **vendored Chart.js**. Read API surface:
  graph stats, paged/filtered vulnerability list, vulnerability trend, top packages,
  severity donut.
- Boot 4 uses Jackson 3 (`tools.jackson`) — inject that `ObjectMapper`, not
  `com.fasterxml`; Jackson exceptions are unchecked now (parse path catches
  `JacksonException`).
- **Inference engine architecture (Phase 4, 2026-06-12):** self-contained `inference`
  Modulith module (peer of graph/project), own `Neo4jClient` Cypher over shared labels —
  no graph-internal imports. `InferenceRule` interface + `InferenceEngine` fixpoint (one
  pass with single self-complete rule R1; ready for R2..Rn). Derived edges carry provenance
  (`inferredBy`, `ruleVersion`, `derivedAt`) and are separable for delete-and-rebuild
  recompute. Trigger is event-driven: `project` publishes `InferenceAPI.DependenciesLinked`
  after import; `@ApplicationModuleListener` (after-commit, async) runs R1 scoped to the
  imported purls — single module edge `project → inference`. Per-call Neo4j commits precede
  the after-commit dispatch, so R1 never races uncommitted DEPENDS_ON edges.
- Scope decision (2026-06-12): engine-first on **SBOM-sourced** DEPENDS_ON edges (cheap,
  precise per-repo), NOT a deps.dev global load yet. Keeps R1 output bounded. deps.dev
  global graph is the chosen-but-deferred heavy path for ecosystem-wide reverse queries.
- Design specs live in local `docs/` (gitignored — never committed); research notes
  (embeddings, inference engine, reading list) in `docs/research/EMBEDDINGS-AND-INFERENCE.md`.
  Phase 4 slice-1 spec + plan: `docs/superpowers/{specs,plans}/2026-06-12-phase4-*`.
  Architecture diagrams (mermaid): `docs/ARCHITECTURE-DIAGRAMS.md`.

### Pending Todos

- **Phase 4 next rules:** R2 version-range resolution (materialise `AFFECTS` from OSV
  `AFFECTS_PACKAGE` ranges — SemVer first, Maven second), then R3 alias merge, R4 withdrawn
  retraction. The `AFFECTS_PACKAGE {rangesJson}` edges already carry the raw OSV ranges R2
  consumes. Extension points (rule interface, scope, provenance, recompute) are in place.
- **deps.dev global dependency graph** (the user's chosen heavy path): bulk-ingest
  package→package edges for ecosystem-wide reasoning + reverse "who is exposed" queries.
  Needs the bulk UNWIND path. Additive to the engine, which already traverses any
  `DEPENDS_ON` edges.
- Bulk import path for the OSV dump — stream local dump files, UNWIND-batched Cypher
  instead of per-doc worker throughput. Same bulk path deps.dev needs.
- Continuous/polling scraper (finish Phase 3) — current OSV fetch is manual trigger only.
- Optional UI: a recompute button in the Jobs tab wired to `POST /api/v1/inference/recompute`
  (endpoint exists; only curl-reachable today).
- Untracked `tools/jackson/databind/JsonNode.java` in working tree — stray decompiled
  source; delete or gitignore.
- User's RabbitMQ-tuning WIP (`application.yaml` listener concurrency, `OsvQueueListener.java`)
  is uncommitted — left untouched during Phase 4; the user owns it.

### Blockers/Concerns

- Spring Boot/Modulith on RC builds — move to GA when released (drop the Spring milestone
  repo in `settings.gradle.kts`).
- R1 write Cypher enumerates all `DEPENDS_ON*` paths before collapsing to shortest depth —
  fine on the bounded SBOM subgraph, but revisit for cost when deps.dev global edges land
  (cyclic/diamond graphs).

## Session Continuity

Last session: 2026-06-12 — Phase 4 slice 1 (R1 transitive exposure) shipped via
subagent-driven development: `inference` module, SBOM dependency-edge parsing, transitive
exposure on the project detail, recompute endpoint, UI card. Full `clean check` green
(63 tests). Merged to main (fast-forward, 27a135b..8fa7f20). Also fixed this session:
project-detail route params (fc56731), graph-wipe tx-memory batch (a46c515), H2 AUTO_SERVER
startup lock (9bbe51a).
Stopped at: 8fa7f20. Working tree: untracked `tools/` + user's RabbitMQ-tuning WIP.
Resume file: None — next is Phase 4 R2 (range resolution) or the deps.dev global graph.
