# End-to-End Completion Plan

**Status:** Draft for approval · **Date:** 2026-10-23
**Goal:** Every flow the platform supports can be run from the Angular frontend against the Spring Boot backend on a local machine, and is covered by automated browser tests.

**Decisions already made**
- Payments use a **local fake Razorpay** (no account or keys needed).
- In scope: **booking cancellation, reviews, email notifications (Mailpit), automated E2E tests (Playwright)**.
- Keycloak is configured **by hand** using the steps in Phase 0.
- Work proceeds **phase by phase**, with a review after each phase.

**Local environment assumed**
| Service | Where | Notes |
|---|---|---|
| MySQL | Windows MySQL80, `localhost:3306`, DB `marketplace_dev`, `root`/`root` | Not the Compose MySQL |
| Keycloak | Container `keycloak-clean` (26.5.4), `localhost:8080`, realm `marketplace-realm` | Not the Compose Keycloak |
| MinIO | Compose `minio` + `minio-init`, `localhost:9000` (console 9001) | Start only these services |
| Mailpit | Compose `mailpit`, SMTP 1025, UI `localhost:8025` | Needed from Phase 6 |
| Backend | `localhost:8081`, profile `dev` | Needs JDK 21 |
| Frontend | `localhost:4200`, proxies `/api` to 8081 | |

> Never run a bare `docker compose up -d`: the Compose MySQL and Keycloak would clash with MySQL80 (3306) and `keycloak-clean` (8080). Use `docker compose up -d minio minio-init mailpit`.

---

## Target flow (what "complete" means)

```
Renter signs up ─► Renter applies to become owner (KYC + documents)
   ─► Admin approves KYC ─► user automatically gets ROLE_OWNER
   ─► Owner creates listing + photos ─► submits ─► Admin approves listing
   ─► Renter searches, books ─► Owner accepts ─► Renter pays (fake Razorpay) ─► CONFIRMED
        └─ either side may cancel before handover (policy refund)
   ─► Handover with OTP + photos ─► IN_PROGRESS
   ─► Return with OTP + photos ─► RETURNED
   ─► Owner approves condition ─► INSPECTION_PASSED      (or owner disputes ─► Admin resolves)
   ─► Deposit refund + owner settlement ─► COMPLETED
   ─► Both sides review ─► CLOSED (or review window expires)
Email at every step, visible in Mailpit.
```

---

## Phase 0 — Environment setup (you, by hand)

### 0.1 Start object storage and mail
```powershell
cd E:\Rentals\infrastructure
docker compose up -d minio minio-init mailpit
docker compose ps
```
Check http://localhost:9001 (login `minioadmin` / `minioadminpassword`): bucket `marketplace-private` must exist.

### 0.2 Keycloak admin console (http://localhost:8080/admin → realm `marketplace-realm`)

1. **Realm roles** (Realm roles → Create role). Create exactly, case-sensitive: `ROLE_RENTER`, `ROLE_OWNER`, `ROLE_ADMIN`, `ROLE_SUPPORT`.
2. **Default role for new sign-ups.** Realm settings → User registration → Default roles → Assign role → `ROLE_RENTER`.
3. **Self-registration.** Realm settings → Login: turn on *User registration*, *Login with email*, *Forgot password*.
4. **Frontend client** (Clients → Create client):
   - Client ID `marketplace-frontend`, OpenID Connect.
   - Client authentication **OFF** (public). Standard flow **ON**. Direct access grants **ON** (used by smoke tests and Playwright).
   - Valid redirect URIs: `http://localhost:4200/*` and `http://localhost:4200`.
   - Valid post logout redirect URIs: `http://localhost:4200/*`.
   - Web origins: `http://localhost:4200`.
   - Advanced tab → Proof Key for Code Exchange method: `S256`.
5. **Roles inside the ID token (fixes the "pages won't open" problem).** Client scopes → `roles` → Mappers → `realm roles` → turn **Add to ID token ON** → Save.
6. **Backend service account** (Phase 2 needs it to grant `ROLE_OWNER` automatically):
   - Clients → Create client → Client ID `marketplace-backend`, Client authentication **ON**, Service accounts roles **ON**, Standard flow **OFF**.
   - Credentials tab → copy the client secret (goes into an environment variable, never into Git).
   - Service account roles tab → Assign role → Filter by clients → `realm-management` → `manage-users` and `view-users`.
7. **Test users** (Users → Add user; set Email verified ON; Credentials → set password, Temporary OFF):
   | Email | Roles | Purpose |
   |---|---|---|
   | `renter@example.com` | `ROLE_RENTER` | Books equipment |
   | `owner@example.com` | `ROLE_RENTER` (gets `ROLE_OWNER` after KYC approval) | Lists equipment |
   | `admin@example.com` | `ROLE_ADMIN` | Approves KYC, listings, disputes |

   Every user needs an email, first and last name: the backend rejects tokens without an `email` claim when creating bookings.
8. **Realm email (optional, for password-reset mails).** Realm settings → Email: host `host.docker.internal`, port `1025`, from `noreply@marketplace.local`, no auth, no TLS.

### 0.3 Backend environment
Spring does not read `backend/.env`. Set real environment variables in your run configuration (IDE or PowerShell) instead, and fix `.env` so it no longer points at the non-existent `rental` database:

| Variable | Value |
|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/marketplace_dev?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `root` |
| `KEYCLOAK_ADMIN_CLIENT_SECRET` | secret from step 0.2.6 (used from Phase 2) |

### 0.4 Check
- Sign in at http://localhost:4200 as `renter@example.com`. *My Bookings* should open (before step 0.2.5 it bounced back to the catalogue).
- `http://localhost:8081/api/v1/test/renter-only` with the renter's token returns 200.

**Done when:** all three users can sign in, and each sees the menu items for their role.

---

## Phase 1 — Backend bug fixes

| # | Fix | Where |
|---|---|---|
| 1.1 | Add `COMPLETED`, `CLOSED`, `CANCELLED_BY_RENTER`, `CANCELLED_BY_OWNER` to `BookingStatus`, and update `blocksInventory` (all four release the slot). Stops the 500 on `GET /bookings` after settlement. | `booking/BookingStatus.java`, `payment/PaymentService.java:283` |
| 1.2 | Dispute endpoint returns a JSON object `{disputeId, bookingId, status}` instead of a bare string. | `rental/RentalInspectionController.java:63-70` |
| 1.3 | Admin actions that write audit rows create the admin's `users` row on first use (same "find or create from token" helper the booking flow uses), so dispute resolution stops returning 404. | `payment/PaymentService.java:493-499, 542` |
| 1.4 | Add `@PreAuthorize("hasRole('ADMIN')")` to `AdminOwnerReviewController` for defence in depth. | `owner/AdminOwnerReviewController.java` |
| 1.5 | Delete the stray empty `backend/backend/` folder and the stale `backend/target/` output. | — |

**Tests:** unit test that every status written by SQL in the code base exists in `BookingStatus` (guards against 1.1 recurring); controller test that the dispute response is `application/json`.

---

## Phase 2 — Missing backend endpoints

| # | Endpoint | Role | Purpose |
|---|---|---|---|
| 2.1 | `GET /api/v1/owner/bookings?status=&page=&size=` | OWNER | Owner's incoming and active bookings (paged, newest first, with equipment title and renter name) |
| 2.2 | `GET /api/v1/owner/onboarding/me` | RENTER, OWNER | Current user's owner profile and KYC status (or 404 if none), so the onboarding page survives a reload |
| 2.3 | `GET /api/v1/admin/disputes?status=` and `GET /api/v1/admin/disputes/{id}` | ADMIN | Dispute queue and details with inspection photos |
| 2.4 | `POST /api/v1/owner/rentals/{id}/approve-condition` | OWNER | `RETURNED → INSPECTION_PASSED` within 24 h (today only the 24 h scheduler can do this) |
| 2.5 | Owner approval grants `ROLE_OWNER` in Keycloak | — | `KeycloakAdminClient` using the `marketplace-backend` service account; called after `PATCH /admin/owners/{id}/approve` commits. If the call fails, approval still succeeds and the admin sees a warning to assign the role by hand. The user must sign in again to get the new role in their token. |
| 2.6 | `GET /api/v1/bookings/{id}` extended | RENTER, OWNER, ADMIN | Add payment summary, inspection uploads, OTP status, dispute, and allowed next actions (`availableActions: ["PAY","CANCEL",...]`) so the UI doesn't duplicate state-machine rules |
| 2.7 | `GET /api/v1/bookings` returns a page object `{content,page,size,totalElements,totalPages}` like `/equipment` | RENTER | Consistency with the catalogue and the API contract |
| 2.8 | `PATCH /api/v1/admin/owners/{id}/razorpay-account` | ADMIN | Store `razorpay_linked_account_id` so owner settlement transfers run (fake account IDs in dev) |

**API contract update:** fix the contradictions found in the audit (POST vs PATCH for accept/reject, `/admin/owners/pending` vs `?status=`), and document every new endpoint in `docs/api/rest-api-contract.md`.

---

## Phase 3 — Fake Razorpay

**Design:** an in-process fake, not a separate server. The backend already talks to Razorpay only through the `RazorpayGateway` interface, so a `FakeRazorpayGateway` bean replaces `HttpRazorpayGateway` when `payment.razorpay.fake=true`. This means no extra process to start. (A standalone mock server on port 18080 remains possible later if wanted.)

| # | Work |
|---|---|
| 3.1 | `FakeRazorpayGateway` (active only when `payment.razorpay.fake=true` **and** profile is `dev` or `test`; fails startup if enabled in `prod`). Stores orders, payments, transfers and refunds in memory; IDs look like `order_FAKE…`, `pay_FAKE…`. |
| 3.2 | `application-dev.yml`: `payment.razorpay.fake=true`, `payment.operations.enabled=true`, `payment.operations.sandbox-only=true`, key ID `rzp_test_fake`, fixed dev-only key and webhook secrets. The existing fail-closed gate stays in place for prod. |
| 3.3 | Dev-only endpoint `POST /api/v1/dev/payments/{orderId}/simulate` with body `{outcome: SUCCESS|FAILURE}`. Returns `{razorpay_order_id, razorpay_payment_id, razorpay_signature}` signed with the dev key secret, exactly as Razorpay Checkout would, and also posts the matching webhook to the backend so the webhook path is exercised. |
| 3.4 | `POST /bookings/{id}/payment-order` response gains `provider: "FAKE" | "RAZORPAY"` so the frontend knows which checkout to show. |

**Tests:** full payment → confirmed → refund → settlement run against the fake; prod-profile test that the fake cannot be enabled.

---

## Phase 4 — Cancellation

Follows `docs/architecture/rental-workflow.md:63-64` and the **draft** policy in `docs/requirements/unresolved-business-decisions.md` §Decision 6 (legal review still pending; amounts are configurable, not hard-coded).

| # | Endpoint / rule |
|---|---|
| 4.1 | `POST /api/v1/bookings/{id}/cancel` (RENTER) with body `{reason}`. Before payment (`REQUESTED`, `PAYMENT_PENDING`, `PAYMENT_FAILED`): no money moves. After payment (`CONFIRMED`, before handover): rental refund >48 h 100% minus 2% gateway fee; 24–48 h 50%; <24 h 0% (owner gets 80% of expected payout); deposit always 100% refunded. → `CANCELLED_BY_RENTER` |
| 4.2 | `POST /api/v1/owner/bookings/{id}/cancel` (OWNER) with body `{reason}`, only before handover: 100% refund to renter; owner penalty recorded on the owner profile. → `CANCELLED_BY_OWNER` |
| 4.3 | Refund amounts calculated by a `CancellationPolicy` class with properties in `application.yml` (`cancellation.full-refund-hours=48`, etc.). Refunds go through the existing idempotent refund code; the booking row is locked (`SELECT … FOR UPDATE`) as for every other transition. |
| 4.4 | Both endpoints return the refund breakdown so the UI can show it; a dry-run `GET /bookings/{id}/cancellation-quote` shows it **before** the user confirms. |

**Open question for the business owner:** the ₹500 platform credit on owner cancellation needs a credit ledger. Proposal for this plan: record it in the audit log and owner penalty field only; build the ledger later.

---

## Phase 5 — Reviews and CLOSED state

Uses the existing `reviews` table (V1 migration).

| # | Work |
|---|---|
| 5.1 | `POST /api/v1/bookings/{id}/reviews` with body `{rating 1-5, comments}`. Renter reviews owner and equipment; owner reviews renter. Allowed only in `COMPLETED`, once per side (unique key already exists). |
| 5.2 | `GET /api/v1/equipment/{id}/reviews` (public, paged) and average rating + count added to the equipment details response. |
| 5.3 | `COMPLETED → CLOSED` when both sides have reviewed, or by a scheduled job when the review window (`reviews.window-days=14`) expires. |

---

## Phase 6 — Email notifications

| # | Work |
|---|---|
| 6.1 | Add `spring-boot-starter-mail`; dev config points at Mailpit `localhost:1025`. A `notifications.enabled` flag lets tests and environments without SMTP switch it off. |
| 6.2 | `NotificationService` sends after the database transaction commits (`@TransactionalEventListener(AFTER_COMMIT)`), so a failed email never rolls back a booking. Failures are logged, not thrown. |
| 6.3 | Plain HTML templates (Thymeleaf) for: KYC submitted/approved/rejected · listing approved/rejected · booking requested (to owner) · accepted/rejected (to renter) · payment received · cancelled (both) with refund breakdown · handover/return OTP (to the other party) · dispute opened/resolved · completed + review invitation. |
| 6.4 | OTP codes are sent by email in addition to the current API response. The API response keeps the code only in `dev`/`test`, and stops returning it in `prod`. |

---

## Phase 7 — Frontend screens

Shared groundwork first:

| # | Work |
|---|---|
| 7.0a | Silent token refresh (`setupAutomaticSilentRefresh`), and wait for login to finish before route guards run so deep links and page refreshes keep their route. |
| 7.0b | `/unauthorized` page; remove or build the dead `/how-it-works` and footer links. |
| 7.0c | Shared booking status badge, money formatter (₹, from backend amounts only), and confirm dialog. |

Screens:

| # | Route | Role | What it does |
|---|---|---|---|
| 7.1 | `/owner/onboarding` (rework) | RENTER | Loads status from `GET /owner/onboarding/me`; shows "under review / approved / rejected"; after approval, prompts to sign in again to get owner access |
| 7.2 | `/admin/owners` | ADMIN | KYC queue; view and download documents; approve / reject with reason; set fake Razorpay account ID |
| 7.3 | `/owner/bookings` | OWNER | Incoming requests and active rentals; accept / reject with reason; cancel |
| 7.4 | `/bookings/:id` | RENTER, OWNER | Booking details page that drives the whole lifecycle using `availableActions`: Pay, Cancel (with quote), Generate OTP, Upload photos, Confirm handover/return, Approve condition, Open dispute, Review |
| 7.5 | Payment dialog | RENTER | Creates the order; if `provider=FAKE`, shows a "Fake Razorpay" dialog with *Pay successfully* / *Fail payment* buttons calling the simulate endpoint, then verifies; if `RAZORPAY`, loads Razorpay Checkout (kept for later) |
| 7.6 | Handover / return wizard (inside 7.4) | RENTER, OWNER | One side generates the OTP, the other enters it; 4 required photos uploaded straight to MinIO via presigned URLs |
| 7.7 | `/admin/disputes` and `/admin/disputes/:id` | ADMIN | Queue; evidence photos; resolve with damage amount |
| 7.8 | Reviews | all | Review form on completed bookings; reviews and average rating on equipment details |
| 7.9 | Owner listings (rework) | OWNER | Edit existing listing (uses the unused `PUT`), real specifications fields instead of `{}` |
| 7.10 | Booking history (rework) | RENTER | Show equipment title, link each row to `/bookings/:id` |

---

## Phase 8 — Dev tools and automated E2E tests

| # | Work |
|---|---|
| 8.1 | Dev-only "time travel": `POST /api/v1/dev/bookings/{id}/shift-time?hours=-48` moves a booking's start, end and hold times, so handover and return can be tested immediately instead of waiting a day. Bean exists only in `dev`/`test` profiles; a test checks it is absent in `prod`. |
| 8.2 | Dev seed endpoint (or SQL script) creating a verified owner with one approved listing, for quick manual testing. |
| 8.3 | Playwright project in `e2e/` at the repo root, logging in through the real Keycloak screen. |
| 8.4 | Test suites: (1) happy path signup → KYC → listing → booking → payment → handover → return → approve → completed → reviews → closed; (2) owner rejects booking; (3) payment failure then retry; (4) renter cancels at each policy tier; (5) owner cancels; (6) dispute and admin resolution; (7) role checks — renter cannot open owner/admin pages; (8) emails arrive in Mailpit (via the Mailpit API). |
| 8.5 | `docs/deployment/local-dev-guide.md` rewritten for this setup (E: drive paths, MySQL80, `keycloak-clean`, partial Compose start, running Playwright). |

---

## Order, size and review points

| Phase | Depends on | Rough size | Review checkpoint |
|---|---|---|---|
| 0 Setup | — | 1–2 h (you) | All three users sign in and see their pages |
| 1 Bug fixes | 0 | Small | Backend tests pass |
| 2 Missing endpoints | 1 | Medium | Endpoints tried in Swagger UI |
| 3 Fake Razorpay | 1 | Medium | Booking reaches `CONFIRMED` via Swagger |
| 4 Cancellation | 3 | Medium | Refund amounts match the policy table |
| 5 Reviews | 3 | Small | |
| 6 Email | 2 | Medium | Emails visible in Mailpit |
| 7 Frontend | 2–6 (each screen needs its endpoint) | Large | Full flow clicked through by hand |
| 8 E2E tests | 7 | Medium | Playwright suite green |

Phases 4, 5 and 6 are independent of each other and can be done in any order after Phase 3. Frontend screens can start as soon as their endpoints exist; they don't need to wait for all backend phases.

## Prerequisites and risks

- **JDK 21** must be installed (or the IDE's JDK used) so backend tests and `bootRun` can run during each phase.
- **Keycloak 26.5.4 vs 25.0.6:** the project targets 25; 26 should work but is not tested. Playwright tests will run against whatever version is installed.
- **Policy amounts are drafts.** Cancellation percentages and fees come from a draft that still needs legal review; they are kept in configuration so they can change without code changes.
- **Fake payments only.** Nothing in this plan enables real money movement; the prod payment gate stays closed.
- **Tests on H2 vs MySQL.** Most existing backend tests use H2. New lifecycle tests should run against MySQL via Testcontainers (needs Docker) so SQL differences are caught.
