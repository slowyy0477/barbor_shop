<#
  Prints the most recent sign-in code so the salon owner can read it to the
  customer standing at the counter.

  Why this exists: a laptop server has no SMS contract yet, so the app writes
  each verification code to ops\salon-sign-in-codes.log instead of sending an
  SMS. Codes expire in about five minutes and are single use - read the newest
  line only, and never share the file itself.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\ops\show-last-code.ps1
#>

$ErrorActionPreference = "Stop"
$opsDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$logPath = Join-Path $opsDir "salon-sign-in-codes.log"
$appLogDir = Join-Path $opsDir "..\tmp"

function Show-CodeLine([string]$line, [string]$source) {
    $parts = $line -split "`t"
    if ($parts.Count -lt 3) { return $false }
    $phone = $parts[1].Trim()
    $code = $parts[2].Trim()
    if ($code -notmatch '^\d{4,8}$') { return $false }
    # Show only the last four digits so a shoulder-surfer cannot read the number.
    $masked = if ($phone.Length -gt 4) { ("*" * ($phone.Length - 4)) + $phone.Substring($phone.Length - 4) } else { $phone }
    Write-Host ""
    Write-Host "  Latest sign-in code" -ForegroundColor Cyan
    Write-Host "  -------------------"
    Write-Host "  Mobile : $masked"
    Write-Host "  Code   : $code" -ForegroundColor Green
    Write-Host "  When   : $($parts[0].Trim())"
    Write-Host "  Source : $source"
    Write-Host ""
    Write-Host "  Read this code to the customer now. It expires in a few minutes and works once." -ForegroundColor Yellow
    Write-Host ""
    return $true
}

if (Test-Path -LiteralPath $logPath) {
    $lines = @(Get-Content -LiteralPath $logPath | Where-Object { $_ -match "`t" })
    for ($index = $lines.Count - 1; $index -ge 0; $index--) {
        if (Show-CodeLine $lines[$index] "ops\salon-sign-in-codes.log") { exit 0 }
    }
}

# Fall back to the newest server log, which also records each code in local mode.
if (Test-Path -LiteralPath $appLogDir) {
    $newest = Get-ChildItem -LiteralPath $appLogDir -Filter "*.log" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($newest) {
        $matches = @(Select-String -LiteralPath $newest.FullName -Pattern 'Sign-in code for (\d{4,15}) is (\d{4,8})' -AllMatches)
        if ($matches.Count -gt 0) {
            $last = $matches[$matches.Count - 1]
            $phone = $last.Matches[0].Groups[1].Value
            $code = $last.Matches[0].Groups[2].Value
            $masked = if ($phone.Length -gt 4) { ("*" * ($phone.Length - 4)) + $phone.Substring($phone.Length - 4) } else { $phone }
            Write-Host ""
            Write-Host "  Latest sign-in code" -ForegroundColor Cyan
            Write-Host "  -------------------"
            Write-Host "  Mobile : $masked"
            Write-Host "  Code   : $code" -ForegroundColor Green
            Write-Host "  Source : $($newest.Name)"
            Write-Host ""
            Write-Host "  Read this code to the customer now. It expires in a few minutes and works once." -ForegroundColor Yellow
            Write-Host ""
            exit 0
        }
    }
}

Write-Host ""
Write-Host "  No sign-in code has been requested yet." -ForegroundColor Yellow
Write-Host "  Ask the customer to tap 'Send code' in the app first, then run this command again."
Write-Host ""
