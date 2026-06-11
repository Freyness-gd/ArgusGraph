# Roadmap: ArgusGraph

## Overview

From a working skeleton (typed input → Neo4j knowledge graph) through real data import,
to scraper workers and the logic-based inference engine from the project proposal.

## Phases

- [x] **Phase 1: Skeleton** — rename to ArgusGraph, bootstrap Neo4j, working ingest → graph pipeline.
- [x] **Phase 2: Data import** — OSV-schema ingest, CVSS derivation, embeddings, RabbitMQ worker. (Gap: UNWIND bulk path.)
- [~] **Phase 3: Scraper workers** — partial: manual OSV fetch trigger, job registry/status, graph reset. (No continuous polling yet.)
- [~] **Phase 4: Inference engine** — slice 1 (R1 transitive exposure) shipped; R2 range resolution + deps.dev next.
- [~] **Phase 5: SBOM upload + GUI** — Mithril dashboard + project import + transitive exposure card shipped ahead of order.

## Phase Details

### Phase 1: Skeleton ✅
**Goal**: A working program that takes typed input over REST and persists it as a Neo4j graph.
**Depends on**: Template scaffolding.
**Success Criteria**:
  1. Project renamed to ArgusGraph (`dev.argusgraph`), samples removed. ✅
  2. Neo4j bootstrapped (compose + Testcontainers + config), Postgres/JPA gone. ✅
  3. `graph` + `ingest` modules: idempotent upserts for packages/versions/vulnerabilities,
     dependency + affects edges, read endpoint with direct neighbourhood. ✅
  4. Green build: Purl unit tests, Modulith boundary check, Log4Shell end-to-end
     integration test incl. idempotency + error contract. ✅
**Plans**: Done.

### Phase 2: Data import
**Goal**: Real advisory + dependency data from at least one source (start with OSV bulk
data) lands in the graph through the existing ingest path.
**Depends on**: Phase 1.
**Success Criteria**:
  1. A source dump (e.g. OSV ecosystem export) can be imported end-to-end.
  2. Bulk path exists (UNWIND-batched Cypher) — single-row REST calls are not the import path.
  3. Import is idempotent and resumable.
**Plans**: TBD (set during plan-phase)

### Phase 3: Scraper workers
**Goal**: Continuously running workers keep the graph current per source.
**Depends on**: Phase 2.

### Phase 4: Inference engine
**Goal**: Logic component infers transitive exposure (`TRANSITIVELY_VULNERABLE` knowledge)
when data is added.
**Depends on**: Phase 2.

### Phase 5: SBOM upload + GUI
**Goal**: Users upload a project/SBOM and explore its dependency graph and vulnerability
exposure in a minimal UI.
**Depends on**: Phase 4.

## Progress

| Phase | Plans Complete | Status      | Completed  |
|-------|----------------|-------------|------------|
| 1. Skeleton        | done  | Done            | 2026-06-10 |
| 2. Data import     | done  | Done (UNWIND gap)| 2026-06-11 |
| 3. Scraper workers | —     | Partial         | -          |
| 4. Inference engine| 1/N   | Slice 1 (R1) done| 2026-06-12 |
| 5. SBOM + GUI      | —     | Substantially done | 2026-06-11 |
