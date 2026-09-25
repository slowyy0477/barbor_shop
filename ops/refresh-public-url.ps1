<#
  Finds or re-opens the free phone link for the running salon server and saves it
  in ops\salon-public-url.txt. A link is only accepted after it really answers,
  so a dead or made-up address is never handed to the phones.
#>
$ErrorActionPreference = "SilentlyContinue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$opsDir = Join-Path $root "ops"
$statePath = Join-Path $opsDir "salon-server.state.json"
$urlPath = Join-Path $opsDir "salon-public-url.txt"
$logDir = Join-Path $root "tmp"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

$reservedHosts = @("api.trycloudflare.com", "www.trycloudflare.com", "developers.cloudflare.com", "blog.cloudflare.com", "trycloudflare.com")

function Test-TunnelCandidate([string]$candidate) {
    if (-not $candidate) { return $false }
    $hostName = ""
    try { $hostName = ([uri]$candidate).Host.ToLowerInvariant() } catch { return $false }
    if ($reservedHosts -contains $hostName) { return $false }
    if ($hostName -notlike "*.trycloudflare.com") { return $false }
    try {
        $probe = Invoke-WebRequest -UseBasicParsing -Uri "$candidate/actuator/health" -TimeoutSec 6
        # This Windows PowerShell returns the body as a byte array, so it has to
        # be decoded before the "UP" marker can be checked.
        $text = if ($probe.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($probe.Content) } else { [string]$probe.Content }
        return ($probe.StatusCode -eq 200 -and $text -match "UP")
    } catch { return $false }
}

$state = $null
if (Test-Path $statePath) { try { $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json } catch { $state = $null } }
$port = if ($state -and $state.port) { $state.port } else { 8080 }

# 1. An already working link is reused.
if ($state -and $state.tunnelUrl -and (Test-TunnelCandidate $state.tunnelUrl)) {
    Set-Content -LiteralPath $urlPath -Value $state.tunnelUrl -Encoding ASCII
    Write-Host "Phone link is working: $($state.tunnelUrl)"
    exit 0
}
if (Test-Path $urlPath) {
    $existing = (Get-Content -Raw -LiteralPath $urlPath).Trim()
    if (Test-TunnelCandidate $existing) {
        Write-Host "Phone link is working: $existing"
        exit 0
    }
}

# 2. Nothing usable, so a fresh free tunnel is opened.
$cloudflared = Join-Path $opsDir "cloudflared.exe"
if (-not (Test-Path $cloudflared)) { Write-Host "cloudflared.exe is missing; run start-salon-server.ps1 once."; exit 1 }

$tunnelLog = Join-Path $logDir "salon-tunnel.log"
$tunnelErrorLog = Join-Path $logDir "salon-tunnel.err.log"
Remove-Item -LiteralPath $tunnelLog -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $tunnelErrorLog -ErrorAction SilentlyContinue
$tunnelProcess = Start-Process -FilePath $cloudflared `
    -ArgumentList @("tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$port") `
    -RedirectStandardOutput $tunnelLog -RedirectStandardError $tunnelErrorLog -WindowStyle Hidden -PassThru

$tunnelUrl = ""
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    Start-Sleep -Seconds 2
    if ($tunnelUrl) { break }
    $candidates = @()
    foreach ($logFile in @($tunnelLog, $tunnelErrorLog)) {
        if (-not (Test-Path $logFile)) { continue }
        $found = Select-String -Path $logFile -Pattern "https://[a-z0-9][a-z0-9-]{2,}\.trycloudflare\.com" -AllMatches -ErrorAction SilentlyContinue
        foreach ($match in $found) { $candidates += ($match.Matches | ForEach-Object { $_.Value }) }
    }
    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (Test-TunnelCandidate $candidate) { $tunnelUrl = $candidate; break }
    }
}

if (-not $tunnelUrl) {
    Stop-Process -Id $tunnelProcess.Id -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $urlPath -ErrorAction SilentlyContinue
    if ($state) {
        $state.tunnelUrl = ""
        $state.tunnelPid = $null
        $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
    }
    Write-Host "The free internet link is not available right now."
    Write-Host "Phones on the salon Wi-Fi can still use the laptop address from status-salon-server.cmd."
    exit 1
}

Set-Content -LiteralPath $urlPath -Value $tunnelUrl -Encoding ASCII
if ($state) {
    $state.tunnelUrl = $tunnelUrl
    $state.tunnelPid = $tunnelProcess.Id
    $state | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
}
Write-Host "New phone link: $tunnelUrl"
Write-Host "Saved to ops\salon-public-url.txt"
