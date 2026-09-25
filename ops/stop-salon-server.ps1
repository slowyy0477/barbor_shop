<#
  Stops the salon server and the free tunnel that start-salon-server.ps1 began.
  PostgreSQL keeps running so the salon database stays available.
#>
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$statePath = Join-Path $root "ops\salon-server.state.json"
$jarPath = Join-Path $root "server\target\ayan-salon-server-0.1.0-SNAPSHOT.jar"

$stoppedAny = $false
$tunnelStopped = $false
$stillRunning = $false

function Wait-ProcessExit {
    param([int]$ProcessId, [int]$TimeoutSeconds = 20)
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) { return $true }
        Start-Sleep -Milliseconds 400
    }
    return -not [bool](Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Stop-ProcessTree {
    param([int]$ProcessId)
    # A tunnel started through npx.cmd hides its real work in a child process,
    # so the whole little tree is closed with taskkill.
    & taskkill /PID $ProcessId /T /F 2>&1 | Out-Null
    return (Wait-ProcessExit -ProcessId $ProcessId -TimeoutSeconds 20)
}

if (Test-Path $statePath) {
    $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
    foreach ($entry in @(@{ name = "tunnel"; pid = $state.tunnelPid }, @{ name = "server"; pid = $state.serverPid })) {
        if ($entry.pid) {
            $process = Get-Process -Id $entry.pid -ErrorAction SilentlyContinue
            if ($process) {
                # A JVM needs a moment to leave: asking it, then waiting, avoids
                # telling the owner it failed when it is actually closing.
                Stop-Process -Id $entry.pid -Force -ErrorAction SilentlyContinue
                if (-not (Wait-ProcessExit -ProcessId $entry.pid -TimeoutSeconds 20)) {
                    Write-Host "[salon] The salon $($entry.name) (PID $($entry.pid)) did not stop. Close it from Task Manager."
                } else {
                    Write-Host "[salon] Stopped the salon $($entry.name)."
                    $stoppedAny = $true
                    if ($entry.name -eq "tunnel") { $tunnelStopped = $true }
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
    if (-not (Wait-ProcessExit -ProcessId $orphan.ProcessId -TimeoutSeconds 20)) {
        Write-Host "[salon] A leftover salon server (PID $($orphan.ProcessId)) is still running. Close it from Task Manager."
    } else {
        Write-Host "[salon] Stopped a leftover salon server (PID $($orphan.ProcessId))."
        $stoppedAny = $true
    }
}

# The free phone link is opened through cloudflared, ssh or npx, and the saved
# PID is missing whenever an earlier run crashed or a backup tunnel was used.
# Closing the process itself is what actually takes the phone link offline.
$tunnelProcesses = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
    Where-Object {
        $_.CommandLine -and
        $_.Name -in @("node.exe", "cloudflared.exe", "ssh.exe") -and
        ($_.CommandLine -like "*tunnelmole*" -or $_.CommandLine -like "*cloudflared*" -or $_.CommandLine -like "*serveo.net*")
    })
foreach ($tunnelProcess in $tunnelProcesses) {
    if (Stop-ProcessTree -ProcessId $tunnelProcess.ProcessId) {
        Write-Host "[salon] Closed the free phone link (PID $($tunnelProcess.ProcessId))."
        $stoppedAny = $true
        $tunnelStopped = $true
    } else {
        Write-Host "[salon] The free phone link (PID $($tunnelProcess.ProcessId)) is still closing."
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
Write-Host ""
Write-Host "[salon] Salon server  : $(if ($stillRunning) { 'STILL RUNNING - try again or close it from Task Manager' } else { 'OFF' })"
Write-Host "[salon] Phone link     : $(if ($tunnelStopped) { 'OFF' } elseif ($stillRunning) { 'closing with the server' } else { 'already off' })"
Write-Host "[salon] Salon database : still installed and running (this is normal and keeps your records safe)"
