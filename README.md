# MAGE Backend

## Overview

This backend is the Java and Spring Boot service for the MAGE platform. At its current stage, the repository provides the backend foundation plus the first account-authentication flows and preset persistence APIs: application startup, PostgreSQL connectivity, Flyway-managed schema migration, health and readiness endpoints, local account registration and login, Google authentication account provisioning, explicit local and Google account linking, bearer-token authentication middleware, current-user profile lookup, preset creation, preset detail retrieval, owner-only preset deletion, preset tag attachment, preset listing with optional tag filtering, user preset listing, Docker-based local development, and integrated testing.

The codebase is small at the moment, but the documentation and engineering expectations are structured like a team-owned backend project. New contributors should be able to clone the repository, run it locally, understand the architecture, and make disciplined changes without relying on extra explanation.

## Tech Stack

- Java 21
- Spring Boot 4.0.3
- Spring Web MVC
- Spring Security Crypto
- Spring Data JPA
- PostgreSQL 16
- Flyway
- Google API Client
- Maven Wrapper
- JUnit 5, AssertJ, Mockito, and Testcontainers
- Docker and Docker Compose

## Quick Start

For a first run, use the Docker workflow.

Windows PowerShell:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

macOS/Linux:

```bash
cp .env.example .env
docker compose up --build
```

Once the stack is healthy:

- backend: `http://localhost:8080`
- liveness: `http://localhost:8080/health`
- readiness: `http://localhost:8080/ready`
- local registration: `POST http://localhost:8080/api/auth/register`
- local login: `POST http://localhost:8080/api/auth/login`
- Google auth: `POST http://localhost:8080/api/auth/google`
- link Google to an existing local account: `POST http://localhost:8080/api/auth/link/google`
- link local auth to an existing Google-backed account: `POST http://localhost:8080/api/auth/link/local`
- current user profile: `GET http://localhost:8080/api/users/me`
- preset creation: `POST http://localhost:8080/api/presets`
- preset list: `GET http://localhost:8080/api/presets`
- preset list filtered by tag: `GET http://localhost:8080/api/presets?tag=ambient`
- preset detail: `GET http://localhost:8080/api/presets/{id}`
- preset deletion: `DELETE http://localhost:8080/api/presets/{id}`
- preset tag attachment: `POST http://localhost:8080/api/presets/{id}/tags`
- user preset list: `GET http://localhost:8080/api/users/{id}/presets`

Run the test suite with:

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

## Environment and Local Run Notes

- `.env.example` is designed for Docker Compose.
- `MAGE_AUTH_GOOGLE_CLIENT_IDS` must contain the Google OAuth client ID used by the frontend.
- if the backend runs inside Docker, the datasource host is `postgres`
- Flyway runs automatically during application startup
- the test suite requires Docker because integration tests use Testcontainers

Full local setup instructions live in [docs/getting-started.md](docs/getting-started.md).

## Project Structure

```text
mage-backend/
|- docs/
|  |- architecture.md
|  |- engineering-standards.md
|  |- getting-started.md
|  `- operations.md
|- src/
|  |- main/
|  |  |- java/com/bdmage/mage_backend/
|  |  |  |- config/
|  |  |  |- client/
|  |  |  |- controller/
|  |  |  |- dto/
|  |  |  |- exception/
|  |  |  |- service/
|  |  |  `- MageBackendApplication.java
|  |  `- resources/
|  |     |- application.properties
|  |     `- db/migration/
|  `- test/
|     `- java/com/bdmage/mage_backend/
|        |- config/
|        |- controller/
|        |- service/
|        |- support/
|        `- MageBackendApplicationTests.java
|- .env.example
|- CONTRIBUTING.md
|- docker-compose.yml
|- Dockerfile
|- pom.xml
|- mvnw
|- mvnw.cmd
`- README.md
```

## Documentation

- [docs/getting-started.md](docs/getting-started.md): setup, environment variables, local run, tests, migrations, and authentication and account-linking endpoint usage
- [docs/architecture.md](docs/architecture.md): current codebase structure and the layered design behind health, authentication, explicit account linking, and current-user profile features
- [docs/engineering-standards.md](docs/engineering-standards.md): coding, API, persistence, testing, logging, security, and collaboration standards
- [docs/operations.md](docs/operations.md): operational runbook for Docker, health checks, authentication and linking behavior, logs, migrations, and troubleshooting
- [CONTRIBUTING.md](CONTRIBUTING.md): pull request and contribution workflow


