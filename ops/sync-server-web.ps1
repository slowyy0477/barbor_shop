<#
  Copies the salon web app into the Spring server so the same https address
  serves both the phone interface and the API. Same-origin hosting keeps the
  Android WebView working without relaxing its file-access or cleartext rules.

  start-salon-server.ps1 runs this automatically before every build.
#>
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$target = Join-Path $root "server\src\main\resources\static"
$assets = @(
    "index.html",
    "api-client.js",
    "app.js",
    "styles.css",
    "manifest.webmanifest",
    "service-worker.js",
    "icon.svg"
)

New-Item -ItemType Directory -Force -Path $target | Out-Null
foreach ($asset in $assets) {
    $source = Join-Path $root $asset
    if (-not (Test-Path -LiteralPath $source)) { throw "Missing root asset: $source" }
    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Output "Synced $($assets.Count) web assets to $target"
