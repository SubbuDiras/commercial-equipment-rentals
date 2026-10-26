# Commercial Equipment Rental Marketplace
### Asset-Light B2B Equipment Rental Platform for India

---

## 📌 Project Overview
An online B2B marketplace connecting verified equipment owners with construction and industrial businesses needing commercial equipment rentals.

- **Target Market:** India-wide, with controlled region-by-region rollout; first launch cohort not yet selected
- **Architecture:** Modular Monolith (Domain-Driven Design)
- **Backend:** Java 21, Spring Boot 3.3.x, Gradle, Spring Security (OAuth2 Resource Server), Spring Data JPA, Flyway
- **Frontend:** Angular 22 (Standalone components, Reactive Forms, Signals)
- **Database:** MySQL 8.4 LTS (InnoDB, strict `DECIMAL` monetary types, UTC microsecond timestamps)
- **Authentication:** Keycloak 25 (OpenID Connect Authorization Code Flow + PKCE)
- **Payments & Settlements:** Razorpay is an evaluation candidate; no live financial flows before provider and professional legal/accounting approval. The production profile currently lacks a fail-closed feature gate, so do not configure production credentials.
- **Object Storage:** Private S3-compatible storage (MinIO locally; production provider not yet selected) with short-lived presigned URLs

---

## 📂 Phase 0 Architecture & Engineering Documentation

All foundational blueprints, state machines, schemas, and API contracts are documented in `/docs`:

1. **[Business Requirements & Assumptions](./docs/requirements/business-requirements.md)**
   - Core personas, approved India-wide target scope, light-construction category, and explicitly provisional tax assumptions.
2. **[Business Decisions, Assumptions & Approval Gates](./docs/requirements/unresolved-business-decisions.md)**
   - Business-owner-approved product defaults, staged rollout constraints, and remaining legal, tax, provider, and operations gates.
3. **[System Architecture & Modular Monolith Design](./docs/architecture/system-architecture.md)**
   - High-level architecture diagram, DDD bounded contexts, module boundary rules, and acyclic dependency graphs.
4. **[Rental Lifecycle State-Transition Specification](./docs/architecture/rental-workflow.md)**
   - Target booking lifecycle, transition preconditions, postconditions, and concurrency rejection rules; payment and deposit transitions remain gated.
5. **[Database Design, ERD & Flyway Schema](./docs/database/erd.md)**
   - ER diagram, Flyway baseline schema, and data integrity notes.
6. **[Technology Stack & Version Matrix](./docs/architecture/tech-stack-versions.md)**
   - Repository implementation versions and dependencies; support lifecycle must be reviewed before production.
7. **[REST API Contract & Authorization Matrix](./docs/api/rest-api-contract.md)**
   - API behavior and authorization notes for catalogue, owner, booking, payment, rental, and admin endpoints.
8. **[Security Architecture & STRIDE Threat Model](./docs/security/security-threat-model.md)**
   - STRIDE design analysis and controls to implement and verify; not a security certification.
9. **[Phased Implementation Backlog & Acceptance Criteria](./docs/requirements/phased-implementation-backlog.md)**
   - Phase 0 through Phase 6 milestones with strict acceptance criteria and concurrency test specifications.
10. **[Local Development Environment & Setup Guide](./docs/deployment/local-dev-guide.md)**
    - Local Docker Compose setup and backend/frontend validation commands, with environment limitations noted.

---

## 🚀 Repository Structure

### Phase 0 decision status

The business owner approved the product defaults on 2026-10-21. The target market is India-wide, but launch must be staged and the first operating cohort is not selected. Legal/tax advice, Razorpay/provider approval, final published policies, and operational launch readiness remain gates for later phases; they are not represented as completed approvals.

```text
Rentals/
  backend/            # Spring Boot 3.3.x Gradle modular monolith
  frontend/           # Angular 22 SPA (standalone components)
  infrastructure/     # Docker Compose, Keycloak realm config, Nginx proxy configs
  docs/               # Phase 0 product and engineering baseline; open gates are recorded
    requirements/
    architecture/
    database/
    api/
    security/
    deployment/
    operations/
    decisions/
```

Backend commands (Java 21):

```powershell
cd backend
.\gradlew.bat test
.\gradlew.bat bootRun
```

Start the local services with `docker compose up -d` from `infrastructure` before running the backend with its default `dev` profile.
