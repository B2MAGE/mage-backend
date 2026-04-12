# Backend Deployment

This backend is intended to be deployed behind a same-origin reverse proxy.

## Supported Production Contract

The public deployment model is:

- the frontend is served from the public app origin
- `/api/*` is routed to the backend service
- browser auth traffic stays on the same HTTPS origin
- CORS is not required for the supported path

Example:

```text
https://mage.example.com/        -> frontend
https://mage.example.com/api/*   -> backend
```

## Required Environment Variables

The backend must receive:

| Variable | Purpose |
| --- | --- |
| `SERVER_PORT` | Application port. Defaults to `8080`. |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | Database username. |
| `SPRING_DATASOURCE_PASSWORD` | Database password. |
| `MAGE_AUTH_GOOGLE_CLIENT_IDS` | Allowed Google OAuth client IDs for Google sign-in validation. |

The backend will fail fast if required datasource values or `MAGE_AUTH_GOOGLE_CLIENT_IDS` are missing.

## Reverse Proxy Expectations

The reverse proxy should:

- route `/api/*` to the backend service
- keep the frontend on the public app origin for all non-API browser routes
- terminate HTTPS for browser-facing traffic

This repository does not currently treat split-origin frontend/backend deployment as the primary path.

## Container Notes

The backend repo includes a production `Dockerfile`.

It:
- builds the app with the Maven wrapper
- packages the Spring Boot jar
- runs the jar on a JRE image

For Linux-based builders such as Coolify, the Dockerfile explicitly sets execute permission on `mvnw` during the build so it works reliably even when the repo was created on Windows.

## Local Development

None of this replaces the local Docker Compose workflow in this repo.

For local development:
- keep using `.env`
- keep using `docker compose up --build`
- keep using the frontend Vite proxy or local same-origin-equivalent paths during development
