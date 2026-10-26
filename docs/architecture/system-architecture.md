# System Architecture & Modular Monolith Design
## Commercial Equipment Rental Marketplace (Phase 0)

This document describes the target architecture. The repository implements only part of it; component descriptions and invariants are design requirements unless explicitly identified as implemented. In particular, the payment integration exists in code but must not be used with production credentials.

---

## 1. High-Level Architecture Overview

The target system is a **Modular Monolith** deployed on a Linux VPS behind Nginx. It uses Keycloak for OIDC identity and access management, MySQL 8 for relational persistence, S3-compatible object storage (MinIO in local development; a separately approved service in production) for media and documents, and a payment-provider adapter. A Razorpay integration exists in code, but the provider and funds flow are unapproved. Payment, deposit, refund, and settlement operations are protected by a fail-closed gate and default to disabled. Do not configure production payment credentials or enable these flows until written provider/legal/accounting approvals and the required production approval settings are in place.

The intended initial launch scope is India-wide by business-owner decision on 2026-10-22, overriding the earlier regional-cohort rollout recommendation. This decision does not establish nationwide operating capacity or authorize production activation. Keep each service area unavailable until its supply, delivery/service coverage, support capacity, legal/security readiness, and explicit go/no-go evidence are verified.

```mermaid
graph TB
    subgraph Client Tier
        Browser[Angular 22 SPA]
        MobileWeb[Mobile Browser Responsive SPA]
    end

    subgraph Edge & Security Tier
        Nginx[Nginx Reverse Proxy & TLS Termination]
        Keycloak[Keycloak IAM - Auth Code Flow with PKCE]
    end

    subgraph Application Tier [Spring Boot 3.3.x Modular Monolith]
        subgraph Common Core
            Security[OAuth2 Resource Server & JWT Filter]
            Audit[Audit & Activity Logger]
            Exceptions[Central RFC 7807 Exception Handler]
        end

        subgraph Business Modules
            IdentityMod[Identity & User Profile Module]
            OwnerMod[Owner Onboarding & Verification Module]
            EquipmentMod[Equipment Catalogue & Moderation Module]
            SearchMod[Search & Geo-Availability Module]
            PricingMod[Pricing Engine & GST Calculator]
            BookingMod[Booking State Machine & Hold Engine]
            PaymentMod[Payment Provider Adapter & Financial Records]
            RentalOpsMod[Rental Handover & Inspection Module]
            DisputeMod[Dispute & Resolution Module]
            ReviewMod[Review & Rating Module]
            NotificationMod[Notification Dispatcher Module]
        end
    end

    subgraph Data & Storage Tier
        MySQL[(MySQL 8.0+ InnoDB UTF8MB4)]
        S3Storage[(Private Object Storage - S3 / R2 / MinIO)]
    end

    subgraph External Services
        RazorpayGateway[Razorpay Candidate - Approval Required]
        SES[Email Service - AWS SES / Sendgrid]
        WhatsApp[WhatsApp / SMS Gateway - Future]
    end

    Browser -->|HTTPS / WSS| Nginx
    MobileWeb -->|HTTPS| Nginx
    Nginx -->|Reverse Proxy /api/v1| Application Tier
    Nginx -->|Auth /auth| Keycloak
    Browser -->|OIDC Auth Code + PKCE| Keycloak

    Security -->|Validate JWT Token| Keycloak
    Business Modules -->|Flyway Managed JPA/JDBC| MySQL
    EquipmentMod -->|Presigned Upload/Download| S3Storage
    RentalOpsMod -->|Inspection Photos| S3Storage
    PaymentMod -->|Order Creation & Webhooks| RazorpayGateway
    NotificationMod -->|Transactional Emails| SES
```

---

## 2. Modular Monolith Boundary Rules & Invariants

To prevent the common anti-pattern of an unmaintainable "Big Ball of Mud", the backend follows strict **Domain-Driven Design (DDD)** and package boundary rules:

1. **Independent Bounded Contexts:** Each module (`booking`, `equipment`, `payment`, etc.) owns its domain entities, repositories, and internal business logic.
2. **No Cross-Module Entity Sharing:** Modules never expose JPA entities across module boundaries. Communication between modules occurs strictly via:
   - **Public Module Services / Facades:** (e.g., `BookingService` calls `PricingEngine.calculateQuote(...)` passing DTOs).
   - **Internal Application Domain Events:** (e.g., `PaymentConfirmedEvent` published via Spring ApplicationEventPublisher, consumed asynchronously by `BookingStatusListener` and `NotificationListener`).
3. **Strict Acyclic Dependencies:** Dependency flow is strictly unidirectional:
   - `rental_ops` -> depends on -> `booking`
   - `booking` -> depends on -> `equipment`, `pricing`, `user`
   - `payment` -> depends on -> `booking`
   - `equipment` -> depends on -> `owner`, `category`
   - No circular dependencies are permitted.
4. **Zero Client Trust:** Pricing, status changes, user roles, discounts, and payment verifications are computed and validated exclusively on the backend.
5. **Database Consistency:** Database transactions are contained within single module boundaries where possible. Cross-module orchestration uses compensating actions and idempotency tokens.

---

## 3. Module Breakdown & Responsibilities

| Module | Core Responsibility | Key Invariants & Rules |
|---|---|---|
| `identity` & `user` | Maps Keycloak UUID subject (`sub`) to internal user entities; manages profile data, company GSTIN, and address book. | Never stores passwords or tokens. Every incoming request must resolve to an internal `UserRecord`. |
| `owner` | Handles owner onboarding, legal identity documents (PAN, Aadhaar/Passport, GSTIN, Bank details). | Documents stored in private object storage. Owner cannot publish active listings until status is `VERIFIED`. |
| `equipment` & `category` | Equipment catalogue, specifications, serial numbers, replacement values, base daily/weekly rates, maintenance status. | Listings require admin approval before becoming `ACTIVE`. Cannot edit critical specs when booked. |
| `search` & `availability` | High-performance search by category, geo-location bounding box, date ranges, and real-time inventory overlap checks. | Availability checks query committed bookings and active reservation holds; prevents phantom inventory. |
| `pricing` | Calculate rental charges, delivery, draft fees and a provisional tax estimate; snapshot booking and owner earnings. | Implemented draft estimate applies the 3% renter convenience fee to rental + delivery and a separate 12% owner commission to rental only, treating delivery as pass-through and excluding refundable deposits from both fee bases. The 18% GST estimate is on rental + delivery + convenience fee, excluding deposit. GST, invoices, deposit handling, settlement and all production financial use remain gated on qualified review and written approvals. |
| `booking` | Manages rental reservations, hold timeouts (15-min reservation hold, 4-hour owner review), state transitions, and cancellation rules. | Enforces strict concurrency locks (`SELECT ... FOR UPDATE` or serializable isolation) to guarantee zero double-bookings. |
| `payment` | Provider-neutral financial records and adapter boundary; a Razorpay integration exists in code, but its funds flow is not approved. | Webhooks must be idempotent; amounts are server-authoritative; client redirects cannot confirm payment. Financial operations default to disabled and require explicit production approvals; do not use production credentials or enable payment flows until approvals are documented. |
| `rental` (Ops) | Digital chain-of-custody: pre-handover checklist, pickup OTP/signatures, damage photos, return inspection, hour meter readings. | Photos timestamped and geo-tagged. Handover requires both parties' digital acknowledgment. |
| `dispute` | Captures damage claims, missing accessories, or delayed returns with repair quotes, photo evidence, and admin arbitration. | Freezes security deposit release until dispute reaches `RESOLVED` or `REJECTED`. |
| `review` | Two-sided reviews and ratings (Renter rates Owner & Equipment; Owner rates Renter) post-rental. | Reviews are only permissible after booking state is `COMPLETED`. |
| `notification` | Dispatches transactional emails (SES), SMS, and in-app notifications on all critical lifecycle events. | Non-blocking (async); failures are retried with exponential backoff without aborting business transactions. |
| `admin` | Administrative back-office: owner verification queues, listing approvals, booking monitors, manual payouts, and audit trails. | Restricted to `ROLE_ADMIN` with dedicated audit logging for every mutating action. |
