# GitHub handoff

This folder is ready to publish, but no remote repository is configured. That is intentional: the repository URL and GitHub account were not supplied.

For beginner installation, iPhone PWA setup, free-host limitations, server secrets/TLS/backups, and release-key protection, see [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md).

From `E:\Barbar-Shop`, run:

```powershell
git init
git add .
git commit -m "Build Ayan Beauty Salon Java app"
git branch -M main
git remote add origin https://github.com/<your-account>/<your-repository>.git
git push -u origin main
```

Replace the placeholder remote with the exact GitHub repository URL. Do not commit `android/local.properties`, signing keys, provider credentials, OTP secrets, or production database exports.

After the first push, the included GitHub Actions workflow (`.github/workflows/ci.yml`) installs
Android API 30, runs the Java and Spring checks, syncs the web assets, and uploads a debug APK as
the `ayan-salon-debug-apk` workflow artifact. The local machine does not need an Android SDK for
that CI build. Do not upload the local release keystore; configure protected GitHub Actions secrets
before adding a signed release workflow.

## Run the browser build

```powershell
python -m http.server 4173
```

Open `http://localhost:4173`.

## Build the Android app

Open `android` in Android Studio with an Android SDK installed. Before building after a browser change:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\sync-assets.ps1
```

This workstation has the required Android API 30 and Build Tools 30.0.3 under `E:\Barbar-Shop\.android-sdk` (ignored by Git). The local helper keeps Gradle downloads under `E:\Barbar-Shop\.gradle-user`:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\build-apk.ps1 -Variant Debug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. A signed sideload
APK is written to `android/app/build/outputs/apk/release/app-release.apk` after running
`android/create-release-signing.ps1` and `android/build-apk.ps1 -Variant Release -SkipLint`.

## Important production boundary

The included browser/WebView UI is an offline local workspace. Browser QA loads sample records from `qa-seed.js`; the Android asset sync intentionally excludes that file, so the Android entry point uses `mode=release` and starts with no customer or financial records. Owner controls are hidden from customers; the owner unlocks them through the private gesture and access code. Customer lookup accepts an exact Pakistani mobile number and masks unrelated data.

The APK is a safe single-device release when built without an API origin. The WebView client also supports the authenticated Spring API: build a shared deployment with `-ApiBaseUrl=https://...` and a salon UUID, then balances and ledger data come from the server rather than localStorage. Real shared customer data and real money still require owner-controlled PostgreSQL/TLS/OTP/notification/payment-provider configuration.
