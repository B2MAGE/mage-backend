# Deploying MAGE

This guide deploys the full MAGE platform:

- `mage-frontend`: the React/Vite frontend, built with `mage-frontend/Dockerfile` and served by nginx on container port `80`
- `mage-backend`: the Spring Boot API, built with `mage-backend/Dockerfile` and served on container port `8080`
- PostgreSQL: the backend database, deployed as a separate container or Coolify resource alongside the backend
- MinIO: object storage required for scene thumbnail uploads

The supported production shape is same-origin:

```text
https://mage.example.com/        -> frontend
https://mage.example.com/api/*   -> backend
https://storage.example.com/*    -> MinIO thumbnail storage
```

The frontend should be built with `VITE_API_BASE_URL=/api` or left unset. Using `/api` keeps browser API requests on the same public origin as the frontend.

## Before You Start

Prepare these values first:

| Value | Example | Notes |
| --- | --- | --- |
| App domain | `mage.example.com` | Points to the server or Coolify proxy. |
| Storage domain | `storage.example.com` | Used for browser thumbnail uploads and public thumbnail URLs. |
| Google client ID | `abc.apps.googleusercontent.com` | Goes in `MAGE_AUTH_GOOGLE_CLIENT_IDS`. |
| PostgreSQL password | strong generated value | Do not reuse the local `.env.example` password. |
| Thumbnail bucket | `mage-thumbnails` | MinIO bucket name. |
| SMTP values | Brevo or another SMTP provider | Optional, only needed for deployed password reset email. |

This guide assumes MinIO for thumbnail storage. In the Compose deployments, the backend receives `MAGE_THUMBNAIL_PROVIDER=minio` from the compose file and uses the `MAGE_THUMBNAIL_MINIO_*` values you provide.

## Docker Compose On Linux

Use this when you are deploying directly on a Linux server with Docker, without Coolify. This is the terminal equivalent of the Coolify backend service stack: Docker Compose starts `backend`, `postgres`, `minio`, and `minio-init` from `mage-backend/docker-compose.coolify.yml`. The frontend and Caddy reverse proxy then join the same Docker network.

Requirements:

- Docker Engine and the Docker Compose plugin installed on the Linux server
- ports `80` and `443` open in the server firewall/security group
- DNS records for `APP_DOMAIN` and `STORAGE_DOMAIN` pointing to the server
- the backend and frontend repositories available on the server

```bash
# 1. Get the code.
mkdir -p MAGE
cd MAGE
git clone https://github.com/B2MAGE/mage-backend.git mage-backend
git clone https://github.com/B2MAGE/mage-frontend.git mage-frontend

# 2. Set deployment values for this shell session.
export APP_DOMAIN="mage.example.com"
export STORAGE_DOMAIN="storage.example.com"
export POSTGRES_DB="mage"
export POSTGRES_USER="mage"
export POSTGRES_PASSWORD="replace-with-a-strong-database-password"
export MINIO_ROOT_USER="replace-with-a-minio-user"
export MINIO_ROOT_PASSWORD="replace-with-a-strong-minio-password"
export MAGE_THUMBNAIL_BUCKET="mage-thumbnails"
export GOOGLE_CLIENT_IDS="replace-with-your-google-client-id.apps.googleusercontent.com"

# 3. Create the backend stack environment file.
# This is read by mage-backend/docker-compose.coolify.yml.
cat > mage-backend/.env <<EOF
POSTGRES_DB=${POSTGRES_DB}
POSTGRES_USER=${POSTGRES_USER}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}

SPRING_APPLICATION_NAME=mage-backend
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=default
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
MAGE_AUTH_GOOGLE_CLIENT_IDS=${GOOGLE_CLIENT_IDS}

MAGE_THUMBNAIL_MINIO_ROOT_USER=${MINIO_ROOT_USER}
MAGE_THUMBNAIL_MINIO_ROOT_PASSWORD=${MINIO_ROOT_PASSWORD}
MAGE_THUMBNAIL_MINIO_BUCKET=${MAGE_THUMBNAIL_BUCKET}
MAGE_THUMBNAIL_MINIO_REGION=us-east-1
MAGE_THUMBNAIL_MINIO_ENDPOINT=http://minio:9000
MAGE_THUMBNAIL_MINIO_PRESIGN_ENDPOINT=https://${STORAGE_DOMAIN}
MAGE_THUMBNAIL_MINIO_ACCESS_KEY_ID=${MINIO_ROOT_USER}
MAGE_THUMBNAIL_MINIO_SECRET_ACCESS_KEY=${MINIO_ROOT_PASSWORD}
MAGE_THUMBNAIL_MINIO_PUBLIC_BASE_URL=https://${STORAGE_DOMAIN}/${MAGE_THUMBNAIL_BUCKET}
MAGE_THUMBNAIL_KEY_PREFIX=scenes
MAGE_THUMBNAIL_ALLOWED_CONTENT_TYPES=image/jpeg,image/png,image/webp,image/gif
MAGE_THUMBNAIL_MAX_BYTES=5242880
MAGE_THUMBNAIL_PRESIGN_DURATION=PT10M

MAGE_PASSWORD_RESET_DELIVERY=log
MAGE_PASSWORD_RESET_FRONTEND_BASE_URL=https://${APP_DOMAIN}
EOF

# 4. Start the backend stack.
# This creates the backend, postgres, minio, and minio-init containers.
cd mage-backend
docker compose --project-name mage --env-file .env -f docker-compose.coolify.yml up -d --build
cd ..

# 5. Create or update the MinIO bucket CORS rules.
# The compose stack creates the bucket and enables public download; this adds browser PUT upload access.
cat >/tmp/mage-minio-cors.xml <<EOF
<CORSConfiguration>
  <CORSRule>
    <AllowedOrigin>https://${APP_DOMAIN}</AllowedOrigin>
    <AllowedMethod>GET</AllowedMethod>
    <AllowedMethod>PUT</AllowedMethod>
    <AllowedMethod>HEAD</AllowedMethod>
    <AllowedHeader>*</AllowedHeader>
    <ExposeHeader>ETag</ExposeHeader>
  </CORSRule>
</CORSConfiguration>
EOF

docker compose --project-name mage --env-file mage-backend/.env \
  -f mage-backend/docker-compose.coolify.yml run --rm --no-deps --entrypoint sh \
  -v /tmp/mage-minio-cors.xml:/cors.xml:ro \
  -e MINIO_ROOT_USER="$MINIO_ROOT_USER" \
  -e MINIO_ROOT_PASSWORD="$MINIO_ROOT_PASSWORD" \
  -e MAGE_THUMBNAIL_BUCKET="$MAGE_THUMBNAIL_BUCKET" \
  minio-init -ec '
    until mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"; do sleep 2; done
    mc mb --ignore-existing "local/$MAGE_THUMBNAIL_BUCKET"
    mc anonymous set download "local/$MAGE_THUMBNAIL_BUCKET"
    mc cors set "local/$MAGE_THUMBNAIL_BUCKET" /cors.xml
  '

# 6. Build and start the frontend on the same Docker network as the backend stack.
docker build -t mage-frontend:prod --build-arg VITE_API_BASE_URL=/api ./mage-frontend
docker rm -f mage-frontend || true
docker run -d --name mage-frontend \
  --network mage_default \
  --restart unless-stopped \
  mage-frontend:prod

# 7. Create the Caddy reverse proxy config.
# /api is sent to the backend. Everything else goes to the frontend.
cat > Caddyfile <<EOF
${APP_DOMAIN} {
  handle /api* {
    reverse_proxy backend:8080
  }

  handle /health {
    reverse_proxy backend:8080
  }

  handle /ready {
    reverse_proxy backend:8080
  }

  handle {
    reverse_proxy mage-frontend:80
  }
}

${STORAGE_DOMAIN} {
  reverse_proxy minio:9000
}
EOF

# 8. Start Caddy. Caddy will request HTTPS certificates automatically.
docker rm -f mage-caddy || true
docker volume create mage-caddy-data
docker volume create mage-caddy-config
docker run -d --name mage-caddy \
  --network mage_default \
  --restart unless-stopped \
  -p 80:80 \
  -p 443:443 \
  -v "$PWD/Caddyfile:/etc/caddy/Caddyfile:ro" \
  -v mage-caddy-data:/data \
  -v mage-caddy-config:/config \
  caddy:2-alpine

# 9. Verify the deployment.
curl -I "https://${APP_DOMAIN}/"
curl -f "https://${APP_DOMAIN}/ready"
docker compose --project-name mage --env-file mage-backend/.env -f mage-backend/docker-compose.coolify.yml ps
docker logs --tail 100 mage-backend
```

After this finishes, open `https://mage.example.com` in a browser. If login or scene creation fails, check the backend logs first:

```bash
docker logs -f mage-backend
```

## Coolify

Use this when Coolify is installed on the server and you want Coolify to build and run the containers. This path matches a deployment with two Coolify resources:

- one backend service stack from `https://github.com/B2MAGE/mage-backend`
- one frontend application from `https://github.com/B2MAGE/mage-frontend`

The backend service stack uses `docker-compose.coolify.yml`, which contains the backend API, PostgreSQL, MinIO, and a one-time MinIO bucket initializer.

The bucket initializer is built from the pinned MinIO client source in
`docker/minio/Dockerfile` using the `client-runtime` target. It does not pull
`minio/mc:latest` or build/replace the MinIO server. The existing server image,
credentials, bucket setup commands, and persistent volumes are unchanged.
Coolify must build both buildable services (`backend` and `minio-init`) before
starting the stack. The first client build needs access to its Go dependencies
and base images; later builds can reuse the build cache.

With automatic deployments enabled for the connected branch in Coolify, merging
to that branch can trigger a deployment without a GitHub Actions workflow or a
manual deploy. Check Coolify's deployment history, deployed commit, and service
health to confirm success; a successful image build alone is not a successful
deployment.

To validate this configuration and the client-only image locally without starting
the application or touching its data, run
`powershell -File scripts/verify-coolify-minio.ps1 -BuildImage` from the backend
repository. The smoke test has no network access or mounted application volumes.

In this setup, do not create separate Coolify PostgreSQL or MinIO resources. They are services inside the backend stack.

Requirements:

- Coolify installed and connected to your server
- the backend repository connected to Coolify: `https://github.com/B2MAGE/mage-backend`
- the frontend repository connected to Coolify: `https://github.com/B2MAGE/mage-frontend`
- DNS records for the app domain and storage domain pointing to the Coolify server

```text
1. Create the backend service stack in Coolify.

   Source:

   - repository: https://github.com/B2MAGE/mage-backend
   - build pack/resource type: Docker Compose or Service Stack
   - base directory: /
   - compose file: docker-compose.coolify.yml

   This one Coolify resource creates multiple containers:

   - backend
   - postgres
   - minio
   - minio-init

   Domain:

   - assign the backend container to the app domain with the /api path
   - because the backend listens on container port 8080, the Coolify domain field
     may need the internal port before the path:

     https://mage.example.com:8080/api

   If your Coolify version has a separate port field, set the port to 8080 and
   use https://mage.example.com/api for the domain/path. Do not strip the /api
   prefix; the backend routes are already defined under /api.

   Health check:

   - path: /ready
   - port: 8080

2. Add backend service stack environment variables in Coolify.

   PostgreSQL values used by the postgres container:

   POSTGRES_DB=mage
   POSTGRES_USER=mage
   POSTGRES_PASSWORD=<strong-database-password>

   Backend database connection values:

   SERVER_PORT=8080
   SPRING_JPA_HIBERNATE_DDL_AUTO=validate
   SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/mage
   SPRING_DATASOURCE_USERNAME=mage
   SPRING_DATASOURCE_PASSWORD=<same-value-as-POSTGRES_PASSWORD>

   Required auth value:

   MAGE_AUTH_GOOGLE_CLIENT_IDS=<google-client-id.apps.googleusercontent.com>

   MinIO values used by the minio container and backend:

   MAGE_THUMBNAIL_MINIO_ROOT_USER=<strong-minio-user>
   MAGE_THUMBNAIL_MINIO_ROOT_PASSWORD=<strong-minio-password>
   MAGE_THUMBNAIL_MINIO_BUCKET=mage-thumbnails
   MAGE_THUMBNAIL_MINIO_REGION=us-east-1
   MAGE_THUMBNAIL_MINIO_ENDPOINT=http://minio:9000
   MAGE_THUMBNAIL_MINIO_PRESIGN_ENDPOINT=https://storage.example.com
   MAGE_THUMBNAIL_MINIO_ACCESS_KEY_ID=<same-value-as-MAGE_THUMBNAIL_MINIO_ROOT_USER>
   MAGE_THUMBNAIL_MINIO_SECRET_ACCESS_KEY=<same-value-as-MAGE_THUMBNAIL_MINIO_ROOT_PASSWORD>
   MAGE_THUMBNAIL_MINIO_PUBLIC_BASE_URL=https://storage.example.com/mage-thumbnails
   MAGE_THUMBNAIL_KEY_PREFIX=scenes
   MAGE_THUMBNAIL_ALLOWED_CONTENT_TYPES=image/jpeg,image/png,image/webp,image/gif
   MAGE_THUMBNAIL_MAX_BYTES=5242880
   MAGE_THUMBNAIL_PRESIGN_DURATION=PT10M

   Optional password reset email:

   MAGE_PASSWORD_RESET_DELIVERY=smtp
   MAGE_PASSWORD_RESET_FRONTEND_BASE_URL=https://mage.example.com
   MAGE_PASSWORD_RESET_FROM_EMAIL=<verified-sender@example.com>
   MAGE_PASSWORD_RESET_FROM_NAME=MAGE
   MAGE_EMAIL_SMTP_HOST=smtp-relay.brevo.com
   MAGE_EMAIL_SMTP_PORT=587
   MAGE_EMAIL_SMTP_USERNAME=<smtp-username>
   MAGE_EMAIL_SMTP_PASSWORD=<smtp-password>
   MAGE_EMAIL_SMTP_AUTH=true
   MAGE_EMAIL_SMTP_STARTTLS_ENABLE=true

   For a first deployment, you can temporarily use:

   MAGE_PASSWORD_RESET_DELIVERY=log
   MAGE_PASSWORD_RESET_FRONTEND_BASE_URL=https://mage.example.com

3. Expose MinIO through Coolify.

   Assign the minio container to the storage domain:

   https://storage.example.com:9000

   The port tells Coolify to route external HTTPS traffic to MinIO's internal
   port 9000. The public browser URL remains https://storage.example.com.

4. Deploy the backend service stack.

   Wait until the backend stack is healthy before deploying the frontend. The
   stack should start postgres, minio, minio-init, and backend. Use Coolify logs
   to confirm that:

   - postgres became healthy
   - minio started
   - minio-init created the mage-thumbnails bucket
   - backend started and connected to PostgreSQL

   After the first deployment, configure MinIO bucket CORS so the browser can
   upload thumbnails with PUT from:

   https://mage.example.com

   The compose file creates the bucket and enables public downloads, but CORS may
   still need to be set in MinIO depending on your Coolify/MinIO setup.

5. Create the frontend Application in Coolify.

   Source:

   - repository: https://github.com/B2MAGE/mage-frontend
   - build pack: Dockerfile
   - base directory: /
   - Dockerfile: Dockerfile
   - container port: 80

   Domain:

   - https://mage.example.com

   Build-time variable:

   VITE_API_BASE_URL=/api

   The frontend is a static nginx container after it is built, so this value must
   be available during the Docker build. If Coolify offers separate environment
   variable and build argument fields, set it as a build argument.

6. Deploy the frontend.

7. Verify the public app.

   Open:

   https://mage.example.com

   Then test:

   - registration or login
   - public scene discovery
   - create-scene flow
   - thumbnail upload

8. If something fails, check in this order:

   - backend Coolify logs
   - postgres container health inside the backend stack
   - minio and minio-init logs inside the backend stack
   - MAGE_AUTH_GOOGLE_CLIENT_IDS
   - thumbnail bucket CORS
   - thumbnail public base URL
   - frontend build variable VITE_API_BASE_URL=/api
```

## Common Mistakes

- Do not deploy the frontend and backend as unrelated public origins unless you also change the supported CORS/auth model.
- Do not point `VITE_API_BASE_URL` at `http://localhost:8080` in production.
- Do not expose PostgreSQL publicly unless there is a specific operational reason.
- Do not use the passwords from `.env.example` in production.
- Do not skip MinIO bucket CORS. Thumbnail upload uses browser `PUT` requests directly to MinIO.
- Do not expect the frontend container to proxy `/api`. The external proxy, Coolify proxy, or Caddy must route `/api/*` to the backend.
- Do not strip `/api` in the reverse proxy. The backend expects request paths like `/api/auth/login`.

## Related Docs

- [Backend deployment notes](./deployment.md)
- [Frontend deployment notes](https://github.com/B2MAGE/mage-frontend/blob/main/docs/deployment.md)
- [Backend operations runbook](./operations.md)
- [Backend local Docker Compose setup](./getting-started.md)

## Local Review And Content Recovery

The production deployment above does not publish data from a developer's local database. For isolated restart-safe review, use [Pulse local review](./pulse-local-review.md). The [quality catalogue workflow](../scripts/QUALITY_REVIEW.md) explains read-only validation, initial seeding, capture checks, and resumable imports. The thumbnail refresh section in the local-review guide documents backups and recovery. Keep demo-only passwords and content out of production accounts.
