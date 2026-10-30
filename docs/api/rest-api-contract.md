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
  - `city` (string, optional, e.g. "Hyderabad")
  - `minRate` / `maxRate` (BigDecimal, optional)
  - `startTime` / `endTime` (ISO-8601 instants, optional pair); when both are present, results exclude listings with an overlapping blocking booking or unexpired hold.
  - `page` (int, default: 0), `size` (int, default: 20)
- **Response `200 OK`:** `{ "content": [...], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0 }`. Results include only `ACTIVE` listings belonging to `VERIFIED` owners. Date filters must be supplied together and use half-open interval overlap semantics.
- Each result includes `primaryImageUrl`: a short-lived display URL for the listing's cover photo (the primary image, else the first uploaded one), or `null` when the listing has no photos. This field is `null` everywhere except search results.

#### `GET /api/v1/equipment/{equipmentId}/images`
- **Access:** Public, for active listings belonging to verified owners.
- **Description:** Returns short-lived display URLs for uploaded listing images. The underlying object store remains private; only listing imagery receives public, temporary read capabilities.

#### `GET /api/v1/equipment/{equipmentId}`
- **Access:** Public
- **Description:** Retrieve an active listing's details, specifications, rates, city-level location, and delivery options. Draft, pending, rejected, and suspended listings return `404`. Public responses deliberately omit serial number, street address, postal code, and precise coordinates; these remain available only to the owner and authorized admin.
- Includes `averageRating` (two decimals, `null` when there are no reviews) and `reviewCount`, computed from renters' published reviews only. These two fields are `null` in search results and in owner/admin listing views.

#### `GET /api/v1/equipment/{equipmentId}/reviews?page=0&size=20`
- **Access:** Public
- **Description:** Renters' published reviews of an active listing, newest first. The owner's reviews of renters are not shown here. Reviewers appear as first name and last initial. Unknown or non-public listings return `404`; `size` is 1–100.
- **Response `200 OK`:** `{ "content": [ { "reviewId": "…", "rating": 4, "comments": "Solid breaker.", "reviewerName": "Rajesh K.", "createdAt": "…" } ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }`

#### `GET /api/v1/equipment/{equipmentId}/availability?startTime={ISO-8601}&endTime={ISO-8601}`
- **Access:** Public
- **Description:** Check an active, verified-owner listing for reservations that overlap the requested half-open time interval (`existing.start < requested.end` and `existing.end > requested.start`).
- **Response `200 OK`:** `{ "equipmentId": "...", "startTime": "...", "endTime": "...", "available": true }`. This is an informational check; final reservation is rechecked under the equipment row lock when creating a booking.

#### `GET /api/v1/me` and `PUT /api/v1/me`
- **Access:** Any signed-in user (renter, owner or admin), for their own profile only.
- **`GET` response `200 OK`:** `{ "firstName": "Meera", "lastName": "Rao", "email": "meera@example.com", "phone": "+919876543210", "roles": ["ROLE_RENTER"], "memberSince": "2026-10-29T06:00:00Z", "signInNameUpdated": null, "photoUrl": null }`. `phone` is `null` when not added; `roles` are the application roles in the caller's token.
- **`PUT` body:** `{ "firstName": "Meera", "lastName": "Rao-Iyer", "phone": "+919876543210" }`. Names are required (at most 100 characters); `phone` is optional, a 10-digit Indian mobile number with the country code (`+91` then 6–9 and 9 more digits), and an empty string removes it. Email is the sign-in identity and is changed in Keycloak's account page, not here.
- **`PUT /api/v1/me/photo`** (`multipart/form-data`, part `photo`): sets or replaces the caller's profile photo, for any role. JPEG, PNG or WebP, at most 2 MB; the file's bytes must be one of those image types whatever the declared type (`400` otherwise, `413` when too large). The app crops it to 400×400 in the browser first. Stored privately; the old photo is deleted. Returns the profile with `photoUrl`, a short-lived link.
- **`DELETE /api/v1/me/photo`**: removes the photo; returns the profile with `photoUrl: null`. Both are audited (`USER_PHOTO_UPDATED`, `USER_PHOTO_REMOVED`).
- **`PUT` response `200 OK`:** the updated profile. `signInNameUpdated` is `true` when the new name was also copied to Keycloak (new tokens carry it), `false` when the Keycloak service account is unavailable; the marketplace keeps the change either way. Audited as `USER_PROFILE_UPDATED`.
- **Errors:** `400` for invalid fields; `409` when the phone number belongs to another account.

---

### 2.2 Owner Management & Catalogue Endpoints

#### `POST /api/v1/owner/onboarding`
- **Access:** Authenticated (`ROLE_RENTER` or unverified user)
- **Description:** Submit individual or business KYC details to become an equipment owner.
- **Request Body:**
```json
{
  "businessType": "PROPRIETORSHIP",
  "companyName": "Hyderabad Tool Rentals",
  "gstin": "36ABCDE1234F1Z5",
  "pan": "ABCDE1234F",
  "bankAccountNumber": "9876543210",
  "bankIfsc": "HDFC0001234",
  "bankAccountHolderName": "Hyderabad Tool Rentals",
  "firstName": "Suresh",
  "lastName": "Patel",
  "phone": "+919876543210",
  "aadhaarNumber": "234123412346",
  "bankName": "HDFC Bank",
  "bankAccountType": "CURRENT"
}
```
- **Response `201 Created`:** Created `OwnerProfileResponse` with status `UNDER_REVIEW`.
- **Validation:** `aadhaarNumber` is required: 12 digits, not starting with 0 or 1, passing the Verhoeff checksum (`400` otherwise). Only its last 4 digits are stored; the full number is never persisted or logged. `bankName` is required (max 100); `bankAccountType` is `SAVINGS` or `CURRENT`. `pan` is required and must look like ABCDE1234F (5 letters, 4 digits, 1 letter); `bankAccountNumber` must be exactly 10 digits; `phone` must be a 10-digit Indian mobile number with the country code (`+91` then 6–9 and 9 more digits).

The account's `sub` and `email` are taken from the verified Keycloak JWT. Contact name and phone are required in the request because they are not guaranteed to be present in every token. Repeated onboarding for an account with an owner profile returns `409 Conflict`.

#### `GET /api/v1/owner/onboarding/me`
- **Access:** Authenticated (`ROLE_RENTER` or `ROLE_OWNER`).
- **Description:** Return the caller's own owner profile and KYC status so the onboarding page survives a reload. Returns `404 Not Found` if the caller has not submitted onboarding yet.
- **Response `200 OK`:**
```json
{
  "ownerProfileId": "5d0c7a1e-4f0b-4a51-9a51-1f2c3d4e5f60",
  "status": "UNDER_REVIEW",
  "businessType": "PROPRIETORSHIP",
  "businessName": "Hyderabad Tool Rentals",
  "rejectionReason": null,
  "razorpayAccountLinked": false,
  "submittedAt": "2026-10-20T09:15:00Z",
  "verifiedAt": null,
  "kyc": {
    "phone": "+919876543210",
    "pan": "ABCDE1234F",
    "gstin": "36ABCDE1234F1Z5",
    "aadhaarLast4": "2346",
    "bankName": "HDFC Bank",
    "bankAccountType": "CURRENT",
    "bankAccountNumber": "••••••3210",
    "bankIfsc": "HDFC0001234",
    "bankAccountHolderName": "Hyderabad Tool Rentals"
  }
}
```
`status` is one of `UNDER_REVIEW`, `VERIFIED`, `REJECTED`, `SUSPENDED`. `rejectionReason` is set only when `REJECTED`. `kyc` echoes what the owner submitted, with the bank account number masked to its last 4 digits; `aadhaarLast4`, `bankName` and `bankAccountType` are `null` for owners who applied before these fields existed. The admin owner list (`GET /api/v1/admin/owners`) returns the same `kyc` object with the full account number, for checking against the bank proof and setting up payouts.

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
- **Description:** Approve an owner profile currently under review and record the decision in the audit log. After the approval commits, the backend grants the realm role `ROLE_OWNER` to the user in Keycloak, using the `marketplace-backend` service account (`realm-management` roles `manage-users` and `view-users`; secret in `KEYCLOAK_ADMIN_CLIENT_SECRET`).
- **Response `200 OK`:** the owner review record plus the outcome of the role grant:
```json
{
  "ownerProfileId": "5d0c7a1e-4f0b-4a51-9a51-1f2c3d4e5f60",
  "firstName": "Suresh",
  "lastName": "Patel",
  "email": "owner@example.com",
  "businessName": "Hyderabad Tool Rentals",
  "businessType": "PROPRIETORSHIP",
  "status": "VERIFIED",
  "submittedAt": "2026-10-20T09:15:00Z",
  "ownerRoleGranted": false,
  "warning": "Owner approved, but ROLE_OWNER could not be assigned automatically (...). Assign ROLE_OWNER to owner@example.com in Keycloak by hand; the user must then sign in again."
}
```
If Keycloak is unreachable or the service account is not configured, the approval still succeeds: `ownerRoleGranted` is `false` and `warning` tells the admin to assign the role by hand. On success `ownerRoleGranted` is `true` and `warning` is omitted. Either way the user must sign in again to receive a token containing `ROLE_OWNER`.

#### `PATCH /api/v1/admin/owners/{ownerProfileId}/reject`
- **Access:** `ROLE_ADMIN`
- **Request:** `{ "reason": "Please submit a clearer bank document." }`
- **Description:** Reject an owner profile currently under review. A non-empty rejection reason is required and audited.

#### `PATCH /api/v1/admin/owners/{ownerProfileId}/razorpay-account`
- **Access:** `ROLE_ADMIN`
- **Request:** `{ "linkedAccountId": "acc_XXXXXXXXXXXXXX" }` (must match `acc_` followed by letters/digits; any such value works with the local fake Razorpay)
- **Response `200 OK`:** `{ "ownerProfileId": "...", "linkedAccountId": "acc_XXXXXXXXXXXXXX" }`
- **Description:** Store the owner's Razorpay Route linked account id. Owner settlement transfers are skipped (the deposit refund still runs) until this is set. The change is audited. Unknown profile returns `404`; a malformed id returns `400`.

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
  "addressLine": "Shop 14, Cyber Towers Road, HITEC City",
  "city": "Hyderabad",
  "postalCode": "500081",
  "latitude": 17.4474,
  "longitude": 78.3762
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

Owner booking endpoints (list, accept, reject) are in section 2.3.

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
    "addressLine": "Plot 42, Financial District, Gachibowli",
    "city": "Hyderabad",
    "postalCode": "500032"
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
- **Description:** Return the authenticated renter's booking history, newest first. `size` is 1–100.
- **Response `200 OK`:** the same page object as `/equipment`: `{ "content": [ <booking as in the create response, plus "equipmentTitle"> ], "page": 0, "size": 20, "totalElements": 1, "totalPages": 1 }`. A renter with no bookings gets an empty page.

#### `GET /api/v1/bookings/{bookingId}`
- **Access:** Authenticated renter of the booking, owner of the booked equipment, or `ROLE_ADMIN`. Anyone else gets `403`.
- **Description:** Everything the booking page needs in one call: booking and price snapshot, payment summary, submitted inspections with photo URLs, photos uploaded for the inspection in progress, OTP status, the latest dispute, and the actions the caller may take next.
- **Response `200 OK`:**
```json
{
  "bookingId": "3b2f9864-1e52-4422-9011-aa9b48b6cd80",
  "bookingReference": "BKG-202611-0492",
  "equipmentId": "8b9e59d2-5c02-4fc9-b68e-e2dc38b9712a",
  "equipmentTitle": "Bosch GSH 11 E Professional Demolition Hammer 1500W",
  "startTime": "2026-11-01T09:00:00Z",
  "endTime": "2026-11-04T18:00:00Z",
  "deliveryOption": "PICKUP",
  "status": "CONFIRMED",
  "holdExpiresAt": null,
  "pricing": { "currency": "INR", "rentalDays": 4, "...": "as in the create response" },
  "ownerName": "Suresh Patel",
  "renterName": "Rajesh Kumar",
  "viewerRole": "RENTER",
  "payment": {
    "paymentId": "…", "status": "CAPTURED", "amount": 11259.31, "currency": "INR",
    "razorpayOrderId": "order_EKfUsjf8bf781X", "failureReason": null,
    "refunds": [ { "type": "SECURITY_DEPOSIT", "amount": 5000.00, "status": "PROCESSED", "createdAt": "…" } ]
  },
  "inspections": [
    {
      "inspectionId": "…", "type": "HANDOVER", "inspectedAt": "…", "hourMeterReading": 120.5,
      "fuelOrBatteryLevel": "FULL", "conditionSummary": "…",
      "checklist": { "accessories_present": true },
      "photos": [ { "mediaTag": "FRONT", "url": "<15-minute presigned GET>", "capturedAt": "…" } ]
    }
  ],
  "inspectionUploads": [
    { "inspectionType": "HANDOVER", "storageObjectKey": "rentals/…/handover/….jpg", "uploadedByMe": true, "expiresAt": "…" }
  ],
  "otpStatus": {
    "inspectionType": "HANDOVER",
    "ownerRequested": true, "ownerExpiresAt": "…",
    "renterRequested": false, "renterExpiresAt": null
  },
  "dispute": null,
  "disputeDeadline": null,
  "availableActions": ["GENERATE_HANDOVER_OTP", "UPLOAD_HANDOVER_PHOTOS"]
}
```
- `viewerRole` is `RENTER`, `OWNER` or `ADMIN` (admin only when the caller is not a party to the booking).
- `payment` is `null` until a payment order exists. `dispute` is the most recent dispute or `null`.
- `inspectionUploads` and `otpStatus` describe the inspection in progress (`HANDOVER` while `CONFIRMED`/`HANDOVER_PENDING`, `RETURN` while `IN_PROGRESS`/`RETURN_PENDING`) and are empty/`null` otherwise. `inspectionUploads` lists unexpired photos not yet attached to a submitted inspection; their keys are what the handover/return submission needs. OTP codes are never returned here, only whether each party holds an unused, unexpired code.
- `disputeDeadline` is 24 hours after the return inspection.
- `availableActions` is computed by the server; the UI should show only these:

| Action | Who | When |
|---|---|---|
| `PAY` | renter | `PAYMENT_PENDING` or `PAYMENT_FAILED`, hold not expired, payment operations enabled |
| `ACCEPT`, `REJECT` | owner | `REQUESTED`, hold not expired, owner verified |
| `GENERATE_HANDOVER_OTP`, `UPLOAD_HANDOVER_PHOTOS` | renter, owner | `CONFIRMED`/`HANDOVER_PENDING` and start time reached |
| `SUBMIT_HANDOVER` | renter, owner | as above, both parties hold an OTP and at least 4 photos are uploaded |
| `GENERATE_RETURN_OTP`, `UPLOAD_RETURN_PHOTOS`, `SUBMIT_RETURN` | renter, owner | same rules for `IN_PROGRESS`/`RETURN_PENDING` and end time reached |
| `APPROVE_CONDITION` | owner | `RETURNED`, no open dispute |
| `RAISE_DISPUTE` | owner | `RETURNED`, no open dispute, before `disputeDeadline` |
| `RESOLVE_DISPUTE` | admin | `DISPUTED` with an open dispute, payment operations enabled |
| `CANCEL` | renter | `REQUESTED`, `PAYMENT_PENDING`, `PAYMENT_FAILED`, `CONFIRMED`, `HANDOVER_PENDING` (paid ones only when payment operations are enabled) |
| `CANCEL` | owner | `PAYMENT_PENDING`, `PAYMENT_FAILED`, `CONFIRMED`, `HANDOVER_PENDING` (same condition) |
| `REVIEW` | renter, owner | `COMPLETED`, the caller has not reviewed yet, and the review window is open |

`reviews` lists the reviews written on the booking (both sides, with `reviewerRole`) once it is `COMPLETED` or `CLOSED`; otherwise it is empty.

#### `POST /api/v1/bookings/{bookingId}/reviews`
- **Access:** `ROLE_RENTER` or `ROLE_OWNER`; the caller must be the booking's renter or the equipment owner.
- **Request:** `{ "rating": 5, "comments": "Machine in great shape." }` (`rating` 1–5 required; `comments` optional, up to 2000 characters)
- **Description:** The renter reviews the owner and the equipment; the owner reviews the renter. Allowed only while the booking is `COMPLETED` and within the review window (`reviews.window-days`, default 14 days after completion), once per side (`409` on a second attempt, before completion, after the window, or once `CLOSED`). When both sides have reviewed, the booking moves `COMPLETED → CLOSED`. A scheduled job also closes `COMPLETED` bookings whose review window has lapsed, reviewed or not.
- **Response `201 Created`:**
```json
{
  "reviewId": "…", "bookingId": "…", "reviewerRole": "RENTER", "reviewerName": "Rajesh K.",
  "rating": 5, "comments": "Machine in great shape.", "createdAt": "…",
  "bookingStatus": "COMPLETED"
}
```
`bookingStatus` is `CLOSED` if this review was the second one.

On cancelled bookings, `cancellation` holds `{ reason, cancelledAt, razorpayRefundId, breakdown }` with the breakdown that was applied (see below); otherwise it is `null`.

#### `GET /api/v1/bookings/{bookingId}/cancellation-quote`
- **Access:** `ROLE_RENTER` or `ROLE_OWNER`; the caller must be the booking's renter or the equipment owner. The quote follows the caller's side (renter policy or owner policy).
- **Description:** Dry run: what cancelling now would refund. Nothing is changed. Returns `409` if the booking can no longer be cancelled by the caller.
- **Response `200 OK`:** same shape as the cancel response, with `cancelledAt` and `razorpayRefundId` `null` and `bookingStatus` the current status.

#### `POST /api/v1/bookings/{bookingId}/cancel`
- **Access:** `ROLE_RENTER`, the renter of the booking.
- **Request:** `{ "reason": "Site work postponed." }` (required, up to 2000 characters)
- **Description:** Cancel before handover → `CANCELLED_BY_RENTER`. The booking row is locked for the transition; the slot is released at once.
  - `REQUESTED`, `PAYMENT_PENDING`, `PAYMENT_FAILED`: no money moves (`policyTier` `NO_PAYMENT`).
  - `CONFIRMED`, `HANDOVER_PENDING` (paid), by hours until the rental start:
    - ≥ 48 h, `FULL_REFUND`: rental charges refunded less a 2% gateway fee, plus the full deposit.
    - 24–48 h, `PARTIAL_REFUND`: 50% of rental charges plus the full deposit.
    - < 24 h or after the start time (no-show), `NO_RENTAL_REFUND`: only the deposit. The owner receives 80% of their expected payout (base rental less commission) through the normal settlement transfer.
  - "Rental charges" means everything paid except the deposit (rental, delivery, platform fee, tax). One Razorpay refund of `totalRefund` is issued (refund type `CANCELLATION`, idempotent per booking).
- **Response `200 OK`:**
```json
{
  "bookingId": "…",
  "bookingStatus": "CANCELLED_BY_RENTER",
  "cancelledAt": "2026-10-28T10:15:00Z",
  "razorpayRefundId": "rfnd_FAKEa1B2c3D4e5",
  "breakdown": {
    "cancelledBy": "RENTER",
    "policyTier": "FULL_REFUND",
    "hoursBeforeStart": 72.50,
    "currency": "INR",
    "amountPaid": 11259.31,
    "rentalCharges": 6259.31,
    "rentalRefund": 6134.12,
    "gatewayFee": 125.19,
    "depositRefund": 5000.00,
    "totalRefund": 11134.12,
    "ownerCompensation": 0.00,
    "platformCredit": 0.00,
    "policyNotice": "Draft cancellation policy pending legal review; amounts are configurable."
  }
}
```

#### `POST /api/v1/owner/bookings/{bookingId}/cancel`
- **Access:** `ROLE_OWNER`, the owner of the booked equipment.
- **Request:** `{ "reason": "Machine failed pre-hire check." }`
- **Description:** Cancel before handover → `CANCELLED_BY_OWNER`. Allowed from `PAYMENT_PENDING`, `PAYMENT_FAILED`, `CONFIRMED`, `HANDOVER_PENDING`; a `REQUESTED` booking is declined with `PATCH /owner/bookings/{id}/reject` instead (`409` here). For a paid booking the renter gets a 100% refund (`policyTier` `OWNER_CANCELLED`), the owner's `cancellation_penalty_count` is incremented, and an `OWNER_CANCELLATION_PENALTY` audit entry records the ₹500 `platformCredit` owed to the renter. There is no credit ledger yet, so the credit is recorded only. Unpaid bookings are cancelled without refund or penalty.
- **Response `200 OK`:** same shape as the renter cancel response.

**Cancellation policy settings.** The draft policy (Decision 6, pending legal review) is configured under `cancellation.*` in `application.yml`: `full-refund-hours` (48), `partial-refund-hours` (24), `partial-refund-percent` (50), `gateway-fee-percent` (2), `partial-owner-compensation-percent` (0, not specified by the policy), `late-owner-compensation-percent` (80), `owner-cancellation-platform-credit` (500.00). For a paid cancellation the owner settlement is either closed (`CANCELLED`) or re-targeted to `ownerCompensation` and paid by the scheduled settlement task once a linked account exists.

**Known limitation.** Cancelling a `PAYMENT_PENDING` booking does not void its Razorpay order. If the renter completes checkout afterwards, the capture webhook is rejected (the booking is no longer payable) and the captured payment must be refunded manually.

#### `GET /api/v1/owner/bookings?status=&page=0&size=20`
- **Access:** `ROLE_OWNER`
- **Description:** Bookings for all equipment owned by the caller, newest request first. `status` is optional and may repeat or be comma-separated (e.g. `?status=REQUESTED,PAYMENT_PENDING`); an unknown status returns `400`. `size` is 1–100.
- **Response `200 OK`:**
```json
{
  "content": [
    {
      "bookingId": "…", "bookingReference": "BKG-202611-0492",
      "equipmentId": "…", "equipmentTitle": "Bosch GSH 11 E Professional Demolition Hammer 1500W",
      "renterName": "Rajesh Kumar",
      "startTime": "…", "endTime": "…", "deliveryOption": "PICKUP",
      "status": "REQUESTED", "holdExpiresAt": "…", "requestedAt": "…",
      "pricing": { "...": "as in the create response" }
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

#### `PATCH /api/v1/owner/bookings/{bookingId}/accept`
- **Access:** Verified owner of the equipment (`ROLE_OWNER`)
- **Description:** Move a `REQUESTED` booking to `PAYMENT_PENDING` and reset its inventory hold to 30 minutes.
- **Response `200 OK`:** Updated booking.

#### `PATCH /api/v1/owner/bookings/{bookingId}/reject`
- **Access:** Verified owner of the equipment (`ROLE_OWNER`)
- **Request:** `{ "reason": "Equipment unavailable." }`
- **Description:** Reject a `REQUESTED` booking, store the required reason in status history, and release its inventory hold.

---

### 2.4 Payment & Webhook Endpoints

#### `POST /api/v1/bookings/{bookingId}/payment-order`
- **Access:** Authenticated Renter
- **Description:** Creates Razorpay payment order for accepted booking.
- **Response `200 OK`:**
```json
{
  "paymentId": "9a3c1f0e-…",
  "razorpayOrderId": "order_EKfUsjf8bf781X",
  "amountInPaise": 1124692,
  "currency": "INR",
  "razorpayKeyId": "rzp_test_SecuredKey",
  "provider": "RAZORPAY"
}
```
The implementation reads amount and currency from the stored booking price snapshot. Repeated requests return the existing order for that booking. When financial operations are disabled, payment and related financial endpoints return `503 Service Unavailable`.

`provider` tells the frontend which checkout to show: `RAZORPAY` opens Razorpay Checkout with `razorpayKeyId`; `FAKE` means the in-process fake gateway is active and the UI should offer the simulated checkout below.

#### `POST /api/v1/payments/{paymentId}/verify`
- **Access:** Authenticated Renter
- **Request:** `{ "razorpayOrderId": "…", "razorpayPaymentId": "…", "razorpaySignature": "…" }` — the values Razorpay Checkout (or the fake simulator) returns as `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature`, renamed to camelCase.
- **Description:** Server-side HMAC-SHA256 signature verification of the checkout result.
- **Response `200 OK`:** Confirmed payment and transition of booking to `CONFIRMED`.
The API validates the payment signature, then fetches the payment from Razorpay and verifies captured status, order id, INR currency, and exact amount before confirming.

#### `POST /api/v1/webhooks/razorpay`
- **Access:** Public (Protected by `X-Razorpay-Signature` over exact raw request bytes and `X-Razorpay-Event-Id` idempotency)
- **Description:** Asynchronous webhook listener processing events (`payment.captured`, `payment.failed`, `transfer.processed`).
- **Response `200 OK`:** Empty acknowledgment.
Supported event IDs are persisted before processing; an identical processed event is acknowledged without applying a second payment or settlement. Reuse of an event ID with a different payload and a second captured payment ID for one order are rejected.

#### `POST /api/v1/dev/payments/{orderId}/simulate` (dev/test only)
- **Access:** `ROLE_RENTER`, and only the renter who owns the booking. The endpoint exists only when `payment.razorpay.fake=true`, which the backend refuses to start with outside the `dev`/`test` profiles.
- **Request:** `{ "outcome": "SUCCESS" }` or `{ "outcome": "FAILURE" }`
- **Description:** Plays the part of Razorpay Checkout. Records a captured or failed payment in the fake gateway, then delivers the matching signed `payment.captured` / `payment.failed` webhook through the real webhook handler, so the booking moves to `CONFIRMED` or `PAYMENT_FAILED` before the response returns. A failed payment can be retried with the same order. Orders created before a backend restart are still accepted.
- **Response `200 OK`:**
```json
{
  "razorpay_order_id": "order_FAKEa1B2c3D4e5",
  "razorpay_payment_id": "pay_FAKEf6G7h8I9j0",
  "razorpay_signature": "<HMAC-SHA256 of order_id|payment_id with the dev key secret; null on FAILURE>",
  "outcome": "SUCCESS",
  "error_description": null
}
```
On `SUCCESS` the frontend then calls `POST /payments/{paymentId}/verify` exactly as it would after real Checkout; this is idempotent because the webhook already confirmed the booking.

**Fake gateway behaviour (local development).** With the dev profile defaults (`RAZORPAY_FAKE=true`, `PAYMENT_OPERATIONS_ENABLED=true`, `PAYMENT_SANDBOX_ONLY=true`, key id `rzp_test_fake`, fixed dev-only key and webhook secrets) payment, refund, transfer and release calls are served in memory with ids prefixed `order_FAKE`, `pay_FAKE`, `rfnd_FAKE`, `trf_FAKE`. State is lost on restart; refunds and transfers for payments made before a restart are accepted so existing dev data keeps flowing. Each of these values can be overridden with the environment variable of the same name. The production profile pins `payment.razorpay.fake: false`, and the existing production approval gate is unchanged.

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
- **Description:** Issue a 15-minute one-time participant challenge. The caller's code is **emailed to the other party**, who reads it back in person, so each side ends up holding the other's code; both codes are required to submit the inspection.
- **Response `200 OK`:** `{ "inspectionType": "HANDOVER", "expiresAt": "…", "code": "123456", "emailedToCounterparty": true }`. `code` is included only under the `dev` and `test` profiles and is `null` everywhere else. Outside dev/test, if email notifications are off the request is refused with `409`, because the code would reach nobody. An SMS channel is still a possible addition before production.

#### `POST /api/v1/rentals/{bookingId}/inspection-uploads?type=HANDOVER|RETURN`
- **Access:** Authenticated booking owner or renter
- **Request:** `{ "contentType": "image/jpeg", "contentLength": 250000 }` (JPEG, PNG, or WebP; size 1 byte–10 MB)
- **Description:** Create a short-lived (10-minute) presigned PUT to the private `marketplace-private` bucket. Keys are scoped to the booking and inspection type; the uploaded object must be present and no larger than 10 MB before inspection submission.

#### `POST /api/v1/rentals/{bookingId}/disputes`
- **Access:** Equipment owner
- **Request:** `{ "category": "DAMAGE", "claimAmount": 2500.00, "description": "Cracked housing." }` (claim cannot exceed the security deposit)
- **Description:** File a damage claim within 24 hours of return. Return inspection photos are required as evidence. Moves the booking `RETURNED → DISPUTED`.
- **Response `200 OK`:** `{ "disputeId": "…", "bookingId": "…", "status": "OPEN" }`

#### `POST /api/v1/owner/rentals/{bookingId}/approve-condition`
- **Access:** `ROLE_OWNER`, and the caller must own the booked equipment.
- **Description:** The owner accepts the returned equipment's condition, moving the booking `RETURNED → INSPECTION_PASSED` without waiting for the 24-hour dispute window to lapse. Deposit refund and owner settlement then proceed through the scheduled settlement task. Returns `409` if the booking is not `RETURNED` or has an open dispute; `403` if the caller is not the owner.
- **Response `200 OK`:** `{ "bookingId": "…", "bookingStatus": "INSPECTION_PASSED", "approvedAt": "…" }`

#### `GET /api/v1/admin/disputes?status=OPEN`
- **Access:** `ROLE_ADMIN`
- **Description:** Dispute queue, oldest first. `status` is optional (`OPEN` or `RESOLVED`); omit it for all disputes.
- **Response `200 OK`:**
```json
[
  {
    "disputeId": "…", "bookingId": "…", "bookingReference": "BKG-202611-0492",
    "equipmentTitle": "Bosch GSH 11 E Professional Demolition Hammer 1500W",
    "category": "DAMAGE", "claimAmount": 2500.00, "securityDeposit": 5000.00,
    "status": "OPEN", "raisedByName": "Suresh Patel", "createdAt": "…"
  }
]
```

#### `GET /api/v1/admin/disputes/{disputeId}`
- **Access:** `ROLE_ADMIN`
- **Description:** One dispute with its booking, both parties, and all submitted inspections with 15-minute inline photo URLs (same `inspections` shape as `GET /bookings/{id}`). Unknown id returns `404`.
- **Response `200 OK`:**
```json
{
  "disputeId": "…", "status": "OPEN", "category": "DAMAGE",
  "claimAmount": 2500.00, "approvedDeductionAmount": 0.00,
  "description": "Cracked housing.", "resolutionNotes": null,
  "createdAt": "…", "resolvedAt": null,
  "bookingId": "…", "bookingReference": "BKG-202611-0492", "bookingStatus": "DISPUTED",
  "equipmentId": "…", "equipmentTitle": "…",
  "startTime": "…", "endTime": "…", "securityDeposit": 5000.00,
  "owner": { "name": "Suresh Patel", "email": "owner@example.com" },
  "renter": { "name": "Rajesh Kumar", "email": "renter@example.com" },
  "inspections": [ { "type": "HANDOVER", "...": "..." }, { "type": "RETURN", "...": "..." } ]
}
```

#### `PATCH /api/v1/admin/disputes/{disputeId}/resolve`
- **Access:** `ROLE_ADMIN`
- **Request:** `{ "approvedDeductionAmount": 250.00, "resolutionNotes": "..." }`
- **Description:** Resolve an open dispute, refund the remaining deposit, audit the decision, and advance the booking toward settlement.

The current code includes post-return deposit-refund and transfer-release behavior. Resolved disputes refund only the undisputed deposit balance; the automatic full-deposit refund is suppressed afterward. These operations are not production-authorized until legal/provider review approves the exact flow. Never configure production Razorpay secrets based solely on this contract.

---

### 2.6 Email Notifications

Emails are sent only after the triggering database transaction commits; a mail failure is logged and never undoes the action or fails the request. Sending is controlled by `notifications.enabled` (off by default; dev sets it from `NOTIFICATIONS_ENABLED` and points `spring.mail` at Mailpit via `MAIL_HOST`/`MAIL_PORT`). Links in emails use `notifications.app-base-url`.

| Event | Recipients |
|---|---|
| KYC submitted / approved / rejected | applicant |
| Listing approved / rejected | owner |
| Booking requested | owner |
| Booking accepted / rejected | renter |
| Payment received | renter and owner |
| Booking cancelled (with refund breakdown) | renter and owner |
| Handover / return code | the other party of the booking |
| Damage claim opened / resolved | renter and owner |
| Booking completed + review invitation | renter and owner |

### 2.7 Admin Governance Endpoints (index)

All admin endpoints require `ROLE_ADMIN` and are specified in the sections above:

| Area | Endpoints | Section |
|---|---|---|
| Owner KYC review | `GET /admin/owners?status=UNDER_REVIEW`, `PATCH /admin/owners/{id}/approve`, `PATCH /admin/owners/{id}/reject` | 2.2 |
| Owner KYC documents | `GET /admin/owners/{id}/documents`, `GET /admin/owners/{id}/documents/{documentId}/download` | 2.2 |
| Owner payouts | `PATCH /admin/owners/{id}/razorpay-account` | 2.2 |
| Listing moderation | `GET /admin/listings/pending`, `PATCH /admin/listings/{id}/approve`, `PATCH /admin/listings/{id}/reject?reason=` | 2.2 |
| Categories | `POST /admin/categories`, `PUT /admin/categories/{id}` | 2.2 |
| Disputes | `GET /admin/disputes?status=`, `GET /admin/disputes/{id}`, `PATCH /admin/disputes/{id}/resolve` | 2.5 |

There is no `/admin/owners/pending` endpoint; use `GET /admin/owners?status=UNDER_REVIEW`. Owner and listing approvals use `PATCH`, not `POST`.
