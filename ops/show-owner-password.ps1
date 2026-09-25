<#
  Shows the owner mobile number and the owner password that is stored on this
  laptop. Use it when the owner forgot the password, or when a new phone has to
  be signed in as the owner.

  Only the digest of the password is kept inside the app and the database; this
  laptop keeps the readable copy in ops\salon-server.local.json, which never
  leaves the machine and is ignored by Git.

  Usage (double-click ops\show-owner-password.cmd for the easy version):
    powershell -ExecutionPolicy Bypass -File .\ops\show-owner-password.ps1
#>

$ErrorActionPreference = "Stop"
$opsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $opsDir "salon-server.local.json"

if (-not (Test-Path -LiteralPath $configPath)) {
    Write-Host ""
    Write-Host "  No salon settings file yet on this laptop." -ForegroundColor Yellow
    Write-Host "  Start the salon server once (ops\start-salon-server.cmd), then run this again."
    Write-Host ""
    exit 1
}

$settings = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$phone = if ($settings.PSObject.Properties["OwnerPhone"]) { "$($settings.OwnerPhone)".Trim() } else { "" }
$secret = if ($settings.PSObject.Properties["OwnerPin"]) { "$($settings.OwnerPin)".Trim() } else { "" }

Write-Host ""
Write-Host "  Owner sign-in details" -ForegroundColor Cyan
Write-Host "  ---------------------"
if ($phone) {
    Write-Host "  Owner mobile   : $phone" -ForegroundColor Green
} else {
    Write-Host "  Owner mobile   : not set yet. Start the server with -OwnerPhone 03xxxxxxxxx once."
}
if ($secret) {
    $shape = if ($secret -match "^\d{4,6}$") { "short PIN" } else { "password" }
    Write-Host "  Owner $shape : $secret" -ForegroundColor Green
} else {
    Write-Host "  Owner password : not set yet. Run ops\set-owner-password.cmd."
}
Write-Host ""
Write-Host "  How to sign in : open the app, tap the salon mark 5 times, tap Owner," -ForegroundColor Yellow
Write-Host "                   then type the mobile number and the password above."
Write-Host "                   The password alone also works - leave the mobile empty."
Write-Host ""
Write-Host "  Change it      : ops\set-owner-password.cmd"
Write-Host "  Newest code    : ops\show-last-code.cmd (for the customer at the counter)"
Write-Host ""
Write-Host "  Keep this window private. Anyone who reads the password can open the" -ForegroundColor DarkYellow
Write-Host "  Owner workspace, so close it before a customer walks up to the counter."
Write-Host ""
