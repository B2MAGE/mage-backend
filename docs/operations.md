# Operations

This document is the practical guide for starting, checking, and troubleshooting the backend.

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
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up --build
```

Use the base file plus the local file for host access:

- `docker-compose.yml`: deployment-friendly base definition
- `docker-compose.local.yml`: local-only port publishing
- `docker-compose.minio.yml`: local thumbnail storage configuration and MinIO services

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

## Route Matrix

| Route                                       | Auth         | Notes                                                               |
| ------------------------------------------- | ------------ | ------------------------------------------------------------------- |
| `GET /health`                               | Public       | Process liveness only                                               |
| `GET /ready`                                | Public       | Includes database readiness                                         |
| `POST /api/auth/register`                   | Public       | Creates a local account                                             |
| `POST /api/auth/login`                      | Public       | Returns an access token for local auth                              |
| `POST /api/auth/google`                     | Public       | Returns an access token for Google auth                             |
| `POST /api/auth/link/google`                | Public       | Requires valid local credentials plus a valid Google ID token       |
| `POST /api/auth/link/local`                 | Public       | Requires a valid Google ID token                                    |
| `GET /api/users/me`                         | Bearer token | Current authenticated user                                          |
| `PUT /api/users/me`                         | Bearer token | Updates the current user's first name, last name, and display name |
| `GET /api/tags`                             | Public       | Returns all tags in name order                                      |
| `POST /api/tags`                            | Public       | Tag creation is currently public                                    |
| `POST /api/scenes`                         | Bearer token | Creates an owned scene with optional description and optionally finalizes a staged thumbnail |
| `POST /api/scenes/thumbnail/presign`       | Bearer token | Presigns a staged thumbnail upload before scene creation           |
| `GET /api/scenes`                          | Public       | Supports `?tag=<name>`                                              |
| `POST /api/scenes/{id}/tags`               | Bearer token | Attaches an existing tag to an existing scene                      |
| `PATCH /api/scenes/{id}/description`       | Bearer token | Owner-only plain-text description add, edit, or clear              |
| `POST /api/scenes/{id}/thumbnail/presign`  | Bearer token | Owner-only presigned thumbnail upload preparation                   |
| `POST /api/scenes/{id}/thumbnail/finalize` | Bearer token | Owner-only thumbnail finalize and replacement                       |
| `GET /api/scenes/{id}`                     | Public       | Scene detail is public                                             |
| `DELETE /api/scenes/{id}`                  | Bearer token | Owner-only                                                          |
| `GET /api/users/{id}/scenes`               | Bearer token | User-scoped scene list                                             |

## Common Success and Failure Signals

### Authentication

- `POST /api/auth/register`: `201` on success, `409` for duplicate or link-required cases
- `POST /api/auth/login`: `200` on success, `401` for invalid credentials
- `POST /api/auth/google`: `201` or `200` on success, `401` for invalid token, `409` for collision or link-required cases
- `POST /api/auth/link/google`: `200` on success, `401` for invalid local credentials, `409` for account conflicts
- `POST /api/auth/link/local`: `200` on success, `401` for invalid Google token, `409` for incompatible account state
- `PUT /api/users/me`: `200` on success, `400` for invalid first name, last name, or display name, `401` without a valid bearer token

Registration requests must include `firstName`, `lastName`, and `displayName`. Auth and profile responses return all three fields, and `displayName` remains the public attribution field used outside authenticated profile surfaces.

### Scenes and Tags

- `GET /api/tags`: `200` on success
- `POST /api/tags`: `201` on success, `409` for duplicates
- `POST /api/scenes`: `201` on success, `400` for invalid scene data, oversized description, or invalid staged thumbnail state when `thumbnailObjectKey` is supplied, `401` without a valid bearer token
- `POST /api/scenes/thumbnail/presign`: `200` on success, `400` for invalid file metadata, `401` without a valid bearer token
- `GET /api/scenes`: `200` on success and public for anonymous discovery, optionally filtered with `?tag=<name>`
- `POST /api/scenes/{id}/tags`: `201` on success, `404` if the scene or tag is missing, `409` if the link already exists
- `PATCH /api/scenes/{id}/description`: `200` on success, `400` for oversized description, `401` without a valid bearer token, `403` for non-owner requests, `404` if missing
- `POST /api/scenes/{id}/thumbnail/presign`: `200` on success, `400` for invalid file metadata, `403` for non-owner requests, `404` if the scene is missing
- `POST /api/scenes/{id}/thumbnail/finalize`: `200` on success, `400` for invalid upload state, `403` for non-owner requests, `404` if the scene is missing
- `GET /api/scenes/{id}`: `200` on success, `404` if missing
- `DELETE /api/scenes/{id}`: `204` on success, `403` for non-owner delete attempts, `404` if missing
- `GET /api/users/{id}/scenes`: `200` on success, `401` without a valid bearer token

## Thumbnail Upload Contract

Thumbnail uploads support two flows.

### New scene creation with a thumbnail

This flow avoids leaving behind a partial scene when upload fails:

1. `POST /api/scenes/thumbnail/presign`
2. browser `PUT` to the returned object-storage upload URL
3. `POST /api/scenes` with `thumbnailObjectKey`

`POST /api/scenes/thumbnail/presign` accepts JSON:

```json
{
  "filename": "cover.png",
  "contentType": "image/png",
  "sizeBytes": 524288
}
```

Requirements:

- include `Authorization: Bearer <accessToken>`
- the caller must be authenticated
- supported content types are `image/jpeg`, `image/png`, `image/webp`, and `image/gif`
- the business validation limit is `5 MB`

Success behavior:

- returns `200 OK`
- returns the object-storage upload URL, HTTP method, object key, and required upload headers
- scopes the staged object key under the authenticated user's pending thumbnail prefix

After the browser upload succeeds, create the scene with:

```json
{
  "name": "Scene Name",
  "description": "Plain-text scene description.",
  "sceneData": {
    "visualizer": {},
    "controls": {},
    "intent": {},
    "fx": {},
    "state": {}
  },
  "thumbnailObjectKey": "scenes/pending/42/thumbnails/abc123.png"
}
```

Success behavior:

- returns `201 Created`
- verifies the uploaded object exists in the configured object-storage provider before the scene is written
- persists the optional plain-text `description` when supplied
- persists `thumbnailRef` using the configured public thumbnail base URL
- attempts to delete the staged uploaded object if scene persistence fails after thumbnail verification

Failure behavior:

- returns `400 INVALID_THUMBNAIL` when the staged object key or uploaded object metadata is invalid
- returns `401 AUTHENTICATION_REQUIRED` when the bearer token is missing or invalid
- returns `503 THUMBNAIL_STORAGE_UNAVAILABLE` when provider presign or verification is unavailable

### Existing scene thumbnail replacement

This flow updates the thumbnail for an already persisted scene:

1. `POST /api/scenes/{id}/thumbnail/presign`
2. browser `PUT` to the returned object-storage upload URL
3. `POST /api/scenes/{id}/thumbnail/finalize`

### Presign request

`POST /api/scenes/{id}/thumbnail/presign` accepts JSON:

### Browser upload

The browser uploads directly to the configured object-storage provider with:

- `PUT <uploadUrl>`
- the returned headers, including `Content-Type`
- the raw file body

This step bypasses the backend application server. The active provider must allow the frontend origins to perform `PUT` uploads.

### Finalize request

`POST /api/scenes/{id}/thumbnail/finalize` accepts JSON:

```json
{
  "objectKey": "scenes/15/thumbnails/abc123.png"
}
```

Success behavior:

- returns `200 OK`
- returns the full updated `SceneResponse`
- verifies the uploaded object exists in the configured object-storage provider
- updates `thumbnailRef` to the configured public thumbnail base URL
- later finalized uploads replace the scene's stored thumbnail reference and attempt to clean up the previous uploaded object

Failure behavior:

- returns `400 INVALID_THUMBNAIL` when the metadata or uploaded object state is invalid
- returns `401 AUTHENTICATION_REQUIRED` when the bearer token is missing or invalid
- returns `403 SCENE_OWNERSHIP_REQUIRED` when the caller does not own the scene
- returns `404 SCENE_NOT_FOUND` when the scene id does not exist
- returns `503 THUMBNAIL_STORAGE_UNAVAILABLE` when provider presign or verification is unavailable

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
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml down -v
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up --build
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
