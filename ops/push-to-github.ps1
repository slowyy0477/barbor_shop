<#
  Uploads this salon project to GitHub in one step.

  The first time, Windows will open a GitHub sign-in window (Git Credential
  Manager). Sign in once and the push finishes by itself. After that, running
  this again simply uploads the newest changes.

  Examples:
    powershell -ExecutionPolicy Bypass -File .\ops\push-to-github.ps1
    powershell -ExecutionPolicy Bypass -File .\ops\push-to-github.ps1 -Repo barbor_shop -Owner slowyy0477

  Optional: put a GitHub personal access token in
  ops\github-token.local.txt (one line) if the browser sign-in is not possible.
  That file is ignored by git and is deleted after a successful push.
#>
param(
    [string]$Owner = "slowyy0477",
    [string]$Repo = "barbor_shop",
    [string]$Branch = "main",
    [string]$Message = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$tokenPath = Join-Path $root "ops\github-token.local.txt"
Set-Location $root

function Write-Step($message) { Write-Host "[github] $message" }

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw "Git is not installed on this laptop. Install it from https://git-scm.com/download/win and run this again."
}

$remoteUrl = "https://github.com/$Owner/$Repo.git"

# 1. Make sure there is a commit to upload.
git rev-parse --verify HEAD *> $null
if ($LASTEXITCODE -ne 0) {
    Write-Step "No saved version yet, so one is being created now."
    git add -A | Out-Null
    git -c user.name="Ayan Salon" -c user.email="$Owner@users.noreply.github.com" commit -q -m "Ayan Beauty Salon release"
}

# 2. Commit anything new since the last upload.
$dirty = (git status --porcelain | Measure-Object).Count
if ($dirty -gt 0) {
    Write-Step "Saving $dirty changed file(s) before uploading."
    git add -A | Out-Null
    $commitMessage = if ($Message) { $Message } else { "Update $(Get-Date -Format 'yyyy-MM-dd HH:mm')" }
    git -c user.name="Ayan Salon" -c user.email="$Owner@users.noreply.github.com" commit -q -m $commitMessage
} else {
    Write-Step "Everything is already saved locally."
}

# 3. Point this folder at the GitHub repository.
$existing = git remote get-url origin 2>$null
if ($existing) {
    if ($existing -ne $remoteUrl) {
        Write-Step "Switching the GitHub address from $existing to $remoteUrl"
        git remote set-url origin $remoteUrl
    }
} else {
    git remote add origin $remoteUrl
}
Write-Step "GitHub repository: $remoteUrl"

# 4. Push. A token file is optional; otherwise GitHub opens a sign-in window.
$token = ""
if (Test-Path $tokenPath) { $token = (Get-Content -Raw -LiteralPath $tokenPath).Trim() }

if ($token) {
    Write-Step "Using the saved access token (no browser window needed)."
    $safeUrl = "https://$token@github.com/$Owner/$Repo.git"
    git push $safeUrl "HEAD:refs/heads/$Branch" --force-with-lease
    $pushExit = $LASTEXITCODE
    if ($pushExit -eq 0) {
        git branch --set-upstream-to "origin/$Branch" $Branch 2>$null | Out-Null
        Remove-Item -LiteralPath $tokenPath -Force -ErrorAction SilentlyContinue
        Write-Step "Token file was deleted again for safety."
    }
} else {
    Write-Step "If a GitHub sign-in window appears, sign in and approve it."
    git push -u origin $Branch
    $pushExit = $LASTEXITCODE
}

if ($pushExit -ne 0) {
    Write-Host ""
    Write-Host "The upload did not finish. The usual reasons are:"
    Write-Host "  1. The repository name is different - run this script with -Repo YOUR-REPO-NAME"
    Write-Host "  2. The sign-in window was closed - run it again and sign in to GitHub"
    Write-Host "  3. The GitHub account has no access to that repository"
    throw "GitHub push failed with exit code $pushExit"
}

Write-Host ""
Write-Host "Uploaded. Open your repository to check:"
Write-Host "  https://github.com/$Owner/$Repo"
Write-Host "Branch: $Branch"
git log --oneline -3
