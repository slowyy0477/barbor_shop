<#
  One-time setup so the salon server turns on by itself - the owner never has to
  remember any command.

  What it does:
    1. Puts "Start Salon Server", "Stop Salon Server" and "Check Salon Server"
       shortcuts on the Desktop and in the Start Menu.
    2. Registers "Ayan Salon Server" to start automatically every time Windows
       signs in (scheduled task, or a Startup-folder shortcut if Windows refuses).
    3. Keeps the laptop awake on mains power while the salon is open, and stores
       the previous power settings so -Uninstall can put them back.

  Examples:
    powershell -ExecutionPolicy Bypass -File .\ops\install-one-click-start.ps1
    powershell -ExecutionPolicy Bypass -File .\ops\install-one-click-start.ps1 -NoAutoStart
    powershell -ExecutionPolicy Bypass -File .\ops\install-one-click-start.ps1 -KeepPowerSettings
    powershell -ExecutionPolicy Bypass -File .\ops\install-one-click-start.ps1 -Uninstall
#>
param(
    [switch]$NoAutoStart,
    [switch]$KeepPowerSettings,
    [switch]$Uninstall
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$opsDir = Join-Path $root "ops"
$script:TaskName = "Ayan Salon Server"
$script:ShortcutName = "Start Salon Server"
$script:PowerBackup = Join-Path $opsDir "power-settings-backup.pow"
$script:PowerRecord = Join-Path $opsDir "power-settings-backup.json"

function Write-Step($message) { Write-Host "[salon] $message" }

function Get-DesktopPath {
    $desktop = [Environment]::GetFolderPath("Desktop")
    if (-not $desktop) { $desktop = Join-Path $env:USERPROFILE "Desktop" }
    return $desktop
}

function Get-StartMenuPath {
    $programs = [Environment]::GetFolderPath("Programs")
    if (-not $programs) { $programs = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs" }
    return (Join-Path $programs "Ayan Salon")
}

function Get-StartupPath {
    $startup = [Environment]::GetFolderPath("Startup")
    if (-not $startup) { $startup = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\Startup" }
    return $startup
}

function New-SalonShortcut {
    param([string]$Folder, [string]$Name, [string]$Target, [string]$Arguments = "", [string]$Description = "")
    if (-not (Test-Path $Folder)) { New-Item -ItemType Directory -Path $Folder -Force | Out-Null }
    $path = Join-Path $Folder "$Name.lnk"
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($path)
    $shortcut.TargetPath = $Target
    if ($Arguments) { $shortcut.Arguments = $Arguments }
    $shortcut.WorkingDirectory = $root
    $shortcut.Description = $Description
    $shortcut.IconLocation = "$env:SystemRoot\System32\shell32.dll,137"
    $shortcut.Save()
    return $path
}

function Remove-SalonShortcuts {
    $removed = @()
    foreach ($folder in @((Get-DesktopPath), (Get-StartMenuPath), (Get-StartupPath))) {
        foreach ($name in @($script:ShortcutName, "Stop Salon Server", "Check Salon Server", "Ayan Salon Server")) {
            $file = Join-Path $folder "$name.lnk"
            if (Test-Path $file) { Remove-Item -LiteralPath $file -Force; $removed += $file }
        }
    }
    $startMenuFolder = Get-StartMenuPath
    if ((Test-Path $startMenuFolder) -and -not (Get-ChildItem -LiteralPath $startMenuFolder -Force | Select-Object -First 1)) {
        Remove-Item -LiteralPath $startMenuFolder -Force -Recurse
    }
    return $removed
}

function Get-CurrentPowerScheme {
    try {
        $output = & powercfg /getactivescheme 2>$null
        if ("$output" -match "([0-9a-fA-F-]{36})") { return $Matches[1] }
    } catch { }
    return ""
}

function Backup-PowerSettings {
    try {
        $guid = Get-CurrentPowerScheme
        if (-not $guid) { Write-Step "Power settings could not be read; they will be left alone."; return $false }
        & powercfg /export $script:PowerBackup $guid 2>$null | Out-Null
        if (-not (Test-Path $script:PowerBackup)) { Write-Step "Power settings could not be saved; they will be left alone."; return $false }
        [pscustomobject]@{ schemeGuid = $guid; backupFile = $script:PowerBackup; savedAt = (Get-Date).ToString("o") } |
            ConvertTo-Json | Set-Content -LiteralPath $script:PowerRecord -Encoding UTF8
        Write-Step "Saved the current power settings so -Uninstall can put them back."
        return $true
    } catch {
        Write-Step "Power settings could not be saved ($($_.Exception.Message)); they will be left alone."
        return $false
    }
}

function Enable-KeepAwake {
    try {
        # Only mains power is changed: on battery the laptop may still sleep.
        & powercfg /change standby-timeout-ac 0 2>$null | Out-Null
        & powercfg /change hibernate-timeout-ac 0 2>$null | Out-Null
        & powercfg /change disk-timeout-ac 0 2>$null | Out-Null
        & powercfg /change monitor-timeout-ac 10 2>$null | Out-Null
        Write-Step "This laptop will now stay awake while plugged in, so the salon can keep working."
    } catch {
        Write-Step "Power settings could not be changed ($($_.Exception.Message)). The salon still works, just keep the lid open."
    }
}

function Restore-PowerSettings {
    if (-not (Test-Path $script:PowerRecord)) {
        Write-Step "No saved power settings were found; leaving the current power settings alone."
        return
    }
    try {
        $record = Get-Content -Raw -LiteralPath $script:PowerRecord | ConvertFrom-Json
        if ($record.backupFile -and (Test-Path $record.backupFile) -and $record.schemeGuid) {
            & powercfg /import $record.backupFile $record.schemeGuid 2>$null | Out-Null
            & powercfg /setactive $record.schemeGuid 2>$null | Out-Null
            Write-Step "Restored the power settings that were saved on $(($record.savedAt -as [datetime]).ToString('yyyy-MM-dd HH:mm'))."
        } else {
            Write-Step "The saved power file is missing; leaving the current power settings alone."
        }
    } catch {
        Write-Step "Power settings could not be restored automatically ($($_.Exception.Message))."
    }
    Remove-Item -LiteralPath $script:PowerRecord -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $script:PowerBackup -ErrorAction SilentlyContinue
}

function Install-AutoStart {
    $startCmd = Join-Path $opsDir "start-salon-server.cmd"
    if (-not (Test-Path $startCmd)) { throw "start-salon-server.cmd was not found in $opsDir" }
    try {
        & schtasks /Create /TN $script:TaskName /SC ONLOGON /RL LIMITED /TR "`"$startCmd`"" /F 2>$null | Out-Null
        & schtasks /Query /TN $script:TaskName 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) {
            Write-Step "Registered '$script:TaskName': the salon server now starts by itself at every Windows sign-in."
            return "task"
        }
    } catch { }
    $startup = Get-StartupPath
    New-SalonShortcut -Folder $startup -Name "Ayan Salon Server" -Target $startCmd -Description "Starts the salon server automatically at sign-in" | Out-Null
    Write-Step "Windows did not allow a scheduled task, so a Startup shortcut was used instead."
    Write-Step "The salon server will still start by itself at every Windows sign-in."
    return "startup"
}

function Uninstall-OneClickStart {
    Write-Step "Removing the one-click setup ..."
    try {
        & schtasks /Delete /TN $script:TaskName /F 2>$null | Out-Null
        Write-Step "Removed the '$script:TaskName' automatic start."
    } catch {
        Write-Step "The automatic start was already removed."
    }
    $removed = Remove-SalonShortcuts
    Write-Step "Removed $($removed.Count) shortcut(s)."
    Restore-PowerSettings
    Write-Host ""
    Write-Host "Uninstalled. Your salon data, database and server files were NOT touched."
    Write-Host "You can still start the server by double-clicking ops\start-salon-server.cmd"
    Write-Host ""
    return
}

if ($Uninstall) { Uninstall-OneClickStart; exit 0 }

Write-Step "Setting up the one-click salon start ..."
$startCmd = Join-Path $opsDir "start-salon-server.cmd"
$stopCmd = Join-Path $opsDir "stop-salon-server.cmd"
$checkCmd = Join-Path $opsDir "status-salon-server.cmd"
foreach ($required in @($startCmd, $stopCmd, $checkCmd)) {
    if (-not (Test-Path $required)) { throw "Missing required file: $required" }
}

$desktop = Get-DesktopPath
$startMenu = Get-StartMenuPath
$made = @()
$made += New-SalonShortcut -Folder $desktop -Name "Start Salon Server" -Target $startCmd -Description "Turns on the salon server and database"
$made += New-SalonShortcut -Folder $desktop -Name "Check Salon Server" -Target $checkCmd -Description "Shows whether the salon server is on"
$made += New-SalonShortcut -Folder $desktop -Name "Stop Salon Server" -Target $stopCmd -Description "Turns the salon server off for the day"
$made += New-SalonShortcut -Folder $startMenu -Name "Start Salon Server" -Target $startCmd -Description "Turns on the salon server and database"
$made += New-SalonShortcut -Folder $startMenu -Name "Check Salon Server" -Target $checkCmd -Description "Shows whether the salon server is on"
$made += New-SalonShortcut -Folder $startMenu -Name "Stop Salon Server" -Target $stopCmd -Description "Turns the salon server off for the day"
Write-Step "Created $($made.Count) shortcuts on the Desktop and in the Start Menu."

if ($NoAutoStart) {
    Write-Step "-NoAutoStart was used, so the server will NOT start by itself. Use the Desktop shortcut each day."
} else {
    Install-AutoStart | Out-Null
}

if ($KeepPowerSettings) {
    Write-Step "-KeepPowerSettings was used, so power settings were not changed."
} else {
    if (Backup-PowerSettings) { Enable-KeepAwake }
}

Write-Host ""
Write-Host "============================================================"
Write-Host "  Done - your salon now starts with one click."
Write-Host "============================================================"
Write-Host ""
Write-Host "Every day:"
if (-not $NoAutoStart) { Write-Host "  - Turn the laptop on and sign in to Windows. The salon server starts by itself." }
Write-Host "  - If you are not sure it is on, double-click 'Check Salon Server' on the Desktop."
Write-Host "  - To start it by hand any time, double-click 'Start Salon Server'."
Write-Host "  - When the salon closes, double-click 'Stop Salon Server' (or just shut the laptop down)."
Write-Host ""
Write-Host "The phone address is printed in the start window and is also saved in"
Write-Host "ops\salon-public-url.txt. It changes whenever the server restarts."
Write-Host ""
