# Engineering Standards

## Purpose

This document defines how backend code should be written in this repository. The goal is consistency. A growing backend becomes hard to work on when every feature invents its own rules.

## Engineering Philosophy

Backend code in this repository should try to be:

- simple enough to scan quickly
- explicit enough to debug without guesswork
- modular enough to change in one place without surprising another
- consistent across packages and features
- easy for another developer to pick up and extend

Prefer the design that makes maintenance easier over the design that looks clever in isolation.

## Language and Framework Standards

- Prefer clear names over shortened or abstract names that hide intent.
- Keep methods small enough that their purpose is obvious from the name and body together.
- Avoid deep inheritance hierarchies. Composition is usually the better default.
- Avoid `null` for ordinary control flow. Use explicit contracts.

## Package and Naming Standards

- classes and records use `PascalCase`
- methods and variables use `camelCase`
- packages use lowercase
- controllers end in `Controller`
- services end in `Service`
- repositories end in `Repository`
- configuration classes end in `Configuration`
- request and response DTOs end in `Request` and `Response`
- integration tests end in `IntegrationTests`

## Layering Standards

### Controllers

Controllers should:

- define routes
- convert HTTP input (JSON, path variables, query parameters) into Java objects.
- trigger validation to ensure the request data is valid.
- call services
- translate service results into HTTP responses

### Services

Services should:

- own use-case logic, meaning the actual things the system does, such as creating a user, processing a payment, or submitting an order
- coordinate multiple repositories or external clients when needed. For example, creating an order might involve checking inventory, saving the order, and sending a notification
- define transaction boundaries when data is being written, so related database changes succeed or fail together
- encode business rules and application decisions, such as whether a user is allowed to perform an action or whether a request should be rejected

### Repositories

Repositories should:

- own database reads and writes, meaning they are responsible for fetching data from the database and saving updates back to it
- hide query details behind clear method names, so callers do not need to understand SQL or query mechanics. For example, findUserByEmail() is clearer than exposing a raw query
- stay focused on persistence behavior, meaning repositories should only deal with storing and retrieving data, not application logic

### Configuration Classes

Configuration should:

- create and configure beans, meaning they define the objects Spring should manage and provide to the rest of the application
- bind configuration properties, such as environment variables or application settings, into strongly typed classes
- validate infrastructure assumptions, such as ensuring required configuration values exist or that a database connection can be created

## API Standards

### Route Design

- Prefer resource-oriented routes such as `/users` and `/users/{id}`.
- Avoid verb-heavy route names such as `/getUsers` or `/createUser`.
- Keep route naming consistent across the codebase.
- Add route prefixes only when they express a real boundary, such as versioning or an admin surface.

### Validation

When request payloads are introduced, validate them at the API boundary.

Standard approach:

- DTO classes or records define the input contract
- Jakarta Bean Validation annotations define structural validation rules
- controllers trigger validation with `@Valid`
- invalid input is translated by centralized exception handling into a consistent error response

### Error Handling

As the API surface grows, use `@ControllerAdvice` to centralize error mapping.

Error responses should be:

- machine-readable
- consistent across endpoints
- free of internal stack traces and secret values

Recommended error fields:

- `code`
- `message`
- `details` when relevant
- `path`
- `timestamp`

### Status Code Guidelines

- `200 OK` for successful reads and updates with a response body
- `201 Created` for successful resource creation
- `204 No Content` for successful operations with no response body
- `400 Bad Request` for malformed requests or validation failures
- `401 Unauthorized` when authentication is required and missing or invalid
- `403 Forbidden` when authentication exists but access is not allowed
- `404 Not Found` when a resource does not exist
- `409 Conflict` when the request conflicts with current server state
- `422 Unprocessable Entity` only if the team intentionally distinguishes semantic business-rule failure from basic request validation
- `503 Service Unavailable` for readiness or downstream dependency failure

## Persistence and Database Standards

- All schema changes go through Flyway migrations.
- Migration files belong in `src/main/resources/db/migration`.
- Use `V<version>__<description>.sql`.
- Treat migrations as append-only once shared with others.
- Prefer lowercase snake_case for table, column, index, and constraint names.
- Keep `spring.jpa.hibernate.ddl-auto=validate` for normal development.
- Use repositories to express query intent instead of scattering persistence logic across services.
- If a feature introduces multi-step writes, define clear transaction boundaries in the service layer.

### Entity Standards for Future Domain Models

Coming soon: a set of entity design standards for future domain models, including naming conventions, relationship mapping, and best practices for JPA usage.

## Configuration and Secrets

- Configuration should come from environment variables or checked-in non-secret property files.
- Secrets do not belong in source control.
- The app should fail fast when required configuration is missing.
- If a new feature needs configuration, document it in `docs/getting-started.md` and `docs/operations.md`.

## Logging Standards

Logging is part of the backend contract with developers and operators.

- Log meaningful state changes and failures.
- Do not log secrets, tokens, passwords, or raw sensitive payloads.
- Prefer logs that help reconstruct what happened without re-running the issue in a debugger.
- Use structured, consistent message wording when possible.

## Security Standards

Security is not fully implemented in this repository yet, but the standards should be clear now.

- Authenticate before trusting client identity.
- Enforce authorization close to business logic, not only at the route declaration.
- Validate all client input.
- Never trust ownership claims from the client without server-side checks.
- Hash passwords with established libraries and never invent custom crypto.
- Keep secrets in environment-based configuration or a proper secret manager.

## External Integration Standards

If external APIs or services are added:

- isolate them behind `client` classes or modules
- define clear request and response boundaries
- set timeouts explicitly
- handle retries deliberately, not accidentally
- make idempotency expectations explicit for write operations
- separate transport failures from domain failures in error handling

## Background Job Standards

If any jobs or scheduled tasks are added:

- place them under a dedicated `job` package
- make them idempotent where possible
- avoid hidden scheduling logic inside controllers or repositories
- log job start, success, failure, and retry behavior clearly
- separate job orchestration from the domain logic it invokes

## Testing Standards

### Unit Tests

Use unit tests for:

- service logic
- controller behavior that can be isolated
- configuration validation
- small utility code

### Integration Tests

Use integration tests for:

- Spring Boot application wiring
- HTTP behavior with real controller registration
- Flyway startup behavior
- real PostgreSQL interactions

Use Testcontainers for database-backed integration tests. Do not assume every contributor has a manually provisioned local database.

### Test Naming and Placement

- mirror the production package structure under `src/test/java`
- use `*Tests` for unit-scope tests
- use `*IntegrationTests` for Spring or database integration tests
- give test methods names that explain behavior, not implementation mechanics

### Testing Expectations

- every behavior change should add or update tests
- bug fixes should include a test that would have caught the bug
- schema changes should be exercised against Flyway-managed startup

## Documentation Standards

- Public APIs should be documented through explicit DTOs and clear route naming.
- Developer workflow changes should update `docs/getting-started.md` or `docs/operations.md` as appropriate.
- Architecture changes should update `docs/architecture.md`.
- Team-process changes should update `CONTRIBUTING.md`.

## Git and Review Standards

- Keep branches focused.
- Use commit messages in imperative mood.
- Keep pull requests reviewable.
- Separate refactors from behavior changes when practical.
- Explain migration, API, or configuration impact in the PR description.
- Review for correctness, architecture, maintainability, and missing tests before style nitpicking.
