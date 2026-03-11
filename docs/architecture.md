# Backend Architecture

Today the backend is responsible for:

- starting the Spring Boot application
- building and validating the PostgreSQL datasource
- applying Flyway migrations on startup
- exposing `/health` and `/ready`
- proving the runtime wiring with unit and integration tests

The repository does not yet contain product-focused business features, but it already has the basic structure those features should use.

## Current Runtime Components

### Application Bootstrap

`MageBackendApplication` is the Spring Boot entry point. It starts the application context and enables component scanning under `com.bdmage.mage_backend`.

### Configuration Layer

The configuration package currently contains:

- `DatabaseProperties`
- `DatabaseConfiguration`

Together they do three important jobs:

- bind datasource configuration from environment variables
- validate that required settings exist and use a PostgreSQL JDBC URL
- create the Hikari `DataSource` and fail startup immediately if PostgreSQL is unreachable

This is intentionally stricter than letting the application start with bad infrastructure settings and fail later on the first request.

### API Layer

The API layer currently consists of one controller:

- `HealthController`

Endpoints:

- `GET /health`
- `GET /ready`

`/health` is a liveness check. It answers the narrow question, "Is the process up?"

`/ready` is a readiness check. It answers the more operationally useful question, "Can this instance actually serve traffic right now?"

### Service Layer

The service layer currently consists of:

- `ReadinessService`

This service combines:

- Spring's `ApplicationAvailability`
- a live database connection check through the configured `DataSource`

The controller owns HTTP concerns, while the service owns the decision logic for readiness.

### DTO Layer

The DTO package currently contains:

- `HealthResponse`
- `ReadinessResponse`

These are explicit API contracts. Even for small endpoints, the repository prefers returning named response types rather than anonymous maps or loosely shaped JSON.

### Persistence and Database Layer

- PostgreSQL is the only database
- Flyway owns schema migration
- Spring Data JPA is available on the classpath
- no entities or repositories have been added yet

At the moment, database access exists only for runtime readiness checks. There is no domain persistence layer because there are no domain features yet.

## Folder Structure

```text
src/
|- main/
|  |- java/com/bdmage/mage_backend/
|  |  |- config/
|  |  |- controller/
|  |  |- dto/
|  |  |- service/
|  |  `- MageBackendApplication.java
|  `- resources/
|     |- application.properties
|     `- db/migration/
`- test/
   `- java/com/bdmage/mage_backend/
      |- config/
      |- controller/
      |- service/
      |- support/
      `- MageBackendApplicationTests.java
```

- `config/` is for wiring and infrastructure setup
- `controller/` is for HTTP entry points
- `service/` is for business or application logic
- `dto/` is for public request and response contracts
- `resources/db/migration/` is for schema evolution
- `test/support/` is for reusable testing infrastructure

## Request Flow Example

The `/ready` endpoint shows the current end-to-end request path clearly:

1. a client calls `GET /ready`
2. `HealthController.ready()` handles the request
3. the controller delegates to `ReadinessService.checkReadiness()`
4. the service checks Spring's readiness state
5. the service opens a database connection from the configured `DataSource`
6. the service validates that connection with `connection.isValid(1)`
7. the service returns a small internal status object
8. the controller maps that status into `ReadinessResponse`
9. the controller returns either `200 OK` or `503 Service Unavailable`

That flow is intentionally simple, but it models the same separation of concerns expected in larger feature work.

## Database and Migration Model

Database behavior in this repository follows these rules:

- the backend expects PostgreSQL
- datasource settings come from environment variables
- startup fails fast if the datasource is invalid or unreachable
- Flyway runs from `src/main/resources/db/migration`

As a result, the expected way to change the schema is straightforward:

1. add a new Flyway migration
2. start the application or run integration tests
3. let Flyway apply the new version

## What Is Not Implemented Yet

The codebase does not currently include:

- domain entities
- repository interfaces
- authentication or authorization
- request validation beyond current simple endpoints

## Target Architecture as the Backend Grows

As the repository grows, new features should extend the existing layering rather than bypass it.

Recommended package shape:

```text
com.bdmage.mage_backend
|- config
|- controller
|- service
|- repository
|- model
|- dto
|- exception
|- client
|- job
`- util
```

Responsibilities:

- `controller`: HTTP request handling, status codes, request parsing, response mapping
- `service`: business rules, orchestration, transaction boundaries, cross-entity behavior
- `repository`: persistence logic and query intent
- `model`: JPA entities and domain objects
- `dto`: request and response contracts
- `exception`: custom exceptions and global error mapping
- `client`: wrappers for external services
- `job`: scheduled work, asynchronous tasks, background processing
