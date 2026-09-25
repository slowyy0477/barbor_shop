# Release signing

The free sideload build can be signed locally without a Play Console account. Run this once from the `android` directory:

```powershell
powershell -ExecutionPolicy Bypass -File .\create-release-signing.ps1
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1 -Variant Release
```

If the local SDK is missing the optional platform-tools package and Gradle cannot run `lintVitalRelease`, install that package or use `-SkipLint` for this offline build:

```powershell
powershell -ExecutionPolicy Bypass -File .\build-apk.ps1 -Variant Release -SkipLint
```

The script creates `android/keys/ayan-salon-release.jks` and `android/release-signing.properties`. Both are ignored by Git and should be backed up privately. The same keystore is required for future updates; losing it means Android will treat a later APK as a different app.

This is an installable release APK for direct sharing. Publishing through Google Play still requires a Play Console account and Play App Signing setup. The APK remains local-only until the Android client is connected to the authenticated Spring backend.
