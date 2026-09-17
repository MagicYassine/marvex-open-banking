<img src="src/main/resources/static/logo/logo.svg" alt="Marvex" width="100%">

# QR Code Open Banking Payments

Marvex is a small full-stack app that lets a merchant (a shop, a freelancer, anyone who wants to get paid) generate a QR code or a plain link. A customer scans it or clicks it, picks their bank, logs into their bank, and the money moves — no card, no card reader, no card fees. This is done through **Open Banking**: instead of Marvex touching anyone's money directly, it asks a company called **Yapily** to connect to the customer's bank on its behalf.

Backend: Spring Boot (Java 21). Frontend: React (Vite). Database: PostgreSQL. It all builds into one deployable jar with the frontend baked in, but you can also run the two separately for development (that's what we do below).

This README is written so that someone who has never touched this project (or maybe Open Banking at all) can get it running on their own machine and actually understand *why* each step exists — not just copy-paste commands.

---

## 1. How it works

1. **A merchant signs up** (`/register`) with an email, verifies it, then fills in their business details **and their own bank account** (sort code + account number — this is where *their* money eventually lands).
2. **The merchant creates a payment** on their dashboard: an amount and an optional note. This gives them a **link** and a **QR code** — nothing has happened with any bank yet, it's just a "please pay me £X" request with a 10-minute expiry.
3. **A customer opens that link**, picks which bank they want to pay from (a dropdown, e.g. "Barclays", "Monzo"...), and gets redirected to their bank's real login page.
4. **The customer logs in and approves the payment** at their bank. The bank redirects them back to Marvex.
5. **Marvex asks Yapily "did that actually go through?"** and once it's confirmed, marks the payment as PAID, pings the merchant's dashboard live (WebSocket), and emails the merchant a notification.

The important thing to understand: **Marvex never sees the customer's bank login, and never touches the money.** Yapily is the middleman that talks to ~2000 European banks using one consistent API, so Marvex only has to integrate once instead of once per bank. That's the entire reason this project depends on Yapily, and why you can't skip setting it up if you want the *real* flow — although, good news, there's also a **Mock mode** that fakes the whole bank part so you can run and test everything without touching Yapily at all (explained below).

---

## 2. Two ways to run this locally

You have two options, and you should pick based on what you're trying to do:

| | **Mock mode** (recommended to start) | **Real Yapily sandbox mode** |
|---|---|---|
| What it does | Fakes the bank entirely — clicking "pay" just shows a Confirm/Cancel button | Talks to Yapily's real sandbox banks, exactly like production would |
| Needs a Yapily account? | **No** | **Yes** |
| Good for | Getting the app running fast, working on anything that isn't the Yapily integration itself | Testing/working on the actual Open Banking integration, or just wanting to see the real flow end to end |
| Set with | `payment.provider: mock` | `payment.provider: yapily` |

Start with Mock mode. Switch to Yapily sandbox mode once you want to see (or work on) the real thing.

---

## 3. Prerequisites

- Java 21
- Maven 3.8+
- PostgreSQL 14+
- Node.js 18+ (only needed if you're running the frontend in dev mode, which you will be)
- Docker (only needed to run the integration tests — the app itself doesn't need it)
- A working email account you can send SMTP from (see the "Emails" note in section 6 — this genuinely is not optional)
- **Only if you're doing Yapily sandbox mode:** a free Yapily Console account (see section 5)

---

## 4. Database setup

```sql
CREATE DATABASE marvex;
```

That's it. Flyway (a migration tool) creates every table automatically the first time the app starts — you never write or run schema SQL by hand.

---

## 5. Setting up your own Yapily account (skip this if you're doing Mock mode)

Yapily doesn't hand out a shared test account — everyone gets their own, for free, and it only takes a few minutes. Here's exactly what to do, based on [Yapily's own "Set Up Your Environment" guide](https://docs.yapily.com/getting-started/get-started):

### Step 1 — Create a Yapily Console account
Go to **[console.yapily.com](https://console.yapily.com/)** and sign up. Free, no card needed for the sandbox.

### Step 2 — Create an Application
An "Application" in Yapily's world is just a container that holds your API credentials and your list of connected banks. Inside the Console:
1. Go to **Applications** → **Create Application**
2. Give it any name you like (e.g. "marvex-local")
3. Click **Download Application ID & Application Secret** — this downloads a JSON file with two values you'll need in a second: `applicationUuid` and `secret`

**Save that Application Secret somewhere safe right now.** Yapily only shows it to you once — if you lose it, you have to revoke it and generate a new one.

### Step 3 — Add and register an institution ("bank")
This is the step people most often miss, and it's why the bank dropdown in the app will be **empty** if you skip it. Yapily doesn't automatically give your Application access to every bank — you have to explicitly add the ones you want to test with:
1. In the Console, go to **Applications**, select the application you just made
2. Open the **Connected Institutions** tab → click **Add Institutions**
3. Search for **`modelo-sandbox`** and tick it, then **Add selected**. (This is Yapily's own recommendation — it's a regulator-provided fake bank that behaves reliably, unlike some individual banks' own sandboxes.)
4. Click **Register**, then choose **Yes, Preconfigured Credentials** — this tells Yapily to use its own built-in test credentials for this bank, so you don't need anything extra from the bank itself.

Once that's done, the banks will ` shows up under your Application's registered institutions — and, this is the part that matters for Marvex, it will now show up when the app calls `GET /institutions`, which is exactly what powers the bank-picker dropdown a customer sees when paying. **If your dropdown is empty when you run the app, this is the step to go back and check.**

You can repeat step 3 for as many banks as you want to test against (there are preconfigured sandboxes for several major banks — filter for **PRECONFIGURED SANDBOX** in the institution picker to find them). `modelo-sandbox` alone is enough to get going.

### Step 4 — Turn your credentials into what the app needs
Marvex sends `Authorization: Basic <yapily.basic-token>` directly, which means it expects the **already base64-encoded** `applicationId:secret` string — not the raw id and secret separately. Generate it like this:

```bash
echo -n "YOUR_APPLICATION_ID:YOUR_APPLICATION_SECRET" | base64
```

That output is your `yapily.basic-token` value.

### To log in as the test customer at the bank
When you get redirected to one of the banks you picked up during a real payment test, use the following for the credentials to the sandbox account. 

(Full list of every sandbox bank's test credentials, in case you add others: [Yapily Sandbox Credentials](https://docs.yapily.com/resources/sandbox/sandbox-credentials).)

---

## 6. Configuration — what every setting actually means

The app uses two config files under `src/main/resources/`:

| File | When it's used | What's in it |
|---|---|---|
| `application-local.yml` | Running on your own machine | Real values, typed directly into the file. **This file is git-ignored — never commit it, it will contain real secrets.** |
| `application-dev.yml` | Deployed environments | Same settings, but every value comes from an environment variable instead of being hardcoded |

Create `application-local.yml` yourself, right next to `application.yml`, with this shape:

```yaml
app:
  base-url: http://localhost:8080

spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: "your-16-character-app-password"

  datasource:
    url: jdbc:postgresql://localhost:5432/marvex
    username: postgres
    password: postgres

jwt:
  secret: "any-long-random-string-works-locally"
  access-token-expiry-ms: 900000
  refresh-token-expiry-ms: 604800000

webhook:
  secret: "any-string-you-like"

payment:
  provider: mock   # or "yapily" — see section 2

mock:
  provider:
    webhook-url: http://localhost:8080/webhooks/payment

# Only needed if payment.provider is "yapily" — see section 5
yapily:
  basic-token: "<base64 of applicationId:secret, from section 5 step 4>"
  user-uuid: "<any UUID you like — used to tag test users in Yapily's system>"
  callback: ${app.base-url}/pay
```

Now, here's what each setting actually *does* and whether you can get away with a throwaway value — this is the part people usually get stuck on:

**`spring.mail.*` — is this actually required?** Yes, and this one genuinely trips people up. When a merchant registers, the "click here to verify your email" link is sent by **real email** — there is no fallback and no console log printing the link for you. If your SMTP settings are wrong or missing, the app doesn't crash, it just quietly fails to send the email (you'll see a warning in the logs) and you will never receive your verification link, which means you can never finish registering. **You need a real, working SMTP account to get past registration**, even in Mock mode. The easiest option is a personal Gmail account with an [App Password](https://myaccount.google.com/apppasswords) (not your normal Gmail password — Google blocks that for this) — takes two minutes to generate.

**`jwt.secret` — what is it, and is it required?** This is the private key the server uses to sign login sessions (JWTs). It's always required, in every environment, because logging in simply won't work without it. Locally it doesn't need to be a "real" secret — any long random string is fine, since only your own machine ever sees it.

**`webhook.secret` — what is it, and is it required?** This is a shared password used to *prove* that a "payment status update" actually came from the payment provider (Yapily, or the mock in Mock mode) and not from some random request hitting your `/webhooks/payment` endpoint. It's required in both modes. You just need *some* string here — the mock provider and the webhook validator both read this same value, so as long as it's set to something, they'll agree with each other automatically.

**`yapily.*` — required only if `payment.provider: yapily`.** If you're running Mock mode, you can delete this whole block, or just leave it with placeholder values — it's never read.

### Optional: pulling secrets from Infisical instead of typing them into the file

Everything above works with literal values sitting in `application-local.yml`, exactly as shown — that's the default and nothing extra is needed. On top of that, this repo also ships an **optional** integration with [Infisical](https://infisical.com) (`InfisicalEnvironmentPostProcessor`, `src/main/java/com/marvex/config/`) for anyone who'd rather centralize secrets than keep them in a local file: it runs before Spring boots, logs into Infisical, and injects whatever it fetches into the app's config under the same property names (`MAIL_PASSWORD`, `JWT_SECRET`, `DB_URL`, etc.) — so the exact same `${MAIL_PASSWORD}`-style placeholders shown above resolve from Infisical instead of a literal value, with no other code changes.

It's entirely opt-in: if you don't add an `infisical` block, it does nothing, and the app behaves exactly as described in this section. To turn it on:

```yaml
infisical:
  client-id: "<machine identity client id>"
  client-secret: "<machine identity client secret>"
  project-id: "<infisical project id>"
  environment-slug: dev
```

Add that to `application-local.yml`, create an Infisical project + Universal Auth machine identity, and add secrets there with the same keys the app already expects (`MAIL_HOST`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `WEBHOOK_SECRET`, `YAPILY_BASIC_TOKEN`, `YAPILY_USER_UUID`, ...). For `application-dev.yml`/production, the same four `infisical.*` values are read from real `INFISICAL_CLIENT_ID` / `INFISICAL_CLIENT_SECRET` / `INFISICAL_PROJECT_ID` environment variables instead of a file.

**You are not required to use Infisical.** This is just the wiring one contributor set up for their own use — nothing about the app depends on it. Plain literal values in `application-local.yml`, real environment variables, or your own equivalent `EnvironmentPostProcessor` for Vault, AWS Secrets Manager, Doppler, 1Password, or anything else all work identically, since Spring only ever cares that these properties resolve from *somewhere*. Use whatever secrets manager fits your setup.

---

## 7. Running it

```bash
# 1. Start the backend
cd marvex
mvn spring-boot:run
```

App (API) runs at **http://localhost:8080**.

```bash
# 2. In a second terminal, start the frontend with hot reload
cd marvex/frontend
npm install
npm run dev
```

Frontend runs at **http://localhost:5173** and proxies API calls to the backend at :8080 — use this URL while developing, not :8080 directly.

**Why a proxy, instead of just calling `:8080` directly?** Even though both are "localhost," a browser treats `http://localhost:5173` and `http://localhost:8080` as two completely different, unrelated origins — same as if they were different websites — because the port is part of what defines an origin ([MDN: Same-origin policy](https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy)). That matters here for two concrete reasons:

- **Login would silently break.** The refresh-token cookie this app relies on (`AuthCookies.java`) is set with `SameSite=Strict`, which means browsers refuse to send it on a request to a different origin ([MDN: Set-Cookie — SameSite](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie#samesitesamesite-value)). Call `:8080` straight from a page served on `:5173`, and the cookie just never gets sent.
- **The backend doesn't allow cross-origin calls anyway.** There's no CORS configuration in this app, so a browser calling `:8080` from a page on `:5173` would be blocked outright ([MDN: CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS)).

Vite's dev-server proxy (configured in `frontend/vite.config.js`) works around both problems by making them invisible: the React code calls a plain relative path like `fetch('/auth/login')`, which the browser sees as a request back to `:5173` itself — same-origin, cookie included, no CORS involved. Vite then quietly forwards that request to `:8080` behind the scenes before the browser ever gets involved. See [Vite: `server.proxy`](https://vite.dev/config/server-options.html#server-proxy) for how the config works.

This is purely a **development-time convenience** — it only runs under `vite dev`/`npm run dev` and has no effect on the production build. In production there's no proxy and none is needed: `vite build` bundles the frontend straight into `src/main/resources/static` (see `build.outDir` in `vite.config.js`), so Spring Boot serves it directly and the frontend and backend are already the same origin.

### Actually trying it out end to end
1. Go to **http://localhost:5173/register**, enter your (real, working) email
2. Check your inbox, click the verification link
3. Fill in business name, pick a payout bank (in dev mode there's a "Sandbox (Testing)" option — pick that unless you're testing something specific), set a password, and your own sort code / account number (any made-up UK-format numbers are fine — they're never used to actually move money in either mode)
4. Log in, go to the dashboard, click **New Payment**, enter an amount
5. Pick a bank:
   - **Mock mode:** you'll land on a fake bank screen with a Confirm/Cancel button
   - **Yapily sandbox mode:** you will see yapily available sanbox banks, pick up one.
6. Open the generated payment link (as if you were the customer). If you are running locally, you cannot scan the code using your phone as this will redirect you to the localhost which is not running on your phone.
7. Login to the bank you selected using teh mock data you find in yapily website. 
7. Confirm the payment — you'll land on a success page, and if you flip back to the dashboard tab, it updates live with no refresh needed

---

## 8. Running the tests

```bash
# Fast — unit tests only, no Docker needed
mvn test -Dtest="PaymentServiceTest,AuthServiceTest,WebhookServiceTest"

# Everything, including integration tests (spins up a real throwaway Postgres via Testcontainers)
mvn test
```

If Docker isn't installed or running, the integration tests skip themselves automatically instead of failing — you don't need to do anything special.

---

## 9. Merchant flow (once logged in)

1. **Dashboard** — create payments, see history, basic analytics (today/week revenue), export everything to CSV
2. **Generate a payment** — amount + optional note → get a link + QR code to hand to a customer
3. **My Account** — edit business/bank details, change password, or delete the account (this removes the merchant and their payments, per UK GDPR's "right to erasure" — see section 13 for what happens to the audit log specifically)

## Customer payment flow

1. Customer scans the QR or opens the link → lands on `/pay/:id`
2. Picks their bank → redirected to that bank to log in and approve
3. Bank redirects back → Marvex double-checks the result with the provider
4. Customer sees a success or failure screen; the merchant's dashboard updates in real time (WebSocket) and they get an email

---

## 10. API reference

The full list of endpoints, grouped by area, is worth skimming if you're integrating against this backend directly:

### Auth
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/auth/register` | Public | Register — sends verification email |
| POST | `/auth/complete-registration` | Public | Finish registration via the emailed token |
| POST | `/auth/login` | Public | Login — returns a JWT + sets a refresh cookie |
| POST | `/auth/logout` | JWT | Revoke refresh token + clear cookie |
| POST | `/auth/refresh` | Cookie | Rotate refresh token, get a new access token |
| POST | `/auth/forgot-password` | Public | Send password reset email |
| POST | `/auth/reset-password` | Public | Reset password via the emailed token |

### Account
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/account` | JWT | Get current merchant details |
| PUT | `/account` | JWT | Update business/bank details |
| PUT | `/account/password` | JWT | Change password (revokes all sessions) |
| DELETE | `/account` | JWT | Delete the account and its payments (GDPR erasure — audit history is kept but scrubbed, see section 13) |
| GET | `/account/export` | JWT | Download payment history as CSV (GDPR Article 20) |

### Payments
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/payments/analytics` | JWT | Revenue/count analytics |
| POST | `/payments` | JWT | Create a new payment request |
| GET | `/payments?page=0&size=20` | JWT | List merchant's payments (paginated) |
| GET | `/payments/:id` | Public | Get payment details |
| POST | `/payments/:id/regenerate` | JWT | Issue a new link for an expired/pending payment |
| POST | `/payments/:id/initiate` | Token | Kick off the Open Banking flow with the chosen bank |
| POST | `/payments/:id/submit` | Token | Submit the bank's consent token after approval |
| POST | `/payments/:id/confirm` | Token | Poll the provider for the final status |
| POST | `/payments/:id/invoice` | JWT | Email an invoice for a completed payment |

### Institutions
| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/institutions` | Public | List banks currently registered on your Yapily Application (see section 5, step 3) |

### Webhooks
| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/webhooks/payment` | HMAC signature | Provider tells us a payment's status changed |

---

## 11. WebSocket (live dashboard updates)

Connect to `/ws` (SockJS/STOMP), sending `Authorization: Bearer <token>` on CONNECT, then subscribe to `/topic/payments/{merchantId}` to get events like:

```json
{ "event": "payment_paid", "paymentId": "uuid", "amount": 150 }
```

You can only ever subscribe to your own merchant topic — the server checks this.

---

## 12. Security

- Passwords are hashed (BCrypt) — never stored in plain text
- Login sessions use short-lived JWTs (15 min) plus a longer-lived, rotating refresh cookie (7 days) that's HttpOnly and Secure so JavaScript can't read it
- If a refresh token is reused after rotation (a sign someone stole one), every session for that account is killed immediately
- Every token stored in the database is hashed first — the raw, usable value is never sitting in the DB
- 10 failed logins in a row locks the account for 15 minutes
- Payment links are single-use, hashed, and expire after 10 minutes
- Incoming webhooks are checked against an HMAC signature so random requests can't fake a "payment succeeded" event
- Rate limiting on login, registration, and general traffic to slow down abuse — based on the caller's real network address, not a header the caller could just set to a different value on every request
- A payout bank (set at registration or in My Account) has to be one of the real banks Marvex actually supports — the server checks this itself, it doesn't just trust whatever the UI happened to send. Same idea for the bank a customer picks when paying: it's checked against the payment provider's real bank list before any payment is attempted against it
- The Mock payment provider only activates when `payment.provider` is explicitly set to `mock` — it can't accidentally end up running in production

## 13. Audit log

Every sensitive action (logins, payments, account changes, exports) is written to an append-only `audit_log` table. Rows can never be deleted, by anyone, ever — not even when a merchant deletes their account. That's deliberate: this table is the fraud/dispute record (e.g. "who changed the payout bank details, and when"), and deleting it right when a merchant closes their account would be exactly the wrong moment to lose it.

What deleting an account *does* do, to satisfy GDPR's "right to erasure", is scrub the personal details off that merchant's audit rows — the IP address, user agent, and free-text detail are cleared — while leaving the event itself (what happened, when, success or failure) in place. There's no UI for any of this — it's a "for whoever's operating this, query the database directly" kind of log, not a customer-facing feature.

---

## 14. Troubleshooting / "why isn't this working"

- **The bank dropdown is empty when a customer tries to pay.** You're in Yapily sandbox mode and haven't registered an institution yet — go back to section 5, step 3.
- **I never got my verification email.** Your `spring.mail.*` settings are wrong, or you're using your normal Gmail password instead of an App Password. Check the backend logs for a `Failed to send verification email` warning to confirm.
- **`400 Bad Request... Consent token is missing from header`** when checking a payment's status. This means the payment hasn't actually been through the bank-approval step yet — it's not a bug, it just means the status is being checked before the customer finished authorising it.
- **I get logged out / redirected to login unexpectedly.** Your access token expired (15 min) and the silent refresh failed — usually because the refresh cookie is missing (check you're going through the frontend's dev server at :5173, not hitting :8080 directly from a different origin without cookies).
- **Switching between Mock and Yapily mode.** Just change `payment.provider` in `application-local.yml` and restart — nothing else needs to change, but a payment that was started under one mode won't carry over cleanly to the other.

---

## 15. Where to go for more (Yapily side)

The app only uses a slice of what Yapily can do. If you want to understand the underlying Open Banking mechanics rather than just this app's use of it, these pages are worth reading, roughly in order of usefulness:

- [Open Banking 101](https://docs.yapily.com/concepts/open-banking-101) — the actual concept, if section 1 above didn't fully click
- [Set Up Your Environment](https://docs.yapily.com/getting-started/get-started) — the account/application/sandbox setup this README's section 5 is based on
- [Authentication](https://docs.yapily.com/api-reference/authentication) — how the Basic Auth / `basic-token` actually works
- [Consent Lifecycle](https://docs.yapily.com/concepts/consent-lifecycle) — what a "consent token" is and why it's needed on every payment-status check (directly relevant to the `submit`/`confirm` endpoints above)
- [Payment Status](https://docs.yapily.com/payments/payment-resources/payment-status) — the full list of statuses a payment can be in and what they mean
- [Sandbox Overview](https://docs.yapily.com/resources/sandbox/overview) and [Sandbox Credentials](https://docs.yapily.com/resources/sandbox/sandbox-credentials) — every test bank and its login details.
- [Webhooks](https://docs.yapily.com/tools-and-services/webhooks/introduction) — background on the `/webhooks/payment` mechanism this app implements
