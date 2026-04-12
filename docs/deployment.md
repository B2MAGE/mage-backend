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
| `AWS_REGION` | AWS region used by the S3 client and presigner. |
| `MAGE_THUMBNAIL_BUCKET` | Private S3 bucket used for preset thumbnails. |
| `MAGE_THUMBNAIL_KEY_PREFIX` | Object key prefix for thumbnail uploads. Defaults to `presets`. |
| `MAGE_THUMBNAIL_PUBLIC_BASE_URL` | Public CloudFront or CDN base URL persisted into `thumbnailRef`. |
| `MAGE_THUMBNAIL_ALLOWED_CONTENT_TYPES` | Comma-separated thumbnail content-type allowlist. |
| `MAGE_THUMBNAIL_MAX_BYTES` | Maximum allowed thumbnail size in bytes. |
| `MAGE_THUMBNAIL_PRESIGN_DURATION` | Presigned upload lifetime, such as `PT10M`. |

The backend will fail fast if required datasource values, Google auth settings, or thumbnail storage settings are missing.

## Thumbnail Upload Infrastructure

Preset thumbnails are no longer stored on local container disk.

The production path is:

1. backend issues a presigned S3 upload for the preset owner
2. the browser uploads the file directly to S3
3. the backend finalizes the object and stores the public CloudFront-backed URL in `thumbnailRef`

Recommended production setup:
- keep the S3 bucket private
- put CloudFront in front of the bucket
- set `MAGE_THUMBNAIL_PUBLIC_BASE_URL` to the CloudFront distribution domain
- use an EC2 IAM role for backend AWS access instead of static AWS keys

Local Docker note:
- if you run the backend on a developer machine instead of on the EC2 host, the container will not inherit the production IAM role
- provide `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, and `AWS_SESSION_TOKEN` through local environment variables when testing presigned uploads outside EC2

Operational requirement:
- the S3 bucket must allow browser `PUT` uploads from the frontend origins through bucket CORS

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
