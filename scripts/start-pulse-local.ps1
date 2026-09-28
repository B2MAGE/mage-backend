param([string]$DatabaseVolume = 'mage-pulse-review-postgres-v1')
$ErrorActionPreference = 'Stop'
if ($DatabaseVolume -notmatch '^mage-pulse-review-[a-z0-9-]+$') {
    throw 'Use a local volume name beginning with mage-pulse-review-.'
}
$repoPath = Split-Path -Parent $PSScriptRoot
$previousVolume = $env:MAGE_PULSE_DATABASE_VOLUME
Push-Location $repoPath
try {
    $env:MAGE_PULSE_DATABASE_VOLUME = $DatabaseVolume
    docker compose -f docker-compose.yml -f docker-compose.local.yml -f docker-compose.minio.yml -f docker-compose.pulse.yml up --build --detach
    if ($LASTEXITCODE -ne 0) { throw 'The local Docker stack did not start.' }
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        try {
            $response = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:8080/api/scenes' -TimeoutSec 2
            if ($response.StatusCode -eq 200) { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'The backend did not become ready. Check docker compose logs backend.' }
    $reviewScenes = @($response.Content | ConvertFrom-Json)
    if ($reviewScenes.Count -eq 0) {
        node scripts/seed-pulse-local.mjs
        if ($LASTEXITCODE -ne 0) { throw 'The local demo seed did not complete.' }
    } else {
        Write-Host ('Ready: preserving {0} existing review scenes; no seed changes were made.' -f $reviewScenes.Count)
    }
} finally {
    $env:MAGE_PULSE_DATABASE_VOLUME = $previousVolume
    Pop-Location
}

