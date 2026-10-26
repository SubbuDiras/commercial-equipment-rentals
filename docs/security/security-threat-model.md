# Security Architecture & STRIDE Threat Model
## Commercial Equipment Rental Marketplace (Phase 0)

This is a **design threat model**, not evidence that every listed control is implemented or independently verified. The repository has partial controls; implementation and security verification status varies by feature. The marketplace may handle high-value commercial assets, financial transactions, and confidential B2B documents (PAN, GSTIN, bank details), so production use requires a completed security review.

---

## 1. Authentication & Security Architecture

1. **Identity & OpenID Connect:**
   - Centralized authentication via **Keycloak**.
   - Angular SPA uses **OAuth 2.0 Authorization Code Flow with Proof Key for Code Exchange (PKCE)**.
   - Client is registered in Keycloak as a **Public Client** (no client secret stored in Angular JavaScript bundles).
   - Backend acts as an **OAuth2 Resource Server** validating RS256/ES256 asymmetric signatures against Keycloak's JWKS endpoint (`/protocol/openid-connect/certs`).
2. **Access Control & Least Privilege (target controls):**
   - Stateless JWT tokens passed in HTTP `Authorization: Bearer` headers.
   - Validate issuer (`iss`), audience (`aud`), expiration (`exp`), and account status; verify each validation is enforced by the deployed resource server configuration.
   - Resource-level ownership checks prevent Insecure Direct Object References (IDOR).
3. **Data Protection at Rest & in Transit (deployment requirements):**
   - Strict TLS 1.3 encryption on Nginx reverse proxy with HSTS (`Strict-Transport-Security`).
   - Database credentials and gateway secrets injected via environment variables; never checked into Git.
   - KYC documents and equipment images are stored in a **private S3 bucket**. KYC downloads are presigned for 15 minutes only after owner/admin authorization; an active, verified public listing intentionally exposes its equipment photos through short-lived signed display URLs.

---

## 2. STRIDE Threat Model & Mitigations

| Threat Category | Specific Attack Scenario | Intended Mitigation (implementation and verification required) |
|---|---|---|
| **Spoofing (Identity)** | Attacker attempts to forge user tokens or impersonate another user to access private booking data. | Backend validates JWT cryptographic signatures against Keycloak JWKS public keys. Tokens with invalid issuer, signature, or expired timestamps are rejected with `401 Unauthorized`. |
| **Spoofing (Webhook)** | Attacker sends counterfeit `POST /api/v1/webhooks/razorpay` requests to mark unpaid bookings as paid. | Backend computes HMAC-SHA256 signature across the **exact raw request bytes** using the server-side `RAZORPAY_WEBHOOK_SECRET` and matches it against `X-Razorpay-Signature`. Webhook payload is rejected if signature fails. |
| **Tampering (Data)** | Attacker modifies the rental price, deposit, or commission in the HTTP request payload before submitting payment. | Zero trust of client-submitted amounts. Total payable amount, platform fees, and security deposits are calculated exclusively server-side and snapshotted into `bookings` and `booking_price_items`. The payment order created with Razorpay uses the server-persisted amount. |
| **Tampering (State)** | Malicious renter attempts to transition booking from `REQUESTED` directly to `COMPLETED` or bypass payment. | State machine enforces valid state transitions in code. Any illegal transition triggers `409 Conflict`. Database row locks prevent racing updates. |
| **Repudiation** | Renter denies receiving damaged equipment or owner falsely claims equipment was returned broken. | Target chain-of-custody controls include timestamped inspection evidence, hour-meter readings, and mutual participant verification. Confirm evidence integrity, retention, audit coverage, and any location-data collection before launch; immutability is not asserted. |
| **Information Disclosure** | Competitor or unverified user scrapes private owner KYC documents (Aadhaar, PAN, Bank statements). | KYC documents are stored with randomized UUID object keys in private S3 buckets. Document retrieval endpoints require `ROLE_ADMIN` or owner self-access. Presigned download URLs expire after 15 minutes. |
| **Denial of Service (DoS)** | Attacker locks up all available equipment by spamming booking requests without paying (Inventory Hoarding). | Intended controls include endpoint rate limits, bounded owner-response/payment holds, and reliable hold expiry. Rate limiting, timer behavior, and abuse resistance must be verified before launch. |
| **Elevation of Privilege** | Standard renter account calls administrative endpoints (e.g., `/api/v1/admin/owners/{id}/approve` or owner payout transfers). | Method-level authorization with Spring Security (`@PreAuthorize("hasRole('ADMIN')")`). Administrative paths are isolated under `/api/v1/admin/**` and blocked at Nginx edge for unauthorized origins. |

---

## 3. Financial & Concurrency Integrity Safeguards

1. **Webhook Idempotency (required where payments are enabled):**
   Every incoming Razorpay webhook event is stored in `payment_webhook_events` by unique `event_id`. If an event ID already exists with status `PROCESSED`, the handler immediately returns `200 OK` without re-processing.
2. **Double-Booking Prevention (target invariant):**
   Availability queries and booking creation run in a single transaction with pessimistic locking on the target equipment record (`SELECT ... FOR UPDATE`), evaluating date overlap intervals `(start_time < :new_end AND end_time > :new_start)`.
3. **Audit Trails (required control):**
   Every administrative action, dispute resolution, refund trigger, and status override writes an immutable record to the `audit_logs` table recording `actor_user_id`, `action`, `entity_name`, `entity_id`, IP address, and payload delta.
