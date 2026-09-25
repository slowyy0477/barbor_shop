param(
    [Parameter(Mandatory = $true)]
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"
$resolved = [System.IO.Path]::GetFullPath($BackupFile)
if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) { throw "Backup file not found: $resolved" }
Write-Warning "Restore replaces data in the configured database. Confirm the target and keep a current backup."
Get-Content -LiteralPath $resolved -Encoding Byte -Raw | docker compose exec -T db pg_restore --clean --if-exists --no-owner --no-acl --username="$env:POSTGRES_USER" --dbname="$env:POSTGRES_DB"
if ($LASTEXITCODE -ne 0) { throw "pg_restore failed" }
Write-Output "Restore completed from $resolved"
