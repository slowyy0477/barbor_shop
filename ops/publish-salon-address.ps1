<#
  Publishes the current salon address to GitHub so the permanent online link

      https://slowyy0477.github.io/barbor_shop/

  always opens the salon app and can find this laptop while it is switched on.
  The address is written to api.json and pushed in one small commit.

  Called automatically every time the free Cloudflare tunnel is opened or
  refreshed. It never stops the salon: if GitHub or the internet is unreachable
  the server, the database and the current phone address keep working.
#>
param(
    [Parameter(Mandatory = $true)][string]$Url
)
$ErrorActionPreference = "SilentlyContinue"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$configPath = Join-Path $root "api.json"
$defaultSalonId = "00000000-0000-0000-0000-000000000001"

function Get-SalonId {
    if (Test-Path -LiteralPath $configPath) {
        try {
            $existing = Get-Content -Raw -LiteralPath $configPath | ConvertFrom-Json
            if ($existing.salonId) { return [string]$existing.salonId }
        } catch { }
    }
    return $defaultSalonId
}

function Set-PublishedAddress([string]$value) {
    $payload = [ordered]@{
        salonId     = (Get-SalonId)
        apiBaseUrl  = [string]$value
        updatedAt   = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        publishedBy = "ops/publish-salon-address.ps1"
    }
    $json = ($payload | ConvertTo-Json) + "`n"
    [System.IO.File]::WriteAllText($configPath, $json, (New-Object System.Text.UTF8Encoding($false)))
}

Set-PublishedAddress $Url

if (-not (Get-Command git -ErrorAction SilentlyContinue)) { exit 0 }
if (-not (Test-Path -LiteralPath (Join-Path $root ".git"))) { exit 0 }

$env:GIT_TERMINAL_PROMPT = "0"
$env:GCM_INTERACTIVE = "never"

git -C $root add -- api.json 2>&1 | Out-Null
$changed = git -C $root status --porcelain -- api.json
if (-not $changed) { exit 0 }

git -C $root -c user.name="Ayan Salon" -c user.email="slowyy0477@users.noreply.github.com" `
    commit -q -m "Publish salon address $Url" -- api.json 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) { exit 0 }

git -C $root push -q origin HEAD:main 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    # Someone else committed to the repository in the meantime: replay on top
    # once, then push again. Other local edits are stashed and restored.
    git -C $root pull --rebase --autostash -q origin main 2>&1 | Out-Null
    git -C $root push -q origin HEAD:main 2>&1 | Out-Null
}
exit 0
