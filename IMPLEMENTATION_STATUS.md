# Ayan Beauty Salon implementation status

This repository was implemented against the 20-page `Salon-App-Blueprint-by-Roy-Digital.pdf` and the Ayan Beauty Salon hardening brief. The PDF was rendered and text-inspected page by page. Indian rupee examples were converted to PKR in the app: PKR 500 paid + PKR 50 bonus = PKR 550, Beard Trim +PKR 99, Head Massage +PKR 199, and PKR 100 + PKR 100 referral rewards.

## What is included

- Java Android application shell with a bundled mobile-first salon interface and restrained motion splash screen.
- Customer onboarding, Pakistani mobile lookup, profile, consent, visit history, bookings, staff/slot selection, optional add-ons, reminders, referrals and wallet ledger.
- Customer sign-in uses the mobile number plus a 4 to 6 digit PIN. The server stores only a salted PBKDF2-HMAC-SHA256 digest with a five-attempt, 15-minute lockout; the offline phone stores a salted local digest and the same lockout. The first PIN is created during signup, the owner can reset a forgotten PIN, and the OTP code path stays available as recovery.
- Animated haircut gallery: owner-uploaded photos enter with a staggered directional reveal, a slow drift, and a gentle sheen; tapping a photo opens the full look with a direct booking action. Animation is skipped when the device requests reduced motion.
- Haircut styles are a full owner-managed section: each style can be linked to one bookable service (`service_id`, migration V13, validated to belong to the same salon), hidden or shown again, and removed from the customer home screen (archived through `DELETE`, never deleted). The customer "Book this look" button now opens booking with the linked service pre-selected, because both the owner list and the public catalog carry `serviceId`.
- Free laptop hosting: `ops/start-salon-server.ps1` prepares the local PostgreSQL database, publishes the web app inside the Spring server, starts it, and opens a free Cloudflare quick tunnel so salon phones can reach the laptop over HTTPS. `ops/stop-salon-server.ps1` stops both processes, and the owner can paste the tunnel address into the app from Owner Settings.
- One-click laptop hosting: `ops/install-one-click-start.ps1` puts Start/Check/Stop shortcuts on the Desktop and in the Start Menu and registers `Ayan Salon Server` to start at every Windows sign-in (falling back to a Startup-folder shortcut when the scheduled task is refused). `start-salon-server.ps1` refuses to start a second copy and reports the running salon instead, and a hidden keep-awake agent holds a plain Windows "system required" request while the server runs, so the laptop stops sleeping mid-shift without needing administrator-only power settings. A tunnel address is only published after it genuinely answers `/actuator/health`, so an error line can never become the salon link, and `ops/refresh-public-url.ps1` re-opens the free link on demand.
- Fresh installs carry no salon identity: migration V14 clears only the untouched seed name/address and the seeded payment account titles, and a release install starts with a blank salon profile, so a customer never sees a salon name, address or receiving account the owner did not type himself. The owner sets them in Owner > Settings.
- No salon identity is compiled into the installed shell: the launcher label stays the neutral `Salon` until the owner uses Owner > Settings > "Phone app name & logo". That saves the name and logo on the phone, shows them on the launch screen from the next start, and offers a home-screen icon carrying the same name and logo, because Android cannot rename an app that is already installed. The same screen holds the salon server address, and when that address stops answering the shell falls back to the bundled offline copy with a plain notice instead of a browser error page.
- Sign-in codes without an SMS bill: on the laptop path the server runs the `local` profile, so each verification code is written to `ops\salon-sign-in-codes.log` (and the server log) instead of being texted. The owner runs `ops\show-last-code.ps1` and reads the newest code to the customer at the counter. The code is single use and expires in about five minutes. This channel exists only because no SMS provider is connected yet and is removed by dropping the `local` profile once one is.
- Owner dashboard, walk-in bookings, status changes, completion automation, customer/service/staff management, payment methods, settings, reports, exports, audit history and reversal/reason workflows.
- Release-mode startup with no customer, booking, visit, deposit, withdrawal, referral or wallet transaction records. The bundled catalog contains the initial Ayan salon configuration and is editable by the owner.
- Spring Boot/PostgreSQL server boundary with integer PKR minor units, append-only financial ledger, manual provider verification, wallet reservations, idempotent mutation keys, tenant checks, actor permissions, referral checks and audit records.

## APK delivered

The latest signed sideload APK is:

`E:\Barbar-Shop\android\app\build\outputs\apk\release\app-release.apk`

SHA-256:

`0807921B79C41296436C979536280C093A49179BEE21361991826488AAE1A9A2`

Package: `com.cornerchair.salon`  
Version: `1.0.0`  
Minimum Android: API 24  
Target Android: API 30  
Signature: APK Signature Scheme v2, one signer  
Debuggable: false  
Alignment: verified

The APK contains no seeded customer/payment fixture markers. No physical phone or `adb` device was available in this workspace, so installation on a real handset still needs to be checked once.

## Customer and owner access

Customers see only Home, Book, Wallet and More. There is no visible Owner menu in a fresh release install. On this single-device build, the owner taps the `AB` salon mark five times quickly and enters the initial code `530146`. The owner can change the code, lock the workspace manually, and is automatically locked after 15 minutes. The customer switch/logout action clears the active customer and the owner timeout also clears it.

This is a local pilot gate, not production authentication. Anyone with physical access to the device could potentially inspect or reset the client. Shared production use must replace it with server-issued owner/staff sessions and permissions.

Customer lookup accepts a complete Pakistani number in `03xx xxx xxxx`, `3xxxxxxxxx`, `+92xxxxxxxxxx` or `0092xxxxxxxxxx` form. The number is canonicalized before an exact match, the result is masked, duplicate matches are rejected, and a no-match path offers profile creation. A number alone never opens a profile: the customer must then enter their own PIN, and the code path stays available for a first sign-up and for recovery. Each wrong PIN is counted and five wrong attempts pause sign-in for 15 minutes on both the phone and the server. On the laptop path the verification code is written to a private file on the shop machine for the owner to read out; a hosted deployment should connect a real SMS provider before exposing profile or wallet data.

## Money and payment boundary

The APK's local workflow simulates state transitions for testing and uses configurable manual Easypaisa, JazzCash, NayaPay and SadaPay claims. A claim remains pending until the owner verifies the external account. Promotional credit is separate and non-withdrawable. No card, UPI or provider PIN/password is stored.

The server is the required source of truth for real funds. It rejects forged add-on prices, locks wallets before wallet-booking commitment, limits deposit/withdrawal submission to customer actors, and limits staff completion to assigned bookings. It is fail-closed in its default production profile until a real identity verifier is configured.

## Verification completed

- JavaScript syntax checks for root and Android assets.
- Java domain, financial hardening and Android navigation self-tests.
- Spring test suite: 69 tests, 0 failures, 0 errors, including PIN registration/sign-in, PIN lockout, current-PIN change checks, media validation, auth/catalog hardening and database idempotency tests.
- APK build, v2 signature verification, zip alignment and manifest inspection.
- Installed-shell checks on the rebuilt release APK: launcher label reads `Salon`, the bundled `assets/app.js` carries the phone branding panel, and the release APK still contains no salon name in its launcher or splash resources.
- Headless browser check of the phone branding panel (`tmp/verify-phone-branding.js`): 14 checks, including the owner-only panel, the name sent to the native bridge, the icon sent as a data URL, a refused blank name and the panel staying hidden in a plain browser.
- Release APK byte scan for customer/payment fixture markers: none found.
- Browser release onboarding and customer/owner/lookup/add-on/full-slot smoke checks were completed during implementation.
- PDF render/text inspection: all 20 pages, 3,305 extracted words.

## Remaining before real shared deployment

The application code and local release build are complete. The remaining work is environment setup that cannot be performed without owner-controlled accounts and secrets:

1. Deploy PostgreSQL and the Spring server behind TLS, then place database/API/OTP/notification secrets in the host secret store. `docker-compose.yml`, `Caddyfile`, backup scripts and rate limits are included.
2. Configure a real SMS OTP provider URL/token and the owner bootstrap phone. The server already issues revocable database-backed opaque sessions and checks roles/permissions; no provider credential is bundled.
3. Build the Android release with the deployed HTTPS API origin (`-ApiBaseUrl=https://...`) and set the salon UUID. The current APK is the safe offline release because no public API origin exists yet.
4. Configure an SMS/push delivery endpoint for the durable notification outbox. The outbox, retry dispatcher and provider boundary are implemented.
5. Connect official Pakistani payment-provider merchant APIs only after merchant credentials and webhook verification are available. Manual owner-approved deposits remain the safe default.
6. Run live PostgreSQL migration/concurrency tests using the owner’s database credentials. H2 and service-level concurrency/idempotency tests pass locally; this workstation has PostgreSQL running but no authorized database password was supplied.

The authoritative server add-on catalog, server-side media uploads for logos/haircut styles, one-time deposit bonus flag, authenticated API client, OTP/session endpoints, owner role checks and outbox notification model are already implemented.

## Build and install

All local SDK, Gradle and Maven caches are on `E:`. From `E:\Barbar-Shop`:

```powershell
powershell -ExecutionPolicy Bypass -File .\android\build-apk.ps1 -Variant Release -SkipLint
```

Copy the resulting APK to the phone, enable installation from the file manager when Android asks, and install it. If an older debug APK with the same package is already installed, uninstall that debug build first because it has a different signing key. Keep `android\keys\ayan-salon-release.jks` and `android\release-signing.properties` private and backed up.

There is no GitHub remote configured yet. A repository URL and GitHub authentication are required before pushing or publishing CI artifacts.
