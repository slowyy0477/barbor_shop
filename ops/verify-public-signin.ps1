# Proves the installed Android app can sign up and sign in over the real public
# address. The bundled page is served from file:///android_asset/index.html, so
# its requests carry "Origin: file://" - the exact origin that used to get an
# empty 403 from Spring. Every account created here is deleted afterwards.
param(
    [string]$SalonId = "00000000-0000-0000-0000-000000000001",
    [string]$PsqlPath = "E:\PostgrelSQL\bin\psql.exe"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$settings = Get-Content -Raw -LiteralPath (Join-Path $root "ops\salon-server.local.json") | ConvertFrom-Json
$base = "$((Get-Content -Raw -LiteralPath (Join-Path $root "api.json") | ConvertFrom-Json).apiBaseUrl)".Trim().TrimEnd("/")
$tag = Get-Date -Format "HHmmss"
$pass = 0; $fail = 0

function Check([string]$name, [bool]$ok, [string]$detail = "") {
    if ($ok) { $script:pass++; Write-Host "  PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "  FAIL  $name  $detail" -ForegroundColor Red }
}

function Call([string]$method, [string]$path, [string]$origin, $body, [string]$device) {
    $headers = @{}
    if ($origin) { $headers["Origin"] = $origin }
    if ($device) { $headers["X-Device-Id"] = $device }
    if ($method -eq "OPTIONS") {
        $headers["Access-Control-Request-Method"] = "POST"
        $headers["Access-Control-Request-Headers"] = "content-type,x-device-id"
    }
    try {
        $params = @{ Uri = "$base$path"; Method = $method; Headers = $headers; UseBasicParsing = $true; TimeoutSec = 30 }
        if ($body) { $params.ContentType = "application/json"; $params.Body = ($body | ConvertTo-Json -Compress) }
        $r = Invoke-WebRequest @params
        $acao = [string]$r.Headers["Access-Control-Allow-Origin"]
        $json = $null
        try { $json = $r.Content | ConvertFrom-Json } catch { }
        return @{ status = [int]$r.StatusCode; json = $json; acao = $acao; ctype = [string]$r.Headers["Content-Type"] }
    } catch {
        $resp = $_.Exception.Response
        $status = if ($resp) { $resp.StatusCode.value__ } else { 0 }
        $text = ""; $ctype = ""
        if ($resp) {
            try { $ctype = [string]$resp.Headers["Content-Type"] } catch { }
            try { $sr = New-Object System.IO.StreamReader($resp.GetResponseStream()); $text = $sr.ReadToEnd() } catch { }
        }
        $json = $null; try { $json = $text | ConvertFrom-Json } catch { }
        return @{ status = $status; json = $json; acao = ""; ctype = $ctype; raw = $text }
    }
}

Write-Host ""
Write-Host "Android APK origin check against $base" -ForegroundColor Cyan

$pre = Call "OPTIONS" "/api/auth/customer/signup" "file://" $null ""
Check "preflight from Origin: file:// is accepted" ($pre.status -eq 200 -and ($pre.acao -eq "*" -or $pre.acao -eq "file://")) "status=$($pre.status) acao=$($pre.acao)"

$device = "qa-dev-$tag"
$phoneA = "0300" + (Get-Random -Minimum 1000000 -Maximum 9999999)
$reg = Call "POST" "/api/auth/customer/signup" "file://" @{ salonId = $SalonId; phone = $phoneA; name = "QA APK $tag"; password = "SalonPass$tag"; marketingConsent = $false } $device
Check "sign up from the APK over the public address" ($reg.status -eq 200 -and "$($reg.json.role)" -eq "CUSTOMER") "status=$($reg.status) body=$($reg.raw)"

$again = Call "POST" "/api/auth/pin/verify" "file://" @{ salonId = $SalonId; phone = $phoneA; pin = "SalonPass$tag" } $device
Check "sign in again with mobile + password" ($again.status -eq 200 -and "$($again.json.role)" -eq "CUSTOMER") "status=$($again.status)"

$weak = Call "POST" "/api/auth/customer/signup" "file://" @{ salonId = $SalonId; phone = ("0301" + (Get-Random -Minimum 1000000 -Maximum 9999999)); name = "QA Weak $tag"; password = "1234"; marketingConsent = $false } "qa-dev-w-$tag"
Check "short PIN style password is refused" ($weak.status -eq 400) "status=$($weak.status)"

$dupe = Call "POST" "/api/auth/customer/signup" "file://" @{ salonId = $SalonId; phone = $phoneA; name = "QA Dupe $tag"; password = "SalonPass$tag"; marketingConsent = $false } "qa-dev-other-$tag"
Check "same mobile number cannot sign up twice" ($dupe.status -eq 409) "status=$($dupe.status) code=$($dupe.json.code)"

$sameDevice = Call "POST" "/api/auth/customer/signup" "file://" @{ salonId = $SalonId; phone = ("0302" + (Get-Random -Minimum 1000000 -Maximum 9999999)); name = "QA Device $tag"; password = "SalonPass$tag"; marketingConsent = $false } $device
Check "same phone cannot create a second account" ($sameDevice.status -eq 409) "status=$($sameDevice.status) code=$($sameDevice.json.code)"

$otp = Call "POST" "/api/auth/otp/request" "file://" @{ salonId = $SalonId; phone = "03001234567" } ""
Check "SMS code route is retired (410)" ($otp.status -eq 410) "status=$($otp.status)"

$pages = Call "POST" "/api/auth/customer/signup" "https://slowyy0477.github.io" @{ salonId = $SalonId; phone = ("0303" + (Get-Random -Minimum 1000000 -Maximum 9999999)); name = "QA Pages $tag"; password = "SalonPass$tag"; marketingConsent = $true } "qa-dev-p-$tag"
Check "same sign up works from the GitHub Pages link" ($pages.status -eq 200 -and "$($pages.json.role)" -eq "CUSTOMER") "status=$($pages.status)"

Write-Host ""
Write-Host "Removing the test accounts..." -ForegroundColor Cyan
$env:PGPASSWORD = $settings.DbPassword
& $PsqlPath -h 127.0.0.1 -U $settings.DbUser -d $settings.Database -q -c "delete from signup_guards where customer_id in (select id from customers where name like 'QA %');" 2>&1 | Out-Null
# Each child table names the customer differently, so the cleanup is explicit
# instead of guessing a shared column name.
$children = @{
    "wallets"             = "customer_id";
    "wallet_transactions" = "customer_id";
    "visits"              = "customer_id";
    "bookings"            = "customer_id";
    "reminders"           = "customer_id";
    "deposits"            = "customer_id";
    "withdrawals"         = "customer_id";
    "sign_in_pins"        = "actor_id";
    "auth_sessions"       = "actor_id";
}
foreach ($table in $children.Keys) {
    $column = $children[$table]
    & $PsqlPath -h 127.0.0.1 -U $settings.DbUser -d $settings.Database -q -c "delete from $table where $column in (select id from customers where name like 'QA %');" 2>&1 | Out-Null
}
& $PsqlPath -h 127.0.0.1 -U $settings.DbUser -d $settings.Database -q -c "delete from customers where name like 'QA %';" 2>&1 | Out-Null
$left = (& $PsqlPath -h 127.0.0.1 -U $settings.DbUser -d $settings.Database -t -A -c "select count(*) from customers;" 2>&1 | Select-Object -First 1).Trim()
Write-Host "  customers left in the salon database: $left"

Write-Host ""
Write-Host "Result: $pass passed, $fail failed" -ForegroundColor $(if ($fail -eq 0) { "Green" } else { "Red" })
