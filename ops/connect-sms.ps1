<#
  Connects a real sign-in message provider so a customer receives the code on
  their own mobile instead of the owner reading it from the laptop.

  Run it with no options and answer the questions, or pass everything in one
  line. Credentials are saved only in ops\salon-server.local.json, which is
  never committed to GitHub.

  Providers:
    twilio   - Twilio Programmable SMS (works with Pakistani numbers)
    whatsapp - Meta WhatsApp Cloud API
    webhook  - any local gateway that accepts {"to":"+92300...","message":"..."}
    off      - go back to the free on-machine codes
#>
param(
    [ValidateSet("twilio", "whatsapp", "webhook", "off", "")][string]$Provider = "",
    [string]$AccountSid = "",
    [string]$AuthToken = "",
    [string]$FromNumber = "",
    [string]$WhatsAppToken = "",
    [string]$PhoneNumberId = "",
    [string]$WebhookUrl = "",
    [string]$WebhookToken = "",
    [string]$TestPhone = "",
    [switch]$NoRestart,
    [switch]$NoTest
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$opsDir = Join-Path $root "ops"
$settingsPath = Join-Path $opsDir "salon-server.local.json"
$salonId = "00000000-0000-0000-0000-000000000001"

function Read-Secret([string]$prompt) {
    $secure = Read-Host -Prompt $prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if (-not (Test-Path -LiteralPath $settingsPath)) {
    Write-Host "Start the salon server once first (ops\start-salon-server.cmd), then run this again."
    exit 1
}

if (-not $Provider) {
    Write-Host ""
    Write-Host "How should customers receive their sign-in code?"
    Write-Host "  1 - Twilio SMS          (paid, works with Pakistani numbers)"
    Write-Host "  2 - WhatsApp Cloud API  (Meta, free service tier available)"
    Write-Host "  3 - My own SMS gateway  (webhook)"
    Write-Host "  0 - Back to the free codes on this laptop"
    $choice = Read-Host -Prompt "Choose 1, 2, 3 or 0"
    switch ($choice.Trim()) {
        "1" { $Provider = "twilio" }
        "2" { $Provider = "whatsapp" }
        "3" { $Provider = "webhook" }
        "0" { $Provider = "off" }
        default { Write-Host "Nothing changed."; exit 0 }
    }
}

if ($Provider -eq "twilio") {
    if (-not $AccountSid) { $AccountSid = (Read-Host -Prompt "Twilio Account SID (starts with AC)").Trim() }
    if (-not $AuthToken) { $AuthToken = Read-Secret "Twilio Auth Token (hidden)" }
    if (-not $FromNumber) { $FromNumber = (Read-Host -Prompt "Twilio sender number (example +1 202 555 0123)").Trim() }
    if ($AccountSid -notlike "AC*" -or -not $AuthToken -or -not $FromNumber) {
        Write-Host "Those Twilio values do not look complete. Nothing changed."
        exit 1
    }
}
if ($Provider -eq "whatsapp") {
    if (-not $WhatsAppToken) { $WhatsAppToken = Read-Secret "WhatsApp Cloud API access token (hidden)" }
    if (-not $PhoneNumberId) { $PhoneNumberId = (Read-Host -Prompt "WhatsApp phone number ID").Trim() }
    if (-not $WhatsAppToken -or -not $PhoneNumberId) {
        Write-Host "Those WhatsApp values do not look complete. Nothing changed."
        exit 1
    }
}
if ($Provider -eq "webhook") {
    if (-not $WebhookUrl) { $WebhookUrl = (Read-Host -Prompt "Gateway URL (https://...)").Trim() }
    if (-not $WebhookToken) { $WebhookToken = Read-Secret "Gateway token, or press Enter when it needs none" }
    if ($WebhookUrl -notlike "https://*") {
        Write-Host "The gateway address must start with https://. Nothing changed."
        exit 1
    }
}

$settings = Get-Content -Raw -LiteralPath $settingsPath | ConvertFrom-Json
$saved = if ($Provider -eq "off") { "" } else { $Provider }
$values = [ordered]@{
    SmsProvider           = $saved
    TwilioAccountSid      = if ($Provider -eq "twilio") { $AccountSid } else { "" }
    TwilioAuthToken       = if ($Provider -eq "twilio") { $AuthToken } else { "" }
    TwilioFromNumber      = if ($Provider -eq "twilio") { $FromNumber } else { "" }
    WhatsAppToken         = if ($Provider -eq "whatsapp") { $WhatsAppToken } else { "" }
    WhatsAppPhoneNumberId = if ($Provider -eq "whatsapp") { $PhoneNumberId } else { "" }
    SmsWebhookUrl         = if ($Provider -eq "webhook") { $WebhookUrl } else { "" }
    SmsWebhookToken       = if ($Provider -eq "webhook") { $WebhookToken } else { "" }
}
foreach ($field in $values.GetEnumerator()) {
    $settings | Add-Member -NotePropertyName $field.Key -NotePropertyValue $field.Value -Force
}
$settings | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $settingsPath -Encoding UTF8
Write-Host "Saved the provider settings in ops\salon-server.local.json (private, never uploaded)."

if ($NoRestart) {
    Write-Host "Restart the salon server for the change to take effect."
    exit 0
}

Write-Host ""
Write-Host "Restarting the salon server so the new setting is used. This can take a minute or two ..."
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $opsDir "stop-salon-server.ps1") | Out-Null
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $opsDir "start-salon-server.ps1")

if ($Provider -eq "off" -or $NoTest) { exit 0 }

$phone = $TestPhone
if (-not $phone -and $settings.PSObject.Properties["OwnerPhone"]) { $phone = [string]$settings.OwnerPhone }
if (-not $phone) {
    Write-Host "No test number was given, so no test message was sent."
    exit 0
}

Write-Host ""
Write-Host "Sending one test code to $phone ..."
try {
    $body = @{ salonId = $salonId; phone = $phone } | ConvertTo-Json
    $result = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/auth/otp/request" `
        -ContentType "application/json" -Body $body -TimeoutSec 45
    if ($result.challengeId) {
        Write-Host "Test code sent. Check that mobile for a message from the salon."
        Write-Host "If nothing arrives within a minute, open ops\salon-server.out.log and look for the provider error."
    } else {
        Write-Host "The server accepted the request but did not return a challenge."
    }
} catch {
    Write-Host "The test request failed: $($_.Exception.Message)"
    Write-Host "The code may still have been written to ops\salon-sign-in-codes.log."
}
