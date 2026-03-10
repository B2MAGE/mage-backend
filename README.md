# MAGE Backend

Backend service for the MAGE platform.

Start here:

- New to Spring Boot: read [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md)
- Want to run the project with Docker: read [docs/development/docker.md](docs/development/docker.md)
- Want to know what already exists: read `Current Repo State`

## Current Repo State

As of March 10, 2026, this repository contains:

- A single Spring Boot application entry point in [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](src/main/java/com/bdmage/mage_backend/MageBackendApplication.java)
- PostgreSQL datasource validation and startup wiring in [src/main/java/com/bdmage/mage_backend/config/DatabaseConfiguration.java](src/main/java/com/bdmage/mage_backend/config/DatabaseConfiguration.java)
- Flyway-based database migrations in [src/main/resources/db/migration](src/main/resources/db/migration)
- Health endpoints at `/health` and `/ready`
- PostgreSQL-backed integration tests via Testcontainers
- A Docker image build in [Dockerfile](Dockerfile)
- A local development stack in [docker-compose.yml](docker-compose.yml)

## Tech Stack

- Java 21
- Spring Boot
- PostgreSQL
- Docker / Docker Compose

The dependency list lives in [pom.xml](pom.xml).

## Project Layout

```text
mage-backend/
|- docs/
|  |- development/
|  |  `- docker.md
|  `- TEAM_GUIDE.md
|- src/
|  |- main/
|  |  |- java/com/bdmage/mage_backend/
|  |  |  |- config/
|  |  |  |- controller/
|  |  |  |- dto/
|  |  |  |- service/
|  |  |  `- MageBackendApplication.java
|  |  `- resources/
|  |     |- application.properties
|  |     `- db/migration/
|  `- test/
|     `- java/com/bdmage/mage_backend/
|        |- controller/
|        |- service/
|        |- support/
|        `- MageBackendApplicationTests.java
|- .dockerignore
|- .env.example
|- docker-compose.yml
|- Dockerfile
|- pom.xml
|- mvnw
|- mvnw.cmd
`- README.md
```

## Getting Started

### Prerequisites

- Docker Desktop installed for the default local setup
- Git installed

### Run with Docker

Before the first Docker run, copy the example env file:

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Windows PowerShell, macOS, or Linux:

```bash
docker compose up --build
```

This is the default local development workflow for this repository.

The local development stack starts:

- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

Useful backend checks once the app is up:

- `http://localhost:8080/health`
- `http://localhost:8080/ready`

For the full Docker workflow, see [docs/development/docker.md](docs/development/docker.md).

## Configuration

Current application config in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
spring.application.name=${SPRING_APPLICATION_NAME:mage-backend}
server.port=${SERVER_PORT:8080}
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.flyway.locations=classpath:db/migration
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:validate}
```

Environment variables currently documented for local development:

- `SPRING_APPLICATION_NAME`
- `SERVER_PORT`
- `SPRING_PROFILES_ACTIVE`
- `SPRING_JPA_HIBERNATE_DDL_AUTO`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

## Database Migrations

Flyway runs automatically during application startup against the configured PostgreSQL database.

Migration files live in [src/main/resources/db/migration](src/main/resources/db/migration) and use the standard versioned naming format:

```text
V1__initial_baseline.sql
V2__create_users_table.sql
V3__add_user_status.sql
```

Practical rule:

- Add schema changes through new versioned SQL files instead of relying on Hibernate to mutate the schema
- Keep `spring.jpa.hibernate.ddl-auto` at `validate` unless you have a very specific temporary reason to override it
- If your local Docker database has stale state from before Flyway was added, reset it with `docker compose down -v`

## Recommended Next Structure

When features start getting added, this is a reasonable package layout to follow:

```text
com.bdmage.mage_backend
|- config
|- controller
|- service
|- repository
|- model
|- dto
|- exception
`- util
```

This keeps responsibilities clear and makes it easier for new contributors to find the right place for a change.
