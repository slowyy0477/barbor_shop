# Ayan Beauty Salon Android app

This directory is an Android Java shell around the offline-first Ayan Beauty Salon app. The web experience is bundled under `app/src/main/assets`, so the APK does not need a local server or a network connection for the customer and owner screens. Release builds load `index.html?mode=release`, start with an empty customer and financial workspace, and use PKR throughout. Browser-only QA fixtures from `qa-seed.js` are not copied into the APK. The initial salon profile is Ayan Beauty Salon, VC8Q+R33 Ayan Beauty Salon, Uqab Plaza, Gate Number 2, Kamra Kalan.

For step-by-step installation on a phone, PWA setup, and production deployment limits, read the repository-level [DEPLOYMENT_GUIDE.md](../DEPLOYMENT_GUIDE.md).

## Build

Open this `android` directory in Android Studio and let it use an Android SDK. The project intentionally uses no third-party runtime libraries. It targets Android API 30, supports API 24+, and uses Android Gradle Plugin 4.2.2 / Gradle 6.7.1. The Gradle wrapper is included, so a separate Gradle installation is not required. Gradle 6.7.1 is run with a Java 8 runtime by the helper; compilation may use a full JDK through `ANDROID_JAVA_HOME`. The app source and target compatibility remain Java 8. Android Studio users should select a compatible JDK (8-15) or upgrade the Gradle/AGP pair together.

The repository build helper uses `android/local.properties` when present. This workspace has API 30 and Build Tools 30.0.3 under `E:\Barbar-Shop\.android-sdk`; the local properties file is ignored and is not part of the source distribution. Gradle's download/cache directory defaults to `E:\Barbar-Shop\.gradle-user` so build downloads remain on the `E:` drive.

Once the SDK is installed, the helper below checks the required packages, synchronises the root web assets, builds the requested variant, and prints its SHA-256 digest:

```powershell
.\build-apk.ps1 -Variant Debug
```

The output debug APK is `app/build/outputs/apk/debug/app-debug.apk`. For the distributable sideload build, create a local signing key once and build Release:

```powershell
.\create-release-signing.ps1
.\build-apk.ps1 -Variant Release -SkipLint
```

The signed output is `app/build/outputs/apk/release/app-release.apk`. Keep `keys/ayan-salon-release.jks` and `release-signing.properties` private and backed up. A salon-signed build will not update over a debug APK unless it uses the same signing key.

The APK starts with an empty customer and financial workspace. Customers see only Home, Book, Wallet and More; the Owner workspace is hidden and can be opened only by the private five-tap salon mark gesture followed by the owner access code. Customer lookup accepts an exact Pakistani mobile number (`03xx`, `+92`, or `0092` form), masks the result, rejects ambiguous duplicates and never exposes another customer's data. This local gate is suitable for a single-device pilot; shared production use requires server-issued owner/customer sessions and OTP/JWT authorization.

If this computer does not have the Android SDK, open **Actions** in GitHub, choose **Ayan Beauty Salon CI**, select **Run workflow**, and download the `ayan-salon-debug-apk` artifact. The workflow also publishes `SHA256SUMS.txt` for verifying the downloaded file.

## WebView security boundary

The APK keeps the offline UI in `file:///android_asset/` and treats that path as the only trusted
top-level document. HTTP(S), phone, email, SMS, geo and UPI links are handed to an external Android
app; unknown schemes and arbitrary `file://` paths are blocked. Local file-to-file and file-to-network
access are disabled, mixed content is rejected, Safe Browsing is enabled where the platform supports
it, and JavaScript share/copy hooks remain inactive until the bundled page has finished loading.
This keeps an accidental redirect or injected remote page from using the native bridge. The bridge
also receives a fresh per-page token (so cross-origin frames cannot call it), caps shared/copied text
at 4,096 characters, and is removed when the activity is destroyed.

The policy is covered by a no-dependency self-test:

```powershell
$javaHome='C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$out='tmp\navigation-policy-check'
New-Item -ItemType Directory -Force -Path $out | Out-Null
& "$javaHome\bin\javac.exe" -source 8 -target 8 -d $out `
  app\src\main\java\com\cornerchair\salon\security\*.java
& "$javaHome\bin\java.exe" -cp $out `
  com.cornerchair.salon.security.NavigationPolicySelfTest
```

## Updating bundled web assets

When the root PWA changes, run `./sync-assets.ps1` (PowerShell) before building. It copies `index.html`, `app.js`, `styles.css`, `manifest.webmanifest`, `service-worker.js`, and `icon.svg` into `app/src/main/assets/`. The source of truth remains the files at the repository root.
