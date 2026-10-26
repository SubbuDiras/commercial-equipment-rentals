# Commercial Equipment Rental Marketplace
## Phase 0: Discovery, Business Requirements & Operational Assumptions

**Document Version:** 1.0.1  
**Status:** Phase 0 product-discovery baseline complete for the current scope; the business owner confirmed product defaults on 2026-10-21. Legal, tax, payment-provider, first-cohort and operational launch gates remain open.
**Target Market:** India-wide intended launch scope, selected by the business owner on 2026-10-22; activate only service areas with verified supply and operational coverage  
**Initial Category:** Light-to-medium commercial construction equipment  

---

## 1. Executive Summary & Business Objective

The platform is an **asset-light, two-sided B2B marketplace** connecting verified commercial equipment owners (suppliers, contractors, rental yards) with business renters (civil subcontractors, interior contractors, MEP engineers, infrastructure builders).

The marketplace operates on a zero-inventory model initially:
- Owners list equipment they own or have explicit legal lease-sublet rights to deploy.
- Renters discover and request equipment rentals. Live payment collection remains disabled until the approved provider/legal flow is confirmed.
- The planned marketplace monetization uses an owner commission and renter convenience fee; the approved planning rates are recorded in the business-decision register and are not production-authorized until tax/accounting review.
- Long-term revenue streams include owner subscriptions, premium listing tiers, equipment damage waivers/insurance partnerships, and invoice financing.

---

## 2. Core Business Actors & Roles

| Role | Key Persona | Primary Motivations & Jobs to be Done |
|---|---|---|
| **Renter** | Subcontractor, Site Engineer, Project Manager | Immediate on-demand equipment access without capital expenditure; guaranteed machine condition; transparent pricing; GST billing; reliable delivery/pickup. |
| **Owner** | Equipment Rental Yard, Contractor with idle machinery | Monetize unutilized capital equipment; guaranteed timely payout; protection against machine damage/theft; low customer acquisition cost. |
| **Admin** | Marketplace Operations Lead | Approve owner KYC; verify machine documentation; resolve disputes; govern payouts and financial reconciliation; oversee platform trust & safety. |
| **Support / Field Operations** | Customer Support Specialist / Field Dispatcher | Track delivery/handover statuses; mediate minor machine breakdown/exchange queries; inspect disputed condition reports. |

---

## 3. Scope of the Initial MVP / Pilot

### 3.1 Initial Pilot Segment
- **Target Category:** Light to Medium Construction Equipment (high velocity, manageable logistics, standardized operation):
  - Demolition hammers (5kg to 16kg)
  - Core cutting machines & diamond core drills
  - Concrete needle vibrators (petrol/electric) & screeds
  - Plate compactors / tamping rammers
  - Portable diesel/petrol generators (3kVA to 10kVA)
  - Submersible dewatering pumps (2" to 4")
- **Target Geography:** **India-wide**, approved by the business owner on 2026-10-21.
  - On 2026-10-22, the business owner selected India-wide as the intended initial launch scope, overriding the earlier regional-cohort rollout recommendation. This does not by itself authorize production activation.
  - Before activating or advertising a service area, verify local supply, support coverage, delivery, owner/listing verification, legal requirements, incident response, and operational go/no-go evidence. Nationwide operational capacity is not established by the scope decision.

### 3.2 Core Operating Workflow
1. **Curated Onboarding:** Owners are vetted (GSTIN, PAN, bank account, equipment serial numbers, maintenance history) before listings are activated.
2. **Owner-Approved Booking Model:** Instant booking is disabled in the pilot. When a renter requests equipment, the owner has a **configurable window (default: 4 hours during business hours)** to accept or decline.
3. **Server-Authoritative Pricing:** All calculations (daily rates, minimum duration, delivery charges, platform fees, taxes and any future deposit) are computed strictly server-side. The 18% GST figure is a provisional modeling assumption and must not be treated as tax advice or applied in production until tax review.
4. **Payment and Settlement Gate:** Do not enable live payment collection, deposits, fund holding or owner settlement until the provider's written approval and legal/accounting review confirm the exact marketplace flow. Razorpay Route is an evaluation candidate, not an assumed escrow capability.
5. **Photographic Chain of Custody:** Handover and return inspections require geo-tagged, timestamped photos of the equipment condition, hour meters, and accessory inventory.

---

## 4. Key Assumptions

1. **Owner Operational Readiness:** Equipment owners have access to smartphones with cameras and data connections to confirm bookings, upload photos during handover, and report returns.
2. **Logistics & Transportation:** In Phase 1-3, equipment logistics will operate on **Owner-Arranged Delivery** or **Renter Self-Pickup**, with platform-assisted delivery coordination introduced in Phase 4 via 3rd-party mini-truck integrations (e.g., Porter API).
3. **Tax & Legal Framework:** GST classification/rate, ECO/TCS obligations, invoicing, tax base and input tax-credit treatment are open professional-advice requirements. The 18% value is only a planning assumption.
4. **Security Deposit Handling:** The product plans for a refundable deposit, but collection is disabled until legal and provider approval confirms a permitted collection, safeguarding, dispute and refund mechanism. A deposit must never be recorded as platform revenue.
5. **Idempotent Financial Operations:** All gateway transactions (Razorpay orders, captures, refunds, transfers) and webhook callbacks must execute idempotently using unique reference keys and database locks.
