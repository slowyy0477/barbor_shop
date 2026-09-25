# Ayan Beauty Salon App: PDF-to-Java implementation mapping

This document maps the attached `Salon-App-Blueprint-by-Roy-Digital.pdf` to the Android Java build. The PDF is the product source; the user's later instruction changes the delivery target from a clickable prototype to a mobile app. Currency is PKR throughout the app, even where the PDF examples use the Indian rupee symbol.

## Product decisions carried into the app

| PDF example or rule | Android value and behavior |
| --- | --- |
| Wallet top-up | Customer pays PKR 500; the salon adds PKR 50 bonus; displayed balance is PKR 550. Paid and bonus balances remain separate. |
| Add-ons | Beard Trim +PKR 99, +12 minutes; Head Massage +PKR 199, +18 minutes. Both are off by default and the running total changes immediately. |
| Haircut reminder | Default 25 days after a completed haircut. Each service stores its own repeat cycle and the owner can edit it. |
| Referral | Ayan defaults to PKR 100 salon credit for the referrer and PKR 100 off for the new customer; both values are owner-editable and stay pending until the first eligible paid visit is completed. |
| Wallet scope | Store credit is redeemable only at this salon, cannot be withdrawn as cash, and expires according to the owner rule. |
| Financial history | Never delete a wallet entry. Refunds and corrections append a reversal/refund entry with a mandatory reason. |
| Communication | Service reminders and promotions require explicit consent/opt-out state. A disabled reminder must be recorded as opted out. |

## Screen map

### Customer navigation

1. **Welcome / mobile lookup**: enter mobile number, verify OTP (or use a verified test code in development), choose the linked salon, and show consent choice.
2. **Home**: greeting, wallet balance, next booking, due reminder, recent visits, and one-tap booking.
3. **Book**: service catalogue, date, eligible staff, working-hour slots, optional add-ons, transparent total/duration, wallet/payment choice, and confirmation.
4. **Booking detail**: status, salon/staff/date/time, receipt, reschedule, and cancel. Cancellation preserves the booking record.
5. **Wallet**: total, paid credit, bonus credit, expiry terms, top-up/payment states, and immutable ledger.
6. **Visit history**: completed service, staff, date, final amount, payment state, and next suggested visit.
7. **Reminder detail**: last service, suggested date, Book Now, reminder preference, and opt-out confirmation.
8. **Refer & Earn**: personal code/link, share action, pending/successful referrals, reward rules, and release state.
9. **Profile & consent**: name, verified phone, last visit, communication consent, and account sign-out.
10. **System states**: loading skeleton, no visits/no bookings/no referrals/no wallet activity, payment success, booking success, slot-full, payment failure, expired-credit, and network/server error with retry.

### Owner/manager navigation

1. **Sign-in / staff role**: owner or manager authentication and salon selection.
2. **Today dashboard**: completed revenue, bookings, average bill, repeat/new customers, wallet credit collected, outstanding credit, reminder bookings, missed bookings, tomorrow count, and quick actions.
3. **Bookings / calendar**: day and list views, walk-in creation, filters, slot collision feedback, and status transitions: Pending, Confirmed, Completed, Cancelled, No-show.
4. **Booking detail / completion**: customer, service, add-ons, payment, notes, status, complete visit, refund/correction entry, and audit trail.
5. **Customers**: verified lookup, create/edit, consent, visit history, wallet summary, referrals, and customer-level reminders.
6. **Services & add-ons**: create/edit/activate services, PKR price, duration, category, repeat cycle, and add-on eligibility/availability by staff.
7. **Staff & working hours**: create/edit/activate staff, skills, weekly hours, breaks, and assigned bookings.
8. **Wallet ledger**: payments, paid credit, bonus credit, debits, refunds, reversals, balances, expiry, mandatory adjustment reason, and export.
9. **Reminders**: due queue, delivery status, converted booking, failed delivery, opt-out, and approved resend.
10. **Referrals**: code/link, invited/registered/pending/rewarded/rejected, fraud checks, reward release, cancellation/refund handling, and monthly cap.
11. **Reports**: barber performance, top customers, popular services, add-on revenue, referrals, due reminders, cancellations, no-shows, and export.
12. **Settings**: salon details, wallet top-up/bonus/expiry, per-service reminder cycles, referral rewards/cap, cancellation policy, message templates, consent policy, and test-data controls.

## Java data model

Use one salon tenant in the first release, but keep `salonId` on every persisted entity. The current bundled Android/WebView build uses browser `localStorage` as a demo cache; the production sync/API layer must own authorization, OTP, payments, messages, and cross-device consistency.

| Collection | Required fields |
| --- | --- |
| `Salon` / `Settings` | `id`, name, logo/contact/address, hours, timezone, currency=`PKR`, wallet top-up, bonus, expiry days, default reminder days, referral rewards, monthly cap, cancellation policy, promotion flag |
| `Customer` | `id`, `salonId`, name, normalized phone, verified-at, consent, reminderOptOut, created-at, last-visit, birthday optional |
| `Service` | `id`, `salonId`, name, category, pricePkr, durationMin, repeatDays, active |
| `AddOn` | `id`, `salonId`, name, pricePkr, extraDurationMin, eligibleServiceIds, eligibleStaffIds, active |
| `Staff` | `id`, `salonId`, name, role, skills, weeklyHours, breaks, active |
| `Booking` | `id`, `salonId`, customerId, serviceId, addOnIds, staffId, start/end, source, status, totalPkr, durationMin, paymentMethod, referralId optional, reminderId optional, created/updated-at |
| `Visit` | `id`, bookingId, customerId, serviceId, staffId, addOnIds, completed-at, finalAmountPkr, paymentStatus, referralEligible |
| `WalletTransaction` | `id`, salonId`, customerId, immutable type, paidDeltaPkr, bonusDeltaPkr, debitDeltaPkr, amountDeltaPkr, balancePaidAfter, balanceBonusAfter, sourceId, reason, actorId, expiryAt, created-at |
| `Referral` | `id`, salonId, referrerId, referredCustomerId, uniqueCode, status, referrerRewardPkr, newCustomerDiscountPkr, firstEligibleBookingId, completedVisitId, rejectionReason, created/updated-at |
| `Reminder` | `id`, salonId, customerId, serviceId, sourceVisitId, dueAt, status, channel, consentSnapshot, bookingId optional, delivered-at, optOutAt |
| `AuditEvent` | `id`, actorId, action, entityType, entityId, reason, before/after snapshot, created-at |

Persist money as integer PKR (or minor units if a payment provider requires it), never as floating point. Normalize phone numbers before duplicate/self-referral checks.

## Automation contracts

### Completed visit

```text
transaction:
  require booking.status in {Confirmed, Pending}
  reject if booking already has a Visit
  set booking.status = Completed
  create Visit with final amount and payment status
  update Customer.lastVisit
  dueAt = completedDate + Service.repeatDays (fallback Settings.defaultReminderDays)
  if customer.reminderOptOut or no communication consent:
      create Reminder(status=Opted out, consentSnapshot=false)
  else:
      create one Reminder(status=Scheduled)
  update revenue, repeat/new, staff, add-on metrics
  if eligible paid first referral visit:
      validate referral and append both reward ledger entries
```

### Confirmed wallet top-up

```text
payment provider returns confirmed:
  append Paid credit transaction (+configured top-up)
  append Bonus credit transaction (+configured bonus)
  update materialized paid/bonus balances
  issue receipt with salon, amounts, expiry and balance
payment fails/cancels:
  append no credit entries; show retryable failure
```

### Confirmed booking

```text
validate service/staff active and staff skills
validate date >= today and start/end inside working hours/breaks
lock salonId + staffId + start/end
reject any overlapping Pending/Confirmed booking (duration-aware)
create booking and reserve slot
convert one matching open reminder to Converted
notify customer and owner after commit
```

### Referral completion

```text
require one referrer per referred phone and different normalized phones
require booking is paid, completed, first eligible visit, not cancelled/refunded
require monthly cap not exceeded
append referrer salon-credit reward transaction
record new-customer discount on the eligible invoice (not as wallet credit)
set referral.status = Rewarded; retain all prior states/audit events
```

### Daily owner summary

At the configured closing time, calculate from completed/paid records for the salon day: revenue, completed bookings, no-shows, cancellations, new versus repeat customers, add-on revenue, wallet credit collected/used, and tomorrow's booking count. Store the summary so a failed message can be retried without recalculating a different result.

## Motion and state requirements

The Android shell should use a short splash fade/scale, bottom-sheet transitions for booking/payment, 180-260 ms button/list transitions, skeleton shimmer while loading, and a reduced-motion mode that removes nonessential animation. Motion must never change the size of buttons, slots, cards, or table rows. Every async action needs visible loading, success, and error states; destructive-looking actions need an explicit reason/undo-safe reversal path.

## Acceptance checklist

- [ ] PKR appears in every customer and owner money label; no rupee symbol or INR wording remains.
- [ ] Owner edits to price, duration, repeat cycle, wallet rule, expiry, and referral reward affect future operations only; historical transactions keep their original values.
- [ ] Mobile number is normalized and verified before customer data is shown.
- [ ] Add-ons are unchecked initially; total and duration update on each change.
- [ ] Slot availability checks staff hours, breaks, service duration, and overlapping bookings.
- [ ] Duplicate confirmation is idempotent; one booking creates at most one visit/reminder cycle.
- [ ] Completed visit creates history and one service-specific reminder, respecting opt-out.
- [ ] Wallet confirmation creates two ledger rows for paid and bonus credit; failed payment changes nothing.
- [ ] Wallet debit consumes paid and bonus balances deterministically and records one debit entry.
- [ ] Refunds/corrections append entries with actor and mandatory reason; no financial row is deleted.
- [ ] Referral reward remains pending through registration and cancellation; releases only after a completed paid first visit.
- [ ] Self-referral, duplicate referrer assignment, refunded visit, and monthly cap are rejected.
- [ ] Customer can reschedule/cancel and the original booking remains auditable.
- [ ] Dashboard totals match completed visits and ledger entries for the selected salon day.
- [ ] Empty, loading, success, error, full-slot, expired-credit, failed-payment, no-show, and reminder-opt-out states are reachable from the UI.
- [ ] No raw card, UPI, OTP, or payment credentials are persisted in local storage or logs.

## Java hardening update

The Android model package now treats `WalletLedger` as a single-customer/single-salon stream. A
ledger rejects mixed transaction scopes and every mutating operation checks the supplied customer
and salon before replaying or appending an entry. Debit, refund, manual-adjustment, deposit,
withdrawal, service-payment, referral-reward, and reversal operations use stable IDs/references;
the same request can be retried safely, while a changed payload is rejected. Financial corrections
remain append-only.

`BookingRules.confirmBooking` compares all immutable booking fields (customer, salon, service,
staff, date/time, price, duration, add-ons, cycle key, and creation time) before accepting a retry.
`StaffMember` keeps role defaults separate from owner-issued explicit permissions, so changing a
role recalculates defaults without silently dropping explicit grants. `IdentityRules` and
`ReferralRules.claimUniqueByIdentity` normalize verified Pakistan phone formats and reject
self-referrals even when customer IDs differ.

The executable checks are `DomainSelfTest` and `HardeningSelfTest` under
`app/src/main/java/com/cornerchair/salon/model`. They compile with Java source/target 8 and pass
on the available JDK. Database-level uniqueness/locking is still required around these pure
methods when the production repository is connected.

## Android WebView trust boundary

`MainActivity` now keeps the native bridge behind a bundled-asset trust check. Only top-level
`file:///android_asset/...` documents can remain in the WebView; arbitrary local files, unknown
schemes and `javascript:` navigations are blocked. Known user-facing schemes (HTTP(S), phone,
email, SMS, geo and UPI) leave the WebView through Android's external intent handler. File-to-file
and file-to-network access, content-provider access, multiple windows, and mixed content are
disabled; Safe Browsing is enabled on API 26+. The bridge's copy/share methods are gated until the
trusted page finishes loading, receive a fresh per-document token (which cross-origin frames cannot
read), are bound to the activity instance, length-limited, and removed during activity teardown.
`NavigationPolicySelfTest` exercises traversal, encoded traversal, remote-page, malformed URL, and
external-scheme cases without requiring an Android SDK.

## PDF source versus user instruction

The PDF calls the supplied build a prototype and gives a seven-day prototype plan. The user's current instruction asks for a full mobile app in Java with motion GUI. Therefore the PDF's product rules, screens, collections, automation behavior, statuses, message principles, test cases, and PKR conversions are binding; its prototype-only delivery limit is superseded. A production release still needs an authenticated backend, payment gateway, notification provider, secure secrets, backups, monitoring, and Android signing configuration.
