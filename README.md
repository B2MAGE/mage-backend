# MAGE Backend

Spring Boot service for the MAGE platform.

This repository currently provides the backend foundations for:
- health and readiness checks
- local account registration and login
- Google authentication and explicit provider linking
- bearer-token authentication for protected routes
- user profile lookup
- preset creation, retrieval, deletion, and user-scoped listing
- tag creation, retrieval, and preset tagging

## Stack

- Java 21
- Spring Boot 4
- Spring Web MVC
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Google API Client
- Maven Wrapper
- JUnit 5, Mockito, AssertJ, Testcontainers
- Docker Compose

## Getting Started

The default local workflow uses Docker Compose.

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

To run local self-hosted object storage instead of AWS S3, add the MinIO override:

```bash
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up --build
```

That override reads its own `MAGE_THUMBNAIL_MINIO_*` values from `.env`, so you can keep your normal S3 settings in the same file.

`docker-compose.yml` stays deployment-friendly and does not publish host ports.
`docker-compose.local.yml` adds the local host bindings for:
- backend: `http://localhost:8080`
- postgres: `localhost:5432`

Once the stack is up:
- app: `http://localhost:8080`
- liveness: `GET /health`
- readiness: `GET /ready`

Run tests with:

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

The test suite uses Testcontainers, so Docker must be running.

## Configuration

The backend reads configuration from environment variables. The local Docker setup is driven by `.env`.

Required values for local development:

| Variable | Purpose |
| --- | --- |
| `SERVER_PORT` | Backend HTTP port. Defaults to `8080`. |
| `MAGE_AUTH_GOOGLE_CLIENT_IDS` | Allowed Google OAuth client IDs for server-side ID token verification. |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL. Docker Compose expects `jdbc:postgresql://postgres:5432/mage`. |
| `SPRING_DATASOURCE_USERNAME` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | Database password. |
| `MAGE_THUMBNAIL_PROVIDER` | Thumbnail storage provider. Supported values are `aws-s3` and `minio`. |
| `MAGE_THUMBNAIL_BUCKET` | Object-storage bucket used for preset thumbnails. |
| `MAGE_THUMBNAIL_PUBLIC_BASE_URL` | Public base URL used in persisted `thumbnailRef` values. |

Other useful defaults in `.env.example`:
- `MAGE_THUMBNAIL_REGION`
- `SPRING_APPLICATION_NAME`
- `SPRING_PROFILES_ACTIVE`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `MAGE_THUMBNAIL_KEY_PREFIX`
- `MAGE_THUMBNAIL_ENDPOINT`
- `MAGE_THUMBNAIL_PRESIGN_ENDPOINT`
- `MAGE_THUMBNAIL_PATH_STYLE_ACCESS`
- `MAGE_THUMBNAIL_ALLOWED_CONTENT_TYPES`
- `MAGE_THUMBNAIL_MAX_BYTES`
- `MAGE_THUMBNAIL_PRESIGN_DURATION`

Optional for `aws-s3` local Docker development outside EC2:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`
- `AWS_SESSION_TOKEN`

Optional for `minio` local/self-hosted mode:
- `MAGE_THUMBNAIL_MINIO_ACCESS_KEY_ID`
- `MAGE_THUMBNAIL_MINIO_SECRET_ACCESS_KEY`
- `MAGE_THUMBNAIL_MINIO_ROOT_USER`
- `MAGE_THUMBNAIL_MINIO_ROOT_PASSWORD`
- `MAGE_THUMBNAIL_MINIO_BUCKET`
- `MAGE_THUMBNAIL_MINIO_ENDPOINT`
- `MAGE_THUMBNAIL_MINIO_PRESIGN_ENDPOINT`
- `MAGE_THUMBNAIL_MINIO_PUBLIC_BASE_URL`

The backend generates presigned object-storage uploads itself. In `aws-s3` mode, the Dockerized backend needs valid AWS credentials when it is not running on the EC2 host with the attached IAM role. In `minio` mode, the backend signs requests against the configured MinIO endpoint with static credentials.

## Deployment Strategy

The supported production path is same-origin deployment behind a reverse proxy:

- the frontend is served from the public app origin
- `/api/*` is routed to this backend service
- browser auth requests stay on the same HTTPS origin as the frontend
- CORS is not part of the supported deployment path

See [docs/deployment.md](docs/deployment.md) for the expected reverse-proxy contract and the required backend environment variables.

## Current API Surface

| Route | Auth | Purpose |
| --- | --- | --- |
| `GET /health` | Public | Process liveness |
| `GET /ready` | Public | Application and database readiness |
| `POST /api/auth/register` | Public | Create a local account |
| `POST /api/auth/login` | Public | Authenticate a local account |
| `POST /api/auth/google` | Public | Authenticate with a Google ID token |
| `POST /api/auth/link/google` | Public | Link Google auth to an existing local account |
| `POST /api/auth/link/local` | Public | Add local auth to an existing Google-backed account |
| `GET /api/users/me` | Bearer token | Return the current user profile |
| `GET /api/tags` | Public | List available tags |
| `POST /api/tags` | Public | Create a tag |
| `POST /api/presets` | Bearer token | Create a preset owned by the authenticated user and optionally finalize a staged thumbnail |
| `POST /api/presets/thumbnail/presign` | Bearer token | Presign a staged thumbnail upload before preset creation |
| `GET /api/presets` | Public | List presets, optionally filtered by tag |
| `POST /api/presets/{id}/tags` | Bearer token | Attach a tag to a preset |
| `POST /api/presets/{id}/thumbnail/presign` | Bearer token | Owner-only presigned thumbnail upload preparation |
| `POST /api/presets/{id}/thumbnail/finalize` | Bearer token | Owner-only thumbnail finalize and replacement |
| `GET /api/presets/{id}` | Public | Fetch a preset by id |
| `DELETE /api/presets/{id}` | Bearer token | Delete a preset owned by the authenticated user |
| `GET /api/users/{id}/presets` | Bearer token | List presets for a specific user |

## Repository Layout

```text
mage-backend/
|- docs/
|- src/
|  |- main/
|  |  |- java/com/bdmage/mage_backend/
|  |  |  |- client/
|  |  |  |- config/
|  |  |  |- controller/
|  |  |  |- dto/
|  |  |  |- exception/
|  |  |  |- model/
|  |  |  |- repository/
|  |  |  `- service/
|  |  `- resources/
|  |     `- db/migration/
|  `- test/
|- CONTRIBUTING.md
|- docker-compose.local.yml
|- docker-compose.minio.yml
|- docker-compose.yml
|- Dockerfile
|- pom.xml
`- README.md
```

## Documentation

- [docs/README.md](docs/README.md): documentation index and reading order
- [docs/getting-started.md](docs/getting-started.md): local setup, configuration, tests, and first verification steps
- [docs/deployment.md](docs/deployment.md): same-origin production deployment contract and reverse-proxy expectations
- [docs/architecture.md](docs/architecture.md): package layout, request flow, auth model, and persistence model
- [docs/operations.md](docs/operations.md): runbook, health checks, auth matrix, and troubleshooting
- [docs/engineering-standards.md](docs/engineering-standards.md): coding, API, testing, and review expectations
- [CONTRIBUTING.md](CONTRIBUTING.md): branch, PR, and review workflow

## Current Scope

The backend is still early-stage infrastructure. It already has a real authentication model, persistence layer, migrations, and tests, but it does not yet have a broader authorization framework, logout/token revocation, or token expiration.
