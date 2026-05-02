# MAGE Backend

Spring Boot service for the MAGE platform.

This repository currently provides the backend foundations for:

- health and readiness checks
- local account registration and login
- Google authentication and explicit provider linking
- bearer-token authentication for protected routes
- user profile lookup
- scene creation, retrieval, deletion, and user-scoped listing
- tag creation, retrieval, and scene tagging

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
docker compose down -v
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up --build
```

macOS/Linux:

```bash
cp .env.example .env
docker compose down -v
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up --build
```

The local workflow currently expects the MinIO override because thumbnail storage configuration is required at startup:

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

## Deployment Strategy

See [docs/deployment.md](docs/deployment.md) for the expected reverse-proxy contract and the required backend environment variables.

## Current API Surface

| Route                                       | Auth         | Purpose                                                                                    |
| ------------------------------------------- | ------------ | ------------------------------------------------------------------------------------------ |
| `GET /health`                               | Public       | Process liveness                                                                           |
| `GET /ready`                                | Public       | Application and database readiness                                                         |
| `POST /api/auth/register`                   | Public       | Create a local account                                                                     |
| `POST /api/auth/login`                      | Public       | Authenticate a local account                                                               |
| `POST /api/auth/google`                     | Public       | Authenticate with a Google ID token                                                        |
| `POST /api/auth/link/google`                | Public       | Link Google auth to an existing local account                                              |
| `POST /api/auth/link/local`                 | Public       | Add local auth to an existing Google-backed account                                        |
| `GET /api/users/me`                         | Bearer token | Return the current user profile                                                            |
| `PUT /api/users/me`                         | Bearer token | Update the authenticated user's first name, last name, and display name                    |
| `GET /api/tags`                             | Public       | List available tags                                                                        |
| `POST /api/tags`                            | Public       | Create a tag                                                                               |
| `POST /api/scenes`                         | Bearer token | Create a scene with optional description and optionally finalize a staged thumbnail       |
| `POST /api/scenes/thumbnail/presign`       | Bearer token | Presign a staged thumbnail upload before scene creation                                   |
| `GET /api/scenes`                          | Public       | List scenes, optionally filtered by tag                                                   |
| `POST /api/scenes/{id}/tags`               | Bearer token | Attach a tag to a scene                                                                   |
| `PATCH /api/scenes/{id}/description`       | Bearer token | Owner-only plain-text description add, edit, or clear                                     |
| `POST /api/scenes/{id}/thumbnail/presign`  | Bearer token | Owner-only presigned thumbnail upload preparation                                          |
| `POST /api/scenes/{id}/thumbnail/finalize` | Bearer token | Owner-only thumbnail finalize and replacement                                              |
| `GET /api/scenes/{id}`                     | Public       | Fetch a scene by id                                                                       |
| `DELETE /api/scenes/{id}`                  | Bearer token | Delete a scene owned by the authenticated user                                            |
| `GET /api/users/{id}/scenes`               | Bearer token | List scenes for a specific user                                                           |

## Scene Contract

`POST /api/scenes` accepts an optional plain-text `description` up to 1000 characters. Blank descriptions are stored as no description, and scene list/detail responses return the stored `description` value. Owners can add, edit, or clear the description after creation with `PATCH /api/scenes/{id}/description`.

## Auth And Profile Contract

`POST /api/auth/register` now accepts:

```json
{
  "email": "new-user@example.com",
  "password": "secret-value",
  "firstName": "New",
  "lastName": "User",
  "displayName": "New User"
}
```

Successful auth and profile responses return the structured personal-name fields alongside the public attribution name:

```json
{
  "userId": 42,
  "email": "new-user@example.com",
  "firstName": "New",
  "lastName": "User",
  "displayName": "New User",
  "authProvider": "LOCAL"
}
```

`displayName` remains the public-facing creator name used for scene attribution and other public surfaces.

`PUT /api/users/me` accepts:

```json
{
  "firstName": "Updated",
  "lastName": "User",
  "displayName": "Updated User"
}
```

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
