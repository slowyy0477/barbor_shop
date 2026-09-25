param(
    [string]$Alias = 'ayan-salon-release',
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '.')).Path
$keysDir = Join-Path $androidRoot 'keys'
$keystorePath = Join-Path $keysDir 'ayan-salon-release.jks'
$propertiesPath = Join-Path $androidRoot 'release-signing.properties'

if (Test-Path -LiteralPath $propertiesPath) {
    throw "Signing properties already exist at $propertiesPath. Keep using the existing key for updates."
}
if (Test-Path -LiteralPath $keystorePath) {
    throw "A keystore already exists at $keystorePath but its properties file is missing. Restore the properties file or choose a new key path before continuing."
}
if ($Alias -notmatch '^[A-Za-z0-9._-]{3,32}$') {
    # Keep shell metacharacters out of the keytool arguments.
    throw 'Alias must contain only letters, numbers, dot, underscore or hyphen.'
}

$javaHome = $env:ANDROID_JAVA_HOME
if (-not $javaHome) { $javaHome = $env:JAVA_HOME }
if (-not $javaHome) {
    $javaHome = Get-ChildItem -Path "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin\keytool.exe') } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
$keytool = if ($javaHome) { Join-Path $javaHome 'bin\keytool.exe' } else { 'keytool.exe' }
if (-not (Test-Path -LiteralPath $keytool) -and $keytool -eq 'keytool.exe') {
    $keytool = (Get-Command keytool.exe -ErrorAction SilentlyContinue).Source
}
if (-not $keytool -or -not (Test-Path -LiteralPath $keytool)) {
    throw 'Java keytool was not found. Set ANDROID_JAVA_HOME or JAVA_HOME to a full JDK.'
}

New-Item -ItemType Directory -Force -Path $keysDir | Out-Null
$bytes = New-Object byte[] 24
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$password = [Convert]::ToBase64String($bytes).Replace('+','A').Replace('/','B').Replace('=','C')
$subject = 'CN=Ayan Beauty Salon, OU=Mobile, O=Ayan Beauty Salon, L=Kamra Kalan, ST=Punjab, C=PK'

& $keytool -genkeypair -v -keystore $keystorePath -storetype JKS -storepass $password -keypass $password -alias $Alias -keyalg RSA -keysize 2048 -validity $ValidityDays -dname $subject -noprompt
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

$escapedPath = $keystorePath.Replace('\', '\\')
@"
storeFile=$escapedPath
storePassword=$password
keyAlias=$Alias
keyPassword=$password
"@ | Set-Content -LiteralPath $propertiesPath -Encoding ASCII

Write-Host "Created release keystore: $keystorePath"
Write-Host "Created local signing properties: $propertiesPath"
Write-Host 'Back up both files securely. They are ignored by Git and required for future APK updates.'
