# Getting Started

## Purpose

This guide provides the fastest path from cloning the repository to running the MAGE backend locally. It assumes contributors are comfortable with developer tooling but may be new to this repository's setup and conventions.

The local development environment is fully containerized using Docker Compose. This keeps setup consistent across machines and avoids installing PostgreSQL or managing runtime configuration manually.

## Prerequisites

| Tool                            | Required version        | Why it matters                                                |
| ------------------------------- | ----------------------- | ------------------------------------------------------------- |
| Git                             | current stable          | clone the repository and manage branches                      |
| Docker Desktop or Docker Engine | current stable          | run the backend and PostgreSQL locally                        |
| Java                            | 21                      | required by the build and test runtime                        |
| Maven                           | not required separately | use the Maven wrapper included in the repository              |
| IDE                             | optional                | IntelliJ IDEA or VS Code with Java support are common choices |

Docker must also be running when executing the integration test suite because Testcontainers is used.

## Clone the Repository

    git clone https://github.com/B2MAGE/mage-backend.git
    cd mage-backend

The first Maven wrapper command downloads project dependencies automatically.

## Configure Environment Variables

Copy the example environment file.

Windows PowerShell:

    Copy-Item .env.example .env

macOS/Linux:

    cp .env.example .env

The `.env` file provides configuration used by Docker Compose.

Important details:

- the backend connects to PostgreSQL using the hostname `postgres`
- this hostname works because both containers run in the same Docker network
- `MAGE_AUTH_GOOGLE_CLIENT_IDS` must be set to the Google OAuth client ID used by the frontend
- multiple Google client IDs can be provided as a comma-separated list if more than one frontend must be trusted

Avoid changing these values unless you understand the container network configuration.

## Start the Backend

Run the full development stack:

    docker compose up --build

This command will:

- build the backend image
- start the PostgreSQL container
- wait for the database health check
- start the backend service
- stream logs to your terminal

## Verify the Application

Once the backend is running, open the following endpoints:

- http://localhost:8080/health
- http://localhost:8080/ready
- POST http://localhost:8080/auth/register
- POST http://localhost:8080/auth/login
- POST http://localhost:8080/auth/google
- GET http://localhost:8080/users/me
- POST http://localhost:8080/presets
- GET http://localhost:8080/users/{id}/presets

Expected responses:

- `/health` returns `200 OK` with `{"status":"UP"}`
- `/ready` returns `200 OK` with `{"status":"UP","database":"UP"}` when PostgreSQL is reachable
- `POST /auth/register` returns `201 Created` for a new local account and never returns the raw password or stored password hash
- `POST /auth/login` returns `200 OK` when a local account's credentials are valid and includes an `accessToken` for protected endpoints without exposing the raw password or stored password hash
- `POST /auth/google` returns either `201 Created` or `200 OK` and includes an `accessToken` for protected endpoints
- `GET /users/me` returns `200 OK` with the authenticated user's profile when the request includes `Authorization: Bearer <accessToken>`
- `POST /presets` returns `201 Created` with the created preset fields when the request includes `Authorization: Bearer <accessToken>`
- `GET /users/{id}/presets` returns `200 OK` with an array of presets for the requested user when the request includes `Authorization: Bearer <accessToken>`

If `/ready` returns `503`, the application process is running but not yet ready to serve traffic.

To exercise the local registration endpoint:

    curl -X POST http://localhost:8080/auth/register \
      -H "Content-Type: application/json" \
      -d '{"email":"user@example.com","password":"example-password","displayName":"Example User"}'

To exercise the local login endpoint:

    curl -X POST http://localhost:8080/auth/login \
      -H "Content-Type: application/json" \
      -d '{"email":"user@example.com","password":"example-password"}'

Use the `accessToken` from the login response or Google auth response when calling protected endpoints.

    curl http://localhost:8080/users/me \
      -H "Authorization: Bearer <access-token>"

    curl -X POST http://localhost:8080/presets \
      -H "Authorization: Bearer <access-token>" \
      -H "Content-Type: application/json" \
      -d '{"name":"Aurora Drift","sceneData":{"visualizer":{"shader":"nebula"}},"thumbnailRef":"thumbnails/preset-1.png"}'

    curl http://localhost:8080/users/<user-id>/presets \
      -H "Authorization: Bearer <access-token>"

To exercise the Google auth endpoint, send a Google ID token issued for one of the configured client IDs:

    curl -X POST http://localhost:8080/auth/google \
      -H "Content-Type: application/json" \
      -d '{"idToken":"<google-id-token>"}'

## Running Tests

Run the full test suite using the Maven wrapper.

Windows PowerShell:

    .\mvnw.cmd test

macOS/Linux:

    ./mvnw test

The test suite includes:

- unit tests for controllers, services, and configuration logic
- Spring Boot integration tests
- PostgreSQL-backed integration tests using Testcontainers
- Flyway migration verification

Docker must be running because Testcontainers starts PostgreSQL automatically during integration tests.

## Database Migrations

Flyway migrations run automatically during application startup.

Schema changes must follow these rules:

- add migration files under `src/main/resources/db/migration`
- use filenames in the format `V<version>__<description>.sql`
- treat migrations as append-only
- do not modify migrations that have already been applied in shared environments

To test migrations against a clean database:

    docker compose down -v
    docker compose up --build

This resets the PostgreSQL volume and re-applies all migrations.

## Common Setup Problems

### Backend fails with a datasource configuration error

Check that:

- the `.env` file exists
- Docker Compose is loading environment variables correctly
- PostgreSQL container is running

### `/ready` returns `503 Service Unavailable`

This usually means PostgreSQL is not reachable yet. Check container health and backend logs.

### Docker commands fail

Make sure Docker Desktop or your local Docker daemon is running.

### Port `5432` or `8080` is already in use

Stop the conflicting process or change the port mapping in `docker-compose.yml`.

### Database state appears inconsistent

Reset the Docker volume:

    docker compose down -v
    docker compose up --build

### First run is slow

On a fresh machine, Docker images and Maven dependencies must be downloaded. Subsequent runs will be significantly faster.

## Suggested First-Day Workflow

If you are new to the repository, this sequence builds the fastest mental model of the system:

1. clone the repository
2. run `docker compose up --build`
3. verify `/health` and `/ready`
4. run the test suite
5. read `architecture.md`
6. read `engineering-standards.md`
7. trace the `/ready` endpoint from controller to service to datasource
8. trace `POST /auth/register` from controller to service to repository and password hashing
9. trace `POST /auth/login` from controller to service to repository and password hashing
10. trace `POST /auth/google` from controller to service to verifier to repository
11. trace `GET /users/me` from authentication middleware to controller to service to repository
12. trace `POST /presets` from authentication middleware to controller to service to repository
13. trace `GET /users/{id}/presets` from authentication middleware to controller to service to repository

## Expected Change Workflow

When making backend changes, follow this order:

1. understand the current behavior
2. determine which layer owns the change
3. implement the change
4. add or update tests
5. add Flyway migrations if the schema changes
6. update documentation if developer or operational workflows change

Also check out: [architecture.md](architecture.md), [engineering-standards.md](engineering-standards.md), and [operations.md](operations.md)
