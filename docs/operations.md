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
- Google ID tokens are verified server-side against `MAGE_AUTH_GOOGLE_CLIENT_IDS`
- local and Google auth providers can be linked explicitly, but never auto-linked only because emails match

## Local Startup Runbook

```bash
docker compose up --build
```

Use this when:

- validating Docker-based local behavior
- testing migration behavior against a clean containerized stack

Before using the Google auth endpoints, replace the placeholder value in `.env` for `MAGE_AUTH_GOOGLE_CLIENT_IDS` with the Google OAuth client ID used by the frontend.

## Health Checks and Auth Endpoints

The backend currently exposes six relevant HTTP endpoints:

- `GET /health`
- `GET /ready`
- `POST /auth/register`
- `POST /auth/google`
- `POST /auth/link/google`
- `POST /auth/link/local`

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

## Operational Verification Checklist

After startup, verify these items in order:

1. `docker compose ps` shows healthy PostgreSQL if you are using Docker Compose
2. backend logs show Hikari datasource startup
3. backend logs show Flyway applying or validating migrations
4. `curl http://localhost:8080/health` returns `200`
5. `curl http://localhost:8080/ready` returns `200`
6. `POST /auth/register` succeeds for a new local email address
7. `POST /auth/google` succeeds with a valid Google ID token issued for a configured client ID
8. `POST /auth/link/google` succeeds when both the local credentials and Google token prove ownership of the same email
9. `POST /auth/link/local` succeeds for an existing Google-backed account with a valid Google ID token

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
.\mvnw.cmd clean test
```

macOS/Linux:

```bash
./mvnw clean test
```
