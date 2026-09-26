# Salon OS

A mobile-first, working salon operations app based on the **Salon App Blueprint by
Roy Digital**. It runs **offline on the phone**: the Android app and the iPhone web
app keep every customer, booking and wallet entry inside the phone, and nothing is
sent to the internet or published anywhere.

The salon's own name, tagline, address, logo and colours are set by the owner inside
the app (Owner > Settings) rather than being fixed in the code. Browser QA loads
fixtures from the separate `qa-seed.js` file, which is deliberately excluded from
the Android assets, so a fresh Android install starts with an empty customer and
financial workspace. Money is displayed in PKR throughout.

## Run it

**The app:** <https://slowyy0477.github.io/barbar_shop/> - open it on any phone and
add it to the home screen, or install the APK below. Both work offline and share the
same screens, the same data model and the same owner settings. There is no server to
start, no database to run and no address to type anywhere.

To preview the web app on a computer, from this folder:

```powershell
python -m http.server 4173
```

Open [http://localhost:4173](http://localhost:4173).

For phone installation, owner sign in and the weekly data backup, see
[START-HERE.txt](START-HERE.txt) and
[docs/HOW-TO-USE-ON-PHONE.md](docs/HOW-TO-USE-ON-PHONE.md). For the Java mobile
package, see [GITHUB_SETUP.md](GITHUB_SETUP.md) and
[android/README.md](android/README.md).

## APK

The signed sideload APK is generated at
`android/app/build/outputs/apk/release/app-release.apk` and copied to
`Salon-App-v1.0.0.apk`. Build it with:

```powershell
.\android\build-apk.ps1 -Variant Release -SkipLint
```

The release artifact is built from release-mode assets, starts with no customer or
financial records, and is signed with the salon-owned key created by
`android/create-release-signing.ps1`. Keep the keystore and
`android/release-signing.properties` backed up privately; they are ignored by Git
and the same key is required for every future update. `-SkipLint` is needed only on
this workstation because the optional Android platform-tools package is not
installed. For a first-time setup, run `android/create-release-signing.ps1` once.
The debug APK remains available for development, but it cannot update an
installation signed with the salon key.

## Sign in

- Customers use a mobile number and a password of 10 to 20 characters with at least
  one letter and one number. There is no SMS code anywhere in the app.
- A new customer creates the account in one step and is signed in immediately.
- The owner menu is hidden: tap the round salon logo 5 times, then enter the owner
  access code. Change the code that the app was built with on your first day.
- Customers can never open the owner menu.

## Implemented

- Customer flow: profile, consent, visit history, salon wallet, separate paid/bonus
  balances, immutable wallet ledger, simulated top-up success/failure, service-based
  reminders, one-tap booking, optional add-ons, staff and slot selection, wallet
  checkout, referrals and reward states.
- Owner flow: dashboard metrics, today queue, walk-in and customer bookings, status
  updates, completion automation, customer management, editable services, prices,
  durations, repeat cycles, staff hours, optional add-on catalogue, payment methods,
  settings, reports, daily export and JSON export.
- Data safety: no card or UPI credentials, no financial record deletion, booking
  collision checks, consent-aware reminders, referral release after paid completion,
  and local test-data reset.

Offline rules and self-tests cover manual deposit verification, cash-only withdrawal
reservations, promotional-first service payments, owner/staff permissions and
append-only audit events. The app is a complete offline single-device workflow; real
money still needs owner accounts with the payment providers, and payments are
approved by the owner by hand.

## Blueprint implementation notes

### Screen map

Customer: Home, Book, Wallet, More/Profile, Refer & Earn (within More), booking
confirmation, wallet top-up state, wallet ledger.

Owner: Today dashboard, Bookings, Customers, Services & staff, Reports
(overview/barbers/top customers/popular services/add-on revenue/referrals/due
reminders/attendance), Settings (business profile, wallet, payment methods,
reminders, referrals, data controls).

### Collections

`Customers`, `Services`, `Staff`, `Bookings`, `Visits`, `WalletTransactions`,
`Referrals`, `Reminders`, `Settings` are represented in the persisted state object
in `app.js`.

### Automations

1. Completing a booking writes a visit, updates the customer last visit, calculates
   the service repeat date, schedules a consent-aware reminder and updates owner
   metrics.
2. Confirming a wallet top-up writes separate paid-credit and bonus-credit entries
   and updates the customer balance. Failed payment writes nothing.
3. Confirming a booking checks the staff/date/time collision, snapshots selected
   add-on prices, records the booking source and converts any same-cycle open
   reminder to `Converted`.
4. Completing a paid first visit for a referred customer changes the referral to
   `Rewarded` and releases the configured rewards.
5. The owner dashboard exposes the daily summary: revenue, completed/missed,
   new/repeat, add-on revenue and tomorrow's bookings; dedicated reports cover
   popular services, add-on revenue and due reminders.

### Status values

Bookings: `Pending`, `Confirmed`, `Completed`, `Cancelled`, `No-show`.

Wallet transactions: `Credited`, `Debited`, `Refunded`, `Reversed`.

Referrals: `Invited`, `Registered`, `Pending visit`, `Rewarded`, `Rejected`.

Reminders: `Scheduled`, `Sent`, `Delivered`, `Failed`, `Converted`, `Opted out`.

### PKR defaults

The sample wallet offer is **pay PKR 500 + PKR 50 bonus = PKR 550**. Sample add-ons
are **Beard Trim +PKR 99** and **Head Massage +PKR 199**. The default referral reward
is **PKR 100 salon credit** for the referrer and **PKR 100 off** for the new
customer; both values are editable under Owner > Settings.

### QA checklist

- Duplicate booking: confirm the same staff/date/time is rejected.
- Full slot: occupied slots render disabled in the booking flow.
- Failed payment: verify balance and ledger remain unchanged.
- Refund/reversal: preserve the original transaction and append a new correcting
  entry linked to its source transaction and reason.
- Expired credit: show configured expiry terms before payment.
- Self-referral: reject when the referrer and referred mobile numbers match.
- Cancelled referral booking: keep referral pending; only a completed paid visit can
  reward it.
- Reminder opt-out: toggle consent off and verify the next reminder is `Opted out`.
- Add-ons: verify no paid add-on is selected by default and totals update immediately.
- Owner edits: change a service price, then open a new booking and confirm the new
  PKR value appears.
