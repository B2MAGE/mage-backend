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
- successful `POST /auth/login` and `POST /auth/google` requests establish a server-side HTTP session used by `GET /users/me`

## Local Startup Runbook

```bash
docker compose up --build
```

Use this when:

- validating Docker-based local behavior
- testing migration behavior against a clean containerized stack

Before using `POST /auth/google`, replace the placeholder value in `.env` for `MAGE_AUTH_GOOGLE_CLIENT_IDS` with the Google OAuth client ID used by the frontend.

## Health Checks and Auth Endpoints

The backend currently exposes six operational endpoints:

- `GET /health`
- `GET /ready`
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/google`
- `GET /users/me`

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
- HTTP `409 Conflict` when the email is already registered for an existing account

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
- response includes the authenticated user identity fields and auth provider
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

Failure behavior:

- HTTP `400 Bad Request` for malformed JSON or blank `idToken`
- HTTP `401 Unauthorized` for invalid, expired, or unverified Google identities
- HTTP `409 Conflict` when the Google-authenticated email conflicts with existing account rules

### `GET /users/me`

Purpose:

- return the profile of the authenticated user

Request notes:

- requires the session cookie established by `POST /auth/login` or `POST /auth/google`

Success behavior:

- HTTP `200 OK` for an authenticated session
- response includes the authenticated user's identity fields, auth provider, and creation timestamp
- response never includes the raw password, stored password hash, or Google subject

Failure behavior:

- HTTP `401 Unauthorized` when the request has no authenticated session or the session user no longer exists

## Operational Verification Checklist

After startup, verify these items in order:

1. `docker compose ps` shows healthy PostgreSQL if you are using Docker Compose
2. backend logs show Hikari datasource startup
3. backend logs show Flyway applying or validating migrations
4. `curl http://localhost:8080/health` returns `200`
5. `curl http://localhost:8080/ready` returns `200`
6. `POST /auth/register` succeeds for a new local email address
7. `POST /auth/login` succeeds for that local account
8. `GET /users/me` succeeds when called with the login session cookie
9. `POST /auth/google` succeeds with a valid Google ID token issued for a configured client ID

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

- the verified Google email conflicts with an existing account and account linking is not implemented yet

### `POST /auth/register` returns `409`

Interpretation:

- the supplied email already belongs to an existing local or Google-backed account

### `POST /auth/login` returns `401`

Interpretation:

- the supplied credentials did not match a local account

### `GET /users/me` returns `401`

Interpretation:

- the request was missing the authenticated session cookie, or the session points to a user record that no longer exists

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
