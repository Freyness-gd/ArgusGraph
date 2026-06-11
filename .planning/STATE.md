# Project State

## Project Reference

See: .planning/PROJECT.md

**Core value:** Transitive vulnerability detection as a graph problem — dependencies +
advisories in one version-level Neo4j knowledge graph.
**Current focus:** Ingest + dashboard delivered ahead of roadmap order (Phases 2, 3-partial,
5 substantially shipped). Main unbuilt piece is **Phase 4 — inference engine** (materialised
Cypher rules, fixpoint, incremental recompute). Secondary: true bulk import path (UNWIND)
for the OSV dump — current import is per-doc through the RabbitMQ worker.

## Current Position

Phase: roadmap order overtaken — see "Reconciliation" below
Plan: between plans (last shipped: dashboard charts)
Status: End-to-end pipeline live — OSV REST/fetch trigger → RabbitMQ → graph; embedding
queue attaches 384-dim MiniLM vectors; Mithril dashboard SPA (stats/browse/lookup/jobs/
projects + charts); project module imports CycloneDX SBOMs into H2 and matches purls
against the graph. Working tree clean except untracked `tools/`.
Last activity: 2026-06-11 — dashboard charts (vulnerability trend, top packages, severity
donut): stats endpoints + vendored Chart.js views (9e2fe4e, c7309b8, 162a482)

Progress: [███████░░░] ~70%

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
- Design specs live in local `docs/` (gitignored — never committed); research notes
  (embeddings, inference engine, reading list) in `docs/research/EMBEDDINGS-AND-INFERENCE.md`.

### Pending Todos

- **Phase 4 inference engine** (next major): materialised Cypher rules with provenance
  (R1 transitive exposure, R2 range resolution, R3 alias merge, R4 withdrawn retraction),
  fixpoint loop, Modulith events for incremental recompute. Starter plan in
  `docs/research/EMBEDDINGS-AND-INFERENCE.md`.
- Bulk import path for the OSV dump — stream local dump files (user downloads the GCS
  bucket separately), UNWIND-batched Cypher instead of per-doc worker throughput.
- Continuous/polling scraper (finish Phase 3) — current OSV fetch is manual trigger only.
- Update ROADMAP.md progress table to match delivered reality.
- Untracked `tools/jackson/databind/JsonNode.java` in working tree — stray decompiled
  source; delete or gitignore.

### Blockers/Concerns

- Spring Boot/Modulith on RC builds — move to GA when released (drop the Spring milestone
  repo in `settings.gradle.kts`).
- 19 test classes; suite not re-run during this reconciliation session — verify green
  before next commit.

## Session Continuity

Last session: 2026-06-11 — dashboard charts shipped (trend, top packages, severity donut).
Stopped at: 9e2fe4e (docs: chart endpoints and dashboard blurb). Working tree clean except
untracked `tools/`.
Resume file: None — pick next from Pending Todos (Phase 4 inference engine is the headline).
