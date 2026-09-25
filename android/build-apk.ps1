param(
    [ValidateSet('Debug', 'Release')]
    [string]$Variant = 'Debug',
    [switch]$SkipLint,
    # Reuse the Gradle cache without touching the network. Useful on a slow or
    # metered connection once the project has been built at least once.
    [switch]$Offline,
    [string]$ApiBaseUrl = '',
    [string]$SalonId = '00000000-0000-0000-0000-000000000001'
)

$ErrorActionPreference = 'Stop'
$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '.')).Path
$repoRoot = (Resolve-Path (Join-Path $androidRoot '..')).Path

# Gradle 6.7.1 supports Java 8-15. On older Windows installs the full JDK
# may be Java 17 while a Java 8 runtime is available; run Gradle on Java 8
# and let app/build.gradle fork javac from ANDROID_JAVA_HOME.
$compilerHome = $env:ANDROID_JAVA_HOME
if (-not $compilerHome -and $env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $compilerHome = $env:JAVA_HOME
}
if (-not $compilerHome) {
    $compilerHome = Get-ChildItem -Path "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^(jdk|microsoft-jdk)-' -and (Test-Path (Join-Path $_.FullName 'bin\javac.exe')) } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1 -ExpandProperty FullName
}
if ($compilerHome) {
    $env:ANDROID_JAVA_HOME = $compilerHome
}
if (-not $compilerHome) {
    throw 'A full JDK with javac is required. Set ANDROID_JAVA_HOME to a JDK installation.'
}

$runtimeJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
$savedErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVersion = (& $runtimeJava -version 2>&1 | Out-String)
$ErrorActionPreference = $savedErrorActionPreference
if ($javaVersion -match 'version\s+"(?:1\.)?(1[6-9]|[2-9][0-9])') {
    $java8Home = Get-ChildItem -Path "$env:ProgramFiles\Java" -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match '^jre-?1\.8' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
        Select-Object -First 1 -ExpandProperty FullName
    if ($java8Home) {
        $env:JAVA_HOME = $java8Home
    }
}
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $repoRoot '.gradle-user'
}
$workspaceRoot = (Resolve-Path (Join-Path $androidRoot '..')).Path

# Keep Gradle's wrapper and dependency downloads on the workspace drive by default.
if (-not $env:GRADLE_USER_HOME) {
    $env:GRADLE_USER_HOME = Join-Path $workspaceRoot '.gradle-user'
}
New-Item -ItemType Directory -Force -Path $env:GRADLE_USER_HOME | Out-Null

function Get-SdkFromLocalProperties {
    $propertiesPath = Join-Path $androidRoot 'local.properties'
    if (-not (Test-Path -LiteralPath $propertiesPath)) {
        return $null
    }

    $line = Get-Content -LiteralPath $propertiesPath |
        Where-Object { $_ -match '^\s*sdk\.dir\s*=' } |
        Select-Object -First 1
    if (-not $line) {
        return $null
    }

    $value = ($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim()
    if (-not $value) {
        return $null
    }

    # local.properties uses backslash escaping for Windows paths.
    return ($value -replace '\\\\', '\')
}

$sdkRoot = $env:ANDROID_SDK_ROOT
if (-not $sdkRoot) {
    $sdkRoot = $env:ANDROID_HOME
}
if (-not $sdkRoot) {
    $sdkRoot = Get-SdkFromLocalProperties
}

if (-not $sdkRoot) {
    throw @"
Android SDK was not found.
Install Android API 30 and Build Tools 30.0.3, then set ANDROID_SDK_ROOT or add sdk.dir to android/local.properties.
GitHub Actions can build this project automatically from the Actions tab.
"@
}

$sdkRoot = [Environment]::ExpandEnvironmentVariables($sdkRoot)
$sdkRoot = (Resolve-Path -LiteralPath $sdkRoot -ErrorAction Stop).Path
$platformJar = Join-Path $sdkRoot 'platforms\android-30\android.jar'
$aapt2 = Join-Path $sdkRoot 'build-tools\30.0.3\aapt2.exe'
if (-not (Test-Path -LiteralPath $platformJar)) {
    throw "Android API 30 is missing: $platformJar"
}
if (-not (Test-Path -LiteralPath $aapt2)) {
    throw "Android Build Tools 30.0.3 are missing: $aapt2"
}

$env:ANDROID_SDK_ROOT = $sdkRoot
$env:ANDROID_HOME = $sdkRoot

Write-Host 'Syncing bundled web assets...'
& (Join-Path $androidRoot 'sync-assets.ps1')
if (-not $?) {
    throw "Asset sync failed with exit code $LASTEXITCODE"
}

$task = "assemble$Variant"
if ($Variant -eq 'Release' -and -not (Test-Path -LiteralPath (Join-Path $androidRoot 'release-signing.properties'))) {
    throw "Release signing is not configured. Run .\create-release-signing.ps1 once, then rerun this command. The keystore and properties stay local and are ignored by Git."
}
Write-Host "Building $task..."
Push-Location $androidRoot
try {
    $cachedGradle = Get-ChildItem -Path (Join-Path $env:GRADLE_USER_HOME 'wrapper\dists\gradle-6.7.1-bin') -Filter 'gradle.bat' -Recurse -ErrorAction SilentlyContinue |
        Sort-Object FullName.Length |
        Select-Object -First 1 -ExpandProperty FullName
    $gradleCommand = if ($cachedGradle) { $cachedGradle } else { Join-Path $androidRoot 'gradlew.bat' }
    $gradleArgs = @('--no-daemon', $task, "-PAPI_BASE_URL=$ApiBaseUrl", "-PsalonId=$SalonId")
    if ($Offline) { $gradleArgs = @('--offline') + $gradleArgs }
    if ($SkipLint) { $gradleArgs += '-x'; $gradleArgs += "lintVital$Variant" }
    & $gradleCommand @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$variantDir = $Variant.ToLowerInvariant()
$apkPath = Join-Path $androidRoot "app\build\outputs\apk\$variantDir\app-$variantDir.apk"
if (-not (Test-Path -LiteralPath $apkPath)) {
    $unsignedPath = Join-Path $androidRoot "app\build\outputs\apk\$variantDir\app-$variantDir-unsigned.apk"
    if (Test-Path -LiteralPath $unsignedPath) {
        throw "Gradle produced an unsigned APK at $unsignedPath. Configure release signing and build again."
    }
    throw "Gradle completed but APK was not found at $apkPath"
}

$hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $apkPath).Hash.ToLowerInvariant()
Write-Host "APK: $apkPath"
Write-Host "SHA-256: $hash"
