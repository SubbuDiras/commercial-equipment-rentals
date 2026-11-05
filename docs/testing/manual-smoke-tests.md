# Manual Smoke Tests

A click-through check of every feature in the end-to-end completion plan (Phases 1–8). Work top to bottom: later sections reuse the listing and bookings made earlier. Tick each box as it passes; if one fails, note the step, what you saw, and the backend log line.

Allow about 60–90 minutes for a full run. Sections 1–4 (about 20 minutes) are the minimum after any change.

---

## 0. Before you start

### Services running

| Service | URL | Check |
|---|---|---|
| Frontend | http://localhost:4200 | Catalogue page opens |
| Backend | http://localhost:8081/actuator/health | Shows `{"status":"UP"}` |
| Swagger UI | http://localhost:8081/swagger-ui.html | Endpoint list loads |
| Keycloak | http://localhost:8080 | Admin console opens |
| Mailpit (emails) | http://localhost:8025 | Inbox opens |
| MinIO (photos, documents) | http://localhost:9001 | Bucket `marketplace-private` exists |

`backend/env` must contain `RAZORPAY_FAKE=true` and `NOTIFICATIONS_ENABLED=true` (with `MAIL_HOST=localhost`, `MAIL_PORT=1025`).

### Users

| User | Role |
|---|---|
| `renter@example.com` | Renter |
| `owner@example.com` | Owner (verified) |
| `admin@example.com` | Admin |
| a new account you register in section 2 | Becomes an owner during the test |

Use a **separate browser profile or an incognito window per user** so you can stay signed in as two people at once (for example, renter in a normal window and owner in incognito).

### Dev shortcuts (time travel)

Real bookings take days. These PowerShell helpers move a booking through time so you can test handover and return immediately. Paste them into a PowerShell window once per session, entering the renter's password:

```powershell
$api = 'http://localhost:8081/api/v1/dev'
$pw  = Read-Host 'renter@example.com password'
function Auth { $t = (Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/realms/marketplace-realm/protocol/openid-connect/token' `
    -Body @{ client_id='marketplace-frontend'; grant_type='password'; username='renter@example.com'; password=$pw }).access_token
  @{ Authorization = "Bearer $t" } }
function Shift($bookingId, $hours) { Invoke-RestMethod -Method Post -Headers (Auth) -Uri "$api/bookings/$bookingId/shift-time?hours=$hours" }
function RunJobs { Invoke-RestMethod -Method Post -Headers (Auth) -Uri "$api/scheduler/run" }
```

- `Shift <bookingId> -73`: moves a booking that starts in 3 days so it starts now (handover becomes possible).
- `Shift <bookingId> -49`: moves a 2-day booking past its end (return becomes possible).
- `RunJobs`: runs the background jobs now (expire holds, start and finish rentals, release payouts, close review windows).

The **booking ID** is the last part of the booking page URL: `http://localhost:4200/bookings/<bookingId>`.

---

## 1. Sign-in and role checks (Phase 0 / 1)

- [ ] Signed out, open http://localhost:4200/bookings. You are sent to the Keycloak sign-in page. Sign in as `renter@example.com`: you land back on **My Bookings**, not the home page.
- [ ] As renter, the header shows **Equipment Catalogue**, **My Bookings** and **Become an Owner**, and not **My Listings**, **Owner KYC** or **Admin Portal**.
- [ ] As renter, open http://localhost:4200/admin/owners and http://localhost:4200/owner/bookings. Each shows **"You don't have access to this page"**.
- [ ] Sign in as `owner@example.com`: the header also shows **My Listings** and **Owner Bookings**. http://localhost:4200/admin/disputes shows "You don't have access to this page".
- [ ] Sign in as `admin@example.com`: the header shows **Owner KYC**, **Listing Reviews**, **Disputes** and **Admin Portal**, and each page opens.
- [ ] **Sign Out** returns you to the app signed out; the header shows **Sign In / Register**.

## 2. New owner: sign-up and KYC (Phase 2)

- [ ] Click **Sign In / Register** → **Register**. Create an account with email, first name, last name and password. You come back to the app signed in as a renter.
- [ ] Click **Become an Owner**. Fill in first name, last name, phone (`+9198xxxxxxxx`), account holder name, bank account number (`123456789012`) and IFSC (`HDFC0001234`). Click **Submit for verification**. Status shows **Under review**.
- [ ] Upload an ID document (any small PDF). The file name appears in the list.
- [ ] Mailpit: the new user received **"We received your owner application"**.
- [ ] As admin, open **Owner KYC**. The application appears with the uploaded document; the document opens.
- [ ] Click **Approve** → confirm. You see a success message. (If it says the role **could not be assigned**, the Keycloak service account for `marketplace-backend` is not set up; see the local development guide.)
- [ ] Switch to the **Verified** tab. On the new owner, click **Generate fake ID**, then **Save**. It shows **Payout account acc_FAKE…**.
- [ ] Mailpit: the new owner received **"Your owner application is approved"**.
- [ ] As the new owner, **Sign Out and sign in again**. The header now shows **My Listings** and **Owner Bookings**.
- [ ] Rejection path: register a second account, submit KYC, and as admin click **Reject** with a reason. The applicant sees the reason and receives **"Your owner application needs changes"**.

## 3. Listing: create, approve, search (Phase 2 / 7)

- [ ] As the new owner, open **My Listings**. Fill in:

  | Field | Value |
  |---|---|
  | Category | *Demolition Hammers & Breakers* |
  | Listing title | `Smoke test breaker <today's date>` |
  | Description, Manufacturer, Model | anything |
  | Daily rate (₹) | `1000` |
  | Refundable deposit (₹) | `3000` |
  | Specifications | add one row, e.g. `Weight` / `11 kg` |
  | Address | `Plot 1, HITEC City, Madhapur` |
  | City / Postal code | `Hyderabad` / `500081` |
  | Latitude / Longitude | `17.4474` / `78.3762` |

- [ ] Click **Save draft** (shows **Draft saved**), upload at least one photo, then click **Submit for approval** (shows **Listing submitted for admin approval.**).
- [ ] Edit the draft or submitted listing: changes save and the specification row is kept.
- [ ] As admin, open **Listing Reviews**. The listing appears; click **Approve**. It disappears from the queue.
- [ ] Mailpit: owner received **"Your listing "…" is live"**.
- [ ] Signed out, open **Equipment Catalogue**. Search with City `hyderabad`, Category *Demolition Hammers & Breakers*, rate ₹500–₹2000: the listing appears. Search with City `Mumbai`: it does not.
- [ ] Open the listing: the photos, rates, deposit and the **Weight: 11 kg** specification are shown.
- [ ] Rejection path: submit a second listing; as admin, reject it with a reason. The owner sees the reason and receives **"…needs changes"**.

## 4. Booking request, accept, pay (Phase 1 / 3)

- [ ] As `renter@example.com`, open the listing and click **Request booking**. Start: **3 days from now, 09:00**. End: **2 days later, 09:00**. Click **Request booking**.
- [ ] Click **Open booking**. Status shows **Requested**; the price breakdown (rental, deposit, fees, total) is shown in ₹.
- [ ] Mailpit: owner received **"New booking request BKG-… for …"**.
- [ ] As the owner, open **Owner Bookings**. The request shows a **Respond by** deadline (4 hours after the request). Click **Accept**: you see **Accepted BKG-…**. The renter now has **30 minutes** to pay.
- [ ] Mailpit: renter received **"Booking BKG-… accepted - complete payment"**.
- [ ] As renter, reload the booking. Status **Awaiting payment**. Click **Pay now**: the **Fake Razorpay** dialog opens.
- [ ] Click **Fail payment**: the dialog says the payment was **declined**. Close it. Status **Payment failed**.
- [ ] Click **Pay now** again → **Pay successfully**. You see **Payment received. Your booking is confirmed.** Status **Confirmed**.
- [ ] Mailpit: both renter and owner received **"Payment received - booking BKG-… confirmed"**.
- [ ] Booking history (**My Bookings** for renter, **Owner Bookings** for owner) lists the booking with the right status.

Keep this booking for sections 5–7. Note its booking ID.

## 5. Handover (Phase 1 / 7)

- [ ] In PowerShell: `Shift <bookingId> -73`. Reload the booking page as both renter and owner.
- [ ] Owner clicks **Generate my code**: a 6-digit code is shown under **Development only**. Mailpit: the **renter** received **"Your handover code for booking BKG-…"** containing the owner's code.
- [ ] Renter clicks **Generate my code**. Mailpit: the **owner** received the renter's code.
- [ ] Renter uploads **4 photos** in the inspection section. It shows **Photos (4 of at least 4)**.
- [ ] Owner reloads and fills in: hour meter `100`, ticks **All accessories present** and **Overall condition acceptable**, **Your code** = owner's code, **Renter's code** = renter's code. Click **Confirm handover**.
- [ ] Shows **Handover confirmed**; status **In progress**.
- [ ] Negative check (on another booking, or before confirming): a wrong code is rejected with an error message, and the status does not change.

## 6. Return and condition approval (Phase 1 / 7)

- [ ] `Shift <bookingId> -49`. Reload both pages.
- [ ] Repeat the code and photo steps for the **return** (hour meter `112.5`). Click **Confirm return**. Status **Returned**.
- [ ] Owner sees **Approve condition** and **Raise damage claim** buttons.
- [ ] Owner clicks **Approve condition** → confirm. Status **Inspection passed**.
- [ ] `RunJobs`, then `Shift <bookingId> -1`, then `RunJobs` again. Reload: status **Completed**.
- [ ] Mailpit: both received **"Booking BKG-… completed - leave a review"**.
- [ ] The booking page shows the deposit refunded to the renter and the payout to the owner.

## 7. Reviews and CLOSED (Phase 5)

- [ ] Owner picks **5 stars**, writes a comment, clicks **Submit review**: **Thank you for your review.** Status stays **Completed** (waiting for the renter).
- [ ] Renter picks **4 stars**, writes a comment, **Submit review**. Status becomes **Closed**.
- [ ] Open the listing page: the **renter's** review and the rating appear. The owner's review of the renter does **not** appear on the listing.
- [ ] A second review attempt by the same person is refused (no review form shown).
- [ ] Review window: on another completed booking, `Shift <bookingId> -340` (more than 14 days) and `RunJobs`. The booking becomes **Closed** without reviews and the review form is gone.

## 8. Owner declines a request (Phase 2)

- [ ] Renter requests a new booking (3 days ahead).
- [ ] Owner, in **Owner Bookings**, clicks **Decline**, enters **Reason (shown to the renter)**, confirms. Shows **Declined BKG-…**.
- [ ] Renter's booking shows **Declined** with the reason, and no **Pay now** button. Mailpit: **"Booking BKG-… was declined"**.
- [ ] Response deadline (the owner has 4 hours): request another booking, do not answer, `Shift <bookingId> -5` and `RunJobs`. Status **Request expired**.
- [ ] Payment deadline (the renter has 30 minutes after acceptance): request and accept another booking, do not pay, `Shift <bookingId> -1` and `RunJobs`. Status **Payment window expired**.

## 9. Cancellation (Phase 4)

The policy (draft, configurable in `application.yml` under `cancellation`):

| When the renter cancels | Rental charges refunded | Deposit |
|---|---|---|
| Before paying | nothing was taken | — |
| 48 hours or more before start | 100% minus 2% gateway fee | full |
| 24–48 hours before start | 50% | full |
| Less than 24 hours before start | 0% (owner gets 80% of their expected payout) | full |
| **Owner** cancels, any time | 100% | full, plus ₹500 platform credit recorded and a penalty on the owner profile |

For each row, make a fresh booking, then click **Cancel booking**, check the dialog, enter **Reason for cancelling**, and confirm:

- [ ] **Before payment** (accepted, not paid): dialog says **No payment has been taken**. Status **Cancelled by renter**.
- [ ] **≥ 48 h** (paid, start 3 days ahead): dialog says **More than 48 hours before the start**. **Total refund** = rental × 98% + deposit.
- [ ] **24–48 h** (paid, then `Shift <bookingId> -42` so the start is about 30 h away): **24–48 hours before the start**. Total refund = rental × 50% + deposit.
- [ ] **< 24 h** (paid, then `Shift <bookingId> -62` so the start is about 10 h away): **Less than 24 hours before the start**. Total refund = deposit only.
- [ ] **Owner cancels** a paid booking from **Owner Bookings → Upcoming → Cancel**: dialog says **Owner cancellation: the renter gets a full refund.**, shows **Platform credit to renter ₹500**, and warns that the penalty is **recorded on your owner profile**. Status **Cancelled by owner**.
- [ ] In every case the amounts in the dialog match what the booking page shows after cancelling, and both sides received **"Booking BKG-… cancelled"**.
- [ ] Cancel is not offered once the rental is **In progress**.

## 10. Damage claim and dispute (Phase 2 / 7)

- [ ] Run a fresh booking to **Returned** (sections 4–6, without approving the condition).
- [ ] Owner clicks **Raise damage claim**: Category **Damage**, **Claim amount (₹)** `500`, **What happened** text, **Submit claim**. Shows **Damage claim submitted.**; status **Disputed**.
- [ ] Mailpit: **"Damage claim opened on booking BKG-…"** to renter, owner and admins.
- [ ] Admin opens **Disputes**: the claim is listed. Open it: **Return evidence** shows the handover and return photos (8).
- [ ] Enter **Deduction from deposit (₹)** `400` and **Decision notes**, click **Resolve claim** → **Resolve**. Shows **Claim resolved.** and status **Resolved**.
- [ ] Renter's booking shows **Approved deduction ₹400** and a deposit balance refund of deposit − ₹400. Mailpit: **"Damage claim on booking BKG-… resolved"** to both.
- [ ] Claim window (24 hours after return): on another returned booking, `Shift <bookingId> -25` and `RunJobs`. **Raise damage claim** is no longer offered.

## 11. Privacy and access (Phase 1)

- [ ] As a different renter (register one), open the first renter's booking URL: an error is shown, not the booking.
- [ ] As admin, the same booking URL opens.
- [ ] Inspection photos and KYC documents open only for the people involved and admins (copy a photo link into a signed-out window: it is refused or expired).

## 12. Admin portal (existing)

- [ ] **Admin Portal** shows the metrics page (bookings, GMV, etc.) and the numbers include the bookings made during this run.

## 13. Swagger checks (Phase 2 / 3 review checkpoints)

At http://localhost:8081/swagger-ui.html click **Authorize** and paste a token (from the `Auth` helper: `(Auth).Authorization`, without the `Bearer ` part if Swagger adds it).

- [ ] `GET /api/v1/bookings/{id}` returns `availableActions` that match the buttons on the booking page for that user.
- [ ] `GET /api/v1/bookings/{id}/cancellation-quote` returns the same numbers as the cancel dialog.
- [ ] Every endpoint listed in `docs/api/rest-api-contract.md` appears in Swagger.

## 14. Automated suite (Phase 8)

- [ ] `cd e2e; npm test`. All 14 Playwright tests pass (needs the full stack running).
- [ ] `cd backend; .\gradlew.bat test`. All tests pass (Docker running for the MySQL tests).
- [ ] `cd frontend; npm test; npm run build`. Tests pass, build has no errors.

---

## Recording results

| Section | Pass / Fail | Notes (step, what happened, log line) |
|---|---|---|
| 1 Sign-in and roles | | |
| 2 Owner KYC | | |
| 3 Listing | | |
| 4 Booking and payment | | |
| 5 Handover | | |
| 6 Return | | |
| 7 Reviews | | |
| 8 Decline | | |
| 9 Cancellation | | |
| 10 Dispute | | |
| 11 Privacy | | |
| 12 Admin portal | | |
| 13 Swagger | | |
| 14 Automated | | |
