# Operations

## Purpose

This document focuses on what a developer or operator needs to know to start the service, verify it, inspect failures, reset local state, and understand the repository's current operational behavior.

## Current Operational Model

The local stack currently contains two services:

- `backend`: the Spring Boot application built from this repository
- `postgres`: a PostgreSQL 16 container used for local development

Operationally important behavior:

- PostgreSQL exposes a health check through `pg_isready`
- the backend waits for PostgreSQL to become healthy in Docker Compose
- the backend validates datasource settings at startup
- the backend opens a real database connection during startup and fails if the connection cannot be made
- Flyway applies migrations automatically on startup
- local passwords are hashed and verified with BCrypt through the shared password hashing service
- Google ID tokens are verified server-side against `MAGE_AUTH_GOOGLE_CLIENT_IDS`
- successful `POST /auth/login` and `POST /auth/google` requests issue bearer access tokens
- local and Google auth providers can be linked explicitly, but never auto-linked only because emails match
- authentication middleware validates bearer tokens for protected `/users/**` endpoints and protected preset routes, while `GET /presets/{id}` remains public

## Local Startup Runbook

```bash
docker compose up --build
```

Use this when:

- validating Docker-based local behavior
- testing migration behavior against a clean containerized stack

Before using `POST /auth/google`, replace the placeholder value in `.env` for `MAGE_AUTH_GOOGLE_CLIENT_IDS` with the Google OAuth client ID used by the frontend.

## Health Checks and Auth Endpoints

The backend currently exposes fifteen operational endpoints:

- `GET /health`
- `GET /ready`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/google`
- `POST /auth/link/google`
- `POST /auth/link/local`
- `GET /users/me`
- `POST /tags`
- `POST /presets`
- `GET /presets`
- `POST /presets/{id}/tags`
- `GET /presets/{id}`
- `DELETE /presets/{id}`
- `GET /users/{id}/presets`

### `/health`

Purpose:

- liveness check

Meaning:

- the application process is up and can respond to a minimal HTTP request

Expected response:

```json
{ "status": "UP" }
```

### `/ready`

Purpose:

- readiness check

Meaning:

- the application is accepting traffic
- PostgreSQL is reachable through the configured datasource

Expected ready response:

```json
{ "status": "UP", "database": "UP" }
```

Expected not-ready behavior:

- HTTP `503 Service Unavailable`
- response body shows the application and database status

### `POST /auth/register`

Purpose:

- create a local email-and-password account

Request:

```json
{
  "email": "user@example.com",
  "password": "example-password",
  "displayName": "Example User"
}
```

Success behavior:

- HTTP `201 Created` for a newly created local account
- response includes the new user identity fields and auth provider
- response never includes the raw password or stored password hash

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `409 Conflict` with `EMAIL_ALREADY_REGISTERED` when local authentication is already configured for that email
- HTTP `409 Conflict` with `ACCOUNT_LINK_REQUIRED` when the email belongs to a Google-backed account and explicit linking is required

### `POST /auth/login`

Purpose:

- authenticate an existing local email-and-password account

Request:

```json
{
  "email": "user@example.com",
  "password": "example-password"
}
```

Success behavior:

- HTTP `200 OK` for a valid local account credential pair
- response includes the authenticated user identity fields, auth provider, and an `accessToken`
- response never includes the raw password or stored password hash

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `401 Unauthorized` when the credentials do not match a local account

### `POST /auth/google`

Purpose:

- authenticate a frontend-supplied Google ID token
- create a Google-backed user on first successful authentication
- reuse that same Google-backed user on later authentications

Request:

```json
{ "idToken": "<google-id-token>" }
```

Success behavior:

- HTTP `201 Created` when the backend creates a new Google-backed user
- HTTP `200 OK` when the backend reuses an existing Google-backed user
- both success responses include an `accessToken`

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or blank `idToken`
- HTTP `401 Unauthorized` for invalid, expired, or unverified Google identities
- HTTP `409 Conflict` with `ACCOUNT_LINK_REQUIRED` when a local-only account already exists for the verified email
- HTTP `409 Conflict` with `ACCOUNT_CONFLICT` when a different Google identity already owns that email in the backend

### `POST /auth/link/google`

Purpose:

- explicitly link Google authentication to an existing local account
- require both local-credential ownership and Google-token ownership before the link is persisted

Request:

```json
{
  "email": "user@example.com",
  "password": "example-password",
  "idToken": "<google-id-token>"
}
```

Success behavior:

- HTTP `200 OK` when the Google provider is linked to the existing account
- response includes the user identity fields, `LOCAL_GOOGLE` auth provider state, and a `linked` flag

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `401 Unauthorized` with `INVALID_LOCAL_CREDENTIALS` when the supplied local email/password pair is invalid
- HTTP `409 Conflict` with `ACCOUNT_CONFLICT` when the Google token email does not match the local email or the Google identity already belongs to another account

### `POST /auth/link/local`

Purpose:

- explicitly add local email-and-password authentication to an existing Google-backed account
- require Google-token ownership before a password hash is attached to the user record

Request:

```json
{
  "idToken": "<google-id-token>",
  "password": "example-password"
}
```

Success behavior:

- HTTP `200 OK` when local authentication is linked to the Google-backed account
- response includes the user identity fields, `LOCAL_GOOGLE` auth provider state, and a `linked` flag

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `401 Unauthorized` for invalid, expired, or unverified Google identities
- HTTP `409 Conflict` with `ACCOUNT_LINK_REQUIRED` when no Google-backed account exists yet for that Google identity
- HTTP `409 Conflict` with `ACCOUNT_CONFLICT` when the link request collides with an incompatible existing account state

### `GET /users/me`

Purpose:

- return the profile of the authenticated user

Request notes:

- requires an `Authorization: Bearer <accessToken>` header using a token issued by `POST /auth/login` or `POST /auth/google`

Success behavior:

- HTTP `200 OK` for a valid authenticated bearer token
- response includes the authenticated user's identity fields, auth provider, and creation timestamp
- response never includes the raw password, stored password hash, or Google subject

Failure behavior:

- HTTP `401 Unauthorized` when the request is missing a bearer token, uses an invalid token, or the token points to a user record that no longer exists

### `POST /tags`

Purpose:

- create a new tag

Request:

```json
{
  "name": "ambient"
}
```

Success behavior:

- HTTP `201 Created` for a valid tag creation request
- response includes the persisted tag id and normalized tag name

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `409 Conflict` when the supplied tag name already exists after normalization

### `POST /presets`

Purpose:

- create a new preset owned by the authenticated user

Request notes:

- requires an `Authorization: Bearer <accessToken>` header using a token issued by `POST /auth/login` or `POST /auth/google`

Request:

```json
{
  "name": "Aurora Drift",
  "sceneData": {
    "visualizer": {
      "shader": "nebula"
    }
  },
  "thumbnailRef": "thumbnails/preset-1.png"
}
```

Success behavior:

- HTTP `201 Created` for a valid authenticated request
- response includes the created preset id, owner user id, preset metadata, scene data, and creation timestamp

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `401 Unauthorized` when the request is missing a bearer token, uses an invalid token, or the token points to a user record that no longer exists

### `GET /presets`

Purpose:

- return persisted presets
- optionally filter presets by tag using `?tag=<name>`

Request notes:

- requires an `Authorization: Bearer <accessToken>` header using a token issued by `POST /auth/login` or `POST /auth/google`
- when `tag` is omitted, the endpoint returns all persisted presets
- when `tag` is provided, the backend trims and normalizes it before looking up linked presets

Success behavior:

- HTTP `200 OK` for a valid authenticated request
- response includes an array of preset records
- `GET /presets?tag=ambient` returns only presets linked to that tag
- returns an empty array when no presets match the supplied tag

Failure behavior:

- HTTP `401 Unauthorized` when the request is missing a bearer token, uses an invalid token, or the token points to a user record that no longer exists

### `POST /presets/{id}/tags`

Purpose:

- attach an existing tag to an existing preset

Request notes:

- requires an `Authorization: Bearer <accessToken>` header using a token issued by `POST /auth/login` or `POST /auth/google`

Request:

```json
{
  "tagId": 15
}
```

Success behavior:

- HTTP `201 Created` for a valid authenticated request
- response includes the linked preset id and tag id

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or request validation failures
- HTTP `401 Unauthorized` when the request is missing a bearer token, uses an invalid token, or the token points to a user record that no longer exists
- HTTP `404 Not Found` when either the preset or tag does not exist
- HTTP `409 Conflict` when the preset already has that tag attached

### `GET /presets/{id}`

Purpose:

- return a persisted preset by id

Request notes:

- does not require authentication
- ignores missing bearer tokens and returns preset data when the preset exists

Success behavior:

- HTTP `200 OK` when the preset exists
- response includes the preset id, owner user id, preset metadata, scene data, thumbnail reference, and creation timestamp

Failure behavior:

- HTTP `404 Not Found` when no preset exists for the supplied id

### `DELETE /presets/{id}`

Purpose:

- delete a persisted preset owned by the authenticated user

Request notes:

- requires an `Authorization: Bearer <accessToken>` header using a token issued by `POST /auth/login` or `POST /auth/google`

Success behavior:

- HTTP `204 No Content` for a valid authenticated request when the preset exists and the authenticated user owns it
- deleting a preset also removes dependent preset/tag links through cascading database constraints

Failure behavior:

- HTTP `401 Unauthorized` when the request is missing a bearer token, uses an invalid token, or the token points to a user record that no longer exists
- HTTP `403 Forbidden` when the request is authenticated successfully but the preset belongs to a different user
- HTTP `404 Not Found` when no preset exists for the supplied id

### `GET /users/{id}/presets`

Purpose:

- return presets owned by the requested user

Request notes:

- requires an `Authorization: Bearer <accessToken>` header using a token issued by `POST /auth/login` or `POST /auth/google`

Success behavior:

- HTTP `200 OK` for a valid authenticated request
- response includes an array of preset records for the requested user id
- returns an empty array when the requested user has no presets

Failure behavior:

- HTTP `401 Unauthorized` when the request is missing a bearer token, uses an invalid token, or the token points to a user record that no longer exists

## Operational Verification Checklist

After startup, verify these items in order:

1. `docker compose ps` shows healthy PostgreSQL if you are using Docker Compose
2. backend logs show Hikari datasource startup
3. backend logs show Flyway applying or validating migrations
4. `curl http://localhost:8080/health` returns `200`
5. `curl http://localhost:8080/ready` returns `200`
6. `POST /auth/register` succeeds for a new local email address
7. `POST /auth/login` succeeds for that local account and returns an `accessToken`
8. `GET /users/me` succeeds when called with `Authorization: Bearer <accessToken>`
9. `POST /auth/google` succeeds with a valid Google ID token issued for a configured client ID and returns an `accessToken`
10. `POST /auth/link/google` succeeds when both the local credentials and Google token prove ownership of the same email
11. `POST /auth/link/local` succeeds for an existing Google-backed account with a valid Google ID token
12. `POST /tags` succeeds with a valid tag payload and returns the normalized tag record
13. `POST /presets` succeeds when called with `Authorization: Bearer <accessToken>` and a valid preset payload
14. `GET /presets` succeeds when called with `Authorization: Bearer <accessToken>` and returns either all presets, only presets matching `?tag=<name>`, or an empty array when no presets match
15. `POST /presets/{id}/tags` succeeds when called with `Authorization: Bearer <accessToken>`, an existing preset id, and an existing tag id
16. `GET /presets/{id}` succeeds for public requests and returns the preset when the id exists
17. `DELETE /presets/{id}` succeeds when called with `Authorization: Bearer <accessToken>` by the preset owner and returns `204 No Content`
18. `GET /users/{id}/presets` succeeds when called with `Authorization: Bearer <accessToken>` and returns either preset records or an empty array

If step 5 fails with `503`, the app is running but not ready to serve traffic.

## Logs and What to Look For

Useful log commands:

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f postgres
```

During a healthy startup, expect to see:

- backend startup on port `8080`
- Hikari datasource creation
- successful PostgreSQL connection
- Flyway validation and migration output
- no Google tokens, passwords, or raw auth payloads in logs

If the backend fails early, focus on the first infrastructure error rather than the final stack trace tail.

## Database Operations

### Where Schema Changes Live

Schema changes belong in:

`src/main/resources/db/migration`

### How Migrations Run

Flyway runs automatically during application startup.

### Migration Rules

- add a new versioned file for every schema change
- do not rewrite previously shared migrations
- keep Hibernate schema mode at `validate`

### Reset Local Database State

If local schema state becomes inconsistent:

```bash
docker compose down -v
docker compose up --build
```

This removes the PostgreSQL volume and recreates the local database from scratch.

## Troubleshooting

### The backend fails with a datasource validation or connection error

Check:

- `SPRING_DATASOURCE_URL` is present
- the URL starts with `jdbc:postgresql:`
- username and password are present
- the hostname matches the runtime mode you are using

### The backend fails during Google auth configuration

Check:

- `MAGE_AUTH_GOOGLE_CLIENT_IDS` is present
- the value contains the frontend Google OAuth client ID
- multiple client IDs, if used, are comma-separated without extra quoting

### PostgreSQL never becomes healthy in Docker Compose

Check:

- Docker is actually running
- port `5432` is not already taken
- the configured database username and password are valid in `.env`

If needed, reset the volume:

```bash
docker compose down -v
docker compose up --build
```

### `/ready` returns `503`

Interpretation:

- the backend process is up
- either Spring is not yet accepting traffic or the database probe failed

Check:

- backend logs
- PostgreSQL container health
- datasource environment variables

### `POST /auth/google` returns `401`

Interpretation:

- the Google ID token was invalid, expired, missing required claims, or the email was not verified

Check:

- the frontend sent an ID token, not an access token
- the token was issued for a client ID listed in `MAGE_AUTH_GOOGLE_CLIENT_IDS`
- the token has not expired

### `POST /auth/google` returns `409`

Interpretation:

- either a local-only account already owns that email and `/auth/link/google` must be used
- or a different Google identity already owns that email in the backend

### `POST /auth/register` returns `409`

Interpretation:

- either local authentication is already configured for that email
- or the email belongs to a Google-backed account and `/auth/link/local` must be used instead

### `POST /auth/login` returns `401`

Interpretation:

- the supplied credentials did not match an account with local authentication

### `POST /auth/link/google` returns `401`

Interpretation:

- the supplied local email/password pair did not pass the ownership check

### `POST /auth/link/google` returns `409`

Interpretation:

- the Google token email does not match the local account email
- or the Google identity is already linked to a different account

### `POST /auth/link/local` returns `409`

Interpretation:

- the Google-backed account has not been provisioned yet through `POST /auth/google`
- or the request conflicts with an incompatible existing account state

### `GET /users/me` returns `401`

Interpretation:

- the request was missing a bearer token, the token was invalid, or the token points to a user record that no longer exists

### `POST /tags` returns `409`

Interpretation:

- the supplied tag name already exists after normalization, so the backend rejects the duplicate tag creation request

### `POST /presets` returns `401`

Interpretation:

- the request was missing a bearer token, the token was invalid, or the token points to a user record that no longer exists

### `POST /presets/{id}/tags` returns `401`

Interpretation:

- the request was missing a bearer token, the token was invalid, or the token points to a user record that no longer exists

### `GET /presets` returns `401`

Interpretation:

- the request was missing a bearer token, the token was invalid, or the token points to a user record that no longer exists

### `DELETE /presets/{id}` returns `401`

Interpretation:

- the request was missing a bearer token, the token was invalid, or the token points to a user record that no longer exists

### `GET /users/{id}/presets` returns `401`

Interpretation:

- the request was missing a bearer token, the token was invalid, or the token points to a user record that no longer exists

### `GET /presets/{id}` returns `404`

Interpretation:

- no preset exists for that id, regardless of whether the caller is signed in

### `DELETE /presets/{id}` returns `403`

Interpretation:

- the request was authenticated successfully, but the preset belongs to a different user

### `DELETE /presets/{id}` returns `404`

Interpretation:

- the request was authenticated successfully, but no preset exists for that id

### `POST /presets/{id}/tags` returns `404`

Interpretation:

- the request was authenticated successfully, but either the preset id or tag id did not match an existing record

### `POST /presets/{id}/tags` returns `409`

Interpretation:

- the supplied tag is already attached to the preset, so the backend rejects the duplicate association request

### Tests fail before running assertions

Likely cause:

- Docker is unavailable, so Testcontainers cannot start PostgreSQL

### The database seems out of sync with migrations

Check:

- migration files are in the correct folder
- filenames use Flyway naming conventions
- local state was not carried forward from an old incompatible volume

Resetting the local volume is usually the fastest way to recover.

## Test Operations

Run the full backend suite with:

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```
