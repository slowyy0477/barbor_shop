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
$publishScript = Join-Path $opsDir "publish-salon-address.ps1"

# The permanent customer link is served from GitHub Pages and learns the current
# tunnel address from api.json, so every refreshed address is published once.
function Publish-SalonAddress([string]$value) {
    if (-not $value) { return }
    if (Test-Path -LiteralPath $publishScript) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $publishScript -Url $value | Out-Null
    }
}

$reservedHosts = @("api.trycloudflare.com", "www.trycloudflare.com", "developers.cloudflare.com", "blog.cloudflare.com", "trycloudflare.com")

function Test-TunnelCandidate([string]$candidate) {
    if (-not $candidate) { return $false }
    $hostName = ""
    try { $hostName = ([uri]$candidate).Host.ToLowerInvariant() } catch { return $false }
    if ($reservedHosts -contains $hostName) { return $false }
    # Cloudflare is the first choice; serveo.net and localhost.run are free
    # backups that also work on networks which block their SSH port 22.
    if ($hostName -notlike "*.trycloudflare.com" -and
        $hostName -notlike "*.serveousercontent.com" -and
        $hostName -notlike "*.serveo.net" -and
        $hostName -notlike "*.lhr.life") { return $false }
    # A brand new quick tunnel can need about twenty seconds before its first
    # request is answered, so the first probe waits patiently and later ones are
    # quick. Without this a good link can be thrown away as "not available".
    foreach ($probeTimeout in @(25, 12, 8)) {
        try {
            $probe = Invoke-WebRequest -UseBasicParsing -Uri "$candidate/actuator/health" -TimeoutSec $probeTimeout
            # This Windows PowerShell returns the body as a byte array, so it has to
            # be decoded before the "UP" marker can be checked.
            $text = if ($probe.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($probe.Content) } else { [string]$probe.Content }
            if ($probe.StatusCode -eq 200 -and $text -match "UP") { return $true }
        } catch { }
    }
    return $false
}

$state = $null
if (Test-Path $statePath) { try { $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json } catch { $state = $null } }
$port = if ($state -and $state.port) { $state.port } else { 8080 }

# 1. An already working link is reused.
if ($state -and $state.tunnelUrl -and (Test-TunnelCandidate $state.tunnelUrl)) {
    Set-Content -LiteralPath $urlPath -Value $state.tunnelUrl -Encoding ASCII
    Write-Host "Phone link is working: $($state.tunnelUrl)"
    Publish-SalonAddress $state.tunnelUrl
    exit 0
}
if (Test-Path $urlPath) {
    $existing = (Get-Content -Raw -LiteralPath $urlPath).Trim()
    if (Test-TunnelCandidate $existing) {
        Write-Host "Phone link is working: $existing"
        Publish-SalonAddress $existing
        exit 0
    }
}

# 2. Nothing usable, so a fresh free tunnel is opened.
$cloudflared = Join-Path $opsDir "cloudflared.exe"
if (-not (Test-Path $cloudflared)) { Write-Host "cloudflared.exe is missing; run start-salon-server.ps1 once."; exit 1 }

$tunnelLog = Join-Path $logDir "salon-tunnel.log"
$tunnelErrorLog = Join-Path $logDir "salon-tunnel.err.log"
$tunnelUrl = ""
$triedCandidates = @{}
$tunnelProcess = $null
# Cloudflare's free tunnel service is sometimes throttled from a home
# connection. A quick reachability check avoids waiting minutes before the
# backup link is opened.
$cloudflareAttemptLimit = 3
try {
    $null = Invoke-WebRequest -UseBasicParsing -Method Head -Uri "https://api.trycloudflare.com" -TimeoutSec 10
} catch {
    $cloudflareStatus = $null
    try { $cloudflareStatus = $_.Exception.Response.StatusCode.value__ } catch { }
    if (-not $cloudflareStatus) {
        $cloudflareAttemptLimit = 0
        Write-Host "Cloudflare's free tunnel service is not answering right now; using the backup phone link."
    }
}
# Cloudflare's free tunnel service occasionally times out while handing out an
# address, so a failed attempt is tried again before giving up for good.
$tunnelAttempts = 0
while (-not $tunnelUrl -and $tunnelAttempts -lt $cloudflareAttemptLimit) {
    $tunnelAttempts++
    if ($tunnelAttempts -gt 1) {
        Write-Host "The free tunnel did not answer; trying again ($tunnelAttempts of 3) ..."
        Start-Sleep -Seconds 4
    }
    Remove-Item -LiteralPath $tunnelLog -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $tunnelErrorLog -ErrorAction SilentlyContinue
    $tunnelProcess = Start-Process -FilePath $cloudflared `
        -ArgumentList @("tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$port") `
        -RedirectStandardOutput $tunnelLog -RedirectStandardError $tunnelErrorLog -WindowStyle Hidden -PassThru
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
            if ($triedCandidates.ContainsKey($candidate)) { continue }
            if (Test-TunnelCandidate $candidate) { $tunnelUrl = $candidate; break }
            $triedCandidates[$candidate] = $true
        }
    }
    if (-not $tunnelUrl -and $tunnelProcess) {
        Stop-Process -Id $tunnelProcess.Id -Force -ErrorAction SilentlyContinue
        $tunnelProcess = $null
    }
}

# 3. Cloudflare is throttled or unreachable, so the free SSH backup link is
#    opened instead. It runs on port 443, which this network allows, and the
#    permanent GitHub link learns the new address exactly like before.
if (-not $tunnelUrl) {
    $ssh = Join-Path $env:SystemRoot "System32\OpenSSH\ssh.exe"
    if (-not (Test-Path -LiteralPath $ssh)) {
        $ssh = (Get-Command ssh -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty Source)
    }
    if ($ssh) {
        Write-Host "The Cloudflare link did not answer; opening the backup phone link ..."
        $knownHosts = Join-Path $logDir "salon-known-hosts.txt"
        Remove-Item -LiteralPath $tunnelLog, $tunnelErrorLog -ErrorAction SilentlyContinue
        $tunnelProcess = Start-Process -FilePath $ssh `
            -ArgumentList @("-o", "StrictHostKeyChecking=no", "-o", "UserKnownHostsFile=$knownHosts",
                "-o", "ExitOnForwardFailure=yes", "-o", "ServerAliveInterval=20", "-N",
                "-p", "443", "-R", "80:localhost:$port", "serveo.net") `
            -RedirectStandardOutput $tunnelLog -RedirectStandardError $tunnelErrorLog -WindowStyle Hidden -PassThru
        for ($attempt = 0; $attempt -lt 20 -and -not $tunnelUrl; $attempt++) {
            Start-Sleep -Seconds 2
            $candidates = @()
            foreach ($logFile in @($tunnelLog, $tunnelErrorLog)) {
                if (-not (Test-Path $logFile)) { continue }
                $found = Select-String -Path $logFile -Pattern "https://[a-z0-9-]+\.serveousercontent\.com" -AllMatches -ErrorAction SilentlyContinue
                foreach ($match in $found) { $candidates += ($match.Matches | ForEach-Object { $_.Value }) }
            }
            foreach ($candidate in ($candidates | Select-Object -Unique)) {
                if (Test-TunnelCandidate $candidate) { $tunnelUrl = $candidate; break }
            }
        }
        if (-not $tunnelUrl -and $tunnelProcess) {
            Stop-Process -Id $tunnelProcess.Id -Force -ErrorAction SilentlyContinue
            $tunnelProcess = $null
        }
    }
}

if (-not $tunnelUrl) {
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
Publish-SalonAddress $tunnelUrl
Write-Host "Published to the permanent link: https://slowyy0477.github.io/barbor_shop/"
