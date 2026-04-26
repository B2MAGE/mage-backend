# Architecture

This document describes the backend as it exists today.

## Responsibilities

The service currently owns five main areas:

- health and readiness
- authentication and account linking
- user profile lookup
- tag management
- scene storage and tagging

It also owns the infrastructure needed to support them:

- PostgreSQL connectivity
- Flyway migrations
- bearer-token validation for protected routes
- Google ID token verification

## High-Level Structure

```text
src/main/java/com/bdmage/mage_backend/
|- client/
|- config/
|- controller/
|- dto/
|- exception/
|- model/
|- repository/
`- service/
```

Each package has a narrow role:

- `controller`: HTTP entry points and response shaping
- `service`: application rules and orchestration
- `repository`: persistence access and query intent
- `model`: JPA entities and enums
- `dto`: request and response contracts
- `config`: Spring wiring, environment binding, and authentication plumbing
- `client`: external integration boundaries
- `exception`: custom exceptions and centralized API error mapping

## Request Flow

The normal request path is:

1. a controller accepts the HTTP request
2. request DTO validation runs at the API boundary
3. the controller delegates to a service
4. the service applies business rules and coordinates repositories or clients
5. repositories read or write PostgreSQL
6. the controller returns a DTO-backed response

Protected routes add one more step first:

1. the authentication interceptor validates the bearer token
2. the authenticated user is attached to request context
3. controller and service logic run with a trusted server-side identity

## Controllers

The current API is split across five controllers:

- `HealthController`
- `AuthController`
- `UserController`
- `TagController`
- `SceneController`

## Service Layer

The service layer owns the real behavior:

- `ReadinessService`: readiness state and database reachability
- `RegistrationService`: local account creation
- `LoginService`: local credential verification
- `GoogleAuthenticationService`: Google token verification and account provisioning
- `AccountLinkingService`: explicit local/Google linking flows
- `AuthenticationTokenService`: token creation and validation
- `UserProfileService`: current-user lookups
- `TagService`: tag normalization and duplicate checks
- `SceneService`: scene creation, lookup, deletion, filtering, tagging, and presigned thumbnail upload orchestration
- `ObjectStorageThumbnailStorageService`: S3-compatible presign, object verification, public URL generation, and replacement cleanup for AWS S3 or MinIO
- `PasswordHashingService`: password hashing and verification

The controller should not contain those decisions.

## Authentication Model

The backend supports three account states:

- `LOCAL`
- `GOOGLE`
- `LOCAL_GOOGLE`

Important rules:

- local and Google accounts are not auto-linked just because emails match
- linking requires proof of ownership for both sides
- successful local login and Google auth issue bearer tokens
- protected routes trust the authenticated user only after token validation in the interceptor

## Persistence Model

The current data model is centered around these tables:

- `users`
- `auth_tokens`
- `tags`
- `scenes`
- `scene_tags`

Those are backed by:

- JPA entities in `model/`
- Spring Data repositories in `repository/`
- Flyway migrations in `src/main/resources/db/migration`

Schema changes are migration-driven. The repo expects PostgreSQL and keeps Hibernate schema mode at `validate` for normal development.

Within `users`, the backend now stores `first_name`, `last_name`, and `display_name`. `display_name` remains the public attribution field, while auth and profile flows expose all three values.

Within `scenes`, the backend stores the scene name, optional plain-text description, JSON scene data, optional thumbnail reference, owner, and creation timestamp.

## External Boundary

Google sign-in is isolated behind the `GoogleTokenVerifier` interface. The production implementation is `GoogleApiClientTokenVerifier`.

That keeps token verification logic out of controllers and makes the auth service easier to test.

## Error Handling

`ApiExceptionHandler` centralizes HTTP error mapping for:

- validation failures
- authentication failures
- account conflicts and link-required cases
- duplicate tags and duplicate scene-tag links
- invalid thumbnail uploads, thumbnail storage availability, and owner-only thumbnail enforcement
- missing scenes or tags
- forbidden scene deletion

## Testing Shape

The test suite mirrors the production layout:

- controller tests
- service tests
- repository integration tests
- configuration tests
- migration integration tests

Testcontainers provides PostgreSQL for integration coverage, which means the repo tests against real database behavior instead of mocks alone.
