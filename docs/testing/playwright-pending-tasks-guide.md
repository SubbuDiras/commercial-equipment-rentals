# Playwright Pending Tasks: Step-by-Step Guide

**Date:** 2026-11-05 · **Companion to:** [playwright-automation-plan.md](playwright-automation-plan.md)
**Goal:** Close every pending item from the plan's checklist. Each task gives the files to touch, the steps in order, ready-to-use code, and how to check it is done.

**Who this is for:** whoever picks up the e2e work. It assumes the local stack runs (plan, Step 1) and `npm test` in `e2e\` passes today.

> Commands are for **Command Prompt** (`cmd`). In PowerShell, replace `set NAME=value` with `$env:NAME = 'value'`.

## Status (2026-11-05)

Implemented. Full Chromium suite: **all 43 pass**, about 2.7 min on 1 worker, 1.4–2 min with `E2E_WORKERS=3`.

| Item | State | Where |
|---|---|---|
| Task 0 helpers | Done | `support/fixtures.ts`, `support/ui.ts`, `support/env.ts` |
| C1, C2 expiry | Pass | `tests/13-expiry.spec.ts` |
| C3 double booking | Pass, after fixing the backend time-zone bug (below) | `tests/14-double-booking.spec.ts` |
| C4 form validation | Pass | `tests/15-booking-validation.spec.ts` |
| C5 amounts | Pass (the payment email does not show the amount, so it is not checked) | `tests/16-amounts.spec.ts` |
| C6 owner isolation, KYC endpoints | Pass | `tests/07-role-checks.spec.ts` |
| C7 dispute outcomes | Pass | `tests/06-dispute.spec.ts` |
| C8 KYC privacy | Pass | step in `tests/01-happy-path.spec.ts` |
| B saved sign-in | Done; falls back to the form if a session has expired | `tests/auth.setup.ts`, `signedInPage` in `support/ui.ts` |
| B page objects | Every main page: catalogue, booking request, booking details, my bookings, profile, owner onboarding, owner listings, owner bookings, admin owners/listings/disputes. Old `ui.ts` helpers delegate to them | `pages/`, `support/test.ts` |
| D tags, scripts, CI config | Done | `package.json`, `playwright.config.ts` |
| D CI job | Written, **not yet run on GitHub** | `.github/workflows/ci.yml` (`e2e` job) |
| D flaky label | Not done (create `flaky-test` label in GitHub) | — |
| E per-worker users | Done (opt in with `E2E_WORKERS=N`; CI uses 2) | `support/env.ts`, `global-setup.ts` |
| E other browsers | Smoke on Firefox and WebKit: pass, except the double-booking bug. Phone (Pixel 7): 2 more fail, layout bugs (below) | `playwright.config.ts`; `npm run install-all-browsers` once |
| F accessibility | Written; **all 7 fail on colour contrast** (below); excluded from `npm test` | `tests/17-accessibility.spec.ts`, `npm run test:a11y` |
| F P2 coverage | Booking history, unknown listing, server error, session loss, onboarding validation, extra emails | `tests/18`–`20`, `tests/08-emails.spec.ts` |
| F PR template | Done | `.github/pull_request_template.md` |

**Known problems the tests found**

1. **Fixed: requests did not block each other when MySQL runs on India time.** Hold expiry is stored in UTC but compared with MySQL's `CURRENT_TIMESTAMP` (local time) in `BookingService.hasOverlappingBooking` and `EquipmentCatalogueService`. On a MySQL set to IST (UTC+5:30), a new 4-hour hold already looks 1.5 hours expired, so overlapping requests are accepted. CI's MySQL container runs in UTC, so CI will not show it. Both queries now compare against a bound `Instant.now()` parameter, as `expireHolds` does.
2. **No phone layout for the header.** At phone width the nav links, user name, role badge and Sign Out overlap and run off the right edge, so the page scrolls sideways. "Become an Owner" is covered and cannot be tapped, and in the cancel dialog the Cancel booking button is partly covered. Fails the mobile smoke runs of `01-happy-path` and `04-renter-cancels`.
3. **Colour contrast below WCAG AA** on the small green tagline in the header (`header.component.ts:49`, `#059669` on white, 3.73:1) and on neutral status badges (`.badge-neutral` in `styles.css`, `#64748b` on `#f1f5f9`, 4.34:1). Needs 4.5:1.

---

## Contents

- [Order of work](#order-of-work)
- [Facts the tests rely on](#facts-the-tests-rely-on)
- [Task 0: Shared preparation](#task-0-shared-preparation) (do first)
- **Phase C: P1 tests**
  - [Task C1: Request expires when the owner does not answer](#task-c1-request-expires-when-the-owner-does-not-answer)
  - [Task C2: Payment window expires](#task-c2-payment-window-expires)
  - [Task C3: Double booking is refused](#task-c3-double-booking-is-refused)
  - [Task C4: Booking request form validation](#task-c4-booking-request-form-validation)
  - [Task C5: Amounts match from quote to payment to email](#task-c5-amounts-match-from-quote-to-payment-to-email)
  - [Task C6: Owner sees only their own bookings](#task-c6-owner-sees-only-their-own-bookings)
  - [Task C7: Every dispute outcome pays out correctly](#task-c7-every-dispute-outcome-pays-out-correctly)
  - [Task C8: Owner's bank and Aadhaar numbers never reach a renter](#task-c8-owners-bank-and-aadhaar-numbers-never-reach-a-renter)
- [Phase A: Baseline](#phase-a-baseline)
- [Phase B: Speed and structure](#phase-b-speed-and-structure)
- [Phase D: CI](#phase-d-ci)
- [Phase E: Scale](#phase-e-scale)
- [Phase F: Quality extras](#phase-f-quality-extras)
- [Final checklist](#final-checklist)

---

## Order of work

| Order | Task | Why this order |
|---|---|---|
| 1 | Phase A (baseline) | You need a green run and a timing to compare against |
| 2 | Task 0 | Every new test uses these helpers |
| 3 | C1 → C8 | Money, access and booking correctness. Each test is independent, so they can be split between people |
| 4 | Phase D (tags + CI) | Protects everything above on every pull request |
| 5 | Phase B (speed) | Makes the bigger suite fast |
| 6 | Phase E, then F | Scale and polish |

Add each test in its own commit. Run it alone, then with the whole suite, before moving on.

---

## Facts the tests rely on

These come from the current code. If one changes, update the matching test.

| Fact | Source |
|---|---|
| Owner must answer a request within **4 hours** | `OWNER_REVIEW_WINDOW` in [BookingService.java:38](../../backend/src/main/java/com/marketplace/booking/BookingService.java#L38) |
| Renter must pay within **30 minutes** of acceptance | `PAYMENT_WINDOW` in [BookingService.java:39](../../backend/src/main/java/com/marketplace/booking/BookingService.java#L39) |
| Expiry job: `REQUESTED` → `REQUEST_EXPIRED`; `PAYMENT_PENDING`/`PAYMENT_FAILED` → `HOLD_EXPIRED` | `expireHolds()` in BookingService |
| `POST /api/v1/dev/scheduler/run` runs that job now | `DevToolsService.runScheduledJobs()` |
| `shift-time` moves start, end **and** hold expiry by N hours | `DevToolsService.shiftTime()` |
| Overlap is refused with **"The equipment is already reserved for an overlapping period."** (HTTP 409) | [BookingService.java:75](../../backend/src/main/java/com/marketplace/booking/BookingService.java#L75) |
| Past start is refused with **"Rental start time must be in the future."** | `validatePeriod()` in BookingService |
| Form shows **"The end must be later than the start."** before calling the backend | [booking-request.component.ts](../../frontend/src/app/features/bookings/booking-request.component.ts) |
| Backend error text is shown in the form's `role="alert"` paragraph | `errorInterceptor` uses the problem's `detail` |
| Status labels: **"Request expired"**, **"Payment window expired"** | [status-badge.component.ts](../../frontend/src/app/shared/status-badge.component.ts) |
| Owner bookings tabs: Requests, Upcoming, Active, **Past** (holds both expired states), All | [owner-bookings.component.ts](../../frontend/src/app/features/owner/owner-bookings.component.ts) |
| Quote dialog shows **"Total payable"**; payment dialog shows **"Amount:"** | booking-request and payment-dialog components |

---

## Task 0: Shared preparation

Small helper changes that the new tests need. Do these in one commit.

### 0.1 Return `equipmentId` from `bookingIn`

Several tests book the **same** equipment a second time. In [support/fixtures.ts](../../e2e/support/fixtures.ts), change the return value:

```ts
export async function bookingIn(
  state: 'requested' | 'accepted' | 'paid',
  startInHours = 72
): Promise<{ bookingId: string; title: string; equipmentId: string }> {
  const { equipmentId, title } = await freshListing();
  const bookingId = await renterApi().requestBooking(equipmentId, startInHours);
  if (state !== 'requested') {
    await ownerApi().accept(bookingId);
  }
  if (state === 'paid') {
    await renterApi().pay(bookingId);
  }
  return { bookingId, title, equipmentId };
}
```

Existing callers keep working because they only destructure the fields they use.

### 0.2 Add an `outsiderApi` shortcut

Same file, next to the others:

```ts
export const outsiderApi = () => Api.as(users.outsider.email);
```

### 0.3 Move `localDateTime` into `support/ui.ts`

It is a private function at the bottom of [01-happy-path.spec.ts](../../e2e/tests/01-happy-path.spec.ts). Move it to [support/ui.ts](../../e2e/support/ui.ts), export it, and import it in 01-happy-path:

```ts
/** Formats a date for an <input type="datetime-local">, in the browser's local time. */
export function localDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

/** A start time `hours` from now, on the hour, so form input and API agree. */
export function hoursFromNow(hours: number): Date {
  const date = new Date(Date.now() + hours * 3_600_000);
  date.setMinutes(0, 0, 0);
  return date;
}
```

### 0.4 Add a second fixed owner (for Task C6)

In [support/env.ts](../../e2e/support/env.ts), add to `users`:

```ts
  /** A second verified owner, to check owners only see their own bookings. */
  otherOwner: { email: 'e2e-other-owner@example.com', firstName: 'Olga', lastName: 'Otherowner', roles: ['ROLE_RENTER', 'ROLE_OWNER'] },
```

In [global-setup.ts](../../e2e/global-setup.ts), after the existing `seedOwnerListing()` line:

```ts
  await Api.as(users.otherOwner.email).seedOwnerListing();
```

`global-setup` already loops over every user in `users`, so the Keycloak account is created automatically.

### 0.5 Check

```cmd
cd e2e
npm run typecheck
npx playwright test tests/01-happy-path.spec.ts
```

**Done when:** typecheck passes and 01-happy-path still passes with the moved helper.

---

## Task C1: Request expires when the owner does not answer

**File:** `e2e/tests/13-expiry.spec.ts` (new; C2 goes in the same file)

**What it proves:** a request the owner ignores for 4 hours becomes "Request expired" for both sides, and the dates become free again.

**Steps**
1. Create a request 72 hours ahead with `bookingIn('requested', 72)`.
2. Shift the booking back 5 hours. The 4-hour hold is now 1 hour in the past (the rental still starts 67 hours ahead).
3. Run the scheduler and check the API status is `REQUEST_EXPIRED`.
4. Renter opens the booking: badge says **Request expired**, no **Pay now** button.
5. Owner opens **Bookings for my equipment** → **Past** tab: the booking is listed as expired, with no Accept button.
6. Another renter books the same equipment for the same dates. It must succeed.

**Code**

```ts
import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, outsiderApi, renterApi } from '../support/fixtures';
import { expectStatus, openBooking, signedInPage } from '../support/ui';

test.describe('expiry', () => {
  test('a request the owner does not answer in 4 hours expires and frees the dates', async ({ browser }) => {
    const { bookingId, title, equipmentId } = await bookingIn('requested', 72);
    const renter = renterApi();

    await test.step('time passes the owner review window', async () => {
      await renter.shiftTime(bookingId, -5);
      await renter.runScheduler();
      expect(await renter.status(bookingId)).toBe('REQUEST_EXPIRED');
    });

    await test.step('renter sees it expired', async () => {
      const page = await signedInPage(browser, users.renter.email);
      await openBooking(page, bookingId);
      await expectStatus(page, 'Request expired');
      await expect(page.getByRole('button', { name: 'Pay now' })).toHaveCount(0);
    });

    await test.step('owner finds it under Past, with nothing to answer', async () => {
      const page = await signedInPage(browser, users.owner.email, '/owner/bookings');
      await page.getByRole('tab', { name: 'Past' }).click();
      const row = page.locator('article', { hasText: title });
      await expect(row.getByText('Request expired')).toBeVisible();
      await expect(row.getByRole('button', { name: 'Accept' })).toHaveCount(0);
    });

    await test.step('the same dates can be booked again', async () => {
      const second = await outsiderApi().requestBooking(equipmentId, 72);
      expect(await outsiderApi().status(second)).toBe('REQUESTED');
    });
  });
});
```

**Watch out for**
- `runScheduler` expires **every** overdue hold in the database, including leftovers from other runs. That is fine with one worker. When you move to parallel workers (Phase E), assert only on this test's booking, as above.
- If the owner row shows the status somewhere other than a badge, adjust step 5's locator after opening the page in `--ui` mode.

**Done when:** `npx playwright test tests/13-expiry.spec.ts -g "owner does not answer"` passes.

---

## Task C2: Payment window expires

**File:** `e2e/tests/13-expiry.spec.ts` (add inside the same `describe`)

**What it proves:** a renter who does not pay within 30 minutes of acceptance loses the hold, cannot pay any more, and someone else can take the dates.

**Steps**
1. `bookingIn('accepted', 72)`: the booking is `PAYMENT_PENDING` with a 30-minute hold.
2. Shift back 1 hour, run the scheduler, check `HOLD_EXPIRED`.
3. Renter opens the booking: **Payment window expired**, no **Pay now**.
4. Calling the payment-order API now must fail (the backend must not take money for an expired hold).
5. Another renter books the same dates successfully.
6. Repeat steps 1–3 starting from a **failed** payment (`PAYMENT_FAILED` also expires).

**Code**

```ts
  test('a renter who does not pay in 30 minutes loses the hold', async ({ browser }) => {
    const { bookingId, equipmentId } = await bookingIn('accepted', 72);
    const renter = renterApi();

    await renter.shiftTime(bookingId, -1);
    await renter.runScheduler();
    expect(await renter.status(bookingId)).toBe('HOLD_EXPIRED');

    const page = await signedInPage(browser, users.renter.email);
    await openBooking(page, bookingId);
    await expectStatus(page, 'Payment window expired');
    await expect(page.getByRole('button', { name: 'Pay now' })).toHaveCount(0);

    await expect(renter.post(`/api/v1/bookings/${bookingId}/payment-order`)).rejects.toThrow(/HTTP 4\d\d/);

    const second = await outsiderApi().requestBooking(equipmentId, 72);
    expect(await outsiderApi().status(second)).toBe('REQUESTED');
  });

  test('a failed payment that is not retried in time also expires', async () => {
    const { bookingId } = await bookingIn('accepted', 72);
    const renter = renterApi();
    await renter.pay(bookingId, 'FAILURE');
    expect(await renter.status(bookingId)).toBe('PAYMENT_FAILED');

    await renter.shiftTime(bookingId, -1);
    await renter.runScheduler();
    expect(await renter.status(bookingId)).toBe('HOLD_EXPIRED');
  });
```

**Watch out for:** if step 4 returns 2xx, that is a **backend bug** (payment allowed after expiry), not a test problem. Raise it rather than loosening the test.

**Done when:** all three tests in `13-expiry.spec.ts` pass.

---

## Task C3: Double booking is refused

**File:** `e2e/tests/14-double-booking.spec.ts` (new)

**What it proves:** while one renter holds dates, another renter cannot book overlapping dates, through the UI or by sending two requests at the same moment.

**Steps**
1. Renter requests days 3–5 on a fresh listing (`bookingIn('requested', 72)`).
2. The outsider opens `/equipment/<id>/book`, enters an overlapping period (one day later), and submits.
3. The form's alert shows the overlap message; no quote dialog appears.
4. A non-overlapping period (after the first booking ends) succeeds.
5. Race: on a new listing, send two identical requests at once with `Promise.allSettled`. Exactly one succeeds; the other fails with HTTP 409.

**Code**

```ts
import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, freshListing, outsiderApi, renterApi } from '../support/fixtures';
import { hoursFromNow, localDateTime, signedInPage } from '../support/ui';

async function requestInForm(page: import('@playwright/test').Page, start: Date, days: number) {
  await page.getByLabel('Start').fill(localDateTime(start));
  await page.getByLabel('End').fill(localDateTime(new Date(start.getTime() + days * 86_400_000)));
  await page.getByRole('button', { name: 'Request booking' }).click();
}

test('a second renter cannot book dates that overlap a held booking', async ({ browser }) => {
  const { equipmentId } = await bookingIn('requested', 72);
  const page = await signedInPage(browser, users.outsider.email, `/equipment/${equipmentId}/book`);

  await requestInForm(page, hoursFromNow(96), 2);
  await expect(page.getByRole('alert')).toContainText('already reserved for an overlapping period');
  await expect(page.getByRole('dialog')).toHaveCount(0);

  // After the first booking ends (72h + 2 days = 120h), the dates are free.
  await requestInForm(page, hoursFromNow(144), 2);
  await expect(page.getByRole('dialog').getByText('Request sent')).toBeVisible();
});

test('two requests sent at the same moment: only one wins', async () => {
  const { equipmentId } = await freshListing();
  const results = await Promise.allSettled([
    renterApi().requestBooking(equipmentId, 72),
    outsiderApi().requestBooking(equipmentId, 72)
  ]);
  expect(results.filter(r => r.status === 'fulfilled')).toHaveLength(1);
  const failure = results.find(r => r.status === 'rejected') as PromiseRejectedResult;
  expect(String(failure.reason)).toMatch(/HTTP 409/);
});
```

**Watch out for:** if the race test sometimes gets **two** successes, the backend has a real concurrency bug (the overlap check and insert are not atomic). Report it with the trace; don't add a retry.

**Done when:** both tests pass 5 runs in a row: `npx playwright test tests/14-double-booking.spec.ts --repeat-each=5`.

---

## Task C4: Booking request form validation

**File:** `e2e/tests/15-booking-validation.spec.ts` (new)

**What it proves:** bad dates are explained to the renter and never create a booking.

**Cases**

| Input | Expected alert | Who catches it |
|---|---|---|
| Start and End empty | "Please check the highlighted fields: Start, End." | Form |
| End before start | "The end must be later than the start." | Form |
| End equal to start | "The end must be later than the start." | Form |
| Start in the past | "Rental start time must be in the future." | Backend (409) |
| Owner delivery with an empty address | "Complete all delivery contact and address fields." | Form |

**Code**

```ts
import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { freshListing } from '../support/fixtures';
import { hoursFromNow, localDateTime, signedInPage } from '../support/ui';

test('the booking form explains bad dates and creates nothing', async ({ browser }) => {
  const { equipmentId } = await freshListing();
  const page = await signedInPage(browser, users.renter.email, `/equipment/${equipmentId}/book`);
  const alert = page.getByRole('alert');
  const submit = page.getByRole('button', { name: 'Request booking' });
  const start = hoursFromNow(72);

  await submit.click();
  await expect(alert).toContainText('Start');
  await expect(alert).toContainText('End');

  await page.getByLabel('Start').fill(localDateTime(start));
  await page.getByLabel('End').fill(localDateTime(new Date(start.getTime() - 86_400_000)));
  await submit.click();
  await expect(alert).toHaveText('The end must be later than the start.');

  await page.getByLabel('End').fill(localDateTime(start));
  await submit.click();
  await expect(alert).toHaveText('The end must be later than the start.');

  const past = hoursFromNow(-24);
  await page.getByLabel('Start').fill(localDateTime(past));
  await page.getByLabel('End').fill(localDateTime(new Date(past.getTime() + 2 * 86_400_000)));
  await submit.click();
  await expect(alert).toContainText('Rental start time must be in the future.');

  await page.getByLabel('Start').fill(localDateTime(start));
  await page.getByLabel('End').fill(localDateTime(new Date(start.getTime() + 2 * 86_400_000)));
  await page.getByLabel('Collection / delivery').selectOption('OWNER_DELIVERY');
  await submit.click();
  await expect(alert).toHaveText('Complete all delivery contact and address fields.');

  await expect(page.getByRole('dialog')).toHaveCount(0);
});
```

**Notes**
- The first case's exact wording comes from `invalidFieldsMessage` in [form-feedback.ts](../../frontend/src/app/shared/form-feedback.ts). Use `toContainText` so field order doesn't matter.
- The delivery case needs a listing that offers owner delivery. If the seeded listing doesn't, the backend says "Owner delivery is not available for this equipment." Check [12-delivery.spec.ts](../../e2e/tests/12-delivery.spec.ts) for how it gets a delivery-enabled listing, and reuse that.

**Done when:** the test passes, and the renter's booking list has no new booking for this listing.

---

## Task C5: Amounts match from quote to payment to email

**File:** `e2e/tests/16-amounts.spec.ts` (new)

**What it proves:** the total the renter is quoted is exactly what the payment dialog charges and what the backend records. A mismatch here means charging the wrong amount.

**Steps**
1. Renter requests a 2-day booking through the form and reads **Total payable** from the quote dialog.
2. Read `pricing.totalPayableAmount` from the API and check: rental + delivery + fee + GST + deposit = total.
3. Owner accepts (API). Renter opens the booking and clicks **Pay now**.
4. Payment dialog **Amount:** equals the quoted total.
5. Pay successfully. `payment.amount` in the API equals the total.
6. If the payment email shows the amount, check it too.

**Code**

```ts
import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { freshListing, ownerApi, renterApi } from '../support/fixtures';
import { mailpit } from '../support/mailpit';
import { dialog, hoursFromNow, localDateTime, signedInPage } from '../support/ui';

/** "₹4,512.00" or "₹ 4,512.00" → 4512 */
const rupees = (text: string | null) => Number((text ?? '').replace(/[^\d.]/g, ''));

test('the quoted total is what the renter pays', async ({ browser }) => {
  const { equipmentId } = await freshListing();
  const page = await signedInPage(browser, users.renter.email, `/equipment/${equipmentId}/book`);

  const start = hoursFromNow(72);
  await page.getByLabel('Start').fill(localDateTime(start));
  await page.getByLabel('End').fill(localDateTime(new Date(start.getTime() + 2 * 86_400_000)));
  await page.getByRole('button', { name: 'Request booking' }).click();

  const quote = dialog(page);
  const quoted = rupees(await quote.locator('dd.quote-total').textContent());
  expect(quoted).toBeGreaterThan(0);
  await quote.getByRole('link', { name: 'Open booking' }).click();
  const bookingId = page.url().split('/').pop()!;

  const pricing = (await renterApi().details(bookingId)).pricing;
  expect(pricing.totalPayableAmount).toBeCloseTo(quoted, 2);
  expect(pricing.baseRentalAmount + pricing.deliveryCharge + pricing.platformFeeAmount
    + pricing.taxAmount + pricing.securityDepositAmount).toBeCloseTo(pricing.totalPayableAmount, 2);

  await ownerApi().accept(bookingId);
  await page.reload();
  await page.getByRole('button', { name: 'Pay now' }).click();
  const amountShown = rupees(await dialog(page).getByText(/^Amount:/).locator('strong').textContent());
  expect(amountShown).toBeCloseTo(quoted, 2);

  await dialog(page).getByRole('button', { name: 'Pay successfully' }).click();
  await expect(page.getByText('Payment received. Your booking is confirmed.')).toBeVisible();
  expect((await renterApi().details(bookingId)).payment.amount).toBeCloseTo(quoted, 2);

  const reference = (await renterApi().details(bookingId)).bookingReference;
  const mail = await mailpit.waitFor(users.renter.email, `Payment received - booking ${reference}`);
  // Keep this line only if the email template shows the amount; otherwise delete it.
  expect(rupees(mail.html.match(/₹\s?[\d,]+\.\d{2}/)?.[0] ?? null)).toBeCloseTo(quoted, 2);
});
```

**Watch out for**
- The quote uses Angular's `currency:'INR'` pipe and the booking page uses the app's `inr` pipe. They may format differently (comma grouping for lakhs). Always compare **numbers** with `rupees()`, never strings.
- If the API returns amounts as strings, wrap them in `Number(...)`.
- If the price breakdown doesn't add up, check whether GST is calculated on the fee only. The quote dialog's note says fee and GST bases exclude the deposit. Adjust the formula to the business rule in [business-requirements.md](../requirements/business-requirements.md), not to whatever the code does.

**Done when:** the test passes, and changing the daily rate on a listing still gives matching numbers.

---

## Task C6: Owner sees only their own bookings

**File:** `e2e/tests/07-role-checks.spec.ts` (add to the existing file)

**Needs:** Task 0.4 (the `otherOwner` user).

**What it proves:** one owner cannot see or act on bookings for another owner's equipment.

**Steps**
1. `otherOwner` seeds a listing; the renter requests it.
2. The e2e owner opens **Bookings for my equipment** → **All**: the booking is not listed.
3. The e2e owner opens `/bookings/<id>` directly: sees no access (unauthorized page or "not found").
4. The e2e owner calls accept on it through the API: gets 403 or 404, and the status stays `REQUESTED`.

**Code**

```ts
test("an owner cannot see or accept bookings for another owner's equipment", async ({ browser }) => {
  const other = Api.as(users.otherOwner.email);
  const { equipmentId, title } = await other.seedOwnerListing();
  const bookingId = await renterApi().requestBooking(equipmentId, 72);

  const page = await signedInPage(browser, users.owner.email, '/owner/bookings');
  await page.getByRole('tab', { name: 'All' }).click();
  await expect(page.locator('article', { hasText: title })).toHaveCount(0);

  await page.goto(`/bookings/${bookingId}`);
  await expect(page.getByText(/^Booking BKG-/)).toHaveCount(0);

  await expect(ownerApi().accept(bookingId)).rejects.toThrow(/HTTP (403|404)/);
  expect(await renterApi().status(bookingId)).toBe('REQUESTED');
});
```

Add these imports at the top if missing: `Api` from `../support/api`, `ownerApi`/`renterApi` from `../support/fixtures`.

**Watch out for:** the **All** tab is paged (20 per page). Because the title is unique per run, "count 0" is still correct; just make sure the page has finished loading (wait for "Loading bookings…" to disappear) before asserting.

**Done when:** the test passes; also run the full `07-role-checks.spec.ts` to make sure the new user didn't affect the others.

---

## Task C7: Every dispute outcome pays out correctly

**File:** `e2e/tests/06-dispute.spec.ts` (extend)

The existing test covers a **partial** deduction (₹400 of a ₹500 claim). Add the two boundary outcomes.

| Case | Deduction | Expected |
|---|---|---|
| Claim rejected | ₹0 | Renter gets the whole deposit back |
| Partial (exists) | ₹400 | Deposit − 400 back |
| Full claim | equal to claim, not more than the deposit | Deposit − claim back |
| Over the deposit | more than the deposit | Admin form refuses it |

**Steps**
1. Move the "run the rental to return through the API" lines of the existing test into a helper `returnedWithClaim(claimAmount)` that ends with the owner raising the claim by API (`owner.raiseDispute(bookingId, amount)`). Keep the existing UI test as it is.
2. Write one parameterised test for the 0 and full cases, through the admin UI.
3. Write one test that enters more than the deposit and expects an error and no resolution.

**Code**

```ts
async function returnedWithClaim(claimAmount: number) {
  const { bookingId, title } = await bookingIn('paid');
  const renter = renterApi();
  const owner = ownerApi();
  await renter.shiftTime(bookingId, -73);
  await owner.completeInspection(bookingId, 'HANDOVER', renter);
  await renter.shiftTime(bookingId, -49);
  await owner.completeInspection(bookingId, 'RETURN', renter);
  await owner.raiseDispute(bookingId, claimAmount);
  return { bookingId, title };
}

for (const { name, deduction } of [
  { name: 'rejects the claim', deduction: 0 },
  { name: 'approves the full claim', deduction: 500 }
]) {
  test(`admin ${name}: deposit refund is correct`, async ({ browser }) => {
    const { bookingId, title } = await returnedWithClaim(500);
    const admin = await signedInPage(browser, users.admin.email, '/admin/disputes');
    await admin.getByRole('link', { name: new RegExp(title) }).click();
    await admin.getByLabel('Deduction from deposit (₹)').fill(String(deduction));
    await admin.getByLabel('Decision notes (shared with both parties)').fill(`E2E: ${name}.`);
    await admin.getByRole('button', { name: 'Resolve claim' }).click();
    await dialog(admin).getByRole('button', { name: 'Resolve' }).click();
    await expect(admin.getByText('Claim resolved.')).toBeVisible();

    const details = await renterApi().details(bookingId);
    expect(details.dispute.approvedDeductionAmount).toBe(deduction);
    const refunded = details.payment.refunds
      .filter((r: { type: string }) => r.type === 'DISPUTE_BALANCE')
      .reduce((sum: number, r: { amount: number }) => sum + r.amount, 0);
    expect(refunded).toBe(details.pricing.securityDepositAmount - deduction);
  });
}

test('admin cannot deduct more than the deposit', async ({ browser }) => {
  const { bookingId, title } = await returnedWithClaim(500);
  const deposit = (await renterApi().details(bookingId)).pricing.securityDepositAmount;
  const admin = await signedInPage(browser, users.admin.email, '/admin/disputes');
  await admin.getByRole('link', { name: new RegExp(title) }).click();
  await admin.getByLabel('Deduction from deposit (₹)').fill(String(deposit + 1));
  await admin.getByRole('button', { name: 'Resolve claim' }).click();
  // Either the form refuses it, or the confirm dialog's Resolve fails with an alert.
  const confirm = dialog(admin).getByRole('button', { name: 'Resolve' });
  if (await confirm.isVisible()) {
    await confirm.click();
  }
  await expect(admin.getByRole('alert')).toBeVisible();
  expect((await renterApi().details(bookingId)).status).toBe('DISPUTED');
});
```

**Before writing the assertions, confirm two rules** with the backend code ([AdminDisputeService.java](../../backend/src/main/java/com/marketplace/rental/AdminDisputeService.java)) or the business owner:
- With a ₹0 deduction, is the whole deposit recorded as one `DISPUTE_BALANCE` refund, or released through the normal deposit refund? Adjust the `refunds` filter to match.
- Can the admin deduct more than the claim (but within the deposit)? If yes, add that case; if no, add a case expecting it to be refused.

**Done when:** all dispute tests pass, and the full-claim case's refund equals deposit − claim.

---

## Task C8: Owner's bank and Aadhaar numbers never reach a renter

**File:** `e2e/tests/01-happy-path.spec.ts` (add a step) and `e2e/tests/07-role-checks.spec.ts`

The happy path creates an owner with known numbers (Aadhaar `2341 2341 2346`, account `1234567890`, PAN `ABCDE1234F`). It is the right place to check that a renter never sees them. The dev-seeded owners have no real KYC numbers, so a separate test would have nothing to look for.

**Steps**
1. In 01-happy-path, after the renter opens the booking (step 5), add a privacy step.
2. Read the page's visible text and the API responses the renter can call (booking details, public listing).
3. None may contain the full Aadhaar (with or without spaces), the account number, or the PAN.

**Code** (inside 01-happy-path, once `renterPage` and `bookingId` exist):

```ts
  await test.step("renter never sees the owner's KYC numbers", async () => {
    const secrets = ['234123412346', '2341 2341 2346', '1234567890', 'ABCDE1234F'];
    const renterApi = Api.as(users.renter.email);
    const sources = {
      'booking page': await renterPage.locator('body').innerText(),
      'booking API': JSON.stringify(await renterApi.details(bookingId)),
      'listing API': JSON.stringify(await renterApi.get(`/api/v1/equipment/${listing.id}`))
    };
    for (const [where, text] of Object.entries(sources)) {
      for (const secret of secrets) {
        expect(text, `${secret} found in ${where}`).not.toContain(secret);
      }
    }
  });
```

**Add to 07-role-checks:** a renter calling owner-only KYC endpoints gets 403. Find them with:

```cmd
findstr /s /n "RequestMapping" backend\src\main\java\com\marketplace\owner\*.java
```

Then for each owner or admin KYC URL: `await expect(renterApi().get('<url>')).rejects.toThrow(/HTTP 403/)`.

**Done when:** 01-happy-path passes with the new step, and 07-role-checks has a 403 check for each KYC endpoint.

---

## Phase A: Baseline

1. **Run the full suite** from `e2e\` with the frontend, backend, Keycloak, MinIO and Mailpit running:
   ```cmd
   npm test
   ```
2. Note the total time printed at the end (for example `22 passed (14.2m)`) in the plan's checklist, item A2.
3. If anything fails, open `npm run report`, read the trace, and fix it **before** adding new tests. A red baseline hides new failures.
4. **Agree the layout** (plan, Step 3) in a short team call. New files in this guide use flat numbering (`13-…`, `14-…`) to match today's folder; moving to sub-folders happens in Phase B.
5. **Commit** the plan, this guide and the `env.ts` admin-password fix:
   ```cmd
   git add docs/testing/playwright-automation-plan.md docs/testing/playwright-pending-tasks-guide.md e2e/support/env.ts
   git commit -m "Add Playwright automation plan and pending-tasks guide"
   ```

---

## Phase B: Speed and structure

### B1. Save each role's sign-in

1. Create `e2e/tests/auth.setup.ts` with the code in the plan, Step 4.1.
2. In `playwright.config.ts`, add the `setup` project and make `chromium` depend on it (plan, Step 4.2).
3. Add `.auth/` to `e2e/.gitignore`.
4. Run `npx playwright test --project=setup` and check that `e2e/.auth/` has four JSON files.
5. **Prove the shortcut works:** in a scratch test, open a context with `storageState: '.auth/renter.json'`, go to `/`, click **Sign In**, and check that **Sign Out** appears **without** the Keycloak form. If the form appears, the Keycloak session cookie isn't being kept; stop and use plan option "inject `sessionStorage` with `addInitScript`" instead.

### B2. Switch tests to the saved sign-in

1. Add `signedInAs(browser, role, path)` to `support/ui.ts` (plan, Step 4.3).
2. Replace `signedInPage(browser, users.X.email, …)` with `signedInAs(browser, 'X', …)` in every spec **except** `register`, `signInAgain` and the signed-out redirect test.
3. Run the suite and compare the time with the baseline.

### B3. First page object: booking details

1. Create `e2e/pages/booking-details.page.ts` (plan, Step 5.1).
2. Move `openBooking`, `expectStatus`, `generateCode`, `uploadInspectionPhotos`, `confirmInspection` and `cancelFromBookingPage` from `support/ui.ts` into it as methods.
3. Create `e2e/support/test.ts` exposing it as a fixture (plan, Step 5.2).
4. Update the specs that used those helpers to `import { test, expect } from '../support/test'` and use `bookingPage`.
5. Run the suite.

### B4. Remaining page objects

One per route, in this order (most used first): owner bookings, owner listings, booking request, admin owners, admin listings, admin disputes, catalogue, equipment details, profile, owner onboarding, booking history, metrics. Convert a spec to page objects whenever you next edit it; don't do a big-bang rewrite.

### B5. Locator review

```cmd
findstr /s /n /r "locator('\. locator(\"\. xpath= nth(" e2e\tests\*.ts
```

For each hit, replace it with a role, label or scoped locator (plan, Step 7). Leave a comment where a CSS locator is truly needed (for example `.photo-grid img`).

---

## Phase D: CI

### D1. Tags and scripts

1. Add `{ tag: '@smoke' }` to one test per critical flow:

   | Spec | Test |
   |---|---|
   | 01-happy-path | sign-up to closed booking (also tag `@slow`) |
   | 03-payment-retry | failed payment retried |
   | 04-renter-cancels | one tier only |
   | 07-role-checks | renter cannot open owner or admin pages |
   | 13-expiry | request expires |
   | 14-double-booking | overlapping dates refused |
   | 16-amounts | quoted total is what is paid |

   ```ts
   test('a renter cannot open owner or admin pages', { tag: '@smoke' }, async ({ browser }) => { … });
   ```
2. Add to `e2e/package.json` scripts:
   ```json
   "test:smoke": "playwright test --grep @smoke",
   "test:regression": "playwright test --grep-invert @smoke"
   ```
3. Run `npm run test:smoke`. It should finish in under 5 minutes.

### D2. CI-friendly config

In `playwright.config.ts`:

```ts
forbidOnly: !!process.env.CI,
retries: process.env.CI ? 1 : 0,
reporter: process.env.CI
  ? [['github'], ['html', { open: 'never' }], ['junit', { outputFile: 'results.xml' }]]
  : [['list'], ['html', { open: 'never' }]],
```

### D3. Check the realm import has what the tests need

Open [infrastructure/keycloak/marketplace-realm.json](../../infrastructure/keycloak/marketplace-realm.json) and confirm:
1. Client `marketplace-frontend`: `"directAccessGrantsEnabled": true` and `http://localhost:4200/*` in `redirectUris`.
2. Roles `ROLE_RENTER`, `ROLE_OWNER`, `ROLE_ADMIN` exist.
3. Self-registration is on (`"registrationAllowed": true`): 01-happy-path signs up a new user.
4. The backend's service-account client exists (used to grant `ROLE_OWNER` on KYC approval).

Your local Keycloak was imported from this file, and the suite passes locally, so these are probably already right. Check anyway, because CI starts from the file alone.

### D4. Add the `e2e` job

1. Copy the job outline from the plan, Step 13.2, into [.github/workflows/ci.yml](../../.github/workflows/ci.yml).
2. Check which environment variables the backend's `dev` profile reads for the database, Keycloak, MinIO and mail in [application-dev.yml](../../backend/src/main/resources/application-dev.yml), and set them in the "Start backend" step so they point at the CI service containers.
3. Use `--grep @smoke` on pull requests and the full suite on `main`:
   ```yaml
   run: npx playwright test ${{ github.event_name == 'pull_request' && '--grep @smoke' || '' }}
   ```
4. Push to a branch and open a draft pull request. Iterate until the job is green.
5. Add a nightly `schedule:` trigger (for example `cron: '30 20 * * *'` = 02:00 IST) that runs all projects.

### D5. Flaky-test policy

1. Create a GitHub label `flaky-test`.
2. Add the policy from the plan, Step 14, to the team's working agreement.
3. Check the HTML report's **Flaky** filter after each CI run for the first two weeks.

---

## Phase E: Scale

### E1. Per-worker users

1. In `env.ts`, add `usersFor(workerIndex)` returning `e2e-renter-w<N>@example.com` and so on (plan, Step 10.1). Keep `admin` shared.
2. Create a worker-scoped fixture in `support/test.ts` that, once per worker, calls `KeycloakAdmin.ensureUser` for those users and `seedOwnerListing()` for that worker's owner.
3. Change `fixtures.ts` (`renterApi`, `ownerApi`, `bookingIn`) to take the worker's users instead of the fixed ones.
4. Scope every list assertion to the test's own item (`locator('article', { hasText: title })`). Search for `toHaveCount(` on lists that aren't scoped.
5. Wrap tests that need the whole database (`runScheduler` checks across bookings, admin metrics) in `test.describe.configure({ mode: 'serial' })`.
6. Set `fullyParallel: true, workers: 2`, run 5 times with `--repeat-each=5`, fix failures, then raise to 4.

### E2. More browsers

1. `npx playwright install` (all browsers).
2. Add the `firefox`, `webkit` and `mobile` projects from the plan, Step 11, each with `grep: /@smoke/`.
3. Run `npx playwright test --project=webkit`. Expect a few date-input differences on WebKit; fix them in the page objects, not in each test.

---

## Phase F: Quality extras

### F1. Accessibility

1. `npm i -D @axe-core/playwright`
2. Create `e2e/tests/17-accessibility.spec.ts` with one test per main page (catalogue, details, booking request, booking page, owner listings, owner bookings, admin queues), tagged `@a11y`, using the code in the plan, Step 12.1.
3. First run: list the violations, fix the serious ones in the frontend, then make the test strict.

### F2. Remaining coverage (P2 and P3)

| Test | File | Key steps |
|---|---|---|
| Booking history | `18-booking-history.spec.ts` | Create one booking in each of requested, paid, cancelled; open `/bookings`; each appears with the right status; clicking opens it |
| Catalogue filters | extend `09-buttons.spec.ts` | Filter by category and city; only matching cards; empty search shows the empty message |
| Owner onboarding validation | extend `11-owner-reapply.spec.ts` | Bad PAN (`ABC123`), bad Aadhaar checksum (`1234 5678 9012`), bad IFSC (`HDFC123`): each shows an error and nothing is submitted |
| Listing form validation | extend `09-buttons.spec.ts` | Save draft with no title or rate: errors shown, no draft created |
| Profile phone validation | extend `10-profile.spec.ts` | Letters or too few digits are refused |
| Unknown listing | `19-not-found.spec.ts` | `/equipment/00000000-0000-0000-0000-000000000000` shows a not-found message, not a blank page |
| Server error | `19-not-found.spec.ts` | `page.route('**/api/v1/equipment*', r => r.fulfill({ status: 500 }))`, then the catalogue shows a friendly error |
| Session expiry | `20-session.spec.ts` | Sign in, clear `sessionStorage`, open a protected page: user is sent to sign in and returns to the same page |
| Reviews | `21-reviews.spec.ts` | Use `bookingIn('paid')` + API handover/return/approve to reach the review step; each side reviews once; second review is refused; rating shows on the listing |
| All email templates | extend `08-emails.spec.ts` | Add declined, cancelled (both sides), expired, dispute raised and dispute resolved emails |

### F3. Definition of done

1. Create `.github/pull_request_template.md` with the checklist from the plan, Step 15.
2. Update [manual-smoke-tests.md](manual-smoke-tests.md) section 14 with the new test count.

---

## Final checklist

**Task 0**
- [ ] 0.1 `bookingIn` returns `equipmentId`
- [ ] 0.2 `outsiderApi` added
- [ ] 0.3 `localDateTime` and `hoursFromNow` in `support/ui.ts`
- [ ] 0.4 `otherOwner` user created and seeded in global setup

**Phase C (P1)**
- [ ] C1 Request expiry test
- [ ] C2 Payment-window expiry tests (pending and failed)
- [ ] C3 Double booking: UI and race
- [ ] C4 Booking form validation
- [ ] C5 Quote, payment and recorded amount match
- [ ] C6 Owner sees only own bookings
- [ ] C7 Dispute outcomes: rejected, full, over-deposit
- [ ] C8 KYC numbers never reach a renter; renter gets 403 on KYC endpoints

**Phase A**
- [ ] Full suite green; baseline time recorded
- [ ] Layout agreed
- [ ] Plan, guide and `env.ts` fix committed

**Phase B**
- [ ] Saved sign-in per role, verified
- [ ] Specs use `signedInAs`
- [ ] Booking page object and fixture
- [ ] Remaining page objects
- [ ] Locator review done

**Phase D**
- [ ] Tags and smoke/regression scripts
- [ ] CI config (`forbidOnly`, retries, reporters)
- [ ] Realm import checked
- [ ] `e2e` job green on a pull request; nightly run added
- [ ] Flaky-test label and policy

**Phase E**
- [ ] Per-worker users; stable at 4 workers
- [ ] Firefox, WebKit, mobile smoke

**Phase F**
- [ ] Accessibility tests
- [ ] P2/P3 coverage table done
- [ ] PR template and smoke-test doc updated
