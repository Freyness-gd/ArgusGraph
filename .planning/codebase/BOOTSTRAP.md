# Dev Bootstrap

How to get this codebase running and how a developer (or AI agent) should orient before
making changes.

## Prerequisites

- **JDK 25** — Gradle auto-provisions BellSoft Liberica via the Foojay resolver if it's not
  installed, so you usually don't need to install it manually.
- **Docker** — for Neo4j + RabbitMQ (compose) and for Testcontainers-based tests.
- That's it. No global Gradle install needed — use the `./gradlew` wrapper.

## Fastest path to a running app

**From the IDE (recommended, zero setup):** run the `TestArgusGraphApplication` main class
under `src/test/java`. It boots the real app against an auto-started Neo4j Testcontainer —
no `.env`, no manual database.

**From the CLI against compose Neo4j:**

```bash
cp .env.example .env
docker compose up -d          # Neo4j + RabbitMQ (the `app` service is behind the `app` profile)
./gradlew bootRun             # app on http://localhost:8080
curl http://localhost:8080/actuator/health    # -> {"status":"UP"}
# Dashboard SPA at http://localhost:8080/
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

- Dashboard UI: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Health: `http://localhost:8080/actuator/health`
- Neo4j browser: `http://localhost:7474` (credentials from `.env`)
- RabbitMQ management UI: `http://localhost:15672` (credentials from `.env`)
- Bruno collection: `bruno/argusgraph-api` (Ingest requests, then **Get Package Version**)

## Common gotchas

- **REST API is under `/api/v1`** (controller prefix applied by `WebConfig`, not a servlet
  context path). UI, actuator, and Swagger are at server root — no prefix. `TestRestTemplate`
  does **not** auto-prefix — spell `/api/v1/...` in tests.
- **Purls in query strings need URL-encoding** (`@` → `%40`).
- **Uniqueness constraints auto-create on boot** (`GraphSchemaInitializer`) — no manual
  schema step; switch to a migration tool (e.g. neo4j-migrations) when schema work grows.
- **Security is open** by default — every endpoint is reachable with no token.
- **Checkstyle never fails the build** here (warn-only) — it checks module boundaries +
  imports. There is no auto-formatter; `.editorconfig` governs whitespace/indentation.
