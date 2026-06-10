# Structure

```
argusgraph/
├── build.gradle.kts · settings.gradle.kts · gradle.properties
├── gradle/libs.versions.toml · gradle/wrapper/ · gradlew(.bat)
├── config/checkstyle/            # checkstyle.xml, import-control.xml, suppressions
├── lombok.config · .editorconfig · .gitattributes · .gitignore · .dockerignore
├── Dockerfile · compose.yaml · .env.example          # compose runs Neo4j (+ app profile)
├── bruno/argusgraph-api/         # Bruno API collection (Health, Ingest, Graph + Local env)
├── .planning/                    # GSD workspace (committed) — config, ROADMAP, STATE, PROJECT, codebase intel
└── src/
    ├── main/java/dev/argusgraph/
    │   ├── ArgusGraphApplication.java        # @Modulith entry point
    │   ├── app/                              # cross-cutting
    │   │   ├── api/OpenApiConfig.java
    │   │   ├── api/exception/GlobalExceptionHandler.java
    │   │   └── infrastructure/{SecurityConfig, AppProperties}.java
    │   ├── shared/exception/{ResourceNotFoundException, BusinessRuleException}.java
    │   ├── graph/                            # KG core module
    │   │   ├── Purl.java · PackageVersion.java · Vulnerability.java   # domain (root pkg)
    │   │   ├── GraphAPI.java · package-info.java                      # published contract
    │   │   ├── api/{GraphController, PackageVersionResponse}.java     # read side
    │   │   ├── application/{GraphService, GraphRepository, PackageVersionDetails}.java
    │   │   └── infrastructure/persistence/{Neo4jGraphRepository, GraphSchemaInitializer}.java
    │   └── ingest/                           # input-adapter module (calls GraphAPI)
    │       ├── package-info.java
    │       ├── api/IngestController.java
    │       ├── api/{IngestPackageVersionRequest, IngestVulnerabilityRequest,
    │       │       IngestDependencyRequest, IngestAffectsRequest}.java
    │       ├── api/{IngestPackageVersionResponse, IngestVulnerabilityResponse}.java
    │       └── application/IngestService.java
    ├── main/resources/
    │   └── application.yaml · application-dev.yaml    # spring.neo4j.* env-driven
    └── test/java/dev/argusgraph/
        ├── ModulithTests.java                # boundary verification (no Docker)
        ├── TestcontainersConfiguration.java  # Neo4j @ServiceConnection
        ├── TestArgusGraphApplication.java    # run app against a throwaway container
        ├── IngestGraphIntegrationTest.java   # Log4Shell end-to-end flow + idempotency + errors
        └── graph/PurlTest.java               # purl canonicalisation rules
```

## Where things go

| You want to…                          | Edit…                                                        |
|---------------------------------------|--------------------------------------------------------------|
| Add an ingest input shape             | `ingest/api/` (DTO + controller method) → `IngestService`    |
| Add a graph write/read                | `GraphAPI` (if cross-module) → `GraphService` → port + Cypher adapter |
| Change a domain rule                  | `graph/Purl`, `graph/PackageVersion`, `graph/Vulnerability`  |
| Change Cypher / node properties       | `graph/infrastructure/persistence/Neo4jGraphRepository`      |
| Add a constraint/index                | `graph/infrastructure/persistence/GraphSchemaInitializer`    |
| Let module B call module A            | add a method to A's `*API`; `allow` it in `import-control.xml` |
| Map a new exception to HTTP           | add an `@ExceptionHandler` in `GlobalExceptionHandler`       |
| Add config keys                       | `AppProperties` + `application.yaml`                         |
