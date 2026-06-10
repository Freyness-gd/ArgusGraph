# Dev Bootstrap

How to get this codebase running and how a developer (or AI agent) should orient before
making changes.

## Prerequisites

- **JDK 25** — Gradle auto-provisions BellSoft Liberica via the Foojay resolver if it's not
  installed, so you usually don't need to install it manually.
- **Docker** — for Neo4j (compose) and for Testcontainers-based tests.
- That's it. No global Gradle install needed — use the `./gradlew` wrapper.

## Fastest path to a running app

**From the IDE (recommended, zero setup):** run the `TestArgusGraphApplication` main class
under `src/test/java`. It boots the real app against an auto-started Neo4j Testcontainer —
no `.env`, no manual database.

**From the CLI against compose Neo4j:**

```bash
cp .env.example .env
docker compose up -d          # Neo4j only (the `app` service is behind the `app` profile)
./gradlew bootRun             # app on http://localhost:8080  (context path /api/v1)
curl http://localhost:8080/api/v1/actuator/health    # -> {"status":"UP"}
```

**Fully containerised:**

```bash
cp .env.example .env
docker compose --profile app up --build
```

## Verify the build

```bash
./gradlew build                         # compile + Checkstyle (non-blocking) + tests + jar
./gradlew test --tests ModulithTests    # boundary check only — no Docker required
```

`ModulithTests` and `PurlTest` run without Docker. The integration test needs a running
Docker daemon.

## First things to read (for an agent picking this up)

1. `.planning/codebase/ARCHITECTURE.md` — the module model, graph model, and contract rule.
2. `.planning/codebase/CONVENTIONS.md` — how a vertical slice is structured and named.
3. `graph/` then `ingest/` — `ingest` shows the cross-module call through `GraphAPI`.
4. `.planning/codebase/TESTING.md` — how tests are wired (Testcontainers, Modulith verify).

## Endpoints once running

- Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api/v1/v3/api-docs`
- Health: `http://localhost:8080/api/v1/actuator/health`
- Neo4j browser: `http://localhost:7474` (credentials from `.env`)
- Bruno collection: `bruno/argusgraph-api` (Ingest requests, then **Get Package Version**)

## Common gotchas

- **Everything is under `/api/v1`** (server context path). Don't forget it in curl/Bruno
  URLs — but `TestRestTemplate` in tests already includes it (use `/ingest/...`).
- **Purls in query strings need URL-encoding** (`@` → `%40`).
- **Uniqueness constraints auto-create on boot** (`GraphSchemaInitializer`) — no manual
  schema step; switch to a migration tool (e.g. neo4j-migrations) when schema work grows.
- **Security is open** by default — every endpoint is reachable with no token.
- **Checkstyle never fails the build** here (warn-only) — it checks module boundaries +
  imports. There is no auto-formatter; `.editorconfig` governs whitespace/indentation.
