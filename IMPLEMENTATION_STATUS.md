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
- Sign-in codes without an SMS bill: on the laptop path the server runs the `local` profile, so each verification code is written to `ops\salon-sign-in-codes.log` (and the server log) instead of being texted. The owner runs `ops\show-last-code.ps1` and reads the newest code to the customer at the counter. The code is single use and expires in about five minutes.
- Real sign-in codes: `SmsDeliveryClient` sends the same code as a real message through Twilio, the WhatsApp Cloud API or any gateway webhook (`AYAN_SMS_PROVIDER`). `ops\connect-sms.ps1` (double-click `ops\connect-sms.cmd`) asks for the credentials, stores them only in the ignored `ops\salon-server.local.json`, restarts the server and sends one test code. On the laptop profile a provider failure falls back to the on-machine code file, so a customer at the counter is never stranded; on a hosted server the failure stays visible and the outbox keeps the notification for a retry.
- Permanent public link: the repository is published with GitHub Pages at <https://slowyy0477.github.io/barbor_shop/>. `api.json` carries the current salon address, the laptop republishes it through `ops\publish-salon-address.ps1` every time the free tunnel opens or is refreshed, and the page (and the installed phone app) read it automatically. The page is same-origin aware: when the salon server itself serves it, no lookup is needed.
- Owner dashboard, walk-in bookings, status changes, completion automation, customer/service/staff management, payment methods, settings, reports, exports, audit history and reversal/reason workflows.
- Release-mode startup with no customer, booking, visit, deposit, withdrawal, referral or wallet transaction records. The bundled catalog contains the initial Ayan salon configuration and is editable by the owner.
- Spring Boot/PostgreSQL server boundary with integer PKR minor units, append-only financial ledger, manual provider verification, wallet reservations, idempotent mutation keys, tenant checks, actor permissions, referral checks and audit records.

## APK delivered

The latest signed sideload APK is:

`E:\Barbar-Shop\Salon-App-v1.0.0.apk` (identical copy of `E:\Barbar-Shop\android\app\build\outputs\apk\release\app-release.apk`)

SHA-256:

`a3b946393fa22c7fd373eba1a8e078616ced166488ed7b075a924bdf37f584da`

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
- Spring test suite: 78 tests, 0 failures, 0 errors, including PIN registration/sign-in, PIN lockout, current-PIN change checks, media validation, auth/catalog hardening, database idempotency tests and the SMS provider request tests (`SmsDeliveryClientTest`).
- Public-link checks (`tmp/verify-public-link.js`): 7 checks, proving the page resolves the salon server when it is served by the server itself, when it is served from GitHub Pages with a published address, when the host publishes nothing, inside the offline phone bundle, and when a phone was deliberately set to offline mode.
- Live checks against the running system: the tunnel and the GitHub Pages link both answered `200`, the API returned `Access-Control-Allow-Origin` for the Pages origin and for the phone bundle origin, and the published `api.json` matched the live tunnel address.
- APK build, v2 signature verification, zip alignment and manifest inspection.
- Installed-shell checks on the rebuilt release APK: launcher label reads `Salon`, the bundled `assets/app.js` carries the phone branding panel, and the release APK still contains no salon name in its launcher or splash resources.
- Headless browser check of the phone branding panel (`tmp/verify-phone-branding.js`): 14 checks, including the owner-only panel, the name sent to the native bridge, the icon sent as a data URL, a refused blank name and the panel staying hidden in a plain browser.
- Release APK byte scan for customer/payment fixture markers: none found.
- Browser release onboarding and customer/owner/lookup/add-on/full-slot smoke checks were completed during implementation.
- PDF render/text inspection: all 20 pages, 3,305 extracted words.

## Remaining before real shared deployment

The application code and local release build are complete. The remaining work is environment setup that cannot be performed without owner-controlled accounts and secrets:

1. Optional: deploy PostgreSQL and the Spring server behind TLS for a host that runs without the laptop (`docs/PERMANENT-HOSTING.md`, `Dockerfile`, `render.yaml`). The laptop path already runs with TLS through the free tunnel.
2. Create the SMS provider account and run `ops\connect-sms.cmd`. The code path, adapters and tests are complete; only owner-controlled credentials are missing, and the on-machine code file covers the counter until then.
3. Optional: build the Android release with a baked HTTPS API origin (`-ApiBaseUrl=https://...`). The current APK publishes its address lookup instead, so a fresh install finds the salon server without any typing.
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

The repository is pushed to <https://github.com/slowyy0477/barbor_shop> (`main`) and published at <https://slowyy0477.github.io/barbor_shop/>. The stored GitHub credential on this laptop lets the laptop publish a new salon address after every restart without asking for a password. `ops\push-to-github.cmd` remains for a fresh machine.
