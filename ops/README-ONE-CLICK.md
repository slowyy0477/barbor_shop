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
