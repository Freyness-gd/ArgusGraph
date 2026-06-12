# Project State

## Project Reference

See: .planning/PROJECT.md

**Core value:** Transitive vulnerability detection as a graph problem — dependencies +
advisories in one version-level Neo4j knowledge graph.
**Current focus:** Phase 4 inference engine — slices 1 (R1 transitive exposure) AND 2
(R2 range resolution + recursive R1 + stratified fixpoint) shipped. Reframed: the engine is
the project "heart" (a logical/Datalog-style KG reasoner) AND a **pluggable, benchmarkable
engine slot**. Next: **slice 4.3** — pluggable engine strategies (eager/lazy/incremental,
naive vs semi-naive) + per-run metrics + UI switch; then **slice 4.4** — embedding engine
(severity imputation via kNN, latent reasoning); then **Phase 6** — evaluation + portfolio.

## Current Position

Phase: 4 (Inference engine) — slices 1 & 2 of N shipped
Plan: slice 4.2 complete. Specs/plans local in `docs/superpowers/` (gitignored).
Status: Engine is now a **recursive multi-rule Datalog reasoner**. R2 (stratum 0) resolves
OSV ranges (Maven + SemVer comparators, Java built-in predicate) → `AFFECTS {inferredBy:'R2'}`;
R1 re-expressed as recursive rules (base depth 1 + step depth+1) the engine **iterates to
fixpoint** (stratified naive forward-chaining; R2 ▶ R1). `recomputeAll` runs R2+R1 globally;
scoped import runs R1 only. Full `clean check` green. Working tree: untracked `tools/`.
Last activity: 2026-06-12 — slice 4.2: version comparators, OSV range evaluator, recursive R1
rules, R2 range resolution, stratified fixpoint engine (d76e623..5df2228)

Progress: [█████████░] ~80%

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
- **Slice 4.2 (2026-06-12) — recursive engine + R2.** Recursion chosen as **recursive rules**
  (option B): R1 = base (`AFFECTS ∧ DEPENDS_ON ⇒ TA depth 1`) + step (`TA ∧ DEPENDS_ON ⇒ TA
  depth+1`), engine iterates to fixpoint (naive forward-chaining) — authentic Datalog over
  Neo4j's native `DEPENDS_ON*` (the fast path becomes a 4.3 benchmark baseline). R2 = stratum
  0, non-recursive, Maven (`ComparableVersion`) + SemVer comparators via a **Java built-in
  predicate** (`OsvRangeEvaluator`; unsupported ecosystems/GIT → unresolved+counted), writes
  `AFFECTS {inferredBy:'R2'}` that R1 consumes. Fixpoint termination via an `ON CREATE _new`
  flag (true creation counts). `runFor` (import) runs R1 only; `recomputeAll` runs R2▶R1.
- **Engine reframed as a pluggable, benchmarkable slot (the project "heart").** Decisions
  with the user: stay **option-1 Cypher-Datalog** only (no real-Datalog engine / DSL). Build
  **2–3 switchable engine strategies** compared on compute-time/efficiency in the UI — the
  course's logical (`r_𝕂`) vs latent (`r_𝕖`) representations made empirical. Course-grounded
  (TU Wien KG, Sallinger): a KG = ground extensional + intensional (rules) + derived
  extensional (reasoning `r:D→D`) — the engine IS what makes this a *knowledge* graph.
  Frame engines via fixpoint/recursive-evaluation/tractability vocabulary in the portfolio.
- **Embeddings → severity imputation (slice 4.4).** Repurpose the MiniLM text vectors:
  embedding-kNN over the vector index → `predictedSeverity {score, confidence, inferredBy:'E1'}`
  for advisories lacking CVSS. This is `r_𝕖` (reasoning with latent knowledge), a *third*
  engine for the comparison (logical vs latent), and gives an **accuracy** metric (hold-out
  MAE/label-accuracy), not just speed. Text embeddings (not structural KGE/TransE — that's
  explicit future work).
- Design specs live in local `docs/` (gitignored — never committed); research notes in
  `docs/research/EMBEDDINGS-AND-INFERENCE.md`. Phase 4 specs/plans:
  `docs/superpowers/{specs,plans}/2026-06-12-phase4-*`. Architecture diagrams:
  `docs/ARCHITECTURE-DIAGRAMS.md`. KG course slides → `/tmp/kg_part{2,3,4}.txt` (transient).

### Pending Todos (ordered roadmap — see ROADMAP.md)

- **Slice 4.3 (next):** pluggable engine strategies (eager / incremental / lazy-query-time;
  naive vs semi-naive) over R1+R2, `InferenceRun` metrics (time/passes/edges/storage), UI
  switch + comparison view. R1's recursive form makes naive-vs-semi-naive a real comparison;
  the native `DEPENDS_ON*` path-match returns here as the fast baseline strategy.
- **Slice 4.4:** embedding engine — severity imputation (above) + accuracy eval.
- **Phase 6:** evaluation + ~6-page portfolio (comparison tables/charts: speed·storage·
  accuracy; course vocabulary).
- **Optional 4.2b:** R3 alias merge (`SAME_AS`), R4 withdrawn retraction → stratified
  negation story (only delete, runs last). Strengthens the logical writeup.
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
- Naive fixpoint re-applies rules each round (re-scans) — intentional baseline for the 4.3
  comparison; semi-naive (delta-driven) is a 4.3 strategy. R2's `r2Candidates()` full-graph
  scan runs only on `recomputeAll` (gated off scoped imports).

## Session Continuity

Last session: 2026-06-12 — Phase 4 **slice 4.2** shipped via subagent-driven development:
version comparators (SemVer + Maven), OSV range evaluator (R2 built-in predicate), recursive
R1 rules (base + step), R2 range resolution, stratified naive-fixpoint engine. Full
`clean check` green. Merged to main (fast-forward, d76e623..5df2228). Earlier same day:
slice 4.1 (27a135b..8fa7f20), severity badge colours (medium=yellow/high=orange/distinct
UNKNOWN), architecture diagrams (`docs/ARCHITECTURE-DIAGRAMS.md`), three bugfixes
(route params, graph-wipe tx memory, H2 AUTO_SERVER lock). Design done this session for the
engine-as-pluggable-benchmarkable-heart + embedding severity imputation (course-grounded vs
TU Wien KG slides).
Stopped at: 5df2228. Working tree: untracked `tools/` only.
Resume file: None — next is **slice 4.3** (pluggable engine strategies + metrics + UI switch).
Specs/plans for 4.3 not yet written.
