<#
  Keeps this laptop awake while the salon server is running and watches the free
  phone link.

  It asks Windows for "system required" - the same thing a video player does -
  without changing any Windows power setting, so no administrator rights and
  nothing to undo. The screen may still switch off to save electricity.

  It also checks every couple of minutes that the free internet link still
  answers. When the link has dropped it opens a fresh one and republishes the
  address, so the phones find the salon again without the owner doing anything.

  start-salon-server.ps1 launches it hidden, stop-salon-server.ps1 closes it.
#>
$ErrorActionPreference = "SilentlyContinue"
$opsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$urlPath = Join-Path $opsDir "salon-public-url.txt"
$refreshScript = Join-Path $opsDir "refresh-public-url.ps1"

if (-not ("Salon.PowerNative" -as [type])) {
    Add-Type -Namespace Salon -Name PowerNative -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("kernel32.dll")]
public static extern uint SetThreadExecutionState(uint esFlags);
'@
}

$ES_CONTINUOUS = [uint32]2147483648
$ES_SYSTEM_REQUIRED = [uint32]1
$flags = [uint32]($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED)

function Test-SalonPhoneLink([string]$url) {
    if (-not $url) { return $false }
    try {
        $probe = Invoke-WebRequest -UseBasicParsing -Uri "$url/actuator/health" -TimeoutSec 20
        $text = if ($probe.Content -is [byte[]]) {
            [System.Text.Encoding]::UTF8.GetString($probe.Content)
        } else {
            [string]$probe.Content
        }
        return ($probe.StatusCode -eq 200 -and $text -match "UP")
    } catch { return $false }
}

$watchCounter = 0
while ($true) {
    try { [void][Salon.PowerNative]::SetThreadExecutionState($flags) } catch { }
    $watchCounter++
    # Roughly every two minutes: is the free phone link still alive?
    if ($watchCounter -ge 3) {
        $watchCounter = 0
        $currentUrl = ""
        if (Test-Path -LiteralPath $urlPath) {
            $currentUrl = (Get-Content -Raw -LiteralPath $urlPath).Trim()
        }
        if (-not (Test-SalonPhoneLink $currentUrl)) {
            if (Test-Path -LiteralPath $refreshScript) {
                & powershell -NoProfile -ExecutionPolicy Bypass -File $refreshScript | Out-Null
            }
        }
    }
    Start-Sleep -Seconds 45
}
