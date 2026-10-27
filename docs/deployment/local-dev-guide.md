# Local Development Guide

How to run the whole marketplace on one Windows machine — Angular frontend, Spring Boot backend, Keycloak, MySQL, MinIO and Mailpit — and how to test it by hand and with the automated suites.

Paths below assume the repository is at `D:\Rentals\commercial-equipment-rentals`; adjust the drive if yours differs (the completion plan was written for `E:\Rentals`).

---

## 1. What runs where

| Service | Where it runs | Address | Notes |
|---|---|---|---|
| MySQL | Windows service **MySQL80** | `localhost:3306`, DB `marketplace_dev` | Flyway creates and migrates the schema on backend start |
| Keycloak | Docker container **`keycloak-clean`** (26.x) | `http://localhost:8080`, realm `marketplace-realm` | Configured by hand (section 3) |
| MinIO | Compose services `minio` + `minio-init` | API `localhost:9000`, console `localhost:9001` | `minio-init` creates the `marketplace-private` bucket |
| Mailpit | Compose service `mailpit` | SMTP `localhost:1025`, inbox `http://localhost:8025` | Catches every email the backend sends |
| Backend | `gradlew bootRun`, profile `dev` | `http://localhost:8081` | Needs JDK 21 |
| Frontend | `npm start` | `http://localhost:4200` | Proxies `/api` to the backend |

Prerequisites: Docker Desktop, JDK 21, Node.js 22 or 24 with npm, Git.

> **Never run a bare `docker compose up -d`.** The Compose file also defines MySQL and Keycloak, which clash with MySQL80 (port 3306) and `keycloak-clean` (port 8080). Start only the services listed here.

**Alternative: everything from Compose.** If you don't use MySQL80/`keycloak-clean`, Compose can provide MySQL and Keycloak as well. Run `$env:MYSQL_HOST_PORT = '3307'; docker compose up -d` so Compose MySQL sits on 3307 next to MySQL80, and use `DB_URL=...localhost:3307...`, `DB_USERNAME=marketplace_user`, `DB_PASSWORD=marketplace_secret`. The Compose Keycloak imports `infrastructure/keycloak/marketplace-realm.json` (admin console login `admin` / `admin_password`). The automated end-to-end suite has been run successfully against this variant.

---

## 2. Start object storage and mail

```powershell
cd D:\Rentals\commercial-equipment-rentals\infrastructure
docker compose up -d minio minio-init mailpit
docker compose ps
```

Check http://localhost:9001 (login `minioadmin` / `minioadminpassword`): the `marketplace-private` bucket must exist. Compose already allows browser uploads from `http://localhost:4200` (CORS).

---

## 3. Keycloak (once)

In http://localhost:8080/admin, realm `marketplace-realm`. The full click-path is in [the completion plan, Phase 0.2](../requirements/end-to-end-completion-plan.md); in short:

1. **Realm roles:** `ROLE_RENTER`, `ROLE_OWNER`, `ROLE_ADMIN`, `ROLE_SUPPORT` (exact, case-sensitive).
2. **Default role for sign-ups:** `ROLE_RENTER`. Turn on *User registration*, *Login with email*, *Forgot password*.
3. **Client `marketplace-frontend`:** public, standard flow on, **direct access grants on** (the automated tests sign in this way), redirect URIs and post-logout redirect URIs `http://localhost:4200/*`, web origin `http://localhost:4200`, PKCE `S256`.
4. **Client `marketplace-backend`:** client authentication on, service accounts on; service-account roles `realm-management` → `manage-users` and `view-users`. Copy its secret into `KEYCLOAK_ADMIN_CLIENT_SECRET` (section 4). With it, approving an owner's KYC grants `ROLE_OWNER` automatically; without it, approval still works and the admin is told to assign the role by hand.
5. **Users** (email verified, password not temporary): `renter@example.com` (`ROLE_RENTER`), `owner@example.com` (`ROLE_RENTER`; gets `ROLE_OWNER` on KYC approval), `admin@example.com` (`ROLE_ADMIN`). Every user needs an email, first and last name.

---

## 4. Backend

### 4.1 Environment variables

The `dev` profile reads its settings from environment variables. Set them in the PowerShell session that starts the backend, or in the IntelliJ Gradle run configuration. Spring does not read `.env` files on its own (an optional `backend/.env` is imported as a properties file, but plain environment variables are simpler).

```powershell
# Database (MySQL80)
$env:DB_URL = 'jdbc:mysql://localhost:3306/marketplace_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = 'root'

# Object storage (Compose MinIO)
$env:S3_ENDPOINT = 'http://localhost:9000'
$env:S3_REGION = 'ap-south-1'
$env:S3_ACCESS_KEY = 'minioadmin'
$env:S3_SECRET_KEY = 'minioadminpassword'
$env:S3_PRIVATE_BUCKET = 'marketplace-private'

# Payments: the in-process fake Razorpay (dev/test only; the backend refuses to start with it under any other profile)
$env:PAYMENT_OPERATIONS_ENABLED = 'true'
$env:PAYMENT_PRODUCTION_APPROVED = 'false'
$env:PAYMENT_SANDBOX_ONLY = 'true'
$env:RAZORPAY_FAKE = 'true'
$env:RAZORPAY_KEY_ID = 'rzp_test_fake'
$env:RAZORPAY_KEY_SECRET = 'dev-only-fake-key-secret'
$env:RAZORPAY_WEBHOOK_SECRET = 'dev-only-fake-webhook-secret'
$env:RAZORPAY_API_BASE_URL = 'http://localhost:18080/v1'

# Inspection one-time codes
$env:OTP_SECRET = 'local-dev-otp-secret'

# Browser origin of the frontend
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:4200'

# Email to Mailpit
$env:MAIL_HOST = 'localhost'
$env:MAIL_PORT = '1025'
$env:NOTIFICATIONS_ENABLED = 'true'

# Keycloak service account that grants ROLE_OWNER on KYC approval (section 3, step 4)
$env:KEYCLOAK_BASE_URL = 'http://localhost:8080'
$env:KEYCLOAK_REALM = 'marketplace-realm'
$env:KEYCLOAK_ADMIN_CLIENT_ID = 'marketplace-backend'
$env:KEYCLOAK_ADMIN_CLIENT_SECRET = '<secret from the marketplace-backend Credentials tab>'
```

These values are for local development only. Never commit real secrets or reuse them anywhere else.

### 4.2 Run

```powershell
cd D:\Rentals\commercial-equipment-rentals\backend
.\gradlew.bat bootRun
```

Check http://localhost:8081/api/v1/health. The log shows `Using the in-process FAKE Razorpay gateway` when payments are faked. Swagger UI: http://localhost:8081/swagger-ui.html.

In IntelliJ: open `backend` as a Gradle project, set the Gradle JVM to Java 21, and run the `bootRun` Gradle task with the variables above in its run configuration.

---

## 5. Frontend

```powershell
cd D:\Rentals\commercial-equipment-rentals\frontend
npm install
npm start
```

Open http://localhost:4200 and sign in. Deep links and page refreshes keep their page; after signing in you return to the page you asked for.

---

## 6. Manual testing shortcuts

These exist only under the `dev` and `test` profiles (they are absent in `prod`). Call them with any signed-in user's token, e.g. from PowerShell:

```powershell
$token = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/realms/marketplace-realm/protocol/openid-connect/token' `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body @{ grant_type='password'; client_id='marketplace-frontend'; username='owner@example.com'; password='Password123!'; scope='openid profile email' }).access_token
$auth = @{ Authorization = "Bearer $token" }
```

| Shortcut | What it does |
|---|---|
| `POST /api/v1/dev/seed/owner-listing` | Makes the **caller** a verified owner (with a fake payout account) and creates one approved listing. Each call adds another listing. Also grants `ROLE_OWNER` in Keycloak if the service account is configured; sign in again afterwards. |
| `POST /api/v1/dev/bookings/{id}/shift-time?hours=-73` | Time travel: moves the booking's start, end and hold times (and its inspections, history and settlement release) by the given hours. Use it to make a handover or return available now instead of waiting. |
| `POST /api/v1/dev/scheduler/run` | Runs every background job once: expire holds, advance rentals, release settlements, close review windows. |
| `POST /api/v1/dev/payments/{orderId}/simulate` | The fake Razorpay checkout (normally called by the payment dialog's **Pay successfully** / **Fail payment** buttons). |

```powershell
Invoke-RestMethod -Method Post -Headers $auth -Uri 'http://localhost:8081/api/v1/dev/seed/owner-listing'
Invoke-RestMethod -Method Post -Headers $auth -Uri 'http://localhost:8081/api/v1/dev/bookings/<bookingId>/shift-time?hours=-73'
Invoke-RestMethod -Method Post -Headers $auth -Uri 'http://localhost:8081/api/v1/dev/scheduler/run'
```

A typical manual run of a full rental: seed a listing as the owner; book it as the renter for a start 3 days ahead; accept as the owner; pay as the renter (*Pay successfully*); `shift-time?hours=-73`; do the handover on the booking page (each side generates a code, which is emailed to the other side and also shown on screen in dev; upload 4 photos; confirm); `shift-time?hours=-49`; do the return the same way; **Approve condition** as the owner; `scheduler/run`, `shift-time?hours=-1`, `scheduler/run` to release the payout and complete the booking; both sides review.

All emails appear at http://localhost:8025.

---

## 7. Automated tests

### Backend

```powershell
cd D:\Rentals\commercial-equipment-rentals\backend
.\gradlew.bat test
```

Most tests use an in-memory H2 database. Two classes start a real MySQL 8.4 with Testcontainers and the Flyway schema: `FlywayMigrationTests` (migrations, booking concurrency, dev seed and time shift) and `MySqlLifecycleTests` (full rental to closed reviews, review-window expiry, cancellation tiers with owner compensation, dispute resolution). Docker must be running, otherwise those are skipped (a green build with skipped MySQL tests is not database acceptance). The test profile has its own configuration and needs none of the environment variables above.

### Frontend unit tests

```powershell
cd D:\Rentals\commercial-equipment-rentals\frontend
npm test
npm run build
```

### End-to-end (Playwright)

The `e2e` project drives a real browser through the running stack, signing in on the real Keycloak screen. Start everything first (sections 2–5, with `RAZORPAY_FAKE=true` and `NOTIFICATIONS_ENABLED=true`), then:

```powershell
cd D:\Rentals\commercial-equipment-rentals\e2e
npm install
npm run install-browsers          # once: downloads Chromium
$env:E2E_KEYCLOAK_ADMIN = 'admin'                     # Keycloak master-realm administrator
$env:E2E_KEYCLOAK_ADMIN_PASSWORD = '<its password>'
npm test                          # or: npm run test:headed / npm run test:ui
npm run report                    # HTML report with traces and videos of failures
```

The global setup creates (or resets) its own accounts — `e2e-renter`, `e2e-owner`, `e2e-admin`, `e2e-outsider` `@example.com`, password `Password123!` — so your manual accounts are untouched. The happy-path test also registers a new user per run through the Keycloak sign-up page. Other overrides: `E2E_APP_URL`, `E2E_API_URL`, `E2E_KEYCLOAK_URL`, `E2E_MAILPIT_URL`, `E2E_USER_PASSWORD`.

| Suite | Covers |
|---|---|
| `01-happy-path` | Sign-up → KYC + document → admin approval and payout account → listing → admin approval → booking → accept → fake payment → handover → return → approve condition → settlement → completed → both reviews → closed |
| `02-owner-rejects` | Owner declines a request with a reason |
| `03-payment-retry` | Failed payment, then a successful retry |
| `04-renter-cancels` | Renter cancels before payment and at each refund tier (>48 h, 24–48 h, <24 h); the dialog shows the server's quote |
| `05-owner-cancels` | Owner cancels a paid booking: full refund, platform credit, penalty notice |
| `06-dispute` | Owner raises a damage claim; admin reviews the photos and resolves it |
| `07-role-checks` | Renters can't open owner/admin pages, owners can't open admin pages, another renter can't open your booking, signed-out deep links return after sign-in |
| `08-emails` | Booking, acceptance, payment and handover-code emails arrive in Mailpit (via its API) |

The suite writes test data (users, listings, bookings) into whichever database the backend uses; point the backend at a disposable database if that matters.

---

## 8. Troubleshooting

| Symptom | Fix |
|---|---|
| Backend fails with `Could not resolve placeholder 'X'` | An environment variable from section 4.1 is missing in the session that started the backend. |
| `Access denied for user ...` | `DB_URL` points at a different MySQL than you think: MySQL80 is on 3306, Compose MySQL (if started) on its `MYSQL_HOST_PORT`. Match the credentials to that server. |
| Signed in, but owner/admin pages say "You don't have access" | The token predates a role change. Sign out and in again. If roles never appear, check the realm roles are assigned and that their names start with `ROLE_`. |
| KYC approval shows "could not be assigned automatically" | `KEYCLOAK_ADMIN_CLIENT_SECRET` is missing or wrong, or the service account lacks `manage-users`/`view-users`. Assign `ROLE_OWNER` by hand meanwhile. |
| Photo or document upload fails in the browser | MinIO isn't running, the bucket is missing (`docker compose up -d minio-init`), or the frontend isn't on `http://localhost:4200` (CORS). |
| Inspection photos disappear before confirming | Uploaded photos must be attached within 10 minutes; upload them again. |
| No emails in Mailpit | `NOTIFICATIONS_ENABLED=true`, `MAIL_HOST=localhost`, `MAIL_PORT=1025`, and `docker compose up -d mailpit`. |
| Payment dialog says the backend isn't using the fake gateway | Set `RAZORPAY_FAKE=true` (dev profile only). |
| The Angular dev server logs `ECONNREFUSED` for `/api` | Start the backend and check http://localhost:8081/api/v1/health. |

Production configuration, TLS, backups and launch checks are in [the pilot hardening runbook](./pilot-hardening-runbook.md). None of the local credentials or HTTP endpoints may be used outside a local machine.
