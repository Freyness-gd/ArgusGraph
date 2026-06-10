# Project: ArgusGraph

> Knowledge-graph-based software supply chain analysis (TU Wien KG course project,
> proposal: "Knowledge Graph-based Software Supply Chain Analysis", Gabriel-Valentin Dinu).

## What this is

ArgusGraph ingests vulnerability and dependency data from public sources (CVE, NVD, OSV,
GitHub Advisory Database, deps.dev) into a Neo4j knowledge graph. Users load a project
(git repo / SBOM) and analyse its dependency structure as a graph, including transitive
vulnerability exposure that naïve direct-dependency scanners miss. A logic-based inference
component derives new knowledge (transitively compromised components) as data is added.

## Core value

Transitive vulnerability detection as a graph problem: once dependencies and advisories
live in one version-level knowledge graph, "is my project exposed?" is a path query plus
inference rules — not per-tool heuristics.

## Constraints & decisions

- **Architecture**: Spring Boot modular monolith, DDD bounded contexts, Spring Modulith +
  jMolecules. Cross-module access only via published `*API` contracts. Modules: `graph`
  (KG core), `ingest` (input adapters), `app`, `shared`.
- **Persistence**: Neo4j only (Postgres/JPA/Flyway removed). Graph access via
  `Neo4jClient` + explicit `MERGE` Cypher behind a repository port — no SDN `@Node`
  mapping (avoids deep-fetch on the cyclic dependency graph; evolves into `UNWIND` bulk
  ingest for the scraper workers).
- **Graph model (model A — version-level)**: `(:Package {purl})-[:HAS_VERSION]->`
  `(:PackageVersion {purl})`; `(:PackageVersion)-[:DEPENDS_ON {scope}]->(:PackageVersion)`;
  `(:Vulnerability {id})-[:AFFECTS]->(:PackageVersion)`. Natural keys: canonical purl
  (qualifiers/subpath dropped) and advisory id. All writes idempotent upserts.
- **Auth**: open by default; flip `SecurityConfig` to a JWT resource server when needed.
- **Embeddings**: placeholder only (`embedding` property on nodes) until the embedding
  pipeline phase.
- Design specs live in local `docs/` (gitignored — specs are never committed).

## Out of scope (current milestone)

- The logic-based inference engine (transitive exposure derivation) — later phase.
- Scraper/worker deployment for the data sources — later phase; for now data enters
  through the typed ingest REST API.
- SBOM upload + GUI — later phase.
- Embedding generation — nodes only carry a placeholder property.

## Reference

- Codebase intel: [`.planning/codebase/`](codebase/) — architecture, stack, conventions, testing.
- Dev bootstrap: [`.planning/codebase/BOOTSTRAP.md`](codebase/BOOTSTRAP.md).
