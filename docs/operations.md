# Operations

This document is the practical runbook for starting, checking, and troubleshooting the backend.

## Supported Production Model

The supported deployment path is same-origin:
- the frontend is served from the public app origin
- `/api/*` is routed to the backend by the reverse proxy
- the backend is not expected to serve browser traffic from a second public origin

This keeps the current bearer-token auth flow working without introducing CORS requirements into the supported deployment path.

## Local Runtime Model

The standard local stack has two services:
- `postgres`
- `backend`

Operationally important behavior:
- PostgreSQL exposes a health check with `pg_isready`
- Docker Compose waits for PostgreSQL before starting the backend
- the backend validates datasource configuration at startup
- Flyway runs automatically on startup
- bearer-token validation protects the authenticated routes

## Start the Stack

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

Use the base file plus the local file for host access:
- `docker-compose.yml`: deployment-friendly base definition
- `docker-compose.local.yml`: local-only port publishing

Useful log commands:

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f postgres
```

For production deployment notes, use [deployment.md](deployment.md).

## Health Checks

### `GET /health`

Use this as a liveness check.

Healthy response:

```json
{ "status": "UP" }
```

### `GET /ready`

Use this as a readiness check.

Healthy response:

```json
{ "status": "UP", "database": "UP" }
```

If this returns `503`, the app process is alive but not ready to serve traffic.

In the supported same-origin deployment model, these health endpoints are typically checked on the backend service itself or through platform health checks, not through the public frontend domain.

## Route Matrix

| Route | Auth | Notes |
| --- | --- | --- |
| `GET /health` | Public | Process liveness only |
| `GET /ready` | Public | Includes database readiness |
| `POST /api/auth/register` | Public | Creates a local account |
| `POST /api/auth/login` | Public | Returns an access token for local auth |
| `POST /api/auth/google` | Public | Returns an access token for Google auth |
| `POST /api/auth/link/google` | Public | Requires valid local credentials plus a valid Google ID token |
| `POST /api/auth/link/local` | Public | Requires a valid Google ID token |
| `GET /api/users/me` | Bearer token | Current authenticated user |
| `POST /api/tags` | Public | Tag creation is currently public |
| `POST /api/presets` | Bearer token | Creates an owned preset |
| `GET /api/presets` | Bearer token | Supports `?tag=<name>` |
| `POST /api/presets/{id}/tags` | Bearer token | Attaches an existing tag to an existing preset |
| `POST /api/presets/{id}/thumbnail` | Bearer token | Owner-only multipart thumbnail upload |
| `GET /api/presets/{id}` | Public | Preset detail is public |
| `DELETE /api/presets/{id}` | Bearer token | Owner-only |
| `GET /api/users/{id}/presets` | Bearer token | User-scoped preset list |

## Common Success and Failure Signals

### Authentication

- `POST /api/auth/register`: `201` on success, `409` for duplicate or link-required cases
- `POST /api/auth/login`: `200` on success, `401` for invalid credentials
- `POST /api/auth/google`: `201` or `200` on success, `401` for invalid token, `409` for collision or link-required cases
- `POST /api/auth/link/google`: `200` on success, `401` for invalid local credentials, `409` for account conflicts
- `POST /api/auth/link/local`: `200` on success, `401` for invalid Google token, `409` for incompatible account state

### Presets and Tags

- `POST /api/tags`: `201` on success, `409` for duplicates
- `POST /api/presets`: `201` on success, `401` without a valid bearer token
- `GET /api/presets`: `200` on success, `401` without a valid bearer token
- `POST /api/presets/{id}/tags`: `201` on success, `404` if the preset or tag is missing, `409` if the link already exists
- `POST /api/presets/{id}/thumbnail`: `200` on success, `400` for invalid uploads, `403` for non-owner uploads, `404` if the preset is missing
- `GET /api/presets/{id}`: `200` on success, `404` if missing
- `DELETE /api/presets/{id}`: `204` on success, `403` for non-owner delete attempts, `404` if missing

## Thumbnail Upload Contract

`POST /api/presets/{id}/thumbnail` accepts `multipart/form-data` with one required part:

- `file`: the thumbnail image payload

Request requirements:
- include `Authorization: Bearer <accessToken>`
- the authenticated user must own the target preset
- supported content types are `image/jpeg`, `image/png`, `image/webp`, and `image/gif`
- the business validation limit is `5 MB`

Success behavior:
- returns `200 OK`
- returns the full updated `PresetResponse`
- updates `thumbnailRef` to the stored thumbnail path
- later uploads replace the preset's stored thumbnail reference and attempt to clean up the previous local file

Failure behavior:
- returns `400 INVALID_THUMBNAIL` when the file is empty, unsupported, or larger than `5 MB`
- returns `401 AUTHENTICATION_REQUIRED` when the bearer token is missing or invalid
- returns `403 PRESET_OWNERSHIP_REQUIRED` when the caller does not own the preset
- returns `404 PRESET_NOT_FOUND` when the preset id does not exist

## Database Operations

### Migrations

Flyway migrations live in:

`src/main/resources/db/migration`

Rules:
- add new versioned SQL files
- do not rewrite shared migrations
- keep local schema changes migration-driven

### Reset Local State

If local database state is corrupted or out of sync:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml down -v
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

This deletes the PostgreSQL volume and recreates the schema from migrations.

## Troubleshooting

### The backend fails on startup with datasource errors

Check:
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- PostgreSQL container health

### `/ready` returns `503`

Check:
- backend logs
- PostgreSQL logs
- datasource values in `.env`

### Google auth fails

Check:
- `MAGE_AUTH_GOOGLE_CLIENT_IDS` is set
- the frontend sent a Google ID token, not an access token
- the ID token was issued for one of the configured client IDs

### Tests fail before any assertions run

The usual cause is that Docker is unavailable, so Testcontainers cannot start PostgreSQL.

### Ports are already in use

The local override publishes:
- `8080` for the backend
- `5432` for PostgreSQL

If either port is busy, either stop the conflicting process or edit `docker-compose.local.yml`.
