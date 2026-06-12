# Project State

## Project Reference

See: .planning/PROJECT.md

**Core value:** Transitive vulnerability detection as a graph problem — dependencies +
advisories in one version-level Neo4j knowledge graph.
**Current focus:** Phase 4 inference engine — slices 4.1 (R1 transitive exposure), 4.2 (R2 +
recursive R1 + fixpoint), 4.3 (pluggable benchmarkable engines), 4.4 (embedding severity
imputation — latent `r_𝕖`), AND the pluggable rule-pipeline slice all shipped. The engine is
the project "heart" — a logical/Datalog-style KG reasoner (logical `r_𝕂`) with 3 switchable
closure strategies, a runtime-editable ordered rule pipeline, AND a latent embedding-kNN
severity engine — all surfaced in the UI. Next: **Phase 6** — evaluation + ~6-page portfolio.

## Current Position

Phase: 4 (Inference engine) — slices 4.1, 4.2, 4.3, 4.4 + the pluggable rule pipeline shipped
Plan: 4.4 + rule pipeline complete. Specs/plans local in `docs/superpowers/` (gitignored).
Status: Engine is a recursive multi-rule Datalog reasoner with **pluggable closure strategies**
— `naive` / `semi-naive` / `native` — all producing IDENTICAL `TRANSITIVELY_AFFECTED`;
`recompute?engine=` records per-run metrics into a bounded in-memory ring buffer
(`InferenceRunLog`, cap 50). **Slice 4.4** adds a latent embedding engine (E1):
`SeverityImputation` imputes `predictedSeverity`/`predictedCvssScore {predictedBy:'E1'}` for
unscored advisories via distance-weighted kNN over the `vulnerability_embedding` index, with
leave-one-out MAE + label-accuracy eval (`POST /inference/{impute,eval}-severity`) — never
overwrites real scores. **Rule pipeline:** rules (R2, R1-base, R1-step) are now an in-memory,
runtime-editable ordered catalog (`RuleRegistry`) — enable/disable + reorder via
`GET/POST /inference/rules*`, run via `POST /inference/run-rules` (engine label `"rules"`);
the engine runs enabled rules in order (per-rule fixpoint), behaviour-preserving for the
default R2▶R1-base▶R1-step order. Dashboard **Inference tab** now has: engine dropdown + run +
comparison table/Chart.js bar, a latent severity-imputation card, and a Rules card (toggle,
▲▼ reorder, Run rules). Bruno collection has an `Inference/` folder for all endpoints. Full
`clean check` green (99 tests). Working tree: untracked `tools/`.
Last activity: 2026-06-13 — slice 4.4 (embedding severity imputation, latent E1) + pluggable
rule pipeline (RuleRegistry, list/toggle/reorder + run-rules, Rules UI card), both merged to main

Progress: [█████████░] ~90%

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

- **Phase 6 (next):** evaluation + ~6-page portfolio. Slice 4.4 (latent embedding engine E1) and
  the pluggable rule pipeline are DONE & merged — the comparison surface now spans the logical
  engines (naive/semi-naive/native: time·rounds·queryCount) and the latent engine (MAE·label-accuracy).
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
- Naive fixpoint re-applies rules each round (re-scans) — intentional baseline; semi-naive
  (delta frontier) + native (`DEPENDS_ON*`) are the faster strategies it's benchmarked against.
  R2's `r2Candidates()` full-graph scan runs only on `recomputeAll` (gated off scoped imports).
- Engine metrics are in-memory only (bounded ring buffer, lost on restart) — generate the
  portfolio comparison table in one benchmarking session. Neo4j persistence is a trivial add.
- Minor (slice 4.3, non-blocking): unknown `?engine=` → 409 (`BusinessRuleException`); 400
  would be more apt for a bad query param. Left as-is.

## Session Continuity

Last session: 2026-06-13 — Phase 4 **slice 4.4** (embedding severity imputation — latent engine
E1) AND the **pluggable rule-pipeline** slice shipped via subagent-driven development. 4.4:
`SeverityBands`, `SeverityImputation` (distance-weighted kNN + leave-one-out MAE/label-accuracy),
repo kNN Cypher, `{impute,eval}-severity` endpoints, integration test (seeds embeddings via
`attachEmbedding`), Inference-tab card. Rule pipeline: in-memory `RuleRegistry` (ordered,
enable/disable, reorder), `run()` refactored to a per-rule fixpoint over the registry
(behaviour-preserving), `runRules()` + `rules`/`rules/{name}/enabled`/`rules/order`/`run-rules`
endpoints, integration test (disabling R2 zeroes exposure), Rules UI card. Bruno `Inference/`
folder added; STATE/ROADMAP updated. Full `clean check` green (99 tests). Both slices merged to
main (fast-forward).
Stopped at: rule-pipeline merge on main. Working tree: untracked `tools/` only.
Resume file: None — next is **Phase 6** (evaluation + ~6-page portfolio). Optional: slice 4.2b
(R3 `SAME_AS` merge + R4 withdrawn retraction → stratified-negation story).
