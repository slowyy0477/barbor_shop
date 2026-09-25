# Real sign-in codes by SMS or WhatsApp (free to start)

Today the salon works without any SMS bill: the code is written to the laptop and
the owner reads it out with `ops\show-last-code.ps1`. This page turns that into a
real message on the customer's phone.

## Status of this salon

- The sending path is built and was proven on this laptop on 25 Sep 2026: an OTP
  request produced a real HTTP POST to the configured provider with the correct
  `+92...` number, the provider token and a 5-minute code in the message body.
- Only the paid provider account is missing. Nothing else has to be coded: the
  owner runs one command, `ops\connect-sms.cmd`, and answers its questions.
- On the laptop the provider is a *primary* channel with a safety net: if the
  provider rejects or times out, the same code is still written to
  `ops\salon-sign-in-codes.log`, so a customer standing at the counter is never
  left without a code.
- Cost, honestly: no provider sends unlimited SMS for free. A Pakistani bulk SMS
  bundle or Twilio/WhatsApp credit costs a few rupees per message. Free trials
  work but can only message numbers you verified first.

The server already supports three providers. Only settings change; no code is
edited and the Android app is **not** rebuilt.

## Easiest way

Double-click `ops\connect-sms.cmd`. It asks which provider you want and for the
credentials, saves them in the private local file, restarts the salon server and
sends one test code to the owner's number. The manual paths below are the same
thing written out, in case you prefer to edit the file yourself.

| Setting `AYAN_SMS_PROVIDER` | What it uses | Cost to start |
| --- | --- | --- |
| *(empty)* | the laptop file + log, exactly like today | free |
| `twilio` | Twilio Programmable SMS | free trial credit |
| `whatsapp` | Meta WhatsApp Cloud API | free service tier |
| `webhook` | any provider that accepts `{ "to", "message" }` JSON | depends on provider |

## The easy way: `ops\connect-sms.cmd`

Double-click `E:\Barbar-Shop\ops\connect-sms.cmd` on the laptop. It asks which
provider and for the keys, saves them privately in `ops\salon-server.local.json`
(never uploaded to GitHub), restarts the salon server and can send one test
message to a number you choose. Run it with `off` to go back to the free
on-laptop codes at any time.

## Option A - Twilio (easiest to test)

1. Sign up at <https://www.twilio.com/try-twilio> with your email and verify your own phone.
2. In the Twilio console copy **Account SID** and **Auth Token**.
3. Buy or use a trial number (Twilio gives trial credit). Copy it in `+1...` form.
4. Put these lines in `E:\Barbar-Shop\ops\salon-server.local.json` (or set them as
   environment variables before starting the server):

```json
{
  "SmsProvider": "twilio",
  "TwilioAccountSid": "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "TwilioAuthToken": "your-auth-token",
  "TwilioFromNumber": "+12025550123"
}
```

5. Restart the salon server (`ops\stop-salon-server.cmd`, then `ops\start-salon-server.cmd`).
6. Try a customer sign-up. The code now arrives as an SMS.

Notes: on a Twilio **trial** account you can only text numbers you verified first -
add each customer number in the Twilio console while testing. Twilio trial messages
start with "Sent from your Twilio trial account".

## Option B - WhatsApp Cloud API (free service tier, best for Pakistan)

1. Create a Meta developer app at <https://developers.facebook.com/apps> and add the
   **WhatsApp** product.
2. Copy the **temporary access token** and the **Phone number ID**.
3. Settings:

```json
{
  "SmsProvider": "whatsapp",
  "WhatsAppToken": "EAAG...",
  "WhatsAppPhoneNumberId": "123456789012345"
}
```

Notes: the customer must have WhatsApp and must have messaged your business number
once in the last 24 hours for a free-form text. For salon reminders outside that
window, Meta requires an approved template message.

## Option C - a local Pakistani gateway

Any provider that accepts a JSON POST works:

```json
{ "SmsProvider": "webhook", "SmsWebhookUrl": "https://provider.example/send", "SmsWebhookToken": "optional-token" }
```

The server sends `{"to":"+923001234567","message":"..."}` and expects HTTP 200.

## Environment variables (if you prefer them over the JSON file)

```
AYAN_SMS_PROVIDER=twilio
AYAN_TWILIO_ACCOUNT_SID=ACxxxxxxxx
AYAN_TWILIO_AUTH_TOKEN=xxxxxxxx
AYAN_TWILIO_FROM_NUMBER=+12025550123
AYAN_WHATSAPP_TOKEN=EAAG...
AYAN_WHATSAPP_PHONE_NUMBER_ID=123456789012345
AYAN_SMS_WEBHOOK_URL=https://provider.example/send
AYAN_SMS_WEBHOOK_TOKEN=optional
```

## Safety rules already built in

- The code is never logged and never stored in the outbox payload.
- On the shop laptop a provider failure falls back to the on-machine code file, so
  a customer at the counter is never stranded; on a hosted server the failure is
  reported instead and the message stays in `notification_outbox` for an automatic
  retry with backoff (up to 10 attempts).
- Numbers are normalised to `+92...` before sending, so `0300 1234567`, `03001234567`
  and `+92 300 1234567` all work.
- Promotional messages still require the customer's saved consent. Sign-in codes are
  not marketing and are always allowed.
- Never paste a provider token into a GitHub issue, a screenshot or this chat.

## Quick check

Run the server and watch the log. A successful send logs no code. A rejected send
throws `SMS provider TWILIO rejected the message with HTTP 401 ...`, which means a
wrong token or a number the trial account has not verified yet.
