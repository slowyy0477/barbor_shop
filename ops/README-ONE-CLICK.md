# One-click salon server (for the owner)

You do **not** need to remember any command. Set this up once, then it works by itself.

## 1. Set it up once

1. Open the folder `E:\Barbar-Shop\ops`.
2. Right-click `install-one-click-start.ps1` and choose **Run with PowerShell**.
3. If Windows asks "Do you want to allow this app to make changes?", click **Yes**.
4. Wait for the line **"Done - your salon now starts with one click."**

That is the only setup step, ever.

## 2. What you get

On your **Desktop** and in the **Start Menu** (folder *Salon Server*):

| Shortcut | What it does |
| --- | --- |
| **1 START Salon** | Turns on the database + server + free phone link, then publishes the link. |
| **2 STOP Salon** | Turns the server and the phone link off at the end of the day. |
| **3 CHECK Salon** | Shows in one screen: database on/off, server on/off, the phone address. |

The same three files also sit in the main folder, so they are easy to find:

    E:\Barbar-Shop\1 START SALON.cmd
    E:\Barbar-Shop\2 STOP SALON.cmd
    E:\Barbar-Shop\3 CHECK SALON.cmd

Double-clicking **1 START Salon** when the server is already on changes
nothing: it says "ALREADY ON" and finishes. Type **R** only when you really want
to restart it (that also gives the phones a new address).

And from now on the server also starts **by itself** every time you sign in to Windows, so most days you do nothing at all.

## 2b. Your permanent online link

Share this address with customers. It never changes:

    https://slowyy0477.github.io/barbor_shop/

It works like this:

- GitHub serves the app page itself, so the link always opens, even when the
  laptop is off (it then says the salon server is not reachable).
- Every time the laptop opens its free phone link, the new address is published
  to the same GitHub repository (`api.json`), so the permanent link always knows
  where the salon is while the laptop is switched on.
- The link is free. Nothing to renew, nothing to pay.
- For a customer to sign in, book or send money, the laptop must be on and
  connected, exactly like the "Phone address" in section 4.

The phone app (APK) uses the same published address automatically. If a salon
phone still shows "Offline app on this phone", open **Owner > Settings** and
press **Save & reconnect** once.

## 3. Daily routine

1. Turn the laptop on and sign in to Windows.
2. The salon server starts by itself (a black window appears for a few seconds, then closes on its own).
3. On each salon phone the app already has the salon address saved. If the phones say the server is not reachable, open **3 CHECK Salon** and copy the new **Phone address** into the phone (see step 4).
4. To put the address on a phone: open the app, tap the round salon mark 5 times, go to **Owner > Settings**, paste it in **Salon server address**, tap **Save**.
5. At closing time: double-click **2 STOP Salon**, or just shut the laptop down.

## 4. The phone address changes

The free phone link (`https://something.trycloudflare.com`) is new every time the server starts.

Where to find the current one:

- the black **1 START Salon** window, on the line **Phone address**, or
- the file `E:\Barbar-Shop\ops\salon-public-url.txt`, or
- the **3 CHECK Salon** shortcut.

If the salon is always on the same Wi-Fi, you can also use the **Same Wi-Fi** address, which never changes.

## 5. If something looks wrong

| What you see | What to do |
| --- | --- |
| "ALREADY ON" | Good - nothing else needed. |
| Check shows **Salon server : OFF** | Double-click **1 START Salon** and wait for "DONE". |
| Check shows **Database : OFF** | Double-click **1 START Salon** - it turns the database on too. |
| Phone cannot reach the salon | Run **3 CHECK Salon**, copy the new **Phone address** into the phone again. |
| The laptop sleeps and phones stop working | Keep the laptop plugged in. The one-click setup already stops it sleeping on mains power. |

## 5b. How people sign in (no SMS, no codes)

Customers and the owner use a mobile number and a password. Nothing is sent to
any phone, so there is no message bill and nothing to read out at the counter.

- New customer: **Sign in > Create new account**, then name, mobile number and
  a password twice. Password rule: 10 to 20 characters with at least one letter
  and one number, for example `SalonPass2026`.
- Coming back: the same mobile number and password. The phone stays signed in.
- Owner: tap the round salon logo 5 times, tap **Owner**, then type the owner
  mobile number and the owner password.
- Forgot a customer password? Owner > Customers > **Reset password**.

New accounts are limited so one person cannot fill the books:

- **ONE** new account per phone.
- **THREE** new accounts per internet connection.
- Change both in `ops\salon-server.local.json`: `MaxAccountsPerDevice` and
  `MaxAccountsPerIp`. `0` switches a limit off. If a family shares one phone,
  set `MaxAccountsPerDevice` to 2 or 3 before they sign up.

The old SMS helper `ops\connect-sms.cmd` and `ops\show-last-code.cmd` are no
longer part of sign-in. You never need to run them.

## 6. Turn the automatic start off again

```powershell
powershell -ExecutionPolicy Bypass -File E:\Barbar-Shop\ops\install-one-click-start.ps1 -Uninstall
```

This removes the shortcuts, stops the server from starting by itself, and puts your old power settings back. Your salon data, database and customers are **not** touched.

Other options:

```powershell
# Shortcuts only, no automatic start at sign-in
powershell -ExecutionPolicy Bypass -File E:\Barbar-Shop\ops\install-one-click-start.ps1 -NoAutoStart

# Do not change any power settings
powershell -ExecutionPolicy Bypass -File E:\Barbar-Shop\ops\install-one-click-start.ps1 -KeepPowerSettings
```

## 7. Privacy and safety

- `ops\salon-server.local.json` holds the database password and the owner password. Never send it to anyone.
- Owner password helpers: `ops\show-owner-password.cmd` shows it, `ops\set-owner-password.cmd` changes it.
- Customer passwords can only be reset by the owner: Owner > Customers > Reset password.
- Important files to keep private: `ops\salon-server.local.json`, `ops\power-settings-backup.json`, `android\release-signing.properties`.
- A backup of the database can be made any time with `ops\backup-postgres.ps1`.
