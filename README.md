# Ayan Beauty Salon OS

A mobile-first, working salon operations app based on the attached **Salon App Blueprint by Roy Digital**. It is branded for **Ayan Beauty Salon**, VC8Q+R33 Ayan Beauty Salon, Uqab Plaza, Gate Number 2, Katba Village, Kamra Kalan (0310 5301460), open Monday-Sunday, 8:00 AM-11:00 PM. Browser QA loads fixtures from the separate `qa-seed.js` file; that file is deliberately excluded from Android assets. A fresh Android install therefore starts with an empty customer and financial workspace. Money is displayed in PKR throughout. Owner-edited prices, service durations, wallet rules, reminder cycles and referral rewards are stored in the same data model that powers the customer view.

## Run it

From this folder:

```powershell
python -m http.server 4173
```

Open [http://localhost:4173](http://localhost:4173).

For a beginner-friendly phone installation, iPhone PWA setup, free-tier limits, server deployment checklist, and release-key backup guidance, see [DEPLOYMENT_GUIDE.md](DEPLOYMENT_GUIDE.md). For the Java mobile package and GitHub publishing steps, see [GITHUB_SETUP.md](GITHUB_SETUP.md) and [android/README.md](android/README.md).

## APK

The verified signed sideload APK is generated at `android/app/build/outputs/apk/release/app-release.apk`. Build it with:

```powershell
.\android\build-apk.ps1 -Variant Release -SkipLint
```

The release artifact is built from release-mode assets, starts with no customer or financial records, and is signed with the local salon-owned key created by `android/create-release-signing.ps1`. Keep `android/keys/ayan-salon-release.jks` and `android/release-signing.properties` backed up privately; they are ignored by Git and are required for future updates. `-SkipLint` is needed only on this workstation because the optional Android platform-tools package is not installed.

For a first-time setup, run `android/create-release-signing.ps1` once. The debug APK remains available for development, but it cannot update an installation signed with the salon key.

## Implemented

- Customer flow: profile, consent, visit history, salon wallet, separate paid/bonus balances, immutable wallet ledger, simulated top-up success/failure, service-based reminders, one-tap booking, optional add-ons, staff and slot selection, wallet checkout, referrals and reward states.
- Owner flow: dashboard metrics, today queue, walk-in and customer bookings, status updates, completion automation, customer management, editable services, prices, durations, repeat cycles, staff hours, optional add-on catalogue, payment methods, settings, reports, daily export and JSON export.
- Data safety: no card or UPI credentials, no financial record deletion, booking collision checks, consent-aware reminders, referral release after paid completion, and local test-data reset.

The Ayan-specific hardening package also includes Java rules and self-tests for manual deposit verification, cash-only withdrawal reservations, promotional-first service payments, owner/staff permissions and append-only audit events. The bundled Android app is a complete offline single-device workflow; shared multi-device operation and real money require the included server-side API, authenticated OTP/JWT identity, notification delivery, backups and official payment-provider integration.

## Blueprint implementation notes

### Screen map

Customer: Home, Book, Wallet, More/Profile, Refer & Earn (within More), booking confirmation, wallet top-up state, wallet ledger.

Owner: Today dashboard, Bookings, Customers, Services & staff, Reports (overview/barbers/top customers/popular services/add-on revenue/referrals/due reminders/attendance), Settings (business profile, wallet, payment methods, reminders, referrals, data controls).

### Collections

`Customers`, `Services`, `Staff`, `Bookings`, `Visits`, `WalletTransactions`, `Referrals`, `Reminders`, `Settings` are represented in the persisted state object in `app.js`.

### Automations

1. Completing a booking writes a visit, updates the customer last visit, calculates the service repeat date, schedules a consent-aware reminder and updates owner metrics.
2. Confirming a wallet top-up writes separate paid-credit and bonus-credit entries and updates the customer balance. Failed payment writes nothing.
3. Confirming a booking checks the staff/date/time collision, snapshots selected add-on prices, records the booking source and converts any same-cycle open reminder to `Converted`.
4. Completing a paid first visit for a referred customer changes the referral to `Rewarded` and releases the configured rewards.
5. The owner dashboard exposes the daily summary: revenue, completed/missed, new/repeat, add-on revenue and tomorrow's bookings; dedicated reports cover popular services, add-on revenue and due reminders.

### Status values

Bookings: `Pending`, `Confirmed`, `Completed`, `Cancelled`, `No-show`.

Wallet transactions: `Credited`, `Debited`, `Refunded`, `Reversed`.

Referrals: `Invited`, `Registered`, `Pending visit`, `Rewarded`, `Rejected`.

Reminders: `Scheduled`, `Sent`, `Delivered`, `Failed`, `Converted`, `Opted out`.

### PKR defaults

The sample wallet offer is **pay PKR 500 + PKR 50 bonus = PKR 550**. Sample add-ons are **Beard Trim +PKR 99** and **Head Massage +PKR 199**. The default referral reward is **PKR 100 salon credit** for the referrer and **PKR 100 off** for the new customer; both values are editable under Owner > Settings.

### QA checklist

- Duplicate booking: confirm the same staff/date/time is rejected.
- Full slot: occupied slots render disabled in the booking flow.
- Failed payment: verify balance and ledger remain unchanged.
- Refund/reversal: preserve the original transaction and append a new correcting entry linked to its source transaction and reason.
- Expired credit: show configured expiry terms before payment; enforce expiry in the production service layer.
- Self-referral: reject when implementing referral signup if referrer and referred mobile numbers match.
- Cancelled referral booking: keep referral pending; only a completed paid visit can reward it.
- Reminder opt-out: toggle consent off and verify next reminder is `Opted out`.
- Add-ons: verify no paid add-on is selected by default and totals update immediately.
- Owner edits: change a service price, then open a new booking and confirm the new PKR value appears.
