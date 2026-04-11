# Backend Architecture

Today the backend is responsible for:

- starting the Spring Boot application
- building and validating the PostgreSQL datasource
- applying Flyway migrations on startup
- exposing `/health`, `/ready`, `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/google`, `POST /api/auth/link/google`, `POST /api/auth/link/local`, `GET /api/users/me`, `POST /api/tags`, `POST /api/presets`, `GET /api/presets`, `POST /api/presets/{id}/tags`, `GET /api/presets/{id}`, `DELETE /api/presets/{id}`, and `GET /api/users/{id}/presets`
- registering local email-and-password accounts through the shared `users` table
- authenticating local email-and-password accounts through the shared `users` table
- verifying Google ID tokens server-side against configured Google OAuth client IDs
- creating or reusing Google-backed user records through the shared `users` table
- explicitly linking local and Google auth providers after ownership checks
- issuing bearer tokens after successful local login and Google auth
- validating bearer tokens in middleware for protected endpoints
- returning the authenticated user's profile from the shared `users` table
- hashing local-account passwords through a shared password hashing service
- exposing shared tag persistence through the `tags` table
- exposing shared preset persistence through the `presets` table
- exposing shared preset/tag persistence through the `preset_tags` table
- proving the runtime wiring with unit and integration tests

The repository does not yet contain product-focused business features, but it already has the basic structure those features should use.

## Current Runtime Components

### Application Bootstrap

`MageBackendApplication` is the Spring Boot entry point. It starts the application context and enables component scanning under `com.bdmage.mage_backend`.

### Configuration Layer

The configuration package currently contains:

- `DatabaseProperties`
- `DatabaseConfiguration`
- `GoogleAuthProperties`
- `GoogleAuthConfiguration`
- `PasswordHashingConfiguration`
- `AuthenticationConfiguration`

Together they do six important jobs:

- bind datasource configuration from environment variables
- validate that required settings exist and use a PostgreSQL JDBC URL
- create the Hikari `DataSource` and fail startup immediately if PostgreSQL is unreachable
- bind and validate the allowed Google OAuth client IDs used for server-side token verification
- expose the shared BCrypt-backed `PasswordEncoder` used by authentication services
- register the bearer-token authentication middleware used by protected endpoints

This is intentionally stricter than letting the application start with bad infrastructure settings and fail later on the first request.

### API Layer

The API layer currently consists of five controllers:

- `HealthController`
- `AuthController`
- `TagController`
- `UserController`
- `PresetController`

Endpoints:

- `GET /health`
- `GET /ready`
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/google`
- `POST /api/auth/link/google`
- `POST /api/auth/link/local`
- `GET /api/users/me`
- `POST /api/tags`
- `POST /api/presets`
- `GET /api/presets`
- `POST /api/presets/{id}/tags`
- `GET /api/presets/{id}`
- `DELETE /api/presets/{id}`
- `GET /api/users/{id}/presets`

`/health` is a liveness check. It answers the narrow question, "Is the process up?"

`/ready` is a readiness check. It answers the more operationally useful question, "Can this instance actually serve traffic right now?"

`POST /api/auth/google` accepts a Google ID token, delegates token verification to the service layer, and returns either a created or reused Google-backed user record plus a bearer access token.

`POST /api/auth/register` accepts email, password, and display name, delegates registration rules to the service layer, and returns a created local account without exposing password material.

`POST /api/auth/login` accepts email and password, delegates credential verification to the service layer, and returns the authenticated local account plus a bearer access token without exposing password material.

`POST /api/auth/link/google` accepts a local email/password pair plus a Google ID token and only links the provider after both ownership checks succeed.

`POST /api/auth/link/local` accepts a Google ID token plus a new password and only links local auth after the Google-backed account context is verified.

`GET /api/users/me` runs behind authentication middleware. The middleware validates the bearer token, places the authenticated user in request context, and the controller delegates profile lookup to the service layer without exposing password hashes or Google subject identifiers.

`POST /api/tags` accepts a tag name, delegates normalization and duplicate-tag checks to the service layer, and stores new tags through the shared `tags` table.

`POST /api/presets` runs behind authentication middleware. The middleware validates the bearer token, places the authenticated user in request context, and the controller delegates preset persistence to the service layer so future preset features share one creation path.

`GET /api/presets` runs behind authentication middleware. The middleware validates the bearer token before the controller delegates preset lookup to the service layer. Without a `tag` query parameter it returns all persisted presets, and with `?tag=<name>` it returns only presets linked to that normalized tag name.

`POST /api/presets/{id}/tags` runs behind authentication middleware. The middleware validates the bearer token, places the authenticated user in request context, and the controller delegates preset/tag association rules to the service layer so tagging and discovery features share one persistence path.

`GET /api/presets/{id}` is public. The controller delegates preset lookup to the service layer and returns the preset metadata, scene data, thumbnail reference, and creation timestamp when the preset exists.

`DELETE /api/presets/{id}` runs behind authentication middleware. The middleware validates the bearer token, places the authenticated user in request context, and the controller delegates owner-only deletion rules to the service layer. The service rejects non-owner deletes with `403 Forbidden` and removes the preset through the shared persistence path, which also clears dependent preset/tag links through database cascading.

`GET /api/users/{id}/presets` runs behind authentication middleware. The middleware validates the bearer token, places the authenticated user in request context, and the controller delegates preset lookup for the requested user id to the service layer.

### Service Layer

The service layer currently consists of:

- `ReadinessService`
- `PasswordHashingService`
- `RegistrationService`
- `LoginService`
- `GoogleAuthenticationService`
- `AccountLinkingService`
- `AuthenticationTokenService`
- `TagService`
- `UserProfileService`
- `PresetService`

These services combine:

- Spring's `ApplicationAvailability`
- a live database connection check through the configured `DataSource`
- BCrypt-backed password hashing for local accounts
- provider-aware user lookups plus local-account creation rules
- provider-aware user lookups plus local-account credential verification rules for any account that supports local auth
- a Google token verifier client
- provider-aware user lookups plus first-login account creation rules
- explicit provider-linking rules that require either local credentials or Google ownership before the second provider is attached
- bearer-token generation plus secure token persistence
- normalized tag creation plus duplicate-tag checks
- request-time bearer-token validation for protected routes
- authenticated-user profile lookup by the middleware-authenticated user identity
- authenticated-user preset creation plus preset listing, preset filtering by tag, preset/tag association, preset retrieval, owner-only preset deletion, and requested-user preset lookup through the shared `presets` and `preset_tags` tables

The controller owns HTTP concerns, while the service owns the decision logic for readiness.

The registration service owns local-account creation rules, including duplicate-account checks and password hashing before persistence.

The login service owns local credential verification rules and ensures only accounts with a stored local password can authenticate through the password flow.

The Google auth service owns the backend rules for token validation, account conflict detection, and Google-backed user creation or reuse.

The account linking service owns the explicit linking rules for local-to-Google and Google-to-local flows. It prevents auto-linking based only on matching email addresses and requires an ownership proof for every supported link path.

The tag service owns normalized tag creation rules and duplicate detection before tag persistence.

The preset service owns authenticated preset creation rules, preset list and preset/tag filter lookups, preset/tag association rules, owner-only preset deletion rules, ensures new presets are linked to the authenticated user identity before persistence, centralizes preset lookup by id for retrieval and deletion endpoints, and supports requested-user preset list lookups.

### DTO Layer

The DTO package currently contains:

- `HealthResponse`
- `ReadinessResponse`
- `RegistrationRequest`
- `RegistrationResponse`
- `LoginRequest`
- `LoginResponse`
- `GoogleAuthenticationRequest`
- `GoogleAuthenticationResponse`
- `GoogleAccountLinkRequest`
- `LocalAccountLinkRequest`
- `AccountLinkResponse`
- `CreateTagRequest`
- `TagResponse`
- `UserProfileResponse`
- `CreatePresetRequest`
- `AttachTagToPresetRequest`
- `PresetResponse`
- `PresetTagResponse`
- `ApiErrorResponse`

These are explicit API contracts. Even for small endpoints, the repository prefers returning named response types rather than anonymous maps or loosely shaped JSON.

### Client and Exception Layers

- `GoogleTokenVerifier` is the external-integration boundary used by the auth service
- `GoogleApiClientTokenVerifier` is the production adapter that uses the Google API Client library
- `ApiExceptionHandler` centralizes HTTP error responses for validation failures, duplicate-email registration attempts, duplicate-tag creation attempts, duplicate preset/tag attachment attempts, invalid local credentials, invalid authentication tokens, invalid Google tokens, link-required collisions, account conflicts, missing presets, forbidden preset deletions, and missing tags

### Persistence and Database Layer

- PostgreSQL is the only database
- Flyway owns schema migration
- Spring Data JPA is available on the classpath
- `User` maps shared local, Google-backed, and linked multi-provider account data to the `users` table
- one `users` row may hold a local password hash, a Google subject, or both
- `UserRepository` supports shared email lookups plus Google subject lookups
- `AuthenticationToken` maps issued bearer tokens to the `auth_tokens` table
- `AuthenticationTokenRepository` supports bearer-token hash lookups
- `Tag` maps normalized tag names to the `tags` table
- `TagRepository` provides shared access to persisted tags used by tagging and discovery features
- `Preset` maps preset records, owner references, and JSON scene payloads to the `presets` table
- `PresetRepository` provides shared access to persisted presets, including optional tag-filter lookups for preset discovery endpoints
- `PresetTag` maps preset/tag associations to the `preset_tags` table
- `PresetTagRepository` provides shared access to preset/tag links for tagging and discovery features

At the moment, the persistence layer supports shared account, tag, preset, and preset/tag storage. More domain entities and repositories should follow the same package and layering conventions.

## Folder Structure

```text
src/
|- main/
|  |- java/com/bdmage/mage_backend/
|  |  |- config/
|  |  |- client/
|  |  |- controller/
|  |  |- dto/
|  |  |- exception/
|  |  |- model/
|  |  |- repository/
|  |  |- service/
|  |  `- MageBackendApplication.java
|  `- resources/
|     |- application.properties
|     `- db/migration/
`- test/
   `- java/com/bdmage/mage_backend/
      |- config/
      |- controller/
      |- repository/
      |- service/
      |- support/
      `- MageBackendApplicationTests.java
```

- `config/` is for wiring and infrastructure setup
- `client/` is for external service verification or transport adapters
- `controller/` is for HTTP entry points
- `service/` is for business or application logic
- `dto/` is for public request and response contracts
- `exception/` is for centralized API error mapping and custom exceptions
- `model/` is for JPA entities and domain objects
- `repository/` is for persistence interfaces and query intent
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

- a general-purpose authorization framework beyond the owner-only preset deletion rule
- logout or token revocation
- token expiration

Successful `POST /api/auth/login` and `POST /api/auth/google` requests issue bearer access tokens. Bearer-token authentication currently protects `GET /api/users/me`, `POST /api/presets`, `GET /api/presets`, `POST /api/presets/{id}/tags`, `DELETE /api/presets/{id}`, and `GET /api/users/{id}/presets`, while `GET /api/presets/{id}` remains public.

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


