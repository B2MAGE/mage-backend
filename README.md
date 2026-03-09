# MAGE Backend

Backend service for the MAGE platform.

Start here:

- New to Spring Boot: read [docs/TEAM_GUIDE.md](docs/TEAM_GUIDE.md)
- Want to run the project with Docker: read [docs/development/docker.md](docs/development/docker.md)
- Want to run the project without Docker: use the commands in `Getting Started`
- Want to know what already exists: read `Current Repo State`

## Current Repo State

As of March 9, 2026, this repository contains:

- A single Spring Boot application entry point in [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](src/main/java/com/bdmage/mage_backend/MageBackendApplication.java)
- Custom PostgreSQL datasource validation and startup wiring in [src/main/java/com/bdmage/mage_backend/config/DatabaseConfiguration.java](src/main/java/com/bdmage/mage_backend/config/DatabaseConfiguration.java)
- Health endpoints at `/health` and `/ready`
- Real PostgreSQL-backed integration tests via Testcontainers
- Application config in [src/main/resources/application.properties](src/main/resources/application.properties)
- A Docker image build in [Dockerfile](Dockerfile)
- A local development stack in [docker-compose.yml](docker-compose.yml)
- Maven wrapper scripts so the team can build without installing Maven globally

## Tech Stack

- Java 21
- Spring Boot 4.0.3
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- Spring Boot DevTools
- Lombok
- JUnit 5 with Spring Boot test support
- Maven Wrapper (`mvnw`, `mvnw.cmd`)
- Docker / Docker Compose for local development

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
|  |     `- application.properties
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
- JDK 21 installed if you want to run the app directly with Maven
- Git installed

You do not need Maven installed globally because this repo includes the Maven wrapper.

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

The `.env` file is only a Docker Compose convenience for local development. The Spring Boot app still reads normal environment variables.

### Run the app without Docker

If you want to run the Spring Boot app directly, you must point it at a reachable PostgreSQL instance.

One simple option is to start only PostgreSQL with Docker and run the backend with Maven.

Windows PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/mage"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="change-me"
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
docker compose up -d postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mage \
SPRING_DATASOURCE_USERNAME=postgres \
SPRING_DATASOURCE_PASSWORD=change-me \
./mvnw spring-boot:run
```

If you changed the values in `.env`, use matching datasource values when you run Maven directly.

Any other reachable PostgreSQL instance also works as long as the datasource environment variables match it.

### Run tests

Windows PowerShell:

```powershell
.\mvnw.cmd test
```

macOS/Linux:

```bash
./mvnw test
```

The test suite starts PostgreSQL with Testcontainers, so Docker Desktop must be running.

### Build a jar

Windows PowerShell:

```powershell
.\mvnw.cmd clean package
```

macOS/Linux:

```bash
./mvnw clean package
```

## How The App Starts

The application boots from [src/main/java/com/bdmage/mage_backend/MageBackendApplication.java](src/main/java/com/bdmage/mage_backend/MageBackendApplication.java).

- `@SpringBootApplication` tells Spring Boot to start auto-configuration and component scanning
- `SpringApplication.run(...)` starts the application
- Spring scans classes under the `com.bdmage.mage_backend` package

That package choice matters. If you add new controllers, services, or configuration classes, keep them under `com.bdmage.mage_backend` unless you intentionally change the scan setup.

## Configuration

Current application config in [src/main/resources/application.properties](src/main/resources/application.properties):

```properties
spring.application.name=${SPRING_APPLICATION_NAME:mage-backend}
server.port=${SERVER_PORT:8080}
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.jpa.hibernate.ddl-auto=${SPRING_JPA_HIBERNATE_DDL_AUTO:update}
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
