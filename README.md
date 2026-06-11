# ArgusGraph

![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0--RC1-6DB33F?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-2.1.0--RC1-6DB33F?logo=spring&logoColor=white)
![Neo4j](https://img.shields.io/badge/Neo4j-5%20Community-018BFF?logo=neo4j&logoColor=white)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-4.3-FF6600?logo=rabbitmq&logoColor=white)
![REST](https://img.shields.io/badge/API-REST%20%2F%20OpenAPI%203-009688)

**Knowledge-graph-based software supply chain analysis.** ArgusGraph ingests
vulnerability and dependency data from public sources (CVE, NVD, OSV, GitHub Advisory
Database, deps.dev) into a Neo4j knowledge graph, so that transitive vulnerability
exposure — the kind naïve "direct dependency" scanners miss — becomes a graph traversal.

Current state: **skeleton + async ingestion** — typed input goes in over REST and comes
out as a graph, and an OSV fetch worker streams ecosystem dumps through RabbitMQ into
the same pipeline. More sources (NVD/GHSA/deps.dev), SBOM upload, the embedding
pipeline, and the logic-based inference engine come next
(see [`.planning/ROADMAP.md`](.planning/ROADMAP.md)).

---

## The graph model

Version-level graph: vulnerabilities attach to concrete package versions, dependencies run
between concrete package versions. Transitive exposure checks need no version-range math at
query time — they are pure path queries.

```
(:Package {purl})─[:HAS_VERSION]→(:PackageVersion {purl, version})
(:PackageVersion)─[:DEPENDS_ON {scope}]→(:PackageVersion)
(:Vulnerability {id, severity, cvssScore, ...})─[:AFFECTS]→(:PackageVersion)
```

Natural keys throughout: canonical [purl](https://github.com/package-url/purl-spec) for
packages (qualifiers/subpath dropped, so all sources merge onto the same node), advisory id
(CVE/GHSA/OSV) for vulnerabilities. All writes are idempotent `MERGE` upserts — sources can
be replayed safely.

## Architecture

```
dev.argusgraph
├── app/      # Bootstrap + cross-cutting: security, error handling, OpenAPI, config
├── shared/   # Tiny shared kernel (common exceptions)
├── graph/    # KG core — domain types, GraphAPI contract, Cypher persistence, read API
├── ingest/   # Input adapters — typed REST + worker pipeline (fetch → RabbitMQ → graph)
└── project/  # SBOM import — CycloneDX parsing, H2-backed project store, vulnerability matching
```

Spring Boot modular monolith (Spring Modulith + jMolecules). The ingest module talks to
the graph module **only** through its published `GraphAPI` contract — enforced at compile
time (Checkstyle ImportControl) and test time (`ModulithTests`).

### Endpoints

| Method & path                       | Purpose                                            |
|-------------------------------------|----------------------------------------------------|
| `POST /ingest/package-versions`     | Upsert a package version (and parent package) by purl |
| `POST /ingest/vulnerabilities`      | Upsert a vulnerability by advisory id              |
| `POST /ingest/dependencies`         | Record `DEPENDS_ON` between two package versions   |
| `POST /ingest/affects`              | Record `AFFECTS` from a vulnerability to a version |
| `POST /ingest/jobs/osv?ecosystem=`  | Fetch an OSV ecosystem dump async via RabbitMQ (202/429) |
| `GET  /graph/package-versions?purl=`| A version + direct dependencies + known vulns      |
| `GET  /graph/stats`                 | Whole-graph counts + severity buckets (dashboard)  |
| `GET  /graph/vulnerabilities`       | Paged vulnerability list with severity/text filters |
| `POST /projects`                    | Import a CycloneDX SBOM as a project (stored in H2) |
| `GET  /projects/{id}`               | Project detail with live vulnerability match       |

The REST API lives under `/api/v1`; the dashboard SPA is served from the root path `/`. Errors are RFC 9457 `problem+json`:
400 validation, 404 unknown node, 409 domain-rule violation (e.g. malformed purl).

---

## Quick start

**Prerequisites:** JDK 25 (Gradle auto-provisions BellSoft Liberica via the Foojay resolver
if missing), Docker.

### Option A — run from your IDE against a throwaway database (zero setup)

Run **`TestArgusGraphApplication`** (under `src/test`). It boots the real app with an
auto-started Neo4j Testcontainer — no `.env`, no `docker compose`.

### Option B — host app + compose Neo4j

```bash
cp .env.example .env
docker compose up -d            # Neo4j + RabbitMQ (UIs on :7474 / :15672)
./gradlew bootRun               # app on http://localhost:8080
curl http://localhost:8080/actuator/health
```

### Option C — fully containerised

```bash
cp .env.example .env
docker compose --profile app up --build    # Neo4j + app
```

Uniqueness constraints are created automatically on boot (`GraphSchemaInitializer`).

### Try it

```bash
curl -X POST http://localhost:8080/api/v1/ingest/package-versions \
  -H 'Content-Type: application/json' \
  -d '{"purl": "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"}'

curl -X POST http://localhost:8080/api/v1/ingest/vulnerabilities \
  -H 'Content-Type: application/json' \
  -d '{"id": "CVE-2021-44228", "severity": "CRITICAL", "cvssScore": 10.0}'

curl -X POST http://localhost:8080/api/v1/ingest/affects \
  -H 'Content-Type: application/json' \
  -d '{"vulnerabilityId": "CVE-2021-44228", "purl": "pkg:maven/org.apache.logging.log4j/log4j-core@2.14.1"}'

curl "http://localhost:8080/api/v1/graph/package-versions?purl=pkg:maven/org.apache.logging.log4j/log4j-core%402.14.1"
```

### Explore

- **Swagger UI** → http://localhost:8080/swagger-ui.html
- **Neo4j browser** → http://localhost:7474 (credentials from `.env`)
- **RabbitMQ management UI** → http://localhost:15672 (credentials from `.env`)
- **Bruno collection** → [`bruno/argusgraph-api`](bruno/argusgraph-api)

---

## Dashboard

A no-build Mithril.js SPA served from the jar at http://localhost:8080/ — graph stats,
vulnerability browsing, purl lookup, manual OSV fetch triggers, and project SBOM import
with live vulnerability matching (Projects tab). Source lives in
`src/main/resources/static/` (no npm, no bundler — edit and refresh).

---

## Tech stack

| Layer         | Technology                                                            |
|---------------|-----------------------------------------------------------------------|
| Framework     | Spring Boot 4.1.0-RC1, Spring Modulith 2.1.0-RC1                      |
| Architecture  | jMolecules (DDD + layered annotations), modular monolith              |
| API           | Spring Web MVC (REST), springdoc OpenAPI 3                            |
| Graph store   | Neo4j 5 Community, Spring Data Neo4j (`Neo4jClient` + explicit Cypher) |
| Relational    | H2 embedded (file-mode, `./data/`) — project/SBOM store               |
| Messaging     | RabbitMQ 4 (AMQP 0-9-1), Spring AMQP — async source-ingestion pipeline |
| Purl parsing  | packageurl-java                                                       |
| Security      | Spring Security — **open by default**, JWT resource-server ready      |
| Testing       | JUnit 5, AssertJ, Testcontainers (Neo4j, RabbitMQ), Spring Modulith test |
| Code quality  | Checkstyle (module-boundary ImportControl, non-blocking), JaCoCo      |
| Build         | Gradle 9.4 (Kotlin DSL), version catalog, dependency locking, JDK 25  |

Spring Boot/Modulith track the **4.1.0 RC** line, so the Spring milestone repository is
enabled in `settings.gradle.kts`. Drop both to a GA release (and remove the repo) when
4.1.0 ships.

---

## Project workspace (`.planning/`)

Developed AI-first with a committed [GSD](.planning/) workspace:

- [`.planning/PROJECT.md`](.planning/PROJECT.md) — what ArgusGraph is and its constraints.
- [`.planning/ROADMAP.md`](.planning/ROADMAP.md) — phases: skeleton → data import → workers → inference.
- [`.planning/codebase/`](.planning/codebase/) — architecture/stack/conventions intel for agents;
  start with [`BOOTSTRAP.md`](.planning/codebase/BOOTSTRAP.md).

---

## Commands

```bash
./gradlew bootRun                       # run the app
./gradlew test                          # all tests (integration test needs Docker)
./gradlew test --tests ModulithTests    # boundary check only — no Docker
./gradlew build                         # full build (checkstyle + tests + jar)
./gradlew dependencies --write-locks    # regenerate the dependency lockfile
```
