# Password sign-in contract (no SMS / no OTP)

Owner decision: the app must never ask a customer or the owner for an SMS code.
Every account is created and opened with **mobile number + password**. This file
is the single source of truth for the server and the phone/web client so both
sides stay byte-compatible.

## 1. Password rules (both roles)

| Rule | Value |
| --- | --- |
| Length | 10 to 20 characters |
| Allowed characters | `A-Z a-z 0-9 @ # $ % ^ & * ! . _ + -` |
| Must contain | at least one letter **and** at least one digit |
| Must not be | only letters, only digits, or equal to the mobile number |

`SalonOwner2026` is the current owner password. A 4-6 digit PIN is **not** a valid
password any more. Existing PIN rows stay readable only so an old installed
client can be told to upgrade; new accounts must use a password.

## 2. Endpoints

All of these are under `/api/auth`, which is already `permitAll` in
`SecurityConfig`, and all of them are rate limited by `RateLimitFilter`.
`salonId` is sent in the body, never as a header.

### 2.1 Create a customer account

```
POST /api/auth/customer/signup
{
  "salonId": "00000000-0000-0000-0000-000000000001",
  "phone": "0300 1234567",
  "name": "Ali Raza",
  "password": "AliRaza2026x",
  "marketingConsent": false
}
```

The device key travels in the `X-Device-Id` request header, which
`api-client.js` sets on every `/api/auth/...` call. A `deviceId` field in the
body is also accepted, and a server-set `ayan_device` cookie is the last
fallback. `/api/auth/password/register` is mapped to the same handler, so an
older cached page keeps working.

`200` -> the same body as `/api/auth/pin/verify`:

```json
{ "accessToken": "...", "expiresAt": "...", "salonId": "...", "actorId": "...", "role": "CUSTOMER" }
```

Failures:

| Status | `code` | Meaning |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | Phone, name or password is not usable |
| 409 | `CONFLICT` | That number already has an account |
| 409 | `CONFLICT` | This phone already created one account for this salon |
| 409 | `CONFLICT` | Too many new accounts from this internet connection |
| 429 | `RATE_LIMITED` | Too many attempts; wait a minute |

All three signup refusals share the 409 status. The message names the rule that
was hit, and the client shows that message, so a customer always learns what to
do next instead of seeing a bare number.

### 2.2 Sign in

```
POST /api/auth/password/login
{ "salonId": "...", "phone": "03001234567", "pin": "AliRaza2026x" }
```

The password travels in the `pin` field for compatibility with the installed
APK. `/api/auth/password/verify` and `/api/auth/pin/verify` are the same handler
under two names.

`200` -> session body (any role: CUSTOMER, OWNER, MANAGER, BARBER, STAFF).

| Status | `code` | Meaning |
| --- | --- | --- |
| 401 | `UNAUTHORIZED` | Wrong number or password. Never say which one. |
| 429 | `UNAUTHORIZED`+lock | 5 wrong tries lock the account for 15 minutes |

`phone` may be empty for the owner so the long owner password alone still opens
the owner workspace (the laptop has no SMS provider).

### 2.3 Owner resets a customer password (recovery without SMS)

```
POST /api/salons/{salonId}/customers/{customerId}/password-reset
{ "password": "NewSecret2026" }
```

Owner-only (role `OWNER` or the `manage_customers` permission). Writes an
`audit_logs` row. This is the only way a customer recovers a forgotten password
because no SMS is sent.

### 2.4 Deprecated endpoints

`/api/auth/otp/request`, `/api/auth/otp/verify` and `/api/auth/customer/register`
stay mapped so an already-installed APK gets a clear message instead of a crash,
but they are switched off with `ayan.auth.otp.enabled=false` (default) and answer
`410 GONE` with code `SMS_DISABLED`:

> "SMS codes are switched off. Please update the app and sign in with your mobile number and password."

`/api/auth/pin/verify` keeps working as an alias of `/api/auth/password/login`
for older clients.

## 3. Anti-abuse rules

1. **One phone, one account.** The client stores a random `deviceId` (UUID v4) in
   `localStorage` and sends it on every register call. The server writes
   `sha256("device:" + deviceId)` into `signup_guards.device_hash`, where a
   partial unique index on `(salon_id, device_hash)` makes the rule race-proof,
   and keeps only the digest. A customer who signs in also claims the device
   when that device has no signup yet, so accounts created before this release
   are covered without locking anybody out.
2. **Three accounts per internet address.** The server writes
   `sha256("network:" + clientIp)` into `signup_guards.ip_hash` and refuses
   registration once `ayan.auth.signup.max-per-network` (default `3`) accounts
   already exist for that `(salon_id, ip_hash)`.
3. The client IP comes from `CF-Connecting-IP`, then the first `X-Forwarded-For`
   entry, then `request.getRemoteAddr()`. A free tunnel makes every phone look
   like one address otherwise, which would block the fourth real customer. The
   forwarded header is trusted only when the request already arrived from the
   loopback or a private address, so a public caller cannot choose its own value.
4. Login is **not** blocked by these rules - a customer may sign in from any
   phone or network. Only creation is limited.
5. Both caps are configurable through `AYAN_SIGNUP_MAX_PER_NETWORK` and
   `AYAN_SIGNUP_MAX_PER_DEVICE`. Zero switches that particular cap off.
   `ops\verify-password-auth.ps1` proves all of the above against a running
   server and removes the accounts it created.

## 4. CORS (this was the cause of "Request Failed (403)")

The APK falls back to `file:///android_asset/index.html`, and a `file://` page
sends `Origin: file://`. Spring answered the preflight with
`403 Invalid CORS request` and an empty body, which the client could only render
as `Request Failed (403)`. Any unknown origin did the same.

Because this API authenticates with a bearer token and never with cookies
(`allowCredentials(false)`), the CORS mapping must accept **every** origin:
allowed origins default to `*`, still overridable with
`AYAN_WEB_ALLOWED_ORIGINS`. This removes the whole class of 403 failures for the
APK, the GitHub Pages link and any future tunnel address.

## 5. Client behaviour

* `AyanApi.deviceId()` returns a stable per-install id from `localStorage`,
  falling back to `sessionStorage`, creating it on first use, and sending it as
  the `X-Device-Id` header on `/api/auth/...` calls only.
* The sign-in sheet has two fields only: mobile number and password.
* The create-account sheet has mobile number, name, password, confirm password
  and the marketing-consent checkbox. It is one step - there is no code step.
* After a successful call the client stores the session and shows the customer
  home (or the owner dashboard), never the sign-in sheet again.
* The owner sheet has an optional mobile number and a password, and no SMS
  button at all.

## 6. Error copy shown to the customer (simple English)

| Server `code` | Message |
| --- | --- |
| `UNAUTHORIZED` | "Wrong mobile number or password. Check both and try again." |
| phone already used | "An account already exists for this mobile number. Sign in with your password instead." |
| device already used | "This phone already created a salon account. Sign in with that mobile number and password, or ask the salon owner to reset it." |
| network cap reached | "This internet connection already created 3 accounts. Sign in to your own account, or ask the salon owner to raise the limit." |
| `SMS_DISABLED` | "SMS verification codes are switched off. Sign in with your mobile number and password." |
| lockout | "Too many wrong tries. Please wait 15 minutes and try again." |
