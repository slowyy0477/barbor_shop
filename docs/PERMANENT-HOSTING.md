# A permanent free online address for the salon

There are two free layers, and both are already set up for this salon:

| Layer | Address | Works when |
| --- | --- | --- |
| Permanent app link (GitHub Pages) | <https://slowyy0477.github.io/barbor_shop/> | Always. It opens the salon app on any phone and looks up where the laptop server is right now. |
| Optional always-on copy (Render + Neon) | `https://<your-service>.onrender.com` | Even when the shop laptop is switched off. Needs the one-time sign-in in Steps 2-3 below. |

The GitHub Pages layer is live and verified: `https://slowyy0477.github.io/barbor_shop/api.json`
carries the laptop's current tunnel address, and every fresh phone (browser or the
Android app) reads that file and connects by itself. When the tunnel address changes,
the laptop republishes it with one small commit - see Step 4.

What the optional always-on copy needs, all free: your GitHub account `slowyy0477`,
a Neon account, a Render account. Both services let you sign in with GitHub, so
there is no new password.

## Step 1 - Push the code to GitHub (already done)

The repository `slowyy0477/barbor_shop` is up to date on `main`. To push later
changes, double-click **`ops\push-to-github.cmd`** and, if GitHub asks, approve the
sign-in window that appears.

## Step 1b - Republish the laptop address after a restart (laptop mode)

The free tunnel address changes whenever the laptop restarts. The laptop fixes this
by itself: `ops\start-salon-server.cmd` opens the tunnel and publishes the new
address to GitHub. If the phones were already using an older address, run
`ops\refresh-public-url.cmd` once to republish without touching the database.

## Step 2 - Free PostgreSQL database (Neon)

1. Go to <https://neon.tech> and sign in with GitHub.
2. Click **New Project**. Any name works, for example `ayan-salon`. Region: Singapore.
3. Copy the connection details shown. You need the **host**, **database**, **user**
   and **password**.
4. Build the JDBC address like this:

```
jdbc:postgresql://<host>/<database>?sslmode=require
```

## Step 3 - Free server (Render)

1. Go to <https://render.com> and sign in with GitHub.
2. Click **New +** then **Blueprint**, and choose the `barbor_shop` repository.
   Render reads `render.yaml` from the project by itself.
3. Paste the environment values one by one:

| Name | Value |
| --- | --- |
| `JDBC_DATABASE_URL` | the `jdbc:postgresql://...` line from Step 2 |
| `JDBC_DATABASE_USERNAME` | Neon user |
| `JDBC_DATABASE_PASSWORD` | Neon password |
| `AYAN_AUTH_SESSION_SECRET` | any long random text, 32+ characters, keep it private |
| `AYAN_AUTH_OWNER_PHONE` | your owner mobile number, e.g. `03007654321` |
| `AYAN_AUTH_OWNER_PIN` | the owner PIN you want, 4 to 6 digits |
| `AYAN_SMS_PROVIDER` | `twilio`, `whatsapp` or `webhook` (see `docs/SMS-SETUP.md`) |
| the SMS values | from your provider |
| `AYAN_WEB_ALLOWED_ORIGINS` | your Render address, e.g. `https://ayan-salon.onrender.com` |

4. Click **Apply / Deploy** and wait for "Live".
5. Open `https://<your-service>.onrender.com/actuator/health`. It must say `{"status":"UP"}`.

## Step 4 - Put the address on the salon phones

Open the app, tap the AB mark 5 times, go to **Owner > Settings**, paste the Render
address into **Salon server address**, and Save. Do this on every salon phone.

## Step 5 - Understand the free tier honestly

| Thing | Free tier | What it means for the salon |
| --- | --- | --- |
| Render web service | free | sleeps after ~15 minutes with no visitors; the next visitor waits ~50 seconds |
| Neon PostgreSQL | free | plenty for thousands of visits; data stays |
| GitHub | free | code, backups by history |
| UptimeRobot (optional, free) | free | ping every 10 minutes to keep it awake during opening hours |
| Twilio / WhatsApp | trial / free tier | sign-in codes; see `docs/SMS-SETUP.md` |
| Your own domain | paid (~$10/year) | only needed for a prettier address |

Anything that costs money: a paid Render instance (never sleeps), a real domain, and
paid SMS bundles. None of them are required to run the salon.

## Step 6 - Updating the app later

Push the new code to GitHub (double-click `ops\push-to-github.cmd`). Render rebuilds
by itself and the phones keep the same address. No APK reinstall is needed for the
web app; a new APK is only needed when the Android shell itself changes.

## Step 7 - Backups (please do this)

On any machine with PostgreSQL tools installed:

```
pg_dump "postgresql://<user>:<password>@<host>/<database>?sslmode=require" > ayan-backup.sql
```

Keep those files private. They contain customer names, phone numbers and wallet history.

## What is different on a hosted server

- `ops\start-salon-server.ps1` and the one-click Desktop shortcuts are for the laptop
  only; a hosted server starts by itself.
- The laptop's "read the code from a file" trick is not usable on a host, so connect a
  real SMS provider first (Step 3) or read the code from the Render log viewer.
- Media (haircut photos) and the database live in Neon/Render, so nothing depends on
  your laptop being switched on.
