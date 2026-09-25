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

Only if auto-find ever fails: open the app, tap the round salon mark 5 times, sign in
as owner, and use **Owner > Settings > Salon server address**. Paste the address
shown by `ops\status-salon-server.cmd` on the laptop. Only `https://` addresses
are accepted.

## 4. Owner menu (customers never see it)

The owner menu is hidden on purpose:

1. Tap the round salon logo at the top of the app 5 times quickly.
2. Tap **Owner**.
3. Type your owner mobile number and your owner password. Tap **Sign in**.

The owner password lives on the shop laptop only. To see it or change it, double
click one of these on the laptop:

    E:\Barbar-Shop\ops\show-owner-password.cmd    (shows it)
    E:\Barbar-Shop\ops\set-owner-password.cmd     (changes it)

Five wrong tries pause the owner sign in for 15 minutes. Use a password of at
least 10 characters for your own safety.

Customers can never see this menu. Only the owner password opens it, and the
server refuses owner data for customer accounts even if someone finds the button.

## 5. Customer sign up and sign in (mobile number + password)

There is no SMS code anywhere in this app. Customers use a mobile number and a
password only.

New customer, first time:

1. Open the app and tap **Sign in**.
2. Tap **Create new account**.
3. Type name, mobile number, password, then the same password again.
4. Tick the box that allows promotional messages (optional, but the customer
   must tick it before any offer message can be sent).
5. Tap **Create account**. The customer is signed in straight away.

The password rule, tell the customer this:

- at least 10 characters and at most 20
- must have at least one letter and at least one number
- example: `SalonPass2026`

Because there is no SMS code, the password cannot be recovered by message. Tell
the customer to remember it.

Coming back later:

1. Tap **Sign in**.
2. Type the same mobile number and password.
3. Tap **Sign in**. The app keeps them signed in on that phone, so they normally
   do not type it again.

Customer forgot the password? You fix it as the owner: open **Owner > Customers**,
find the customer, tap **Reset password**, set a new one, then tell the customer
the new password. They can change it later from **More**.

Limits on new accounts:

- ONE new account per phone. A second account on the same phone is refused.
- THREE new accounts per internet connection. A fourth is refused.
- Change both numbers any time in `E:\Barbar-Shop\ops\salon-server.local.json`
  using `MaxAccountsPerDevice` and `MaxAccountsPerIp`. Set one to 0 to switch
  that limit off.
- If a family shares one phone, set `MaxAccountsPerDevice` to 2 or 3 first,
  otherwise the second family member is refused.

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
