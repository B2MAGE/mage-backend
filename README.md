# MAGE Backend

Spring Boot service for the MAGE platform.

This repository currently provides the backend foundations for:

- health and readiness checks
- local account registration and login
- Google authentication and explicit provider linking
- bearer-token authentication for protected routes
- authenticated profile management and public handle-based profiles
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
| `POST /api/auth/reset-password/request`     | Public       | Request a password reset link for a local or linked local account                          |
| `POST /api/auth/reset-password/confirm`     | Public       | Reset a local password with a valid reset token                                             |
| `POST /api/auth/link/google`                | Public       | Link Google auth to an existing local account                                              |
| `POST /api/auth/link/local`                 | Public       | Add local auth to an existing Google-backed account                                        |
| `GET /api/users/me`                         | Bearer token | Return the current user profile                                                            |
| `PUT /api/users/me`                         | Bearer token | Update the authenticated user's names, unique handle, description, and avatar gradient     |
| `PUT /api/users/me/password`                | Bearer token | Change the authenticated user's local password                                              |
| `GET /api/profiles/{handle}`                | Public       | Return a public profile and its scenes by handle                                            |
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

Scene responses include `availability` and server-owned `sceneMode`, returning `sceneData: null` while unavailable. **Custom rendering defaults to disabled** until isolation release approval and an explicit operator enable. Old documents remain intact and require an owner save through the explicit template/custom contract before playback. See [scene documents and deployment order](docs/scene-documents.md) and the [scene availability runbook](docs/scene-availability.md). Frontend management and live-stop integration is tracked by PP-R03.

Scene creation and content replacement enforce the checked-in
[submission limits policy](docs/scene-submission-limits.md) before persistence.
Request bodies are bounded before JSON binding, including gzip and chunked input.
Run the [read-only inventory](docs/scene-submission-inventory.md) to check existing
records; this change does not rewrite legacy data.

Scene lists from `GET /api/scenes` (including tag-filtered results), `GET /api/users/{id}/scenes`, and public profile scene collections use ascending scene ID order. This stable unique-key order does not depend on database query plans; clients may apply their own newest, most-viewed, or other presentation sorting.

Discovery responses include the creator display name, handle, avatar-gradient colors, real engagement metrics, and attached tag names. Attached tags are loaded in one batch for a scene collection. `GET /api/tags` returns stable name-ordered entries containing `tagId`, `name`, and the real attached `sceneCount`; `?attachedOnly=true` excludes unused tags while the default catalogue retains them for the editor.

`POST /api/scenes` accepts an optional plain-text `description` up to 1000 characters. Blank descriptions are stored as no description, and scene list/detail responses return the stored `description` value. Owners can add, edit, or clear the description after creation with `PATCH /api/scenes/{id}/description`.

## Auth And Profile Contract

Local registration requires a password of 8 to 72 characters and a display name of 2 to 100 characters after trimming. Validation errors use the existing `details.password` and `details.displayName` fields.

`POST /api/auth/register` now accepts:

```json
{
  "email": "new-user@example.com",
  "password": "secret-value",
  "firstName": "New",
  "lastName": "User",
  "displayName": "New User",
  "handle": "@newuser"
}
```

The handle is required, globally unique, and case-insensitive. It must start with `@`; the name after `@` must be 3 to 30 characters, start with a letter, and contain only letters, numbers, or underscores.

Successful auth and authenticated profile responses include the normalized handle without `@` and the optional profile description:

```json
{
  "userId": 42,
  "email": "new-user@example.com",
  "firstName": "New",
  "lastName": "User",
  "displayName": "New User",
  "handle": "newuser",
  "description": null,
  "avatarGradientStart": "#5c51ba",
  "avatarGradientEnd": "#264a48",
  "authProvider": "LOCAL"
}
```

`displayName` remains the public-facing creator name used for scene attribution and other public surfaces.

`PUT /api/users/me` accepts:

```json
{
  "firstName": "Updated",
  "lastName": "User",
  "displayName": "Updated User",
  "handle": "@updateduser",
  "description": "I build quiet, reactive scenes.",
  "avatarGradientStart": "#5c51ba",
  "avatarGradientEnd": "#264a48"
}
```

`description` is optional, may be cleared with `null` or blank text, and is limited to 300 characters. Changing a handle immediately changes the public profile URL.

`avatarGradientStart` and `avatarGradientEnd` are optional six-digit hex colors (`#RRGGBB`), normalized to lowercase. Omitted or `null` colors preserve the user's existing values; empty strings and CSS expressions are rejected. Existing and new users default to `#5c51ba` and `#264a48`. These colors are returned in all auth/profile responses, as `creatorAvatarGradientStart`/`creatorAvatarGradientEnd` on scenes, and as `authorAvatarGradientStart`/`authorAvatarGradientEnd` on comments and replies.

`GET /api/profiles/updateduser` is public and returns only public profile fields (`userId`, `displayName`, `handle`, `description`, `avatarGradientStart`, `avatarGradientEnd`, `createdAt`) plus the user's public scenes. It does not expose email, personal-name fields, or authentication-provider details.

`PUT /api/users/me/password` accepts:

```json
{
  "currentPassword": "current-password",
  "newPassword": "new-password"
}
```

Password reset starts with:

```json
{
  "email": "user@example.com"
}
```

sent to `POST /api/auth/reset-password/request`. The response is intentionally neutral and does not reveal whether the email exists. Local development defaults to logging the reset link in backend logs. Deployed environments can send reset emails through SMTP, including Brevo, by setting:

```text
MAGE_PASSWORD_RESET_DELIVERY=smtp
MAGE_PASSWORD_RESET_FRONTEND_BASE_URL=https://your-frontend-origin
MAGE_PASSWORD_RESET_FROM_EMAIL=verified-sender@example.com
MAGE_EMAIL_SMTP_HOST=smtp-relay.brevo.com
MAGE_EMAIL_SMTP_PORT=587
MAGE_EMAIL_SMTP_USERNAME=<brevo-smtp-login>
MAGE_EMAIL_SMTP_PASSWORD=<brevo-smtp-key>
```

The reset link points to `/reset-password?token=...`, which submits:

```json
{
  "token": "raw-token-from-link",
  "newPassword": "new-password"
}
```

to `POST /api/auth/reset-password/confirm`.

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
