<#
  Checks the whole password-only sign-in story against a running salon server.

  It proves, in order:
    * a customer account is created with mobile number + password and no SMS
    * the same device cannot create a second account
    * a fourth account from one internet address is refused
    * the customer signs in again with the password
    * a wrong password is refused
    * the owner signs in with the owner password
    * the retired SMS route answers 410 instead of sending anything

  Every account it creates is named "QA-..." and every row it creates is deleted
  again at the end, so the salon database stays as empty as it was found.

  Usage:  powershell -ExecutionPolicy Bypass -File .\ops\verify-password-auth.ps1
#>
[CmdletBinding()]
param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [string]$SalonId = "00000000-0000-0000-0000-000000000001",
    [string]$PsqlPath = "E:\PostgrelSQL\bin\psql.exe",
    [switch]$KeepTestData
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$settings = Get-Content -Raw -LiteralPath (Join-Path $root "ops\salon-server.local.json") | ConvertFrom-Json

$script:pass = 0
$script:fail = 0
$script:createdPhones = @()
$script:createdDevices = @()
$tag = (Get-Date -Format "HHmmss")
# 198.18.0.0/15 is a reserved benchmark range, so each run gets its own
# throwaway "internet address" and can never exhaust the real network's
# three-account allowance.
$testNetwork = "198.18.$((Get-Random -Minimum 1 -Maximum 250)).7"

function Write-Result {
    param([string]$Name, [bool]$Ok, [string]$Detail = "")
    if ($Ok) { $script:pass++; Write-Host ("  PASS  " + $Name) -ForegroundColor Green }
    else { $script:fail++; Write-Host ("  FAIL  " + $Name + "  " + $Detail) -ForegroundColor Red }
}

# Returns @{ Status = <int>; Body = <string> }
function Invoke-Api {
    param(
        [string]$Path,
        [string]$Method = "POST",
        [object]$Payload = $null,
        [string]$DeviceId = "",
        [string]$ForwardedFor = ""
    )
    $headers = @{}
    if ($DeviceId) { $headers["X-Device-Id"] = $DeviceId }
    if ($ForwardedFor) { $headers["X-Forwarded-For"] = $ForwardedFor }
    $requestArgs = @{
        Uri         = ($BaseUrl.TrimEnd("/") + $Path)
        Method      = $Method
        # Windows PowerShell 5.1 throws a null-reference error on the IE engine
        # while PowerShell 7 ignores this switch, so asking for basic parsing is
        # what makes the same script work in both.
        UseBasicParsing = $true
        TimeoutSec  = 25
    }
    if ($headers.Count -gt 0) { $requestArgs["Headers"] = $headers }
    if ($Payload -ne $null) {
        $requestArgs["Body"] = ($Payload | ConvertTo-Json -Compress)
        $requestArgs["ContentType"] = "application/json"
    }
    try {
        $response = Invoke-WebRequest @requestArgs
        return @{ Status = [int]$response.StatusCode; Body = (ConvertTo-Text $response.Content) }
    } catch {
        # PowerShell 7 and Windows PowerShell 5.1 expose the failure body in
        # different places, so try both before giving up on the detail.
        $body = $_.ErrorDetails.Message
        $status = 0
        $web = $_.Exception.Response
        if ($web) {
            try { $status = [int]$web.StatusCode } catch { $status = 0 }
            if (-not $body) {
                try {
                    $reader = New-Object System.IO.StreamReader($web.GetResponseStream())
                    $body = $reader.ReadToEnd()
                } catch { $body = "" }
            }
        }
        if (-not $body) { $body = [string]$_.Exception.Message }
        return @{ Status = $status; Body = ($body | Out-String).Trim() }
    }
}

# PowerShell 7 hands back a string; Windows PowerShell 5.1 hands back bytes.
function ConvertTo-Text {
    param($Value)
    if ($null -eq $Value) { return "" }
    if ($Value -is [string]) { return $Value }
    if ($Value -is [byte[]]) { return [System.Text.Encoding]::UTF8.GetString($Value) }
    return [string]$Value
}

function New-TestPhone {
    param([int]$Index)
    $script:createdPhones += ("0300999{0:d4}" -f $Index)
    return $script:createdPhones[-1]
}

Write-Host ""
Write-Host "Password sign-in verification against $BaseUrl" -ForegroundColor Cyan
Write-Host ""

$health = Invoke-Api -Path "/actuator/health" -Method GET
Write-Result "Server answers /actuator/health" ($health.Status -eq 200) $health.Body
if ($health.Status -ne 200) {
    Write-Host "Start the salon first: 1 START SALON.cmd" -ForegroundColor Yellow
    exit 1
}

# --- 1. Create the first account with a password only ----------------------
$deviceA = "qa-device-a-$tag"
$phoneA = New-TestPhone 1
$passwordA = "QaPass${tag}x"
$signupA = Invoke-Api -Path "/api/auth/customer/signup" -DeviceId $deviceA -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneA; name = "QA-One $tag"; password = $passwordA; marketingConsent = $false
}
Write-Result "Create account with mobile + password (no SMS)" ($signupA.Status -eq 200) "status=$($signupA.Status) body=$($signupA.Body)"
$script:createdDevices += $deviceA

# --- 2. The same device must not create a second account -------------------
$phoneB = New-TestPhone 2
$signupSameDevice = Invoke-Api -Path "/api/auth/customer/signup" -DeviceId $deviceA -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneB; name = "QA-Two $tag"; password = "QaPass${tag}y"; marketingConsent = $false
}
Write-Result "One device cannot create a second account" ($signupSameDevice.Status -eq 409) "status=$($signupSameDevice.Status) body=$($signupSameDevice.Body)"

# --- 3. Two more accounts from the same internet address are still allowed --
$phoneC = New-TestPhone 3
$deviceC = "qa-device-c-$tag"
$signupC = Invoke-Api -Path "/api/auth/customer/signup" -DeviceId $deviceC -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneC; name = "QA-Three $tag"; password = "QaPass${tag}z"; marketingConsent = $false
}
Write-Result "Second account from the same network is allowed" ($signupC.Status -eq 200) "status=$($signupC.Status)"
$script:createdDevices += $deviceC

$phoneD = New-TestPhone 4
$deviceD = "qa-device-d-$tag"
$signupD = Invoke-Api -Path "/api/auth/customer/signup" -DeviceId $deviceD -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneD; name = "QA-Four $tag"; password = "QaPass${tag}w"; marketingConsent = $false
}
Write-Result "Third account from the same network is allowed" ($signupD.Status -eq 200) "status=$($signupD.Status)"
$script:createdDevices += $deviceD

# --- 4. The fourth account from that network must be refused ----------------
$phoneE = New-TestPhone 5
$deviceE = "qa-device-e-$tag"
$signupE = Invoke-Api -Path "/api/auth/customer/signup" -DeviceId $deviceE -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneE; name = "QA-Five $tag"; password = "QaPass${tag}v"; marketingConsent = $false
}
Write-Result "Fourth account from one network is refused" ($signupE.Status -eq 409) "status=$($signupE.Status) body=$($signupE.Body)"

# --- 5. Sign in again with the password ------------------------------------
$loginA = Invoke-Api -Path "/api/auth/password/login" -DeviceId $deviceA -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneA; pin = $passwordA
}
$roleA = ""
try { $roleA = (($loginA.Body | ConvertFrom-Json).role) } catch { $roleA = "" }
Write-Result "Customer signs in again with the password" ($loginA.Status -eq 200 -and $roleA -eq "CUSTOMER") "status=$($loginA.Status) role=$roleA"

# --- 6. A wrong password is refused ----------------------------------------
$loginBad = Invoke-Api -Path "/api/auth/password/login" -DeviceId $deviceA -ForwardedFor $testNetwork -Payload @{
    salonId = $SalonId; phone = $phoneA; pin = "WrongPass${tag}x"
}
Write-Result "Wrong password is refused" ($loginBad.Status -eq 401) "status=$($loginBad.Status)"

# --- 7. The owner signs in with the owner password -------------------------
$ownerSecret = [string]$settings.OwnerPin
if ($ownerSecret) {
    $ownerLogin = Invoke-Api -Path "/api/auth/password/login" -Payload @{ salonId = $SalonId; phone = ""; pin = $ownerSecret }
    $ownerRole = ""
    try { $ownerRole = (($ownerLogin.Body | ConvertFrom-Json).role) } catch { $ownerRole = "" }
    Write-Result "Owner signs in with the owner password" ($ownerLogin.Status -eq 200 -and $ownerRole -eq "OWNER") "status=$($ownerLogin.Status) role=$ownerRole"
} else {
    Write-Host "  SKIP  owner password check (no OwnerPin in ops\salon-server.local.json)" -ForegroundColor Yellow
}

# --- 8. The retired SMS route is closed ------------------------------------
$otp = Invoke-Api -Path "/api/auth/otp/request" -Payload @{ salonId = $SalonId; phone = $phoneA }
Write-Result "SMS code route is switched off (410)" ($otp.Status -eq 410) "status=$($otp.Status) body=$($otp.Body)"

Write-Host ""

# --- Clean up everything this script created -------------------------------
if (-not $KeepTestData) {
    if (-not (Test-Path -LiteralPath $PsqlPath)) {
        Write-Host "Could not find psql at $PsqlPath, so the QA accounts were left in the database." -ForegroundColor Yellow
        Write-Host "Remove them by hand or pass -KeepTestData to accept them." -ForegroundColor Yellow
    } else {
        $env:PGPASSWORD = [string]$settings.DbPassword
        $pattern = "QA-%"
        $sql = @"
delete from signup_guards where customer_id in (select id from customers where name like '$pattern');
delete from sign_in_pins where actor_id in (select id from customers where name like '$pattern');
delete from auth_sessions where actor_id in (select id from customers where name like '$pattern');
delete from wallets where customer_id in (select id from customers where name like '$pattern');
delete from customers where name like '$pattern';
select count(*) as customers_left from customers;
"@
        $cleanup = & $PsqlPath -U $settings.DbUser -d $settings.Database -h localhost -v ON_ERROR_STOP=1 -c $sql 2>&1
        $left = ($cleanup | Select-String -Pattern "^\s*\d+\s*$" | Select-Object -Last 1)
        Write-Host "Test accounts removed. Rows reported by PostgreSQL:" -ForegroundColor Cyan
        $cleanup | ForEach-Object { Write-Host "  $_" }
    }
}

Write-Host ""
Write-Host ("Result: {0} passed, {1} failed" -f $script:pass, $script:fail) -ForegroundColor $(if ($script:fail -eq 0) { "Green" } else { "Red" })
exit $(if ($script:fail -eq 0) { 0 } else { 1 })
