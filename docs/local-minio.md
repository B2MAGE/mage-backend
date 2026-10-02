# Local MinIO for MAGE Pulse

The local Compose override builds the AGPL MinIO community server and client from
pinned official source commits. It does not use AIStor, require an activation key,
or depend on the removed Docker Hub images.

## Start

From this repository:

```powershell
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml build minio
docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml up -d
```

The first build downloads and compiles Go dependencies. Later starts reuse the
local image. Both the server and bucket initializer use that same image.
The backend waits until the thumbnail bucket is successfully initialized.

- S3 API: http://localhost:9000
- Object browser: http://localhost:9001
- Credentials: the existing MAGE_THUMBNAIL_MINIO_ROOT_USER and
  MAGE_THUMBNAIL_MINIO_ROOT_PASSWORD settings in your ignored local .env file.

Only loopback ports are exposed. The new minio_community_data volume starts fresh
and does not overwrite the previous minio_data volume from AIStor.

## Version ownership

- Server RELEASE.2025-10-15T17-29-55Z:
  9e49d5e7a648f00e26f2246f4dc28e6b07f8c84a
- Client RELEASE.2025-08-13T08-35-41Z:
  7394ce0dd2a80935aded936b09fa12cbb3cb8096

This is a local development setup. The upstream community repository is archived
and no longer maintained; this is not a production upgrade recommendation.
AIStor is a separate distribution and can also use a vendor-issued free
single-node license, but that activation workflow is not needed here.
