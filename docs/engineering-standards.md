# Engineering Standards

This document defines the baseline for backend changes in this repository.

## Core Principles

Prefer code that is:
- easy to scan
- explicit about behavior
- predictable across packages
- simple to test
- easy for another developer to extend

Avoid cleverness that makes the next change harder.

## Layering

### Controllers

Controllers should:
- define routes
- validate incoming requests
- delegate to services
- shape HTTP responses

Controllers should not own business rules or persistence logic.

### Services

Services should:
- own use-case logic
- coordinate repositories and external clients
- define transaction boundaries when writes span multiple records
- enforce business and authorization rules

### Repositories

Repositories should:
- encapsulate database access
- expose clear query intent through method names
- stay focused on persistence, not application logic

### Configuration

Configuration classes should:
- bind environment-driven settings
- validate required infrastructure assumptions
- create and wire Spring-managed beans

## Naming and Packaging

- classes and records: `PascalCase`
- methods and variables: `camelCase`
- packages: lowercase
- controllers: `*Controller`
- services: `*Service`
- repositories: `*Repository`
- DTOs: `*Request` and `*Response`
- integration tests: `*IntegrationTests`

Keep the package layout aligned with the current repo structure instead of inventing one-off folders.

## API Standards

### Routes

- prefer resource-oriented routes
- keep naming consistent across domains
- avoid verb-heavy endpoint names unless the action is genuinely not resource-like

### Validation

- validate request DTOs at the API boundary
- use Bean Validation annotations for structural rules
- return consistent error shapes through centralized exception handling

### Status Codes

- `200` for successful reads and updates with a body
- `201` for successful creation
- `204` for successful deletes or bodyless success
- `400` for malformed requests or validation failures
- `401` for missing or invalid authentication
- `403` for authenticated but forbidden actions
- `404` for missing resources
- `409` for state conflicts
- `503` for readiness or dependency failures

## Persistence Standards

- all schema changes go through Flyway
- migration files belong in `src/main/resources/db/migration`
- use `V<version>__<description>.sql`
- treat shared migrations as append-only
- keep Hibernate schema mode at `validate` for standard development
- keep persistence logic in repositories and service transaction boundaries

## Security Standards

- never trust client-supplied identity without server-side verification
- keep authorization checks close to the business rule they protect
- do not log secrets, passwords, tokens, or raw sensitive payloads
- use established hashing libraries for password handling
- keep secrets in environment-driven configuration

## External Integration Standards

When adding external dependencies:
- isolate them behind client interfaces or adapters
- make timeout and retry behavior explicit
- separate transport failures from domain failures
- make write idempotency expectations clear

## Testing Standards

Every behavioral change should add or update tests.

Use:
- unit tests for service logic, configuration, and isolated controller behavior
- integration tests for HTTP behavior, persistence, and full application wiring
- Testcontainers for PostgreSQL-backed integration coverage

Good tests should explain behavior, not implementation mechanics.

## Documentation Standards

Update documentation when you change:
- setup or configuration
- operational behavior
- architecture or package boundaries
- contribution workflow

Relevant files:
- `README.md`
- `docs/getting-started.md`
- `docs/architecture.md`
- `docs/operations.md`
- `CONTRIBUTING.md`

## Review Standards

Keep pull requests focused and easy to review.

A good PR should make clear:
- what changed
- why it changed
- how it was tested
- whether it affects API shape, configuration, or schema

Review for correctness, architecture, risk, and missing tests before style nits.
