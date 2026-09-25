<#
  Runs the Ayan Beauty Salon server on this laptop for free.

  It starts the salon database (PostgreSQL already installed on this machine),
  launches the packaged Spring server, and - unless -NoTunnel is used - opens a
  free Cloudflare quick tunnel so the salon phones can reach this laptop over
  https without a paid host, a static IP or router port forwarding.

  Example:
    powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1
    powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -OwnerPhone "0300 1234567"
    powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -PostgresAdminPassword "postgres-password"

  The generated database password and session secret are stored in
  ops\salon-server.local.json, which is ignored by git. Never share that file.

  -PostgresAdminPassword is the password of the PostgreSQL superuser (usually
  "postgres") that was chosen when PostgreSQL was installed. It is used once per
  run to create the salon role/database and is never written to disk. Set it in
  the SALON_PG_ADMIN_PASSWORD environment variable instead if you prefer.
#>
param(
    [int]$Port = 8080,
    [string]$Database = "ayan_salon",
    [string]$DbUser = "ayan",
    [string]$PostgresBin = "",
    [string]$PostgresAdminUser = "postgres",
    [string]$PostgresAdminPassword = "",
    [string]$OwnerPhone = "",
    [string]$OwnerPin = "",
    [string]$SalonId = "00000000-0000-0000-0000-000000000001",
    # Real sign-in codes by SMS/WhatsApp. Empty keeps the free on-machine codes
    # that the owner reads out at the counter.
    [string]$SmsProvider = "",
    [string]$TwilioAccountSid = "",
    [string]$TwilioAuthToken = "",
    [string]$TwilioFromNumber = "",
    [string]$WhatsAppToken = "",
    [string]$WhatsAppPhoneNumberId = "",
    [string]$SmsWebhookUrl = "",
    [string]$SmsWebhookToken = "",
    [switch]$NoTunnel,
    [switch]$SkipBuild,
    # Starting a second copy on the same port always fails, so the default is to
    # report the running salon instead. -Force starts a fresh copy anyway.
    [switch]$Force,
    # Skip the helper that keeps the laptop awake while the salon is serving.
    [switch]$NoKeepAwake
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$opsDir = Join-Path $root "ops"
$settingsPath = Join-Path $opsDir "salon-server.local.json"
$statePath = Join-Path $opsDir "salon-server.state.json"
$logDir = Join-Path $root "tmp"
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir | Out-Null }

function Write-Step($message) { Write-Host "[salon] $message" }

function New-RandomHex([int]$bytes) {
    $buffer = New-Object byte[] $bytes
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($buffer)
    return ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
}

function Load-Settings {
    if (Test-Path $settingsPath) {
        try { return (Get-Content -Raw -LiteralPath $settingsPath | ConvertFrom-Json) } catch { }
    }
    return $null
}

$settings = Load-Settings
if (-not $settings) { $settings = [pscustomobject]@{} }
$changed = $false
foreach ($field in @("DbPassword", "SessionSecret")) {
    if (-not $settings.PSObject.Properties[$field] -or [string]::IsNullOrWhiteSpace($settings.$field)) {
        $settings | Add-Member -NotePropertyName $field -NotePropertyValue (New-RandomHex 24) -Force
        $changed = $true
    }
}
if ($OwnerPhone -and $OwnerPhone.Trim()) {
    $settings | Add-Member -NotePropertyName "OwnerPhone" -NotePropertyValue $OwnerPhone.Trim() -Force
    $changed = $true
}
# The bundled starter catalog already assigns 03001234567 to a barber, and a
# number may belong to only one identity. The salon's own line is a safe default;
# pass -OwnerPhone 03xxxxxxxxx once to register the number you really want.
if (-not $settings.PSObject.Properties["OwnerPhone"] -or [string]::IsNullOrWhiteSpace($settings.OwnerPhone)) {
    $settings | Add-Member -NotePropertyName "OwnerPhone" -NotePropertyValue "03105301460" -Force
    $changed = $true
}
if ($OwnerPin -and $OwnerPin.Trim()) {
    if ($OwnerPin.Trim() -notmatch "^\d{4,6}$") { throw "The owner PIN must be 4 to 6 digits." }
    $settings | Add-Member -NotePropertyName "OwnerPin" -NotePropertyValue $OwnerPin.Trim() -Force
    $changed = $true
}
if (-not $settings.PSObject.Properties["OwnerPin"] -or [string]::IsNullOrWhiteSpace($settings.OwnerPin)) {
    $generatedPin = (Get-Random -Minimum 100000 -Maximum 999999).ToString()
    $settings | Add-Member -NotePropertyName "OwnerPin" -NotePropertyValue $generatedPin -Force
    $changed = $true
}
if (-not $settings.PSObject.Properties["Database"] -or $settings.Database -ne $Database) {
    $settings | Add-Member -NotePropertyName "Database" -NotePropertyValue $Database -Force
    $changed = $true
}
if (-not $settings.PSObject.Properties["DbUser"] -or $settings.DbUser -ne $DbUser) {
    $settings | Add-Member -NotePropertyName "DbUser" -NotePropertyValue $DbUser -Force
    $changed = $true
}
# Provider credentials are only ever stored in this private, ignored file.
$smsFields = [ordered]@{
    SmsProvider           = $SmsProvider
    TwilioAccountSid      = $TwilioAccountSid
    TwilioAuthToken       = $TwilioAuthToken
    TwilioFromNumber      = $TwilioFromNumber
    WhatsAppToken         = $WhatsAppToken
    WhatsAppPhoneNumberId = $WhatsAppPhoneNumberId
    SmsWebhookUrl         = $SmsWebhookUrl
    SmsWebhookToken       = $SmsWebhookToken
}
foreach ($field in $smsFields.GetEnumerator()) {
    if ($field.Value -and $field.Value.Trim()) {
        $settings | Add-Member -NotePropertyName $field.Key -NotePropertyValue $field.Value.Trim() -Force
        $changed = $true
    }
}
if ($changed) {
    $settings | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $settingsPath -Encoding UTF8
    Write-Step "Saved local database credentials to ops\salon-server.local.json (keep this file private)."
}

function Get-RunningSalonServer {
    if (-not (Test-Path $statePath)) { return $null }
    try { $state = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json } catch { return $null }
    if (-not $state) { return $null }
    $runningPid = 0
    if (-not [int]::TryParse([string]$state.serverPid, [ref]$runningPid)) { return $null }
    if (-not (Get-Process -Id $runningPid -ErrorAction SilentlyContinue)) { return $null }
    $healthPort = if ($state.port) { $state.port } else { $Port }
    try {
        $health = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$healthPort/actuator/health" -TimeoutSec 5
        if ($health.StatusCode -ne 200) { return $null }
    } catch { return $null }
    return $state
}

if (-not $Force) {
    $running = Get-RunningSalonServer
    if ($running) {
        Write-Host ""
        Write-Host "The salon server is already running on this laptop - nothing new was started."
        Write-Host "  Laptop address : http://localhost:$($running.port)"
        if ($running.lanAddress) { Write-Host "  Same Wi-Fi     : http://$($running.lanAddress):$($running.port)" }
        if ($running.tunnelUrl) { Write-Host "  Phone address  : $($running.tunnelUrl)" }
        if ($running.startedAt) { Write-Host "  Started at     : $($running.startedAt)" }
        Write-Host ""
        Write-Host "To restart it: run ops\stop-salon-server.cmd, then start again."
        Write-Host "To force a second copy anyway: add -Force."
        exit 0
    }
}

function Resolve-PostgresTool([string]$name) {
    $candidates = @()
    if ($PostgresBin) { $candidates += (Join-Path $PostgresBin "$name.exe") }
    $candidates += (Join-Path "E:\PostgrelSQL\bin" "$name.exe")
    $candidates += (Join-Path "E:\PostgreSQL\bin" "$name.exe")
    $candidates += (Join-Path (Join-Path $env:ProgramFiles "PostgreSQL") "bin\$name.exe")
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    $command = Get-Command $name -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    return ""
}

function Resolve-PostgresData {
    $candidates = @("E:\PostgrelSQL\data", "E:\PostgreSQL\data")
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "postgresql.conf")) { return $candidate }
    }
    return ""
}

$psql = Resolve-PostgresTool "psql"
if (-not $psql) { throw "PostgreSQL client (psql.exe) was not found. Install PostgreSQL or pass -PostgresBin." }
$pgCtl = Resolve-PostgresTool "pg_ctl"

$adminPassword = $PostgresAdminPassword
if (-not $adminPassword -and $env:SALON_PG_ADMIN_PASSWORD) { $adminPassword = $env:SALON_PG_ADMIN_PASSWORD }
if (-not $adminPassword -and $env:PGPASSWORD) { $adminPassword = $env:PGPASSWORD }

function Invoke-Psql([string]$sql, [string]$asUser = "postgres", [string]$password = $null, [string]$database = "") {
    # -w keeps psql from ever prompting for a password, so this script can run
    # unattended. Passwords are handed to psql through PGPASSWORD for one call.
    if ($null -eq $password) { $password = $adminPassword }
    $previousPassword = $env:PGPASSWORD
    try {
        if ($password) { $env:PGPASSWORD = $password }
        else { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }
        $arguments = @("-h", "127.0.0.1", "-p", "5432", "-U", $asUser, "-w", "-X", "-v", "ON_ERROR_STOP=1")
        if ($database) { $arguments += @("-d", $database) }
        $arguments += @("-tAc", $sql)
        $output = & $psql @arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally {
        if ($null -ne $previousPassword) { $env:PGPASSWORD = $previousPassword }
        else { Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue }
    }
    $text = (($output | ForEach-Object { "$_" }) -join " ").Trim()
    if ($exitCode -ne 0) { throw "PostgreSQL said: $text" }
    return (($output | Where-Object { $_ -isnot [System.Management.Automation.ErrorRecord] }) -join " ").Trim()
}

function Test-PsqlFailure([string]$message) {
    return $message -match "no password was supplied|no password supplied|password authentication failed|authentication failed|password is required" -or
        $message -match "fe_sendauth"
}

function Show-PostgresPasswordHelp([string]$details) {
    Write-Host ""
    Write-Step "PostgreSQL is running but this script cannot log in as '$PostgresAdminUser'."
    Write-Host "  Give it the password you chose for PostgreSQL when it was installed, for example:"
    Write-Host "    powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -PostgresAdminPassword ""your-postgres-password"""
    Write-Host "  (or set the SALON_PG_ADMIN_PASSWORD environment variable and run the script again)."
    if ($details) { Write-Host "  Details: $details" }
    Write-Host ""
}

function Test-PostgresAnswering([string]$password) {
    try { Invoke-Psql "select 1" $PostgresAdminUser $password | Out-Null; return $true } catch { return $false }
}

function Test-PostgresPort {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $connect = $client.BeginConnect("127.0.0.1", 5432, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne(1500)) { return $false }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

function Invoke-Native([string]$file, [string[]]$arguments) {
    # Native tools such as pg_ctl write progress to stderr; that must not stop
    # the script, so the strict error preference is relaxed for these calls only.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try { return (& $file @arguments 2>&1 | ForEach-Object { "$_" }) }
    finally { $ErrorActionPreference = $previous }
}

function ConvertTo-CanonicalPhone([string]$value) {
    # Mirrors PhoneIdentity.canonicalPakistani on the server.
    $digits = ($value -replace "[^0-9]", "")
    if ($digits.StartsWith("0092") -and $digits.Length -eq 14) { $digits = "0" + $digits.Substring(4) }
    elseif ($digits.StartsWith("92") -and $digits.Length -eq 12) { $digits = "0" + $digits.Substring(2) }
    elseif ($digits -match "^3\d{9}$") { $digits = "0" + $digits }
    if ($digits -notmatch "^03\d{9}$") { return "" }
    return $digits
}

function Get-Sha256Hex([string]$value) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($value)) }
    finally { $sha.Dispose() }
    return (($bytes | ForEach-Object { $_.ToString("x2") }) -join "")
}

function Get-PostgresDataDir {
    $dataDir = Resolve-PostgresData
    if ($dataDir -and (Test-Path (Join-Path $dataDir "pg_hba.conf"))) { return $dataDir }
    return ""
}

<#
  First run only: PostgreSQL asks for the superuser password that was chosen when
  PostgreSQL was installed. When that password is not supplied this helper opens a
  temporary, local-only "trust" line in pg_hba.conf, provisions the salon role and
  database, and restores the original file immediately afterwards. The entry never
  affects other machines and pg_hba.conf is put back byte for byte.
#>
function Open-TemporaryLocalTrust {
    if (-not $pgCtl) { return $null }
    $dataDir = Get-PostgresDataDir
    if (-not $dataDir) { return $null }
    $hba = Join-Path $dataDir "pg_hba.conf"
    $backup = "$hba.salon-backup"
    try {
        $original = Get-Content -Raw -LiteralPath $hba
        if (-not $original) { return $null }
        Copy-Item -LiteralPath $hba -Destination $backup -Force
        $trust = "# Temporary salon-server provisioning entry (added and removed automatically)`n" +
                 "host    all             all             127.0.0.1/32            trust`n" +
                 "host    all             all             ::1/128                 trust`n"
        Set-Content -LiteralPath $hba -Value ($trust + $original) -Encoding ascii -NoNewline
        Invoke-Native $pgCtl @("reload", "-D", $dataDir) | Out-Null
        for ($attempt = 0; $attempt -lt 15; $attempt++) {
            Start-Sleep -Milliseconds 800
            if (Test-PostgresAnswering "") {
                Write-Step "Prepared a temporary local database login (pg_hba.conf is restored in a moment)."
                return @{ dataDir = $dataDir; hba = $hba; backup = $backup; original = $original }
            }
        }
        Set-Content -LiteralPath $hba -Value $original -Encoding ascii -NoNewline
        Invoke-Native $pgCtl @("reload", "-D", $dataDir) | Out-Null
        Remove-Item -LiteralPath $backup -Force -ErrorAction SilentlyContinue
        return $null
    } catch {
        return $null
    }
}

function Close-TemporaryLocalTrust($state) {
    if (-not $state) { return }
    try {
        Set-Content -LiteralPath $state.hba -Value $state.original -Encoding ascii -NoNewline
        Invoke-Native $pgCtl @("reload", "-D", $state.dataDir) | Out-Null
        $restored = Get-Content -Raw -LiteralPath $state.hba
        if ($restored -eq $state.original) {
            Remove-Item -LiteralPath $state.backup -Force -ErrorAction SilentlyContinue
            Write-Step "PostgreSQL login settings were restored to normal."
        } else {
            Write-Host "[salon] WARNING: pg_hba.conf did not restore cleanly. A copy of the original is at $($state.backup)."
        }
    } catch {
        Write-Host "[salon] WARNING: could not confirm that pg_hba.conf was restored. A copy of the original is at $($state.backup)."
    }
}

# The salon role can already read its own database on every normal start, so the
# PostgreSQL superuser password is only ever needed for the very first run.
$databaseReachable = $false
$probeError = ""
try {
    Invoke-Psql "select 1" $DbUser $settings.DbPassword $Database | Out-Null
    $databaseReachable = $true
    Write-Step "The salon database '$Database' is ready."
} catch {
    $probeError = "$($_.Exception.Message)"
}

if (-not $databaseReachable) {
    $portOpen = Test-PostgresPort
    if (-not $portOpen -and $pgCtl) {
        $dataDir = Get-PostgresDataDir
        if ($dataDir) {
            Write-Step "Starting the local PostgreSQL service from $dataDir ..."
            Invoke-Native $pgCtl @("start", "-D", $dataDir, "-w") | Out-Null
            Start-Sleep -Seconds 3
            $portOpen = Test-PostgresPort
        }
    }
    if (-not $portOpen) {
        throw "PostgreSQL is not reachable on 127.0.0.1:5432. Start the PostgreSQL service, then run this script again."
    }

    # "Rejected" means the server answered but would not accept the login, which
    # is different from a PostgreSQL server that is not running at all.
    $authRejected = -not (Test-PostgresAnswering $adminPassword)

    Write-Step "Preparing the salon database '$Database' (first run) ..."
    $trust = $null
    $adminLogin = $adminPassword
    try {
        if ($authRejected) {
            if ($adminPassword) {
                Show-PostgresPasswordHelp "the supplied administrator password was not accepted"
                throw "PostgreSQL administrator login failed. Rerun with the correct -PostgresAdminPassword (see above)."
            }
            $trust = Open-TemporaryLocalTrust
            if (-not $trust) {
                Show-PostgresPasswordHelp "no administrator password was supplied and pg_hba.conf could not be adjusted"
                throw "PostgreSQL administrator login failed. Rerun with -PostgresAdminPassword (see above)."
            }
            $adminLogin = ""
        }

        $roleExists = Invoke-Psql "select 1 from pg_roles where rolname = '$DbUser'" $PostgresAdminUser $adminLogin
        if ($roleExists -ne "1") {
            Invoke-Psql "create role $DbUser login password '$($settings.DbPassword)'" $PostgresAdminUser $adminLogin | Out-Null
            Write-Step "Created the salon database user '$DbUser'."
        } else {
            Invoke-Psql "alter role $DbUser with password '$($settings.DbPassword)'" $PostgresAdminUser $adminLogin | Out-Null
        }
        $databaseExists = Invoke-Psql "select 1 from pg_database where datname = '$Database'" $PostgresAdminUser $adminLogin
        if ($databaseExists -ne "1") {
            Invoke-Psql "create database $Database owner $DbUser" $PostgresAdminUser $adminLogin | Out-Null
            Write-Step "Created the salon database '$Database'."
        }
    } finally {
        Close-TemporaryLocalTrust $trust
    }

    # Confirm the salon role can log in with the stored password on its own.
    Start-Sleep -Milliseconds 500
    try {
        Invoke-Psql "select 1" $DbUser $settings.DbPassword $Database | Out-Null
    } catch {
        throw "The salon database was prepared, but the salon user could not sign in afterwards. Details: $($_.Exception.Message)"
    }
    $databaseReachable = $true
    Write-Step "The salon database '$Database' is ready."
}

$jar = Join-Path $root "server\target\ayan-salon-server-0.1.0-SNAPSHOT.jar"
Write-Step "Publishing the salon web app inside the server ..."
& (Join-Path $opsDir "sync-server-web.ps1") | Out-Null

# A salon server left over from an earlier start keeps the jar file open, which
# makes Maven fail, so close it before building.
if (Test-Path -LiteralPath $statePath) {
    try {
        $previousState = Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json
        foreach ($processId in @($previousState.tunnelPid, $previousState.serverPid)) {
            if (-not $processId) { continue }
            if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
                Write-Step "Stopping the salon server that was already running (pid $processId) ..."
                Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
            }
        }
    } catch { }
    Remove-Item -LiteralPath $statePath -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

if (-not $SkipBuild) {
    # Maven copies resources over the previous build and never deletes renamed
    # ones, so a stale file (for example an old migration with a version number
    # that still exists under a new name) would end up inside the packaged jar.
    foreach ($stale in @("server\target\classes\db\migration", "server\target\classes\static")) {
        $stalePath = Join-Path $root $stale
        if (Test-Path -LiteralPath $stalePath) { Remove-Item -LiteralPath $stalePath -Recurse -Force }
    }
    Write-Step "Building the salon server (this can take a minute) ..."
    $env:MAVEN_OPTS = "-Xmx1g"
    Push-Location (Join-Path $root "server")
    try {
        & (Join-Path $root "server\mvnw.cmd") -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "The server build failed." }
    } finally {
        Pop-Location
    }
}
if (-not (Test-Path $jar)) { throw "The packaged server jar was not found at $jar." }

# A duplicated Flyway version only shows up as a startup crash, so catch it while
# the server is still being prepared.
Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue
$archive = [System.IO.Compression.ZipFile]::OpenRead($jar)
$migrationVersions = @{}
$duplicateVersions = @()
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match "db/migration/V(\d+)__") {
            $version = $Matches[1]
            if ($migrationVersions.ContainsKey($version)) {
                $duplicateVersions += "V$version ($($migrationVersions[$version]) and $($entry.Name))"
            } else {
                $migrationVersions[$version] = $entry.Name
            }
        }
    }
} finally {
    $archive.Dispose()
}
if ($duplicateVersions.Count -gt 0) {
    throw "The server build has duplicate database migrations: $($duplicateVersions -join '; '). Delete the server\target folder and run the script again."
}
Write-Step "Checked the server build: $($migrationVersions.Count) database migrations, no duplicates."

$java = ""
foreach ($candidate in @(
        (Join-Path $env:ProgramFiles "Java\jdk-21.0.12.1\bin\java.exe"),
        (Join-Path $env:ProgramFiles "Microsoft\jdk-17.0.20.101-hotspot\bin\java.exe"),
        (Join-Path $env:ProgramFiles "Java\jdk-17.0.20.101-hotspot\bin\java.exe"))) {
    if (Test-Path $candidate) { $java = $candidate; break }
}
if (-not $java) {
    $javaCommand = Get-Command java -ErrorAction SilentlyContinue
    if ($javaCommand) { $java = $javaCommand.Source }
}
if (-not $java) { throw "A Java 17+ runtime was not found. Install the JDK, then run this script again." }

$ownerPhone = if ($OwnerPhone -and $OwnerPhone.Trim()) { $OwnerPhone.Trim() }
    elseif ($settings.PSObject.Properties["OwnerPhone"]) { "$($settings.OwnerPhone)".Trim() }
    else { "" }

Write-Step "Starting the salon server on port $Port ..."
if ($ownerPhone) {
    $canonicalOwner = ConvertTo-CanonicalPhone $ownerPhone
    if (-not $canonicalOwner) {
        throw "The owner mobile number '$ownerPhone' is not a complete Pakistani mobile number (for example 03001234567)."
    }
    $ownerHash = Get-Sha256Hex $canonicalOwner
    $existingOwner = Invoke-Psql "select count(*) from auth_accounts where salon_id = '$SalonId' and role = 'OWNER' and phone_hash = '$ownerHash' and status = 'ACTIVE'" $DbUser $settings.DbPassword $Database
    if ($existingOwner -ne "1") {
        $records = @()
        $records += Invoke-Psql "select string_agg(coalesce(name, '?') || '|' || coalesce(phone, ''), chr(10)) from staff where salon_id = '$SalonId' and active = true" $DbUser $settings.DbPassword $Database
        $records += Invoke-Psql "select string_agg(coalesce(name, '?') || '|' || coalesce(phone, ''), chr(10)) from customers where salon_id = '$SalonId'" $DbUser $settings.DbPassword $Database
        $takenBy = @()
        foreach ($record in $records) {
            if (-not $record) { continue }
            foreach ($row in ($record -split "`n")) {
                $parts = $row -split "\|", 2
                if ($parts.Count -lt 2) { continue }
                if ((ConvertTo-CanonicalPhone $parts[1]) -eq $canonicalOwner) {
                    $takenBy += "$($parts[0]) ($($parts[1]))"
                }
            }
        }
        if ($takenBy.Count -gt 0) {
            Write-Host ""
            Write-Step "The owner mobile $canonicalOwner is already used in this salon by $($takenBy -join ', ')."
            Write-Host "  One mobile number cannot be both the owner and a barber/customer, so start the server with the"
            Write-Host "  owner's own number instead, for example:"
            Write-Host "    powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -OwnerPhone 03007654321"
            Write-Host "  The salon ships with three starter barbers on 03001234567, 03001234568 and 03001234569 -"
            Write-Host "  change those in the app (Owner > Staff) if the owner uses one of them."
            Write-Host ""
            throw "Choose a different owner mobile number."
        }
    }
}

$env:SERVER_PORT = "$Port"
$env:JDBC_DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/$Database"
$env:JDBC_DATABASE_USERNAME = $DbUser
$env:JDBC_DATABASE_PASSWORD = $settings.DbPassword
$env:AYAN_AUTH_SESSION_SECRET = $settings.SessionSecret
# "local" turns on the on-machine verification codes (written to the file below)
# so a new customer can still be verified before any SMS provider is connected.
$env:SPRING_PROFILES_ACTIVE = "local"
$env:AYAN_OTP_INBOX_PATH = Join-Path $opsDir "salon-sign-in-codes.log"
$env:AYAN_AUTH_OWNER_SALON_ID = $SalonId
$env:AYAN_AUTH_OWNER_PHONE = $ownerPhone
$env:AYAN_AUTH_OWNER_PIN = $settings.OwnerPin
# "null" is the origin a browser sends for the offline Android bundle; the
# GitHub Pages address is the permanent public link for customers.
$env:AYAN_WEB_ALLOWED_ORIGINS = "http://localhost:$Port,null,https://slowyy0477.github.io"
# Real sign-in codes. When these are empty the server keeps writing the code to
# ops\salon-sign-in-codes.log so the owner can read it to the customer.
$env:AYAN_SMS_PROVIDER = [string]$settings.SmsProvider
$env:AYAN_TWILIO_ACCOUNT_SID = [string]$settings.TwilioAccountSid
$env:AYAN_TWILIO_AUTH_TOKEN = [string]$settings.TwilioAuthToken
$env:AYAN_TWILIO_FROM_NUMBER = [string]$settings.TwilioFromNumber
$env:AYAN_WHATSAPP_TOKEN = [string]$settings.WhatsAppToken
$env:AYAN_WHATSAPP_PHONE_NUMBER_ID = [string]$settings.WhatsAppPhoneNumberId
$env:AYAN_SMS_WEBHOOK_URL = [string]$settings.SmsWebhookUrl
$env:AYAN_SMS_WEBHOOK_TOKEN = [string]$settings.SmsWebhookToken

$stdout = Join-Path $logDir "salon-server.out.log"
$stderr = Join-Path $logDir "salon-server.err.log"
$serverProcess = Start-Process -FilePath $java -ArgumentList @("-jar", $jar) -WorkingDirectory (Join-Path $root "server") `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru

# Keep this laptop from falling asleep while the salon is being served. This uses
# a normal Windows "system required" request, so it needs no administrator rights
# and stops by itself when the agent process is closed.
$keepAwakeScript = Join-Path $opsDir "keep-awake-agent.ps1"
$keepAwakeProcess = $null
if ((Test-Path $keepAwakeScript) -and -not $NoKeepAwake) {
    try {
        $keepAwakeProcess = Start-Process -FilePath "powershell" `
            -ArgumentList @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $keepAwakeScript) `
            -WindowStyle Hidden -PassThru
    } catch {
        Write-Step "The keep-awake helper could not start; keep the lid open so the salon stays reachable."
    }
}

$healthy = $false
for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Seconds 2
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$Port/actuator/health" -TimeoutSec 3
        if ($response.StatusCode -eq 200) { $healthy = $true; break }
    } catch { }
    # A crashed start never becomes healthy, so stop as soon as the process is gone.
    if ($serverProcess.HasExited) {
        Start-Sleep -Seconds 1
        break
    }
}
if (-not $healthy) {
    Write-Host (Get-Content -Tail 30 -LiteralPath $stdout -ErrorAction SilentlyContinue)
    Write-Host (Get-Content -Tail 30 -LiteralPath $stderr -ErrorAction SilentlyContinue)
    $failureText = ""
    try { $failureText = (Get-Content -Raw -LiteralPath $stdout -ErrorAction SilentlyContinue) } catch { }
    if ($failureText -match "Owner phone .*already belongs to") {
        Write-Host ""
        Write-Host "That mobile number already belongs to a barber or staff account in the starter catalog." -ForegroundColor Yellow
        Write-Host "Run the command again with your own number, for example:" -ForegroundColor Yellow
        Write-Host "  powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -OwnerPhone 03xxxxxxxxx -OwnerPin 123456" -ForegroundColor Yellow
        Write-Host ""
    }
    throw "The salon server did not become healthy. See tmp\salon-server.out.log."
}
Write-Step "Salon server is healthy."

$lanAddress = ""
try {
    $lanAddress = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.PrefixOrigin -ne "WellKnown" } |
        Select-Object -First 1 -ExpandProperty IPAddress)
} catch { }

$tunnelUrl = ""
if (-not $NoTunnel) {
    $cloudflared = Join-Path $opsDir "cloudflared.exe"
    if (-not (Test-Path $cloudflared)) {
        Write-Step "Downloading the free Cloudflare tunnel client (one time) ..."
        try {
            Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile $cloudflared
        } catch {
            Write-Step "The tunnel client could not be downloaded. Phones on the same Wi-Fi can still use the laptop address below."
        }
    }
    if (Test-Path $cloudflared) {
        $tunnelLog = Join-Path $logDir "salon-tunnel.log"
        # cloudflared prints its banner - including the public address - on
        # stderr, so both streams have to be searched or the address is missed.
        $tunnelErrorLog = Join-Path $logDir "salon-tunnel.err.log"
        Remove-Item -LiteralPath $tunnelLog -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $tunnelErrorLog -ErrorAction SilentlyContinue
        Write-Step "Opening the free https tunnel ..."
        $tunnelProcess = Start-Process -FilePath $cloudflared `
            -ArgumentList @("tunnel", "--no-autoupdate", "--url", "http://127.0.0.1:$Port") `
            -RedirectStandardOutput $tunnelLog -RedirectStandardError $tunnelErrorLog -WindowStyle Hidden -PassThru
        # A quick-tunnel address looks like https://three-random-words.trycloudflare.com.
        # Error text such as "Post https://api.trycloudflare.com/tunnel failed" also
        # contains a matching host, so a candidate is only accepted after it really
        # serves this salon server; otherwise the phones would be given a dead link.
        $reservedHosts = @("api.trycloudflare.com", "www.trycloudflare.com", "developers.cloudflare.com", "blog.cloudflare.com", "trycloudflare.com")
        function Test-TunnelCandidate([string]$candidate) {
            if (-not $candidate) { return $false }
            $host_ = ""
            try { $host_ = ([uri]$candidate).Host.ToLowerInvariant() } catch { return $false }
            if ($reservedHosts -contains $host_) { return $false }
            if ($host_ -notlike "*.trycloudflare.com") { return $false }
            try {
                $probe = Invoke-WebRequest -UseBasicParsing -Uri "$candidate/actuator/health" -TimeoutSec 6
                # This Windows PowerShell returns the body as a byte array, so it
                # has to be decoded before the "UP" marker can be checked.
                $text = if ($probe.Content -is [byte[]]) { [System.Text.Encoding]::UTF8.GetString($probe.Content) } else { [string]$probe.Content }
                return ($probe.StatusCode -eq 200 -and $text -match "UP")
            } catch { return $false }
        }
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
                if (Test-TunnelCandidate $candidate) { $tunnelUrl = $candidate; break }
            }
        }
        if ($tunnelUrl) {
            # Kept on disk so the owner can find the address again after the
            # window is closed; it changes every time the tunnel restarts.
            Set-Content -LiteralPath (Join-Path $opsDir "salon-public-url.txt") -Value $tunnelUrl -Encoding ASCII
            # The permanent public link reads this address from the repository,
            # so a customer link keeps working after the laptop restarts.
            $publishScript = Join-Path $opsDir "publish-salon-address.ps1"
            if (Test-Path $publishScript) {
                & powershell -NoProfile -ExecutionPolicy Bypass -File $publishScript -Url $tunnelUrl | Out-Null
            }
        } else {
            # Never leave a stale address on disk: a phone pointed at a dead link
            # looks exactly like a broken salon.
            Remove-Item -LiteralPath (Join-Path $opsDir "salon-public-url.txt") -ErrorAction SilentlyContinue
            Write-Step "The free internet link is not available right now (check tmp\salon-tunnel.err.log)."
            Write-Step "Salon phones on this Wi-Fi can still work - use the 'Same Wi-Fi' address below."
        }
    }
}

[pscustomobject]@{
    startedAt   = (Get-Date).ToString("o")
    port        = $Port
    serverPid   = $serverProcess.Id
    tunnelPid   = if ($tunnelProcess) { $tunnelProcess.Id } else { $null }
    keepAwakePid = if ($keepAwakeProcess) { $keepAwakeProcess.Id } else { $null }
    tunnelUrl   = $tunnelUrl
    lanAddress  = $lanAddress
    database    = $Database
} | ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8

Write-Host ""
Write-Host "Ayan Beauty Salon server is running on this laptop."
Write-Host "  Salon database : PostgreSQL database '$Database' on 127.0.0.1:5432"
Write-Host "  Laptop address : http://localhost:$Port"
if ($lanAddress) { Write-Host "  Same Wi-Fi     : http://${lanAddress}:$Port (browser on this network)" }
if ($tunnelUrl) { Write-Host "  Phone address  : $tunnelUrl" }
elseif ($lanAddress) {
    Write-Host "  Phone address  : http://${lanAddress}:$Port   (works on this Wi-Fi)"
    Write-Host "                   The free internet link could not be opened just now."
    Write-Host "                   Phones on the salon Wi-Fi can use the address above."
}
if ($keepAwakeProcess) { Write-Host "  Stay awake     : ON (this laptop will not sleep while the salon is serving)" }
if ($settings.SmsProvider) {
    Write-Host "  Sign-in codes  : real $($settings.SmsProvider) messages sent to the customer's mobile"
} else {
    Write-Host "  Sign-in codes  : written on this laptop to ops\salon-sign-in-codes.log"
    Write-Host "                   Connect a real SMS provider any time: ops\connect-sms.cmd"
}
Write-Host ""
Write-Host "On each salon phone: open the app, tap the AB mark five times, open Owner > Settings,"
Write-Host "and paste the phone address above into 'Salon server address', then save."
if ($ownerPhone) {
    Write-Host "Owner sign-in on the server: mobile $ownerPhone with PIN $($settings.OwnerPin)"
} else {
    Write-Host "Owner sign-in on the server: rerun with -OwnerPhone 03xxxxxxxxx to register the owner mobile."
    Write-Host "The owner PIN stored on this laptop is $($settings.OwnerPin)."
}
Write-Host "Change or reset that PIN by rerunning this script with -OwnerPin 123456 and restarting."
Write-Host "Keep this window's laptop awake while the salon is open. Stop the server with:"
Write-Host "  powershell -ExecutionPolicy Bypass -File .\ops\stop-salon-server.ps1"
