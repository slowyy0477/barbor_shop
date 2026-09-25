# Ayan Beauty Salon: Beginner Deployment Guide

This guide explains what can be used today, how to install the Android APK, how to add the web app to an iPhone, and what is still required before the salon shares one live account database across many phones.

## Fastest way to try it

If you only want to use the app on your Android phone today:

1. Copy `E:\Barbar-Shop\android\app\build\outputs\apk\release\app-release.apk` to the phone.
2. Tap the file in **Files**, allow **Install unknown apps** when Android asks, and tap **Install**.
3. Open **Ayan Beauty Salon**. A fresh release has no demo customers or money records.
4. The owner can unlock the private owner workspace with the five-tap salon mark gesture and owner code; customers do not see an Owner menu.

To try the shared version instead, open <https://slowyy0477.github.io/barbor_shop/> on any phone. That address never changes; while the shop laptop is switched on with the salon server running, the page finds it by itself.

You do not need PostgreSQL, GitHub, a payment account, or an SMS provider for this offline pilot. Those accounts are needed only for a shared internet deployment, described later in this guide.

Real sign-in codes are one script away once you have provider credentials: double-click `ops\connect-sms.cmd`. See `docs/SMS-SETUP.md`.

## First, choose the right mode

| Goal | What to use | Cost and limits |
| --- | --- | --- |
| Try the app on one Android phone | The signed APK | Free and works offline. Data stays on that phone. It is not shared with other phones. |
| Try the customer screens on iPhone | The PWA over an HTTPS URL | Free static hosting is possible, but browser storage is still local until the API is connected. |
| Run shared customer accounts and real balances | Spring server + PostgreSQL + real authentication | Requires an internet host and provider accounts. Free tiers have quotas, sleeping services, storage limits, and no unlimited guarantee. |

There is no reputable hosting, SMS OTP, payment, or backup service that provides unlimited production use for free. Free tiers are useful for a pilot and testing, not for promising uptime or handling real money. Prices and quotas change, so check the provider's current terms before entering payment details.

The coding agent cannot create accounts, accept passwords, or keep provider secrets on your behalf. Create provider accounts yourself and put secrets only in the host's secret settings or a password manager. Never send an OTP, database password, payment PIN, or keystore password in chat.

## A. Install the Android app (easy steps)

### 1. Get the APK

The current signed sideload file is:

E:\Barbar-Shop\android\app\build\outputs\apk\release\app-release.apk

Copy that file to the phone with a USB cable, a private cloud drive, or another private file-transfer method. Do not install an APK received from an unknown person.

Optional integrity check on Windows (run in PowerShell on the computer):

~~~powershell
Get-FileHash 'E:\Barbar-Shop\android\app\build\outputs\apk\release\app-release.apk' -Algorithm SHA256
~~~

Compare the result with the checksum published by the salon owner. A changed checksum means the file is different; do not install it until it is checked.

### 2. Install on Android

1. Open the phone's **Files** app and open **app-release.apk**.
2. If Android asks, open **Settings** and allow **Install unknown apps** for the app you used to open the file (usually Files or Chrome). Return to the installer.
3. Tap **Install**, wait for it to finish, then tap **Open**.
4. After installation, you can turn the **Install unknown apps** permission off again.

If Android says the app is already installed but will not update, the old copy may be a debug build signed with a different key. Export any data you need, uninstall the old **Ayan Salon** app, and install the release APK. Future updates must use the same salon release key.

### 3. What you see after a fresh install

- The first screen is empty onboarding. Demo customers and demo financial records are not bundled in the release APK.
- Customers see **Home**, **Book**, **Wallet**, and **More** only. There is no visible Owner menu.
- The owner opens the private owner gate by tapping the **AB** salon mark five times quickly and entering the owner code. Change the initial code immediately in Owner Settings.
- Customer lookup accepts a complete Pakistani number in **03xx xxx xxxx**, **3xxxxxxxxx**, **+92xxxxxxxxxx**, or **0092xxxxxxxxxx** form. It canonicalizes the number, requires an exact local match, masks the result, rejects ambiguous duplicates, and offers profile creation when there is no match.

This APK is a single-device pilot. The local owner gate and lookup are not a substitute for OTP and server authorization. Do not use this offline build as the source of truth for shared balances.

### 4. Common installation problems

| Message or symptom | Action |
| --- | --- |
| "Can't install" | Check that the download finished, that Android is API 24 or newer, and that there is free storage. Re-copy the APK if its checksum changed. |
| "App not installed" after an older build | Uninstall the debug build first. A debug-signed APK and salon-release-signed APK cannot update one another. |
| App opens but old records appear | The records are local browser storage. Use Owner > Settings > Data controls only when you intentionally want to reset local data; never reset before exporting anything needed. |
| Owner controls are visible to a customer | Lock the owner workspace and switch customer. A fresh release install should not show Owner navigation. |

## B. Build a new APK on the E: drive

The repository is at E:\Barbar-Shop. The helper keeps the Android SDK, Gradle cache, and Maven cache on E:. Do not move or commit those generated folders.

### Build the already configured release variant

Open PowerShell and run:

~~~powershell
Set-Location 'E:\Barbar-Shop'
powershell -ExecutionPolicy Bypass -File .\android\build-apk.ps1 -Variant Release -SkipLint
~~~

Output:

E:\Barbar-Shop\android\app\build\outputs\apk\release\app-release.apk

The script synchronizes the root web files before compiling and prints a SHA-256 checksum. -SkipLint is only needed on this workstation when the optional Android platform-tools/lint dependency is not cached.

### Build the debug variant for development

~~~powershell
Set-Location 'E:\Barbar-Shop'
powershell -ExecutionPolicy Bypass -File .\android\build-apk.ps1 -Variant Debug
~~~

The debug file is android\app\build\outputs\apk\debug\app-debug.apk. Do not distribute it to customers as the production release.

### If the signing key has not been created

Run this once, from the android directory:

~~~powershell
Set-Location 'E:\Barbar-Shop\android'
powershell -ExecutionPolicy Bypass -File .\create-release-signing.ps1
~~~

The command creates:

- E:\Barbar-Shop\android\keys\ayan-salon-release.jks
- E:\Barbar-Shop\android\release-signing.properties

The generated password is written to the local properties file. Move the password to a password manager and protect both files before sharing the APK.

## C. Add the web app to an iPhone (PWA)

Safari can install a web app only when it is served over HTTPS (or from localhost during development). The current local browser workspace is useful for QA, but it is not a public URL.

### Before publishing

The browser build loads `qa-seed.js` only on a local development URL with `mode=qa`. Before making a public PWA URL, confirm all of the following:

- the public entry point uses release mode and does not load demo fixtures;
- no customer names, phone numbers, wallet entries, payment references, or test referral codes are in the published assets;
- API calls use HTTPS and authenticated sessions once the shared backend is enabled;
- the service worker, manifest, and icon are served from the same HTTPS origin.

Do not publish the repository root unchanged until this check is complete. A fresh Android release excludes the real QA fixture file, and the browser loader now rejects QA mode on non-local hosts; still review the published asset list before sharing the URL.

### Free static-host choices

This repository is already published at <https://slowyy0477.github.io/barbor_shop/> with GitHub Pages, at no charge. The address never changes. `api.json` in the same folder holds the address of the running salon server, and `ops\publish-salon-address.ps1` updates it automatically every time the free tunnel is opened or refreshed. When the laptop is off the page still loads and says the salon server is not reachable.

Cloudflare Pages and Netlify offer a similar free tier if you ever want a second copy. These services host files only; they do not run this Spring process or provide a durable PostgreSQL database. Free bandwidth/build quotas and acceptable-use limits apply.

For a hosted backend that runs without the laptop (free tiers sleep when idle), see `docs/PERMANENT-HOSTING.md`.

For a beginner, the simplest sequence is:

1. Open <https://slowyy0477.github.io/barbor_shop/> in Safari on the iPhone.
2. Tap **Share**, choose **Add to Home Screen**, enable **Open as Web App** if shown, then tap **Add**.
3. Keep the shop laptop on with the salon server running while the customer or staff member uses the app.

If you ever publish a different host, keep the release checks above and never commit secrets or signing files.

On iPhone, the installed icon opens in a standalone window. iOS may evict local browser storage when space is low, and background push/SMS behavior depends on the eventual provider integration. A PWA is not a replacement for server backups.

## D. Shared server architecture

### Free laptop server (shop laptop, no monthly host)

The free path for a real shop is to make the salon laptop the server. Two
scripts do everything:

~~~powershell
Set-Location 'E:\Barbar-Shop'
powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -OwnerPhone 03xxxxxxxxx
~~~

The start script:

1. Starts the PostgreSQL install already on this laptop and creates the
   `ayan_salon` database plus an `ayan` login with a random password.
2. Copies the web app into the Spring server so one HTTPS address serves both
   the phone interface and the API (same origin, so the Android WebView never
   needs cleartext or file-URL access).
3. Builds and starts the server, then waits for `/actuator/health`.
4. Downloads the free Cloudflare tunnel client once and opens a
   `https://<random>.trycloudflare.com` address that points at the laptop.
5. Prints the phone address and the owner mobile + PIN.

The first run needs permission to create that database. If the PostgreSQL
superuser password is known, pass it once:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\ops\start-salon-server.ps1 -PostgresAdminPassword 'your-postgres-password' -OwnerPhone 03xxxxxxxxx
~~~

Without that password the script temporarily adds a local-only `trust` line to
`pg_hba.conf`, creates the salon role and database, and restores the original
file immediately afterwards; later starts never touch PostgreSQL settings again
because the salon login can already reach its own database.

Pick an owner mobile number that is not one of the three starter barbers
(`03001234567`, `03001234568`, `03001234569`). One number cannot be both the
owner and a barber, and the script says so before the server starts.

~~~powershell
powershell -ExecutionPolicy Bypass -File .\ops\stop-salon-server.ps1
~~~

The stop script ends the server and the tunnel but leaves PostgreSQL and the
salon data in place.

What this path does and does not give you:

- Free, shared customers, bookings, wallet and audit records while the laptop
  is switched on and online.
- No paid host, no static IP and no router port forwarding.
- The free tunnel address changes on every restart, so it must be pasted into
  each phone again (Owner > Settings > Salon server address). For a stable
  private address, install Tailscale on the laptop and phones, or move to a
  paid host later.
- Keep `ops\salon-server.local.json`, the generated database password and the
  owner PIN private. The file is ignored by Git on purpose.
- Sign-in on this path uses the customer's mobile number plus a 4 to 6 digit
  PIN, because no SMS provider is configured. A real SMS provider can be added
  later with `AYAN_AUTH_OTP_PROVIDER_URL` and `AYAN_AUTH_OTP_PROVIDER_TOKEN`.
- The PIN is created once per customer. Creating a profile, and recovering a
  forgotten PIN, still needs one verification code. With no SMS provider the
  laptop writes that code to `ops\salon-sign-in-codes.log` instead of sending
  an SMS, and the owner reads it out at the counter:

~~~powershell
powershell -ExecutionPolicy Bypass -File .\ops\show-last-code.ps1
~~~

  The code expires in about five minutes and works once. Keep that file, and
  the copy of the same codes inside `tmp\salon-server.out.log`, on the shop
  machine only. Once a paid SMS provider is connected, the file is no longer
  written.

For real shared accounts, use this path:

~~~text
Android APK / iPhone PWA
        | HTTPS + short-lived authenticated session
        v
TLS reverse proxy or managed HTTPS host
        v
Spring Boot API (Java 17)
        v
PostgreSQL + migrations + encrypted backups
~~~

The Android WebView contains the offline workflow and an authenticated API client. In a shared build, pass the HTTPS API origin to `build-apk.ps1`; wallet balances, bookings and ledger data are then fetched from the server and are never sourced from localStorage. The server issues revocable opaque sessions and fails closed until the SMS OTP provider and owner bootstrap values are configured.

### Practical hosting choices

| Choice | Good for | Important limits |
| --- | --- | --- |
| Existing Windows PC on E: + PostgreSQL + Spring | $0 private pilot and learning | PC must stay on; home internet/IP may change; port forwarding is risky; backups and TLS are your responsibility. A Cloudflare Tunnel can avoid opening an inbound port, but it still does not make the PC highly available. |
| Cloudflare Pages + a small managed Spring host + Neon or Supabase Postgres | Small shared test | Free plans sleep or throttle, databases have storage/compute quotas, and free backups/PITR may be limited. Keep test data only. |
| Oracle Cloud Always Free VM | Persistent hobby deployment | Signup/card verification, regional capacity, Linux administration, patching, TLS, backups and monitoring are required. "Always Free" resources can still be reclaimed under provider rules. |
| Paid managed app host + managed PostgreSQL | Real customers and money | Costs money, but gives a clearer support, backup, uptime and scaling path. |

Neon and Supabase are database services, not a free unlimited replacement for the Spring API. Supabase can provide auth/storage features, but SMS OTP and high-volume messaging are normally billable or quota-limited. Render/Railway-style free app plans can sleep, have ephemeral disks, or be trial-only. Verify current limits before choosing one.

### Free image storage for haircut styles and the logo

The server model stores a bounded image URI, not unbounded base64 bytes. For a small pilot, use a provider's free object-storage bucket and keep resized WebP/JPEG images (for example, a few hundred kilobytes each). Supabase Storage, Cloudflare R2, Backblaze B2, and Cloudinary all have free or trial allowances, but their storage, transform, request, and download quotas differ and can change. Some providers require a card even when the allowance is free. Set a hard upload size/type limit, strip metadata, generate a thumbnail, and reject executable or SVG content unless it is sanitized. Do not use a public bucket for customer documents. A free image bucket is not a backup: retain an encrypted export on E: and test restoring it.

## E. Server setup and secrets

### Local smoke run (not production)

Install JDK 17 and PostgreSQL 14 or newer. Then set values in the current PowerShell session:

~~~powershell
Set-Location 'E:\Barbar-Shop\server'
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$env:JDBC_DATABASE_URL = 'jdbc:postgresql://localhost:5432/ayan_salon?sslmode=disable'
$env:JDBC_DATABASE_USERNAME = 'ayan'
$env:JDBC_DATABASE_PASSWORD = 'use-a-local-password-only'
.\mvnw.cmd spring-boot:run
~~~

Flyway applies the versioned migrations at startup. The test suite does not require a live provider or PostgreSQL instance, so a passing unit suite alone does not prove deployment readiness.

For a hosted database, use an SSL URL such as jdbc:postgresql://HOST:5432/DB?sslmode=require when the provider documents that mode. Never leave the sample change-me password in a public deployment.

### Secret checklist

Keep these in the host's encrypted secret store, never in Git, APK assets, JavaScript, screenshots, or chat:

- PostgreSQL URL, username, and password.
- OTP provider credentials, signing/JWKS configuration, issuer and audience.
- JWT/session signing secret or private key, if the selected provider requires one.
- SMS/push provider credentials and webhook signing secret.
- Official payment-provider merchant credentials and webhook secret.
- Application encryption key and any object-storage credentials for haircut/logo images.
- Allowed HTTPS origins and CORS configuration.

Generate a random application secret in PowerShell, then store it in a password manager:

~~~powershell
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Convert]::ToBase64String($bytes)
~~~

The current development bearer format (ROLE:salonUuid:actorUuid) is for tests only. Do not enable the dev profile on the public server. Production must use a real verifier that checks issuer, audience, signature, expiry, salon membership, actor role, and customer ownership.

## F. TLS, backups, monitoring, and rate limits

### TLS

- Managed hosts normally issue HTTPS automatically. Set the app/API base URL to https://..., redirect HTTP to HTTPS, and restrict CORS to the exact PWA origin.
- For a self-hosted VM, put Caddy or Nginx in front of Spring and use a Let's Encrypt certificate. Renew it automatically and test renewal before the first customer uses the system.
- PostgreSQL traffic should use provider TLS (sslmode=require or the provider's stronger certificate verification setting).
- Do not expose PostgreSQL directly to the public internet. Allow only the application host and an administrator's secure network.

### Backups

Free database plans may have no point-in-time recovery. Make an encrypted dump on a different drive or private storage, keep at least three dated copies, and test a restore monthly. Keep the backup folder outside the Git repository. Example PowerShell command (adapt the host, user, and password handling to your provider):

~~~powershell
$backupDir = 'E:\AyanSalon-Private\postgres-backups'
New-Item -ItemType Directory -Force -Path $backupDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
pg_dump --format=custom --no-owner --file (Join-Path $backupDir "ayan-$stamp.dump") 'postgresql://USER:PASSWORD@HOST:5432/ayan_salon?sslmode=require'
~~~

Do not put a password directly in a command saved to shell history. Prefer PGPASSWORD for a one-off local command or, better, a protected .pgpass file/host secret manager. Encrypt the dump before uploading it and confirm that the backup directory is ignored by Git. A backup is only real after a restore test into a separate database succeeds.

### Monitoring

- Keep /actuator/health reachable only to the load balancer/uptime checker; do not expose env, beans, or financial data.
- Alert on repeated 5xx responses, database connection failures, migration failures, queue backlog, disk usage, backup age, and certificate expiry.
- Provider logs plus a small free uptime checker can cover a pilot, but free monitor counts and polling intervals are limited and can change.
- Never log OTPs, bearer tokens, payment credentials, full phone numbers, or wallet secrets.

### Rate limits

Apply limits at the edge and in the application, keyed by IP plus a normalized phone/account where appropriate:

- OTP send: low per phone and per IP; add a cooldown and daily cap.
- OTP verify: low attempt count with temporary lockout.
- Login, lookup, referral claim, deposit, withdrawal, and booking endpoints: authenticated quotas and idempotency keys.
- Provider webhooks: signature verification, replay protection, and bounded retries.
- Admin/report/export endpoints: owner/staff role checks and a separate limit.

Rate limiting alone is not fraud prevention. Keep server-side ownership checks, append-only wallet entries, reversal records, and audit logs.

## G. Before calling it production

Use this checklist with the owner and a test account:

- [ ] Real OTP/session provider is configured and tested for customer, staff, and owner roles.
- [ ] Android and PWA call authenticated server APIs; wallet balances come from server responses, not localStorage.
- [ ] First-time deposit bonus is granted once per verified customer account and cannot be replayed with another deposit.
- [ ] Service and add-on IDs/prices are read from the authoritative server catalogue; client-supplied amounts are rejected.
- [ ] Haircut photos and the owner logo are stored in controlled object storage with size/type checks and cache invalidation.
- [ ] Theme settings, dark mode preference, and catalogue changes are returned by the same API to Android and PWA.
- [ ] Notification outbox retries safely and sends through an official SMS/push provider; opt-out and consent are enforced.
- [ ] Official Pakistani payment APIs and signed webhooks are integrated, or the app is explicitly labeled manual-verification-only.
- [ ] PostgreSQL migration and concurrency tests cover duplicate bookings, withdrawals, service payments, and idempotency races.
- [ ] TLS, secret rotation, backups, restore tests, monitoring, rate limits, and incident contacts are documented.
- [ ] A physical Android phone and at least one iPhone Safari installation have been tested.
- [ ] No QA fixtures, demo accounts, provider credentials, or private keys are in the public build.

## H. Protect the Android release key

Android accepts an update only when it is signed by the same key. Back up both files immediately:

- E:\Barbar-Shop\android\keys\ayan-salon-release.jks
- E:\Barbar-Shop\android\release-signing.properties

Make two encrypted copies outside the Git repository, for example on an encrypted USB drive and a second private drive. Do not email them, commit them, or put them in a public GitHub release. Keep the keystore password and the backup locations in a password manager. Verify a backup before deleting or replacing anything:

~~~powershell
Get-FileHash 'E:\Barbar-Shop\android\keys\ayan-salon-release.jks' -Algorithm SHA256
Get-Item 'E:\Barbar-Shop\android\release-signing.properties'
~~~

If the key is lost, a new APK can still be installed as a new package after uninstalling the old one, but it cannot update existing customer installations. Google Play publication also requires a Play Console account and its own signing setup; direct APK sharing does not.

## I. GitHub and free CI

No GitHub remote is configured in this workspace. When the owner creates one, follow GITHUB_SETUP.md. The included workflow builds a debug APK and runs checks without using the local E: SDK cache. Do not upload the release keystore or production secrets to GitHub Actions until a protected secret-based signing workflow has been deliberately configured.

## Current honest status

The signed APK is suitable for a free, single-device pilot. The Spring code and database migrations provide the server boundary and safety rules, but the shared production path still needs the provider integrations and deployment checklist above. Treat any free host as a test environment until backups, authentication, notifications, payment verification, and concurrency behavior have been exercised against the real PostgreSQL service.
