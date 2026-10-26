# Business Decisions, Assumptions & Approval Gates
## Commercial Equipment Rental Marketplace (Phase 0)

The business owner reviewed and approved the product defaults below on **2026-10-21**. Approval records product direction; it does not replace tax/legal advice, payment-provider approval, or evidence that an external integration is ready.

**Phase 0 decision status:** Product decisions approved. External legal, tax, payment-provider, operational and launch gates remain open and must block the related production functionality.

| Decision | Approved product direction | Remaining gate |
|---|---|---|
| Initial equipment category | Light-to-medium construction equipment | Validate supply and demand before launch |
| Target geography | India-wide intended initial launch scope (selected 2026-10-22) | Verify supply, service coverage, support, and go/no-go readiness before activating each service area |
| Pricing | Documented draft fee model | Tax/accounting review before applying GST or invoicing rules in production |
| Delivery | Renter pickup or owner-arranged delivery at listing price | Define category/region logistics and liability procedures |
| Security deposits | Plan for deposits, but do not collect until approved | Written legal and payment-provider confirmation of permitted collection, holding, refund and dispute flow |
| Cancellation | Documented tiered schedule retained as a policy draft | Legal review and publication of final terms |
| Payments/settlements | No live payments or deposits until approvals | Razorpay eligibility, settlement design, legal/accounting review, sandbox reconciliation and written provider approval |

Approval: **Business owner (user-confirmed)**, 2026-10-21.

---

### Decision 1: Initial Pilot Equipment Category
* **Context:** Commercial equipment ranges from handheld power tools (₹2,000/day) to heavy excavators (₹50,000/day + operator).
* **Options Considered:**
  1. *Heavy Earthmoving & Cranes:* Very high transaction values, but complex operator staffing, legal permits, high downtime risk, difficult dispute resolution.
  2. *Light-to-Medium Construction Equipment (Demolition hammers, vibrators, compactors, generators, core drills):* High rental frequency, transportable in mini-trucks/pickups, standardized specifications, easier damage inspection.
  3. *General Power Tools:* Low transaction value, high theft/non-return risk, lower profit margins per unit.
* **Approved Product Decision:** **Option 2 (Light-to-Medium Construction Equipment).**
* **Trade-Offs:** Lower ticket size per booking compared to excavators, but 10x higher rental frequency, rapid validation cycles, simplified logistics, and lower balance sheet risk during pilot.
* **Status:** **Approved by business owner, 2026-10-21.** Category supply and demand still require validation.

---

### Decision 2: Target Geography & Rollout
* **Context:** A country-wide target market increases operational, supply, delivery, language, support, verification and dispute-resolution complexity compared with a single-city pilot.
* **Options Considered:**
  1. *Single-city pilot:* Concentrates supply and support, but limits initial reach.
  2. *India-wide target market with controlled regional cohorts:* Preserves the intended national market while allowing region-by-region supply and operational validation.
* **Approved Product Decision:** **India-wide target geography.** On 2026-10-22, the business owner selected India-wide as the intended initial launch scope, overriding the earlier regional-cohort rollout recommendation. This scope decision does not itself authorize production activation.
* **Operational Readiness Gate:** Before India-wide activation, demonstrate verified supply, support coverage, delivery rules, incident handling and go/no-go evidence across every advertised service area. Keep areas unavailable until their operational coverage is verified.
* **Trade-Offs:** Larger addressable market and flexibility, with substantially higher onboarding, logistics, support and compliance costs. The product must not advertise service in regions without verified active supply and operational coverage.
* **Status:** India-wide launch scope selected by the business owner on 2026-10-22. Nationwide capacity and operational coverage have not been evidenced in this repository; the owner reports that written provider/legal/tax/accounting approvals and go/no-go evidence are available, but those records have not been provided here for verification.

---

### Decision 3: Marketplace Pricing & Take-Rate Model
* **Context:** Platform revenue must cover payment gateway charges (approx. 2%), hosting, support, and customer acquisition while remaining competitive against unorganized rental yards.
* **Approved Planning Assumptions (not yet production-authorized):**
  - **Owner Base Daily Rate:** Set by the owner (e.g., ₹1,500/day).
  - **Platform Commission (Take Rate):** **12% deducted from Owner Gross Earnings** (Owner gets 88%).
  - **Platform Convenience Fee:** **3% charged to Renter** on total booking value.
  - **Minimum Rental Duration:** 1 Day (24 hours).
  - **GST:** 18% is a provisional modeling assumption only. Taxability, applicable rate, tax base, invoicing, TCS and input tax credit must be confirmed by a qualified Indian tax adviser before production use.
  - **Current non-production estimate convention (business-owner-confirmed, 2026-10-22):** Apply the 3% renter convenience fee to rental plus delivery; calculate the 12% owner commission on rental only and treat delivery as pass-through; exclude the refundable deposit from both fee bases. The provisional 18% GST estimate uses rental + delivery + renter convenience fee and excludes the deposit. These calculation bases clarify draft software estimates only; GST, invoicing, settlement, deposit handling, and production use remain subject to professional/provider approval.
* **Trade-Offs:** The fee model is intended to balance marketplace revenue and owner appeal, but unit economics have not been validated with pilot data. The owner commission and renter fee are separate charges; do not describe them as a verified combined take rate.
* **Status:** **Product direction approved by business owner, 2026-10-21.** Production rates, fee bases, tax and invoice treatment require finance/tax review.

---

### Decision 4: Delivery, Logistics & Transit Liability
* **Context:** Commercial equipment is heavy and bulky. Breakage or delays in transit cause immediate site stoppage.
* **Options Considered:**
  1. *Pure Renter Self-Pickup:* Renter collects from owner yard. Low liability for platform, but friction for renter.
  2. *Owner-Managed Delivery:* Owner provides delivery for a flat or distance-based fee configured per listing.
  3. *Platform-Integrated Logistics (e.g., Porter/Blowhorn API):* Seamless booking, but requires custom API integration and cargo transit insurance.
* **Approved Product Decision:** **Hybrid:** listings can offer renter self-pickup, owner-arranged delivery, or both; the owner-configured charge and supported service area must be clear before a booking request.
* **Trade-Offs:** Minimizes third-party operational dependencies during initial launch while keeping delivery costs transparent.
* **Status:** **Approved by business owner, 2026-10-21.** Region-specific transport, insurance, handover and liability procedures remain operational launch requirements.

---

### Decision 5: Refundable Security Deposit & Damage Claim Policy
* **Context:** Renters may damage machinery or fail to return it on time. Holding customer deposits in India is governed by strict financial guidelines.
* **Approved Product Direction:** Plan for a refundable security deposit, but do not collect it until a lawful provider flow is approved in writing.
* **Draft Policy for review (not an approved live financial rule):**
  - **Deposit Amount:** Configured per equipment category (e.g., 2x to 3x daily rental rate, or flat ₹3,000 to ₹10,000 depending on replacement value).
  - **Holding Mechanism:** To be determined only after legal and payment-provider approval. Do not assume the platform may hold funds or that Razorpay Route provides escrow or a permitted deposit-holding arrangement.
  - **Inspection Window:** 24 hours after equipment return.
  - **Release Rule:** If no damage or discrepancy is reported by the owner within 24 hours post-return, the deposit is automatically refunded to the renter's original payment method.
  - **Deduction Rule:** Any owner damage claim requires photographic proof, repair quote, and admin approval before deposit deduction.
* **Trade-Offs:** A deposit may reduce owner risk but raise renter cost and conversion friction; collecting or holding it without an approved flow creates material legal and financial risk.
* **Status:** **Planning direction approved by business owner, 2026-10-21.** Deposit collection remains blocked pending legal and provider approval of collection, safeguarding, refund and dispute flows.

---

### Decision 6: Cancellation & Refund Policy
* **Context:** Equipment is reserved exclusively for a renter, preventing the owner from renting to others.
* **Approved Policy Direction:** Retain this documented tiered cancellation schedule as a draft for legal review:
  - **> 48 hours before scheduled handover:** 100% refund of rental amount & security deposit (minus payment gateway processing fee of 2%).
  - **24 to 48 hours before handover:** 50% refund of rental amount; 100% refund of security deposit.
  - **< 24 hours or No-Show:** 0% refund of rental amount; 100% refund of security deposit. Owner receives 80% of their expected payout as a reservation compensation.
  - **Owner Cancellation:** 100% refund to renter + ₹500 platform credit. Owner incurs a penalty rating and account review.
* **Trade-Offs:** Balances renter flexibility against protecting equipment owners from last-minute revenue loss. Exact charges, refund timing, gateway fees and owner compensation must be legally reviewed and implemented explicitly.
* **Status:** **Draft approved by business owner, 2026-10-21; not approved for publication or production enforcement until legal review.**

---

### Decision 7: Payment Gateway & Marketplace Settlement
* **Approved Product Decision:** **Do not enable live payment collection, owner settlement or security-deposit collection until the payment provider and qualified legal/accounting advisers approve the marketplace structure and workflow.**
* **Design Constraint:** Razorpay Route, linked-account transfers, `on_hold`, escrow/nodal arrangements, and any alternate payout route are evaluation candidates only. Do not assume eligibility, availability, custody rights, settlement timing, or that any product is legally an escrow service.
* **Required before activation:** Written confirmation of merchant eligibility and exact supported funds flow; reviewed marketplace, tax and accounting treatment; sandbox tests and reconciliation for captures, refunds, disputes and settlements; production secrets, webhook configuration and operational controls.
* **Trade-Offs:** Blocks live revenue and bookings until the flow is approved, while avoiding reliance on unverified payment or fund-holding capabilities.
* **Status:** **Product safety gate approved by business owner, 2026-10-21.** Razorpay approval and professional legal/accounting validation remain outstanding.
