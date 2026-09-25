param(
    [string]$OutputDirectory = "E:\AyanSalon-Private"
)

$ErrorActionPreference = "Stop"
$resolved = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $resolved | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$target = Join-Path $resolved ("ayan-salon-" + $stamp + ".dump")

docker compose exec -T db pg_dump --format=custom --no-owner --no-acl `
  --username="$env:POSTGRES_USER" --dbname="$env:POSTGRES_DB" | Set-Content -Encoding Byte -Path $target
if ($LASTEXITCODE -ne 0) { throw "pg_dump failed" }
Write-Output "Backup written to $target"
