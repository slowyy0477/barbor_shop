<#
  Sets (or changes) the owner password used to open the private Owner workspace
  in the mobile app and on the web page.

  Why this exists: a text-message (SMS) provider is not connected yet, so the
  six-digit code can be hard to reach. A password is a second, always-working
  way in - and it never leaves this laptop except as a salted digest.

  Rules for the password: 10 to 20 characters, using letters, digits or the
  symbols @ # $ % ^ & * ! . _ + - , and it must contain at least one letter and
  at least one digit. A short 4 to 6 digit PIN is still accepted.

  Usage (double-click ops\set-owner-password.cmd for the easy version):
    powershell -ExecutionPolicy Bypass -File .\ops\set-owner-password.ps1
    powershell -ExecutionPolicy Bypass -File .\ops\set-owner-password.ps1 -Password "salonowner2026pk" -Restart
#>

param(
    [string]$Password = "",
    [switch]$Restart
)

$ErrorActionPreference = "Stop"
$opsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceDir = Split-Path -Parent $opsDir
$configPath = Join-Path $opsDir "salon-server.local.json"
$secretPattern = "^[A-Za-z0-9@#$%^&*!._+-]{10,20}$"

function Test-OwnerSecret([string]$value) {
    if ([string]::IsNullOrWhiteSpace($value)) { return $false }
    $trimmed = $value.Trim()
    if ($trimmed -match "^\d{4,6}$") { return $true }
    if ($trimmed -notmatch $secretPattern) { return $false }
    if ($trimmed -notmatch "[A-Za-z]") { return $false }
    if ($trimmed -notmatch "\d") { return $false }
    return $true
}

if (-not (Test-Path -LiteralPath $configPath)) {
    throw "Cannot find $configPath. Start the salon server once so it can create its settings file, then run this again."
}

if (-not $Password -or -not $Password.Trim()) {
    Write-Host ""
    Write-Host "  Choose the owner password" -ForegroundColor Cyan
    Write-Host "  -------------------------"
    Write-Host "  10 to 20 characters. Use letters and numbers together."
    Write-Host "  Example: salonowner2026pk"
    Write-Host ""
    $secure = Read-Host "  New owner password" -AsSecureString
    $plain = [System.Net.NetworkCredential]::new("", $secure).Password
    $confirmSecure = Read-Host "  Type it again" -AsSecureString
    $confirm = [System.Net.NetworkCredential]::new("", $confirmSecure).Password
    if ($plain -ne $confirm) { throw "The two entries did not match. Nothing was changed." }
    $Password = $plain
}

$secret = $Password.Trim()
if (-not (Test-OwnerSecret $secret)) {
    throw "That password is not allowed. Use 10 to 20 characters with at least one letter and one digit (allowed symbols: @ # $ % ^ & * ! . _ + -), or a 4 to 6 digit PIN."
}

$settings = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$settings | Add-Member -NotePropertyName "OwnerPin" -NotePropertyValue $secret -Force
$settings | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $configPath -Encoding UTF8

Write-Host ""
Write-Host "  Owner password saved on this laptop." -ForegroundColor Green
Write-Host "  Owner mobile : $($settings.OwnerPhone)"
Write-Host "  Sign in with : mobile number + this password (tap the salon mark 5 times, then Owner)."
Write-Host ""

if ($Restart) {
    $stopScript = Join-Path $opsDir "stop-salon-server.ps1"
    $startScript = Join-Path $opsDir "start-salon-server.ps1"
    if (Test-Path -LiteralPath $stopScript) {
        Write-Host "  Restarting the salon server so the new password takes effect..." -ForegroundColor Cyan
        & powershell -NoProfile -ExecutionPolicy Bypass -File $stopScript | Out-Null
    }
    if (Test-Path -LiteralPath $startScript) {
        & powershell -NoProfile -ExecutionPolicy Bypass -File $startScript | Out-Null
    }
    Write-Host "  Server restarted." -ForegroundColor Green
}
else {
    Write-Host "  Now run ops\stop-salon-server.cmd and then ops\start-salon-server.cmd" -ForegroundColor Yellow
    Write-Host "  (or start it with the one-click shortcut) so the new password is picked up."
    Write-Host ""
}
