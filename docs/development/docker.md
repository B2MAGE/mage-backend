# Docker Development

This guide covers the local Docker development setup for this repository.

## 1. Purpose

We use Docker Compose because it gives us a reusable local environment for the backend and PostgreSQL without installing PostgreSQL directly on our machines.

This setup is for local development only. It is not a production deployment guide.

## 2. Services In The Stack

The Compose stack currently starts two services:

- `backend`: the Spring Boot application built from the repo's [Dockerfile](../../Dockerfile)
- `postgres`: a PostgreSQL 16 database for local development

Current startup behavior:

- `postgres` exposes a container health check using `pg_isready`
- `backend` waits for `postgres` to become healthy before startup

The stack also creates one named Docker volume:

- `postgres_data`: persists local database files between restarts

Default ports:

- Backend: `8080`
- PostgreSQL: `5432`

## 3. Prerequisites

- Docker Desktop installed and running
- Git installed

You do not need Maven or PostgreSQL installed globally to use the Docker workflow.

## 4. First-Time Setup

Copy the example env file first:

Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

macOS/Linux:

```bash
cp .env.example .env
```

Then run the stack from the repository root:

```bash
docker compose up --build
```

The `.env` file is used by Docker Compose for local variable substitution. The Java application itself still reads standard environment variables.

This command:

- Builds the backend image from the current source
- Pulls the PostgreSQL image if needed
- Starts both containers
- Waits for PostgreSQL readiness before starting the backend
- Attaches to the logs

## 5. Running The Stack

Start the stack in the foreground:

```bash
docker compose up --build
```

Start the stack in the background:

```bash
docker compose up --build -d
```

Check container status:

```bash
docker compose ps
```

When the stack is healthy, PostgreSQL should report a healthy status and the backend should remain up.
Once startup completes, `/health` and `/ready` should both return `200 OK`.

View logs:

```bash
docker compose logs -f
```

View backend logs only:

```bash
docker compose logs -f backend
```

View PostgreSQL logs only:

```bash
docker compose logs -f postgres
```

## 6. Stopping The Stack

Stop and remove containers and the default network:

```bash
docker compose down
```

## 7. Resetting Local Data

If you want a clean PostgreSQL state, remove the volume too:

```bash
docker compose down -v
```

This deletes the `postgres_data` volume and wipes the local database contents.

## 8. Environment Variables And Configuration

The backend container gets its datasource settings from [docker-compose.yml](../../docker-compose.yml), which maps into [src/main/resources/application.properties](../../src/main/resources/application.properties).

Local Docker setup uses values from `.env`. Start by copying [.env.example](../../.env.example) to `.env` and then adjust the values if needed.

Current PostgreSQL settings:

- Host: `postgres`
- Port: `5432`
- Database: from `POSTGRES_DB`
- Username: from `POSTGRES_USER`
- Password: from `POSTGRES_PASSWORD`

Current documented environment variables:

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

## 9. Verifying Everything Works

After startup:

1. Check that both containers are running.

```bash
docker compose ps
```

The PostgreSQL service should become healthy before the backend starts.

2. Check that the backend started successfully.

Look for lines showing:

- Tomcat started on port `8080`
- Hikari connected to PostgreSQL

3. Check the backend liveness endpoint:

```bash
curl http://localhost:8080/health
```

Expected response:

```json
{"status":"UP"}
```

4. Check the backend readiness endpoint:

```bash
curl http://localhost:8080/ready
```

Expected response once PostgreSQL is reachable:

```json
{"status":"UP","database":"UP"}
```

If `/ready` returns `503`, the backend is up but not ready to serve traffic yet. Check the backend logs and PostgreSQL health status.

## 10. Troubleshooting

### Port already in use

If `8080` or `5432` is already in use, stop the conflicting process or change the port mapping in [docker-compose.yml](../../docker-compose.yml).

### Backend fails on datasource startup

Check:

- PostgreSQL container is running
- PostgreSQL health check is passing
- `SPRING_DATASOURCE_*` values from `.env` match the PostgreSQL settings used by [docker-compose.yml](../../docker-compose.yml)
- The backend depends on the PostgreSQL service health status before startup

### PostgreSQL stays unhealthy

Check the PostgreSQL logs:

```bash
docker compose logs -f postgres
```

If needed, reset the local volume and start again:

```bash
docker compose down -v
docker compose up --build
```

### Rebuild after source changes

If the image seems stale, rebuild:

```bash
docker compose up --build
```

### Clean reset

If the database state is causing problems, reset everything:

```bash
docker compose down -v
docker compose up --build
```

## 11. Future Improvements

As this project grows, this guide can expand to cover:

- Wiring the backend container health check to `/ready`
- Database migrations
- Seed data
- Alternate Spring profiles
- Admin tools such as pgAdmin
- Test-specific Compose overrides
