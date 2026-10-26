# Regional Pilot Launch and Metrics (Bengaluru Candidate)

This is a city-specific readiness template, not a decision to launch in Bengaluru or a nationwide operating plan. The business owner selected India-wide as the intended initial launch scope on 2026-10-22. Before advertising or activating any service area, demonstrate its supply, support, delivery, legal, security, and provider readiness; this Bengaluru checklist alone does not establish nationwide coverage.

## Launch state

This repository contains implementation and launch procedures, not a running production deployment. Do not announce the pilot as live until Phase 5 deployment, provider, restore, and security gates have been completed by an authorized operator. Payment operations are protected by a fail-closed feature gate and default to disabled; keep production payment credentials unset and financial flows disabled until written provider/legal/accounting approvals and a recorded go/no-go are in place.

The production frontend and API now use same-origin `/auth` and `/api/v1` paths behind the production Nginx configuration; local development retains its localhost Keycloak endpoint.

## Go/no-go gates

Complete the following in order. Record the operator, timestamp, evidence, and any exception for each gate.

1. **Platform readiness:** complete the Phase 5 restore drill and prove the target recovery time on representative data; pass MySQL 8.4 migration/concurrency tests; close critical/high security findings; validate TLS, OIDC issuer, exact CORS origins, request limits, metrics network access, monitoring alerts, and backup alerting. Verify the payment/deposit feature gate fails closed by default.
2. **Financial readiness:** obtain written provider approval and qualified legal/accounting review of the exact marketplace funds flow, fees, GST/invoice and TCS treatment. Reconcile sandbox orders, captures, refunds, settlements and any deposit only if the provider-approved sandbox flow supports them. Production credentials and live mode require a separate recorded go/no-go approval after independent reconciliation.
3. **Support readiness:** assign named business-hours and after-hours owners for onboarding, booking failures, delivery coordination, disputes, payment reconciliation, security incidents, and backup recovery. Test support escalation and payment reversal procedures.
4. **Supply readiness:** for the selected region, manually verify and approve the initial owner cohort. Confirm contactability, identity/business KYC, equipment ownership or right to rent, serial/condition records, delivery coverage, rates, and customer-support agreement for every listing. Collect payout details or configure deposits only after the approved provider/legal flow permits it. Activate listings only after owner consent and inventory checks.
5. **Controlled launch:** start with invited renters and a low daily booking cap. Monitor the operations dashboard and payment ledger throughout every booking. Increase the cap only after a documented review of completed, reconciled rentals, support incidents, and inventory collisions.
6. **Twenty-transaction acceptance:** after live payments are separately approved, reconcile the first 20 completed commercial rentals from booking reference through provider statement, reviewed tax/fee calculations, any authorized refund/deposit movement, and owner settlement. Until then, do not require or initiate live financial transactions; inventory and operational acceptance alone must not be reported as payment reconciliation.

Never fabricate or backfill pilot counts to satisfy launch targets. Record cancellations and failed payments separately; only reconciled, completed rentals count toward the 20-transaction target.

## Operations dashboard

Administrators can open `/admin` in the frontend. The dashboard refreshes every 30 seconds and supports date filters of up to 366 days. Its API (`GET /api/v1/admin/pilot-metrics?from=YYYY-MM-DD&to=YYYY-MM-DD`) is restricted to `ROLE_ADMIN`.

Metric definitions:

- **Booking conversion:** bookings created in the date window with a captured payment divided by bookings created in that window.
- **Owner response time:** mean elapsed minutes from booking creation to the first `PAYMENT_PENDING` or `OWNER_REJECTED` status history event. Requests without an owner response are not included.
- **Disputes:** disputes created during the date window; open disputes is the current global count.
- **Captured payments:** captured payment amounts for bookings created during the date window (includes refundable deposits; it is not marketplace revenue).
- **Settlement take rate:** recorded platform commission divided by recorded settlement rental gross for settlements created during the date window. This is a settlement ledger ratio, not net profit.
- **Supply:** current counts of verified owners and active equipment listings.
- **Settlement queue:** current pending settlement count and settlements released during the selected date window.

Dashboard numbers are operational indicators, not the source of truth for accounting. Finance must reconcile against booking snapshots, settlement/refund records, and gateway statements before certifying transaction acceptance.

## Pilot review cadence

At opening and close of each pilot day, review booking conversion, pending owner responses, open disputes, pending settlements, payment/webhook failures, and inventory conflicts. Reconcile captures/refunds/transfers daily. Review weekly owner activation and equipment availability with operations. Restrict dashboard and raw financial data to authorized administrators.
