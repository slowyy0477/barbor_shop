# One-click salon server (for the owner)

You do **not** need to remember any command. Set this up once, then it works by itself.

## 1. Set it up once

1. Open the folder `E:\Barbar-Shop\ops`.
2. Right-click `install-one-click-start.ps1` and choose **Run with PowerShell**.
3. If Windows asks "Do you want to allow this app to make changes?", click **Yes**.
4. Wait for the line **"Done - your salon now starts with one click."**

That is the only setup step, ever.

## 2. What you get

On your **Desktop** and in the **Start Menu** (folder *Ayan Salon*):

| Shortcut | What it does |
| --- | --- |
| **Start Salon Server** | Turns on the database + server + phone link. Use it if the server is off. |
| **Check Salon Server** | Shows in one screen: database on/off, server on/off, the phone address. |
| **Stop Salon Server** | Turns the server off at the end of the day. |

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
- For a customer to send money or receive a code, the laptop must be on and
  connected, exactly like the "Phone address" in section 4.

The phone app (APK) uses the same published address automatically. If a salon
phone still shows "Offline app on this phone", open **Owner > Settings** and
press **Save & reconnect** once.

## 3. Daily routine

1. Turn the laptop on and sign in to Windows.
2. The salon server starts by itself (a black window appears for a few seconds).
3. On each salon phone the app already has the salon address saved. If the phones say the server is not reachable, open **Check Salon Server** and copy the new **Phone address** into the phone (see step 4).
4. To put the address on a phone: open the app, tap the **AB** mark 5 times, go to **Owner > Settings**, paste it in **Salon server address**, tap **Save**.
5. At closing time: double-click **Stop Salon Server**, or just shut the laptop down.

## 4. The phone address changes

The free phone link (`https://something.trycloudflare.com`) is new every time the server starts.

Where to find the current one:

- the black **Start Salon Server** window, on the line **Phone address**, or
- the file `E:\Barbar-Shop\ops\salon-public-url.txt`, or
- the **Check Salon Server** shortcut.

If the salon is always on the same Wi-Fi, you can also use the **Same Wi-Fi** address, which never changes.

## 5. If something looks wrong

| What you see | What to do |
| --- | --- |
| "The salon server is already running" | Good - nothing else needed. |
| Check shows **Salon server : OFF** | Double-click **Start Salon Server** and wait for "Done". |
| Check shows **Database : OFF** | Double-click **Start Salon Server** - it turns the database on too. |
| Phone cannot reach the salon | Run **Check Salon Server**, copy the new **Phone address** into the phone again. |
| The laptop sleeps and phones stop working | Keep the laptop plugged in. The one-click setup already stops it sleeping on mains power. |

## 5b. Real sign-in codes by SMS or WhatsApp

Out of the box there is no SMS bill, so the code is written to
`ops\salon-sign-in-codes.log` on this laptop and the owner reads it to the
customer. To send the code straight to the customer's mobile:

1. Create an account with one provider (Twilio and WhatsApp both work with
   Pakistani numbers; your own SMS gateway can be used too).
2. Double-click `ops\connect-sms.cmd`.
3. Answer the four questions. The script saves the credentials privately, then
   restarts the salon server and sends one test message.

Nothing else changes: the same screen, the same code, but it arrives as a real
message. If the provider ever fails, the code is still written to the log on the
laptop so a customer at the counter is never stuck.

The credentials live only in `ops\salon-server.local.json` on this machine and
are never uploaded to GitHub. To go back to the free on-machine codes, run
`ops\connect-sms.cmd` again and choose **0**.

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

- `ops\salon-server.local.json` holds the database password and the owner PIN. Never send it to anyone.
- Important files to keep private: `ops\salon-server.local.json`, `ops\power-settings-backup.json`, `android\release-signing.properties`.
- A backup of the database can be made any time with `ops\backup-postgres.ps1`.
