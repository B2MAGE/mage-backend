param([switch]$BuildImage)

$ErrorActionPreference = 'Stop'
$mageRepo = Split-Path -Parent $PSScriptRoot
$mageImage = 'mage/minio-client:verification-7394ce0'

Push-Location $mageRepo
try {
    # Do not interpolate or print deployment credentials. This does not start services.
    $mageConfigJson = docker compose --project-name mage-minio-client-check -f docker-compose.coolify.yml config --no-interpolate --format json
    if ($LASTEXITCODE -ne 0) { throw 'Coolify Compose configuration is invalid.' }
    $mageConfig = $mageConfigJson | ConvertFrom-Json
    $mageInit = $mageConfig.services.'minio-init'
    if ($mageInit.image) { throw 'The initializer must be built locally, not pulled from a registry.' }
    if ($mageInit.build.target -ne 'client-runtime') { throw 'The initializer must use the client-only runtime.' }
    if ((Resolve-Path $mageInit.build.context).Path -ne (Join-Path $mageRepo 'docker\minio')) {
        throw 'The initializer must reuse the existing pinned MinIO build context.'
    }
    if (($mageInit.entrypoint[0] -ne '/bin/sh') -or ($mageInit.entrypoint[1] -ne '-ec')) {
        throw 'The initializer needs the shell entrypoint, not the mc default entrypoint.'
    }
    foreach ($mageCommand in @('mc alias set local', 'mc mb --ignore-existing', 'mc anonymous set download')) {
        if (-not $mageInit.entrypoint[2].Contains($mageCommand)) { throw "Missing initializer command: $mageCommand" }
    }
    if ($mageConfig.services.backend.depends_on.'minio-init'.condition -ne 'service_completed_successfully') {
        throw 'The API must wait until the thumbnail bucket is initialized.'
    }
    if ($mageConfig.services.minio.image -ne 'minio/minio:latest') { throw 'The storage server image has unexpectedly changed.' }
    if ($mageConfig.services.minio.volumes[0].source -ne 'minio_data') { throw 'The production MinIO volume has unexpectedly changed.' }
    if ($mageConfig.services.postgres.volumes[0].source -ne 'postgres_data') { throw 'The production database volume has unexpectedly changed.' }
    Write-Output 'Coolify MinIO configuration checks passed.'

    if ($BuildImage) {
        docker build --target client-runtime --tag $mageImage --file docker/minio/Dockerfile docker/minio
        if ($LASTEXITCODE -ne 0) { throw 'The MinIO client image did not build.' }
        # Only ephemeral client help/version commands; no network, app services, or host volumes.
        docker run --rm --network none --read-only --tmpfs /root/.mc --entrypoint /bin/sh $mageImage -ec 'mc --version; mc mb --help >/dev/null; mc anonymous set --help >/dev/null; mc cors set --help >/dev/null; test -s /etc/ssl/certs/ca-certificates.crt; ! command -v minio'
        if ($LASTEXITCODE -ne 0) { throw 'The isolated MinIO client smoke test failed.' }
        Write-Output 'Client-only image and isolated command smoke checks passed.'
    }
} finally {
    Pop-Location
}
