<#
  Prints a plain-English health report for the laptop salon server:
  database, server, phone address and what to do next.
#>
$ErrorActionPreference = "SilentlyContinue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$opsDir = Join-Path $root "ops"
$statePath = Join-Path $opsDir "salon-server.state.json"
$urlPath = Join-Path $opsDir "salon-public-url.txt"

Write-Host ""
Write-Host "================ SALON SERVER CHECK ================"

# 1. Database
$pg = Get-Service -Name "postgresql*" | Select-Object -First 1
if ($pg -and $pg.Status -eq "Running") {
    Write-Host "Database        : ON  ($($pg.Name))"
} elseif ($pg) {
    Write-Host "Database        : OFF ($($pg.Name) is $($pg.Status)) - start the server to turn it on"
} else {
    Write-Host "Database        : NOT FOUND - install PostgreSQL or ask your helper"
}

# 2. Server
$state = $null
if (Test-Path $statePath) {
    try { $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json } catch { $state = $null }
}
$port = if ($state -and $state.port) { $state.port } else { 8080 }
$health = $null
try { $health = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5 } catch { $health = $null }
if ($health -and $health.StatusCode -eq 200) {
    $pidText = if ($state -and $state.serverPid) { "process $($state.serverPid)" } else { "running" }
    Write-Host "Salon server    : ON  ($pidText, port $port)"
} else {
    Write-Host "Salon server    : OFF - double-click start-salon-server.cmd"
}

# 3. Phone address
$tunnel = ""
if ($state -and $state.tunnelUrl) { $tunnel = $state.tunnelUrl }
elseif (Test-Path $urlPath) { $tunnel = (Get-Content -Raw -LiteralPath $urlPath).Trim() }
if ($tunnel) {
    Write-Host "Phone address   : $tunnel"
    Write-Host "                  (this link changes every time the server restarts)"
} else {
    Write-Host "Phone address   : not available yet - start the server first"
}

# 4. Same Wi-Fi address
$lan = ""
if ($state -and $state.lanAddress) { $lan = $state.lanAddress }
else {
    $lan = (Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" } |
        Select-Object -First 1 -ExpandProperty IPAddress)
}
if ($lan) { Write-Host "Same Wi-Fi      : http://${lan}:$port" }

Write-Host "----------------------------------------------------"
if ($health -and $health.StatusCode -eq 200) {
    Write-Host "Everything is ON. Salon phones can work now."
} else {
    Write-Host "Next step: double-click start-salon-server.cmd and wait for 'Done'."
}
Write-Host "Stop for the day: double-click stop-salon-server.cmd"
Write-Host "===================================================="
Write-Host ""
