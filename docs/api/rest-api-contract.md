# REST API Contract & Authorization Specification (`/api/v1`)
## Commercial Equipment Rental Marketplace (Phase 0)

All APIs follow the **REST architectural style**, exchange data formatted as JSON (`application/json`), and adhere to **RFC 7807 Problem Details** for error responses.

> **Status and financial safety gate:** This document describes a mixture of implemented endpoints and target behavior; it is not an OpenAPI-generated or production-approved contract. Payment, deposit, refund, and settlement operations are disabled by default. Non-production operation requires explicit enablement and an `rzp_test_` key. Production requires separate enablement, an explicit production-approval flag, and live-mode selection; keep these disabled until provider, legal, tax, and accounting approvals are recorded. Razorpay Route is not assumed to provide escrow or an approved custody mechanism.

---

## 1. Authentication & Role-Based Authorization Model

- **Authentication Scheme:** Bearer Token via HTTP `Authorization: Bearer <JWT>` issued by Keycloak.
- **Roles Extracted from JWT Claims:**
  - `ROLE_RENTER`
  - `ROLE_OWNER`
  - `ROLE_ADMIN`
  - `ROLE_SUPPORT`
- **Ownership Verification:** Every mutating or sensitive retrieval endpoint performs dual checks:
  1. *Role Check* (e.g. `@PreAuthorize("hasRole('OWNER')")`)
  2. *Resource Ownership Check* (e.g., matching the authenticated user's `ownerProfileId` or `userId` against the target entity's owner foreign key).

---

## 2. API Endpoints Specification

### 2.1 Public & Equipment Discovery Endpoints

#### `GET /api/v1/categories`
- **Access:** Public (No authentication required)
- **Description:** Retrieve active hierarchical equipment categories.
- **Response `200 OK`:**
```json
[
  {
    "id": "c1f7a29e-1033-4bc4-9d58-b64bc2504931",
    "slug": "demolition-hammers",
    "name": "Demolition Hammers & Breakers",
    "description": "Electric and pneumatic hammers from 5kg to 16kg",
    "subcategories": []
  }
]
```

#### `GET /api/v1/equipment`
- **Access:** Public
- **Query Parameters:**
  - `categoryId` (UUID, optional)
  - `city` (string, optional, e.g. "Bengaluru")
  - `minRate` / `maxRate` (BigDecimal, optional)
  - `startTime` / `endTime` (ISO-8601 instants, optional pair); when both are present, results exclude listings with an overlapping blocking booking or unexpired hold.
  - `page` (int, default: 0), `size` (int, default: 20)
- **Response `200 OK`:** `{ "content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`. Results include only `ACTIVE` listings belonging to `VERIFIED` owners. Date filters must be supplied together and use half-open interval overlap semantics.

#### `GET /api/v1/equipment/{equipmentId}/images`
- **Access:** Public, for active listings belonging to verified owners.
- **Description:** Returns short-lived display URLs for uploaded listing images. The underlying object store remains private; only listing imagery receives public, temporary read capabilities.

#### `GET /api/v1/equipment/{equipmentId}`
- **Access:** Public
- **Description:** Retrieve an active listing's details, specifications, rates, city-level location, and delivery options. Draft, pending, rejected, and suspended listings return `404`. Public responses deliberately omit serial number, street address, postal code, and precise coordinates; these remain available only to the owner and authorized admin.

#### `GET /api/v1/equipment/{equipmentId}/availability?startTime={ISO-8601}&endTime={ISO-8601}`
- **Access:** Public
- **Description:** Check an active, verified-owner listing for reservations that overlap the requested half-open time interval (`existing.start < requested.end` and `existing.end > requested.start`).
- **Response `200 OK`:** `{ "equipmentId": "...", "startTime": "...", "endTime": "...", "available": true }`. This is an informational check; final reservation is rechecked under the equipment row lock when creating a booking.

---

### 2.2 Owner Management & Catalogue Endpoints

#### `POST /api/v1/owner/onboarding`
- **Access:** Authenticated (`ROLE_RENTER` or unverified user)
- **Description:** Submit individual or business KYC details to become an equipment owner.
- **Request Body:**
```json
{
  "businessType": "PROPRIETORSHIP",
  "companyName": "Bangalore Tool Rentals",
  "gstin": "29ABCDE1234F1Z5",
  "pan": "ABCDE1234F",
  "bankAccountNumber": "987654321098",
  "bankIfsc": "HDFC0001234",
  "bankAccountHolderName": "Bangalore Tool Rentals",
  "firstName": "Suresh",
  "lastName": "Patel",
  "phone": "+919876543210"
}
```
- **Response `201 Created`:** Created `OwnerProfileResponse` with status `UNDER_REVIEW`.

The account's `sub` and `email` are taken from the verified Keycloak JWT. Contact name and phone are required in the request because they are not guaranteed to be present in every token. Repeated onboarding for an account with an owner profile returns `409 Conflict`.

#### `POST /api/v1/owner/onboarding/{ownerProfileId}/documents`
- **Access:** Authenticated profile owner (`ROLE_RENTER` or `ROLE_OWNER`).
- **Description:** Creates a short-lived presigned PUT for a PDF, JPEG, or PNG KYC document (maximum 10 MB); the object is written to the private bucket and staged in `UPLOAD_PENDING`.

#### `POST /api/v1/owner/onboarding/{ownerProfileId}/documents/{documentId}/complete`
- **Access:** Authenticated profile owner.
- **Description:** Verifies the object exists and is within the size limit, then marks it `PENDING`. Repeated or expired completion returns `409 Conflict`.

#### `GET /api/v1/owner/onboarding/{ownerProfileId}/documents` and `GET /api/v1/owner/onboarding/{ownerProfileId}/documents/{documentId}/download`
- **Access:** Authenticated profile owner.
- **Description:** Lists the owner's document metadata or returns a 15-minute attachment download URL. Every download is audited.

#### `GET /api/v1/admin/owners/{ownerProfileId}/documents` and `GET /api/v1/admin/owners/{ownerProfileId}/documents/{documentId}/download`
- **Access:** `ROLE_ADMIN`.
- **Description:** Lists KYC metadata or creates an audited 15-minute attachment download URL for review. Raw object keys and unauthenticated permanent URLs are not returned.

#### `GET /api/v1/admin/owners?status=UNDER_REVIEW`
- **Access:** `ROLE_ADMIN`
- **Description:** List owner profiles for review (defaults to `UNDER_REVIEW`).

#### `PATCH /api/v1/admin/owners/{ownerProfileId}/approve`
- **Access:** `ROLE_ADMIN`
- **Description:** Approve an owner profile currently under review and record the decision in the audit log.

#### `PATCH /api/v1/admin/owners/{ownerProfileId}/reject`
- **Access:** `ROLE_ADMIN`
- **Description:** Reject an owner profile currently under review. A non-empty rejection reason is required and audited.

#### `POST /api/v1/owner/equipment`
- **Access:** Authenticated Owner (`ROLE_OWNER`, verified status required)
- **Description:** Create a new equipment listing in `DRAFT` status.
- **Request Body:**
```json
{
  "categoryId": "c1f7a29e-1033-4bc4-9d58-b64bc2504931",
  "title": "Bosch GSH 11 E Professional Demolition Hammer 1500W",
  "description": "Heavy-duty breaker tool with SDS-Max chuck and chisel set.",
  "manufacturer": "Bosch",
  "modelNumber": "GSH 11 E",
  "serialNumber": "BSH-2023-8821",
  "conditionGrade": "EXCELLENT",
  "baseDailyRate": 1200.00,
  "baseWeeklyRate": 7000.00,
  "securityDeposit": 5000.00,
  "minimumRentalDays": 1,
  "deliverySupported": true,
  "pickupSupported": true,
  "deliveryChargeFlat": 350.00,
  "addressLine": "Shop 14, Ring Road, Whitefield",
  "city": "Bengaluru",
  "postalCode": "560066",
  "latitude": 12.9698,
  "longitude": 77.7499
}
```
- **Response `201 Created`:** Listing DTO created in `DRAFT` status. A valid active category is required.

#### `POST /api/v1/owner/equipment/{equipmentId}/images` and `POST /api/v1/owner/equipment/{equipmentId}/images/{imageId}/complete`
- **Access:** Authenticated verified listing owner (`ROLE_OWNER`).
- **Description:** Presigns a JPEG, PNG, or WebP upload (maximum 10 MB) for a draft or rejected listing; completion verifies the object before it is visible.

#### `POST /api/v1/admin/categories` and `PUT /api/v1/admin/categories/{categoryId}`
- **Access:** `ROLE_ADMIN`.
- **Description:** Create/update active equipment category hierarchy with slug uniqueness and parent-cycle validation. Category changes are audited.

#### `GET /api/v1/owner/equipment`
- **Access:** Authenticated verified owner (`ROLE_OWNER`)
- **Description:** List the authenticated owner's listings, including non-public statuses.

#### `PUT /api/v1/owner/equipment/{equipmentId}`
- **Access:** Authenticated verified owner who owns the listing (`ROLE_OWNER`)
- **Description:** Update a listing only while it is `DRAFT` or `REJECTED`. Another owner's listing is not disclosed.

#### `POST /api/v1/owner/equipment/{equipmentId}/submit`
- **Access:** Authenticated verified owner who owns the listing (`ROLE_OWNER`)
- **Description:** Submit a draft or rejected listing for admin review. The listing becomes `PENDING_APPROVAL`.

#### `GET /api/v1/admin/listings/pending`
- **Access:** `ROLE_ADMIN`
- **Description:** List equipment awaiting moderation.

#### `PATCH /api/v1/admin/listings/{equipmentId}/approve`
- **Access:** `ROLE_ADMIN`
- **Description:** Approve a pending listing, making it publicly discoverable.

#### `PATCH /api/v1/admin/listings/{equipmentId}/reject?reason={reason}`
- **Access:** `ROLE_ADMIN`
- **Description:** Reject a pending listing and store the reason in the audit log.

#### `POST /api/v1/owner/bookings/{bookingId}/accept`
- **Access:** Authenticated Owner (Owner of the booked equipment)
- **Description:** Owner accepts renter's booking request; transitions booking to `PAYMENT_PENDING` and locks slot for 30 mins for payment.
- **Response `200 OK`:** Updated booking status.

#### `POST /api/v1/owner/bookings/{bookingId}/reject`
- **Access:** Authenticated Owner
- **Description:** Owner rejects booking request with recorded reason; frees inventory.

---

### 2.3 Booking & Reservation Endpoints

#### `POST /api/v1/bookings`
- **Access:** Authenticated (`ROLE_RENTER`)
- **Description:** Check real-time availability, compute immutable price breakdown, acquire hold lock, and create a booking in `REQUESTED` status.
- **Request Body:**
```json
{
  "equipmentId": "8b9e59d2-5c02-4fc9-b68e-e2dc38b9712a",
  "startTime": "2026-11-01T09:00:00Z",
  "endTime": "2026-11-04T18:00:00Z",
  "deliveryOption": "OWNER_DELIVERY",
  "deliveryAddress": {
    "siteContactName": "Ramesh Kumar",
    "siteContactPhone": "+919876543210",
    "addressLine": "Plot 42, Prestige Tech Park, Marathahalli",
    "city": "Bengaluru",
    "postalCode": "560103"
  }
}
```
- **Response `201 Created`:**
```json
{
  "bookingId": "3b2f9864-1e52-4422-9011-aa9b48b6cd80",
  "bookingReference": "BKG-202611-0492",
  "status": "REQUESTED",
  "holdExpiresAt": "2026-10-15T20:00:00Z",
  "pricing": {
    "currency": "INR",
    "rentalDays": 4,
    "baseRentalAmount": 4800.00,
    "deliveryCharge": 350.00,
    "platformFeeAmount": 154.50,
    "taxAmount": 954.81,
    "securityDepositAmount": 5000.00,
    "totalPayableAmount": 11259.31
  }
}
```

This response is illustrative, not an approved live quote. For the current draft model, the 3% renter convenience fee is applied to rental plus delivery; the separate 12% owner commission is deducted from rental earnings (delivery is treated as pass-through). The commission and estimated owner earnings are snapshotted internally and are not included in the renter-facing quote. The 18% tax estimate is calculated over rental, delivery, and convenience fee, excluding the refundable deposit; the deposit is excluded from both fee bases. GST/tax treatment remains provisional and requires qualified review. Do not publish or apply these numbers in production until tax/accounting review and required payment approvals are complete.

For the illustrative four-day quote, the owner commission is ₹576.00 and estimated owner earnings are ₹4,574.00 (₹4,800 rental less commission plus ₹350 delivery). Those values are stored with the immutable booking price snapshot.

#### `GET /api/v1/bookings?page=0&size=20`
- **Access:** Authenticated (`ROLE_RENTER`)
- **Description:** Return the authenticated renter's paginated booking history.

#### `GET /api/v1/bookings/{bookingId}`
- **Access:** Authenticated (Renter or Owner of the booking or Admin)
- **Description:** Retrieve live status, inspection reports, price breakdown, and payment details.

#### `PATCH /api/v1/owner/bookings/{bookingId}/accept`
- **Access:** Verified owner of the equipment (`ROLE_OWNER`)
- **Description:** Move a `REQUESTED` booking to `PAYMENT_PENDING` and reset its inventory hold to 30 minutes.

#### `PATCH /api/v1/owner/bookings/{bookingId}/reject`
- **Access:** Verified owner of the equipment (`ROLE_OWNER`)
- **Description:** Reject a `REQUESTED` booking, store the required reason in status history, and release its inventory hold.

---

### 2.4 Payment & Webhook Endpoints

#### `POST /api/v1/bookings/{bookingId}/payment-order`
- **Access:** Authenticated Renter
- **Description:** Creates Razorpay payment order for accepted booking.
- **Response `200 OK`:**
```json
{
  "razorpayOrderId": "order_EKfUsjf8bf781X",
  "amountInPaise": 1124692,
  "currency": "INR",
  "razorpayKeyId": "rzp_test_SecuredKey"
}
```
The implementation reads amount and currency from the stored booking price snapshot. Repeated requests return the existing order for that booking. When financial operations are disabled, payment and related financial endpoints return `503 Service Unavailable`.

#### `POST /api/v1/payments/{paymentId}/verify`
- **Access:** Authenticated Renter
- **Description:** Submits Razorpay client payload (`razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature`) for server-side HMAC-SHA256 signature verification.
- **Response `200 OK`:** Confirmed payment and transition of booking to `CONFIRMED`.
The API validates the payment signature, then fetches the payment from Razorpay and verifies captured status, order id, INR currency, and exact amount before confirming.

#### `POST /api/v1/webhooks/razorpay`
- **Access:** Public (Protected by `X-Razorpay-Signature` over exact raw request bytes and `X-Razorpay-Event-Id` idempotency)
- **Description:** Asynchronous webhook listener processing events (`payment.captured`, `payment.failed`, `transfer.processed`).
- **Response `200 OK`:** Empty acknowledgment.
Supported event IDs are persisted before processing; an identical processed event is acknowledged without applying a second payment or settlement. Reuse of an event ID with a different payload and a second captured payment ID for one order are rejected.

Owner settlement uses the booking's stored commission snapshot. The platform does not create a Route transfer until the return/dispute workflow reaches `INSPECTION_PASSED`; the transfer is then held and released by the scheduled settlement task. This is not escrow and is not evidence of provider approval or legally permitted funds custody. Keep production operations disabled until the required approvals are recorded.

---

### 2.5 Rental Operations & Inspections

#### `POST /api/v1/rentals/{bookingId}/handover`
- **Access:** Authenticated Owner or Renter
- **Description:** Submit handover inspection checklist, hour meter reading, four or more uploaded private photos, and both participant OTPs. Transitions status to `IN_PROGRESS`.

#### `POST /api/v1/rentals/{bookingId}/return`
- **Access:** Authenticated Owner or Renter
- **Description:** Submit return inspection checklist, final hour meter reading, four or more uploaded private photos, and both participant OTPs. Starts the 24-hour dispute window.

#### `POST /api/v1/rentals/{bookingId}/otp?type=HANDOVER|RETURN`
- **Access:** Authenticated booking owner or renter
- **Description:** Issue a 15-minute one-time participant challenge. Each party obtains only their own code; both codes are required to submit the inspection. The authenticated response is the current local delivery mechanism; an SMS provider is still needed before production.

#### `POST /api/v1/rentals/{bookingId}/inspection-uploads?type=HANDOVER|RETURN`
- **Access:** Authenticated booking owner or renter
- **Request:** `{ "contentType": "image/jpeg", "contentLength": 250000 }` (JPEG, PNG, or WebP; size 1 byte–10 MB)
- **Description:** Create a short-lived (10-minute) presigned PUT to the private `marketplace-private` bucket. Keys are scoped to the booking and inspection type; the uploaded object must be present and no larger than 10 MB before inspection submission.

#### `POST /api/v1/rentals/{bookingId}/disputes`
- **Access:** Equipment owner
- **Description:** File a damage claim with category, claim amount, and description within 24 hours of return. Return inspection photos are required as evidence.

#### `PATCH /api/v1/admin/disputes/{disputeId}/resolve`
- **Access:** `ROLE_ADMIN`
- **Request:** `{ "approvedDeductionAmount": 250.00, "resolutionNotes": "..." }`
- **Description:** Resolve an open dispute, refund the remaining deposit, audit the decision, and advance the booking toward settlement.

The current code includes post-return deposit-refund and transfer-release behavior. Resolved disputes refund only the undisputed deposit balance; the automatic full-deposit refund is suppressed afterward. These operations are not production-authorized until legal/provider review approves the exact flow. Never configure production Razorpay secrets based solely on this contract.

---

### 2.6 Admin Governance Endpoints

#### `GET /api/v1/admin/owners/pending`
- **Access:** `ROLE_ADMIN`
- **Description:** List owners awaiting verification with submitted KYC documents.

#### `POST /api/v1/admin/owners/{ownerProfileId}/approve`
- **Access:** `ROLE_ADMIN`
- **Description:** Approves owner profile; activates ability to publish listings.

#### `POST /api/v1/admin/listings/{equipmentId}/approve`
- **Access:** `ROLE_ADMIN`
- **Description:** Reviews and activates equipment listing for public discovery.
