<#
  Stops the salon server and the free tunnel that start-salon-server.ps1 began.
  PostgreSQL keeps running so the salon database stays available.
#>
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$statePath = Join-Path $root "ops\salon-server.state.json"
$jarPath = Join-Path $root "server\target\ayan-salon-server-0.1.0-SNAPSHOT.jar"

$stoppedAny = $false

if (Test-Path $statePath) {
    $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    foreach ($entry in @(@{ name = "tunnel"; pid = $state.tunnelPid }, @{ name = "server"; pid = $state.serverPid })) {
        if ($entry.pid) {
            $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
            if ($process) {
                Stop-Process -Id $entry.pid -Force -ErrorAction SilentlyContinue
                Start-Sleep -Milliseconds 300
                if (Get-Process -Id $entry.pid -ErrorAction SilentlyContinue) {
                    Write-Host "[salon] The salon $($entry.name) (PID $($entry.pid)) did not stop. Close it from Task Manager."
                } else {
                    Write-Host "[salon] Stopped the salon $($entry.name)."
                    $stoppedAny = $true
                }
            }
        }
    }
    # The keep-awake helper exists only to hold the "system required" request
    # while the salon server runs, so it is closed with the server.
    if ($state.keepAwakePid) {
        $helper = Get-Process -Id $state.keepAwakePid -ErrorAction SilentlyContinue
        if ($helper) {
            Stop-Process -Id $state.keepAwakePid -Force -ErrorAction SilentlyContinue
            Write-Host "[salon] Released the keep-awake request; this laptop may sleep normally again."
        }
    }
}

# A run that crashed before it could save its PIDs would otherwise leave the
# server holding port 8080 forever, so match the packaged jar as a last resort.
$orphans = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*$jarPath*" })
foreach ($orphan in $orphans) {
    Stop-Process -Id $orphan.ProcessId -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 300
    if (Get-Process -Id $orphan.ProcessId -ErrorAction SilentlyContinue) {
        Write-Host "[salon] A leftover salon server (PID $($orphan.ProcessId)) is still running. Close it from Task Manager."
    } else {
        Write-Host "[salon] Stopped a leftover salon server (PID $($orphan.ProcessId))."
        $stoppedAny = $true
    }
}

$stillRunning = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -and $_.CommandLine -like "*$jarPath*" }).Count -gt 0
if (-not $stillRunning) {
    # A crashed run could leave the hidden keep-awake helper behind, which would
    # stop the laptop from ever sleeping again.
    $agents = @(Get-CimInstance Win32_Process -Filter "Name = 'powershell.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*keep-awake-agent.ps1*" })
    foreach ($agent in $agents) {
        Stop-Process -Id $agent.ProcessId -Force -ErrorAction SilentlyContinue
    }
    if ($agents.Count) { Write-Host "[salon] Closed $($agents.Count) leftover keep-awake helper(s)." }
}
if ($stillRunning) {
    Write-Host "[salon] The salon server is still running, so the saved state was kept for another try."
} else {
    Remove-Item -LiteralPath $statePath -ErrorAction SilentlyContinue
    if (-not $stoppedAny) { Write-Host "[salon] No running salon server was found." }
}
Write-Host "[salon] PostgreSQL is still running; the salon database was not touched."
