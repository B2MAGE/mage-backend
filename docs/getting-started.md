# Getting Started

This guide is the shortest path from clone to a working local backend.

## Prerequisites

- Git
- Docker Desktop or a local Docker Engine
- Java 21
- an IDE with Java support if you want local editing/debugging

You do not need a separate Maven install. Use the wrapper in the repo.

## First Run

1. Clone the repository and enter the project directory.
2. Copy the example environment file.
3. Start the local stack with Docker Compose.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

macOS/Linux:

```bash
cp .env.example .env
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

This starts:

- `postgres`: PostgreSQL 16
- `backend`: the Spring Boot service from this repo

To run local self-hosted object storage instead of AWS S3, add:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up --build
```

That override uses the `MAGE_THUMBNAIL_MINIO_*` values in `.env.example`, so you do not need to replace your normal S3 settings just to switch locally.

The split is intentional:

- `docker-compose.yml`: base services for deployment-friendly container networking
- `docker-compose.local.yml`: local-only host port bindings for `8080` and `5432`

## Environment Variables

`.env.example` is set up for the containerized workflow. If you change the datasource host, make sure it still matches the way you are running the app.

## Verify the Service

Once startup completes, check:

- `GET http://localhost:8080/health`
- `GET http://localhost:8080/ready`

Expected responses:

- `/health`: `200 OK`
- `/ready`: `200 OK` when PostgreSQL is reachable, `503` while the app is still coming up or the database is unavailable

Useful follow-up checks:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/users/me` with a bearer token
- `POST /api/presets`
- `POST /api/presets/thumbnail/presign`
- `POST /api/presets/{id}/thumbnail/presign`
- `POST /api/presets/{id}/thumbnail/finalize`
- `GET /api/presets/{id}`

For endpoint behavior and auth requirements, use [operations.md](operations.md).

## Running Tests

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

The suite includes:

- unit tests for controllers, services, and configuration
- integration tests for HTTP behavior and persistence
- Flyway migration coverage
- PostgreSQL-backed tests through Testcontainers

Docker must be running for the integration suite.

## Database Migrations

Flyway runs automatically at startup.

Migration rules:

- add new files under `src/main/resources/db/migration`
- use `V<version>__<description>.sql`
- treat shared migrations as append-only

If local schema state gets messy, the quickest reset is:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml down -v
docker compose -f docker-compose.yml -f docker-compose.local.yml up --build
```

## Common Local Issues

### `/ready` returns `503`

The process is up, but the app is not ready to serve traffic yet. Check:

- PostgreSQL container health
- backend logs
- datasource values in `.env`

### Docker startup fails

Check:

- Docker is running
- ports `5432` and `8080` are available

### Google auth fails immediately

Check:

- `MAGE_AUTH_GOOGLE_CLIENT_IDS` is set
- the client ID matches the frontend that issued the ID token

### Thumbnail presign or finalize calls fail

Check:

- `MAGE_THUMBNAIL_PROVIDER`, `MAGE_THUMBNAIL_BUCKET`, and `MAGE_THUMBNAIL_PUBLIC_BASE_URL`
- the active provider credentials available to the backend
- the active provider CORS for local frontend origins if you are testing direct browser uploads

### Tests fail before assertions run

The usual cause is that Docker is unavailable, so Testcontainers cannot start PostgreSQL.

## Suggested Reading Order

If you are new to the backend:

1. Read [README.md](../README.md)
2. Run the stack locally
3. Read [architecture.md](architecture.md)
4. Read [engineering-standards.md](engineering-standards.md)
5. Trace one request end to end, such as `POST /api/auth/register` or `POST /api/presets`
