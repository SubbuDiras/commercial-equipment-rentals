# Phased Implementation Backlog & Acceptance Criteria
## Commercial Equipment Rental Marketplace (Phase 0)

---

## Phase 0: Discovery, Architecture & Design
- **Status:** **Complete for the current product-discovery/design scope.** The business owner confirmed the product defaults on 2026-10-21 and the planning documents now distinguish approved product direction from unapproved external/launch gates. This does not claim legal, tax, provider, security, or launch approval.
- **Deliverables:**
  1. Requirements and Assumptions Document ([business-requirements.md](./business-requirements.md))
  2. Business Decisions, Assumptions & Approval Gates ([unresolved-business-decisions.md](./unresolved-business-decisions.md))
  3. System Architecture & Modular Monolith Design ([system-architecture.md](../architecture/system-architecture.md))
  4. Rental Lifecycle State-Transition Specification ([rental-workflow.md](../architecture/rental-workflow.md))
  5. ERD & Flyway Baseline Database Schema ([erd.md](../database/erd.md))
  6. Technology Stack & Version Matrix ([tech-stack-versions.md](../architecture/tech-stack-versions.md))
  7. REST API Contract & Authorization Matrix ([rest-api-contract.md](../api/rest-api-contract.md))
  8. STRIDE Security Threat Model ([security-threat-model.md](../security/security-threat-model.md))
  9. Phased Implementation Backlog & Acceptance Criteria (this document)
  10. Local Development Environment & Setup Guide ([local-dev-guide.md](../deployment/local-dev-guide.md))
- **Acceptance Criteria:**
  - Product scope, approved assumptions, architecture, data model, target workflows, and threat model are documented consistently.
  - Remaining decisions and external approvals are recorded with a clear owner or phase gate; no legal, tax, or provider approval is implied.
  - Business-owner-approved product defaults are recorded; the India-wide initial launch scope was selected on 2026-10-22, while activation remains subject to Phase 6 operational go/no-go evidence.

---

## Phase 1: Project Foundation & Core Infrastructure
- **Status:** **Complete.** All Phase 1 acceptance checks passed: backend suite 47/47 with no skips, including fresh MySQL 8.4 migration and 10-way overlapping-booking concurrency tests; frontend unit tests and production build; full Docker Compose stack healthy; live migrations applied; and real Keycloak-token authorization verified (401 without a token, 200 with a renter token).
- **Deliverables:**
  - Setup Gradle root backend build with Java 21, Spring Boot 3.3.x, Flyway, and MySQL Connector.
  - Setup Angular 22 frontend repository with standalone components, routing, and reactive forms.
  - Docker Compose environment containing MySQL 8.4, Keycloak 25 (with pre-configured realm), and MinIO (local S3).
  - Spring Security OAuth2 Resource Server configuration validating Keycloak JWTs and mapping roles (`ROLE_RENTER`, `ROLE_OWNER`, `ROLE_ADMIN`).
  - Standardized RFC 7807 global exception handler and structured JSON logging.
  - Baseline health check endpoints (`/actuator/health`, `/api/v1/health`) and initial integration tests using Testcontainers.
- **Acceptance Criteria:**
  - Full local environment starts with `docker compose up -d`; Compose configuration validates and MySQL, Keycloak, MinIO, and Mailpit become healthy.
  - Backend `test` task completes with 47 tests passing and none skipped; Testcontainers runs the MySQL 8.4 migrations against an empty database and verifies exactly one reservation succeeds under 10 simultaneous overlapping requests.
  - Flyway automatically applies all eight migrations to an empty MySQL database and to the local development database.
  - The role-protected test endpoint returns `401 Unauthorized` without a token and `200 OK` with a valid Keycloak renter JWT.
  - Angular unit tests and production build pass.

---

## Phase 2: Owner Onboarding, KYC & Equipment Catalogue
- **Status:** **Complete** for the Phase 2 implementation and automated acceptance scope. Owner onboarding/review and category-management APIs, private KYC upload/access, equipment image upload/display, date-aware public search, and the Angular owner/catalogue flows are implemented and tested. This does not imply production object-storage, legal, tax, or launch approval.
- **Deliverables:**
  - Owner profile registration API and KYC document upload (handling private S3 presigned URLs).
  - Admin approval/rejection endpoints with audit logging for owner verification.
  - Category hierarchy management and seed data for light construction tools.
  - Equipment listing creation, image upload, and draft-to-active workflow.
  - Angular screens: Owner KYC submission form, listing management dashboard, and public equipment search/details page.
- **Acceptance Criteria:**
  - Only authenticated owners with `VERIFIED` status can publish equipment listings.
  - Unapproved or draft listings are not discoverable in public search results.
  - Uploaded KYC documents are stored in private buckets and inaccessible without admin or owner credentials.
  - Only the authenticated owner can create, complete, list, or download that profile's KYC documents; admin document metadata/download access is role-gated and audited.
  - Equipment image uploads are limited to verified listing owners and draft/rejected listings, completed uploads are displayed on approved public listings, and public date-filtered search excludes overlapping active bookings and unexpired holds.
  - Verification at Phase 2 completion: backend suite passed **53 tests with 0 failures and 0 skips**, including MySQL 8.4 migrations V1–V6, owner/listing visibility, availability overlap boundaries, KYC ownership, media completion, and category hierarchy checks. Frontend tests passed **2/2** and the production build succeeded.
  - Local smoke checks confirmed application health, Flyway V6 on the development database, public category retrieval, MinIO browser CORS preflight, and KYC upload authorization (`401` unauthenticated; `404` for an authenticated renter targeting a profile they do not own).

---

## Phase 3: Availability Engine, Pricing & Booking State Machine
- **Status:** **Complete** for implementation and automated acceptance scope. Server-side booking and quote calculations, the approved non-production fee bases, immutable renter and owner pricing snapshots, overlap-safe availability, owner decisions, automatic hold expiry, and Angular quote/history flows are implemented and tested. Production tax/payment approval remains a separate gate.
- **Deliverables:**
  - High-performance availability query with overlap detection.
  - Server-side pricing engine computing rental days, delivery fee, GST (18%), deposit, and immutable snapshotting.
  - Reconcile the implementation with the approved draft: 3% renter convenience fee on total booking value and a separate 12% owner commission. GST/tax bases and deposit treatment remain subject to qualified review; do not apply them in production without approval.
  - Booking request API with pessimistic row locking (`SELECT FOR UPDATE`) on target equipment.
  - Owner accept/reject endpoints and background scheduled task for expiring holds (4-hour owner review, 30-minute payment hold).
  - Angular booking request flow, price quote breakdown modal, and booking history dashboard.
- **Acceptance Criteria:**
  - Automated concurrency test: 10 simultaneous booking requests for the exact same equipment unit and overlapping dates produce exactly 1 successful reservation and 9 gracefully rejected conflicts (`409 Conflict`).
  - Booking price snapshot remains completely unchanged even if the owner modifies listing daily rates later.
  - Expired booking holds automatically release inventory.
  - The confirmed draft model calculates the 3% renter convenience fee from rental plus delivery, and the separate 12% owner commission from rental only; delivery is passed through to the owner. Refundable deposit is excluded from both fee bases.
  - The 18% GST figure is only a provisional estimate on rental + delivery + renter convenience fee, excluding deposit. The quote and line item identify this as provisional; production tax calculation, invoicing, deposits, and live payment remain gated on qualified advice and approvals.
  - Verification: backend suite passes **57 tests with 0 failures and 0 skips**, including all seven Flyway migrations on MySQL 8.4, ten-way booking concurrency, HTTP 409 conflict responses, MySQL-persisted owner commission/convenience fee/earnings snapshots, unchanged booking snapshot after listing rate edits, date-overlap boundaries, and expired-hold release. Frontend tests pass **2/2** and the production build succeeds.

---

## Phase 4: Razorpay Payments Sandbox & Rental Operations
- **Status:** **Complete for implementation and mocked automated acceptance scope.** Payment operations are default-off, sandbox credentials are required outside production, production requires separate explicit approval flags, and a full mocked payment-to-dispute-to-settlement lifecycle is covered. Actual Razorpay sandbox reconciliation and all legal, tax, accounting, and provider approvals remain external gates; do not enable live operations.
- **Deliverables:**
  - Server-side Razorpay order creation tied to booking price snapshot.
  - Fail-closed payment/deposit/refund/settlement gate. Production requires `PAYMENT_OPERATIONS_ENABLED=true`, `PAYMENT_PRODUCTION_APPROVED=true`, and `PAYMENT_SANDBOX_ONLY=false`; all default to disabled/safe values. Non-production operation requires explicit enablement plus an `rzp_test_` key.
  - Payment verification endpoint and raw webhook signature validator (`X-Razorpay-Signature`).
  - Webhook event idempotency engine with `payment_webhook_events` tracking table; duplicate event IDs with changed payloads and second captured payment IDs are rejected.
  - Handover and return inspection APIs with photographic evidence upload, hour-meter logging, and mutual OTP confirmation.
  - 24-hour post-return dispute window, damage claim submission, and automated security deposit refund trigger.
  - Razorpay Route linked-account transfer integration with payout hold/release triggers.
  - Persistent settlement release times and unique refund constraints; unresolved linked accounts can be retried without repeating an already recorded deposit refund. Resolved disputes suppress the automatic full-deposit refund.
  - Approved damage deductions are recorded separately from rental commission and included in the owner payout; the renter receives only the remaining deposit balance.
- **Acceptance Criteria:**
  - Duplicate webhook deliveries do not create duplicate payments, refunds, or settlements.
  - Frontend client redirects or manipulations cannot mark a booking confirmed without server-side verification.
  - Handover and return state transitions strictly enforce checklist and photographic evidence requirements.
  - Mocked full lifecycle test covers payment capture, mutual-OTP inspections, a post-return damage claim, administrator resolution, partial deposit refund, and owner payout release.
  - Verification: the full backend suite passes **62 tests with 0 failures and 0 skips**, including MySQL 8.4 Testcontainers applying Flyway V1–V8, feature-gate tests, duplicate/out-of-order capture handling, repeated refund protection, delayed linked-account payout retry, and the mocked end-to-end dispute lifecycle. No live Razorpay credentials were used; actual sandbox provider reconciliation remains unverified.

---

## Phase 5: Pilot Hardening, Security Audit & Operational Readiness
- **Status:** **In Progress** (security headers/CORS, edge rate limits, Prometheus instrumentation, production Nginx TLS config, CI dependency scans, and backup/restore procedures implemented; the full frontend dependency audit is now clean and unit tests run in CI. A measured restore drill and production/provider verification remain.)
- **Deliverables:**
  - End-to-end integration test suite simulating full lifecycle from renter search to return and payout.
  - Rate limiting, CORS policy, security headers, and Sonar/OWASP dependency vulnerability scan.
  - Automated encrypted backup and restore runbook for MySQL and S3 object storage.
  - Nginx HTTPS configuration, Prometheus metrics export, and production deployment scripts.
  - Pilot onboarding checklist and operational playbooks for field support.
- **Acceptance Criteria:**
  - Zero critical or high vulnerabilities in static analysis and dependency check.
  - Database backup can be restored to a clean instance within 15 minutes with verified data integrity.
  - Full end-to-end user journeys pass in CI/CD pipeline.
  - **Environment-specific blockers:** the latest local OWASP Dependency-Check run has not been confirmed successful and the CI workflow requires the `NVD_API_KEY` secret; the host lacks `mysqldump`, `mysql`, `mc`, and `age`, so no restore drill has been run. Obtain the CI secret and run the drill in a dedicated isolated environment.

---

## Phase 6: Nationwide Launch & Metric Tracking
- **Status:** **In Progress — software implemented; live-launch acceptance remains open.** The admin metrics API/dashboard and go/no-go runbook are implemented. The business owner selected India-wide as the intended initial launch scope on 2026-10-22, overriding the earlier regional-cohort recommendation; this is not evidence of nationwide operating capacity or a production deployment. The owner reports written provider/legal/tax/accounting approvals and go/no-go evidence are available, but those records have not been provided here for verification. No verified owner/listing cohort or live reconciled transactions are evidenced. These operational criteria cannot be marked complete based on repository changes alone.
- **Deliverables:**
  - Controlled deployment across the approved India-wide scope, only after verified supply, support, delivery and incident capacity is demonstrated in each advertised service area. The Bengaluru runbook is a city-specific readiness template, not a nationwide operating plan.
  - Live onboarding of first 10-15 verified equipment owners and ~50 equipment units; this requires authorized, evidence-backed onboarding.
  - Admin-only operational dashboard refreshing every 30 seconds and tracking booking conversion, owner response time, disputes, captured payments, settlement take rate, supply, and settlement queue.
  - Collect owner and renter feedback, track support/dispute outcomes, and maintain a prioritized improvement backlog. A dedicated in-app feedback collection flow is not currently evidenced.
- **Acceptance Criteria:**
  - Initial 20 real commercial rental transactions executed with zero inventory collisions, accurate GST invoices, and 100% financial reconciliation. **Not yet evidenced:** do not initiate live financial operations until the reported approvals and go/no-go evidence are reviewed by the responsible operator; retain booking, provider, invoice, settlement/refund, and inventory evidence for all 20 transactions.
