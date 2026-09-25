# How to use the salon app on your phone

Simple steps. Do them once and the app keeps working.

## 1. The permanent link (works on every phone, forever)

Open this in any phone browser:

**<https://slowyy0477.github.io/barbor_shop/>**

- On iPhone: open it in Safari, tap Share, then **Add to Home Screen**. It looks
  and works like a normal app.
- On Android: open it in Chrome, tap the three dots, then **Add to Home screen**.
- This link never changes. It opens the salon app and finds the shop laptop by
  itself.

## 2. The Android app file (APK)

The file is on the shop laptop here:

`E:\Barbar-Shop\Salon-App-v1.0.0.apk` (about 216 KB)

Steps on the phone:

1. Send that file to the phone (USB cable, or WhatsApp the file to yourself).
2. Tap the file on the phone.
3. Android will say the file is from an unknown source - tap **Settings** and
   allow **Install unknown apps** for the app you are using (Files or Chrome).
4. Tap **Install**, then open the app.

Needs Android 7 or newer. That is almost every phone from 2017 onwards.

## 3. What the app does by itself

1. It looks up the shop server address from the permanent link above.
2. It connects to the salon automatically. You do not type anything, ever.
3. If the shop laptop is switched off or has no internet, the app still opens
   and works in offline mode on the phone's own data.
4. When the laptop is switched on again, the app reconnects on its own.

The laptop also watches the free internet link while the shop is open. It checks
every couple of minutes and, if the link has stopped answering, it opens a fresh
one (Cloudflare first, then serveo, then Tunnelmole) and publishes the new
address. Phones pick it up by themselves within a few minutes, so there is
nothing to do by hand.

Only if auto-find ever fails: open the app, tap the **AB** mark 5 times, sign in
as owner, and use **Owner > Settings > Salon server address**. Paste the address
shown by `ops\status-salon-server.cmd` on the laptop. Only `https://` addresses
are accepted.

## 4. Owner menu (customers never see it)

The owner menu is hidden on purpose:

1. Tap the **AB** logo 5 times quickly.
2. Sign in. Online: mobile **03007654321**, PIN **246813**.
   Offline (no internet): code **530146**.
3. Five wrong tries lock the owner sign-in for 15 minutes.

Please change the PIN later from the owner settings so only you know it.

## 5. Customer sign-in codes

Right now (free mode): when a customer asks for a code, the shop laptop saves it
for 5 minutes. Read it out to the customer with the shortcut
`ops\show-last-code.cmd` on the laptop. Codes work once and then expire.

Later (real SMS): real messages to any mobile number need a paid provider
account - Twilio, Meta WhatsApp Cloud API, or a Pakistani bulk SMS gateway. The
server already supports all three:

1. On the laptop, double-click `ops\connect-sms.cmd`.
2. Answer its questions with your provider keys. They are saved privately in
   `ops\salon-server.local.json`, which is never uploaded to GitHub.
3. It restarts the server and can send one test message.

If the provider ever fails, the free laptop code is still written, so a customer
at the counter is never stuck. Full details: `docs\SMS-SETUP.md`.

## 6. Two ways to run the salon

| Way | Cost | Good for | Watch out |
| --- | --- | --- | --- |
| Shop laptop runs everything | free | normal days, everything stays with you | laptop must stay on while customers use the online mode |
| Free cloud copy (Render + Neon) | free | laptops off, power cut | free servers sleep after ~15 idle minutes and take ~50 seconds to wake |

Steps for the cloud copy: `docs\PERMANENT-HOSTING.md`. You sign in once with your
GitHub account `slowyy0477` and paste the values - nothing else.

## 7. If something does not work

1. Laptop switched on and connected to internet?
2. On the laptop run `ops\status-salon-server.cmd`. It must show the server as
   healthy and print the phone address.
3. Phone has internet (mobile data is fine)?
4. Close the app completely and open it again. Still stuck? Open the permanent
   link in the phone browser - it shows the same app.

## 8. Keep private (never send these to anyone)

- `ops\salon-server.local.json` (database password and provider keys)
- the release keystore and `release-signing.properties` (needed for every future
  app update - always use the same one)
- customer names, phone numbers and wallet history
