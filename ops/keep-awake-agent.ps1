<#
  Keeps this laptop awake while the salon server is running.

  It asks Windows for "system required" - the same thing a video player does -
  without changing any Windows power setting, so no administrator rights and
  nothing to undo. The screen may still switch off to save electricity.

  start-salon-server.ps1 launches it hidden, stop-salon-server.ps1 closes it.
#>
$ErrorActionPreference = "SilentlyContinue"

if (-not ("Salon.PowerNative" -as [type])) {
    Add-Type -Namespace Salon -Name PowerNative -MemberDefinition @'
[System.Runtime.InteropServices.DllImport("kernel32.dll")]
public static extern uint SetThreadExecutionState(uint esFlags);
'@
}

$ES_CONTINUOUS = [uint32]2147483648
$ES_SYSTEM_REQUIRED = [uint32]1
$flags = [uint32]($ES_CONTINUOUS -bor $ES_SYSTEM_REQUIRED)

while ($true) {
    try { [void][Salon.PowerNative]::SetThreadExecutionState($flags) } catch { }
    Start-Sleep -Seconds 45
}
