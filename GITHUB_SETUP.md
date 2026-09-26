# GitHub handoff

This folder is the whole project. Everything in it is safe to publish.

## What is in this repository

- The web app at the top level: `index.html`, `app.js`, `api-client.js`, `styles.css`.
- The Android shell in `android/`.
- The optional Java and PostgreSQL server source in `server/` (used only if the owner ever
  chooses to run it himself).
- The signed install file `Salon-App-v1.0.0.apk`.

Nothing here publishes a computer address. There is no tunnel script, no hosting blueprint and no
automatic workflow, so pushing updates the source and the GitHub Pages web app and nothing else.
No build notification is sent.

## Publish a change

From `E:\Barbar-Shop`:

```powershell
git add .
git commit -m "Update the salon app"
git push
```

## Run the browser build

```powershell
python -m http.server 4173
```

Open `http://localhost:4173`.

## Build the Android app

Open `android` in Android Studio with an Android SDK installed. Before building after a browser
change:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\sync-assets.ps1
```

This workstation has Android API 30 and Build Tools 30.0.3 under `E:\Barbar-Shop\.android-sdk`
(ignored by Git). Gradle downloads stay under `E:\Barbar-Shop\.gradle-user`:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\build-apk.ps1 -Variant Debug
```

The debug APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`. A signed sideload
APK is written to `android/app/build/outputs/apk/release/app-release.apk` after running
`android/create-release-signing.ps1` and `android/build-apk.ps1 -Variant Release -SkipLint`.

## Keep these private

Never commit `android/local.properties`, the release keystore, `android/release-signing.properties`,
or any private file under `ops/` ending in `.local.json` or `.local.txt`. The ignore rules already
block them.

The release keystore is the identity of the app. Every future update must be signed with the same
key, so keep `android/keys/ayan-salon-release.jks` and `android/release-signing.properties` backed
up somewhere safe.

## Offline boundary

The shipped APK and the GitHub Pages web app are the same offline app: all data stays on the phone
that installed it, and no address is looked up, published or stored. The app only talks to a server
when one is deliberately supplied with `?apiBaseUrl=...` or the `window.__AYAN_API_BASE_URL__`
global. The Spring server in `server/` is optional and only runs when the owner starts it himself.
