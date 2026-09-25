$ErrorActionPreference = 'Stop'

$androidDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$workspaceDir = Split-Path -Parent $androidDir
$assetDir = Join-Path $androidDir 'app/src/main/assets'
$assetNames = @(
    'index.html',
    'api-client.js',
    'app.js',
    'styles.css',
    'manifest.webmanifest',
    'service-worker.js',
    'icon.svg',
    'icon-192.png',
    'icon-512.png',
    'icon-maskable-512.png',
    'apple-touch-icon.png'
)

New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
foreach ($assetName in $assetNames) {
    $sourcePath = Join-Path $workspaceDir $assetName
    if (-not (Test-Path -LiteralPath $sourcePath)) {
        throw "Missing root asset: $sourcePath"
    }
    Copy-Item -LiteralPath $sourcePath -Destination $assetDir -Force
}

# Keep the browser-only fixture file out of the APK. The tracked placeholder is
# intentionally harmless and lets index.html use the same script order in WebView.
$qaAssetPath = Join-Path $assetDir 'qa-seed.js'
if (-not (Test-Path -LiteralPath $qaAssetPath) -or (Select-String -Path $qaAssetPath -Pattern 'Ali Raza|EP-DEMO|cus_001' -Quiet)) {
    throw "Android qa-seed.js must remain the fixture-free placeholder: $qaAssetPath"
}

Write-Output "Synced $($assetNames.Count) web assets to $assetDir"
