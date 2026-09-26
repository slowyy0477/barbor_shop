<#
  Copies the salon web app into the optional Spring server, so a server the
  owner starts himself can serve both the phone interface and the API from one
  address. Same-origin hosting keeps the Android WebView working without
  relaxing its file-access or cleartext rules.

  Only needed when the owner chooses to run the Java server on his own machine.
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
    "icon.svg",
    "icon-192.png",
    "icon-512.png",
    "icon-maskable-512.png",
    "apple-touch-icon.png"
)

New-Item -ItemType Directory -Force -Path $target | Out-Null
foreach ($asset in $assets) {
    $source = Join-Path $root $asset
    if (-not (Test-Path -LiteralPath $source)) { throw "Missing root asset: $source" }
    Copy-Item -LiteralPath $source -Destination $target -Force
}

Write-Output "Synced $($assets.Count) web assets to $target"
