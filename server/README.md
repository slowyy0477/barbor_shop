# Ayan Beauty Salon Server

This directory contains the Java 17 Spring Boot backend for the Ayan Beauty Salon mobile app. It is deliberately separate from the offline WebView/Android bundle in the repository root. The server is the authoritative boundary for customer wallets, deposits, withdrawals, service payments, referrals, bookings, reminders, audit history, and owner reporting.

The beginner deployment, secrets, TLS, backup, monitoring, rate-limit, and free-tier guidance is in [DEPLOYMENT_GUIDE.md](../DEPLOYMENT_GUIDE.md). The server is not ready to accept real traffic until the production checklist there is complete.

## Run locally

Prerequisites:

- JDK 17 or newer (`JAVA_HOME` must point to a JDK, not a JRE).
- PostgreSQL 14+ and a database/user matching the environment variables below.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$env:JDBC_DATABASE_URL = 'jdbc:postgresql://localhost:5432/ayan_salon'
$env:JDBC_DATABASE_USERNAME = 'ayan'
$env:JDBC_DATABASE_PASSWORD = 'change-me'
.\mvnw.cmd spring-boot:run
```

Flyway applies `src/main/resources/db/migration/V1__initial_schema.sql`. Hibernate is configured with `ddl-auto=validate`, so an accidental entity/schema mismatch fails startup instead of silently changing financial tables.

Run the focused money tests:

```powershell
.\mvnw.cmd test
```

The tests use Mockito and do not require a live payment provider or PostgreSQL instance.

## Financial invariants

- All amounts are integer PKR minor units. `500 PKR` is stored as `50000`.
- `Wallet` keeps cash available, cash reserved for pending withdrawals, and promotional credit separately.
- `WalletTransaction` is append-only and has a unique `(salon_id, reference_id, type)` constraint.
- Deposit submission never credits a wallet. Owner approval locks the deposit and wallet, then creates separate cash and bonus ledger entries. The bonus is snapshotted when the customer submits the deposit so later setting edits cannot rewrite a pending request.
- Withdrawal request locks the wallet and moves cash into `cash_reserved_minor`. Rejection releases it; completion consumes it. Referral/promotional credit can never be reserved for withdrawal.
- Wallet service payment locks the booking and wallet, consumes promotional credit first when configured, and writes the cash/promo split to the ledger and a business revenue ledger.
- HTTP booking requests must name an active staff member so the server can enforce chair/slot collision checks. Client-supplied add-on amounts are rejected until an authoritative add-on catalogue and IDs are added; the server never accepts a price invented by the mobile client.
- Every mutating endpoint requires an `Idempotency-Key`. Keys are trimmed, bounded to 160 characters, scoped to salon/operation, and cannot be reused for a different response. Database uniqueness and aggregate state transitions make retries non-crediting. A distributed deployment should additionally use atomic key claiming/request fingerprints, a shared transaction/lock manager and duplicate-key monitoring.
- Deposit and withdrawal submission is customer-only; barbers/staff can complete only bookings assigned to their authenticated staff identity. Owner/staff identity is resolved from a server-side authenticated context. JSON fields cannot select a role or salon. The default production verifier fails closed until a real OTP/JWT/session provider is wired in.

## API outline

`/api/salons/{salonId}/deposits`, `/withdrawals`, `/bookings`, `/bookings/{id}/status`, `/bookings/{id}/complete`, `/referrals/{id}/release`, `/customers`, `/services`, `/staff`, `/settings`, and `/dashboard/today` are exposed by the controllers. Use a bearer token in development profile with the test-only form `ROLE:salonUuid:actorUuid`; never enable that verifier in production. Booking status changes require `manage_bookings`, an `Idempotency-Key`, and a reason for cancellation or no-show. Completion remains on `/bookings/{id}/complete` so payment, visit, and reminder automation cannot be bypassed.

Manual Easypaisa, JazzCash, NayaPay, and SadaPay flows are represented by configurable payment-method records and a normalized `provider_code` plus provider reference. `PaymentProvider` is an adapter seam for official merchant integrations. No private API is faked, no provider PIN/password is accepted, and no screenshot is treated as proof by itself.

Completed wallet visits create a `Visit`, calculate the next due date from the editable salon reminder cycle (25 days by default), and schedule one reminder. Confirmed bookings close any scheduled reminder for the same customer/service cycle; the hourly scheduler marks dispatched reminders as sent. Notification delivery is intentionally a replaceable gateway and the included implementation only logs a queue event.

## Production work still required

Before handling real customers or funds, configure the included opaque-session/OTP implementation with a real SMS provider, TLS, secrets management and PostgreSQL. Bind `PaymentProvider` only to official merchant APIs, encrypt/tokenize payout destinations, and run live migration/concurrency tests against the deployed database. The outbox-backed notification provider, authoritative add-on catalogue, atomic idempotency claim/fingerprint support and authenticated Android/WebView API client are included; they still need owner-controlled provider/database credentials.
