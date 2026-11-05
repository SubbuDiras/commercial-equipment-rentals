# Playwright Automation Plan

**Status:** Draft for approval · **Date:** 2026-11-05
**Goal:** A Playwright suite the team trusts. It covers every flow the marketplace supports, runs in minutes, and runs on every pull request, so a broken flow is caught before merge instead of during a manual smoke test.

**Who this is for:** developers and testers on this repository. It assumes you can run the local stack described in [end-to-end-completion-plan.md](../requirements/end-to-end-completion-plan.md).

---

## Contents

1. [Where we are today](#1-where-we-are-today)
2. [How the suite works](#2-how-the-suite-works)
3. [Step 1: Set up a machine to run the suite](#step-1-set-up-a-machine-to-run-the-suite)
4. [Step 2: Run the existing suite and read the results](#step-2-run-the-existing-suite-and-read-the-results)
5. [Step 3: Agree the folder layout and naming rules](#step-3-agree-the-folder-layout-and-naming-rules)
6. [Step 4: Sign in once per role, not once per test](#step-4-sign-in-once-per-role-not-once-per-test)
7. [Step 5: Introduce page objects](#step-5-introduce-page-objects)
8. [Step 6: Make test data predictable](#step-6-make-test-data-predictable)
9. [Step 7: Follow one locator strategy](#step-7-follow-one-locator-strategy)
10. [Step 8: Fill the coverage gaps](#step-8-fill-the-coverage-gaps)
11. [Step 9: Tag tests into smoke and regression suites](#step-9-tag-tests-into-smoke-and-regression-suites)
12. [Step 10: Run tests in parallel](#step-10-run-tests-in-parallel)
13. [Step 11: Add more browsers and a phone-size viewport](#step-11-add-more-browsers-and-a-phone-size-viewport)
14. [Step 12: Add accessibility and visual checks](#step-12-add-accessibility-and-visual-checks)
15. [Step 13: Run the suite in GitHub Actions](#step-13-run-the-suite-in-github-actions)
16. [Step 14: Reporting and handling flaky tests](#step-14-reporting-and-handling-flaky-tests)
17. [Step 15: Keep the suite healthy](#step-15-keep-the-suite-healthy)
18. [Delivery phases and checklist](#delivery-phases-and-checklist)

---

## 1. Where we are today

A Playwright suite already exists in [e2e/](../../e2e/). This plan builds on it; nothing is thrown away.

| Area | Current state |
|---|---|
| Tooling | `@playwright/test` 1.64, TypeScript, Chromium only |
| Config | [e2e/playwright.config.ts](../../e2e/playwright.config.ts): 1 worker, no retries, 180 s per test, trace/screenshot/video kept on failure |
| Setup | [global-setup.ts](../../e2e/global-setup.ts) checks the app, backend and Mailpit are up, creates the four fixed Keycloak users, and makes the owner a verified owner |
| Helpers | [support/](../../e2e/support/): `api.ts` (REST shortcuts and dev tools), `ui.ts` (sign-in, booking page actions), `fixtures.ts` (bookings in a given state), `keycloak-admin.ts`, `mailpit.ts`, `files.ts` |
| Tests | 12 spec files: happy path, owner rejects, payment retry, renter cancels (per refund tier), owner cancels, dispute, role checks, emails, buttons, profile, owner re-applies, delivery |
| CI | **Not run.** [ci.yml](../../.github/workflows/ci.yml) runs backend and frontend unit tests only |

**What already works well**
- Tests drive the real Keycloak login page, real backend, real MinIO uploads and real emails (Mailpit).
- Slow setup is done through the API (`bookingIn('paid')`), and only the step under test goes through the UI.
- Time-based rules are tested without waiting, using `shiftTime` and `runScheduler` from the backend's dev tools.
- Locators mostly use roles and labels, which is the Playwright recommendation.

**What this plan fixes**

| Gap | Effect today | Fixed in |
|---|---|---|
| Every test signs in through the Keycloak form | Slow; most of the run time is logging in | Step 4 |
| Page actions are repeated in each spec | A UI change means editing many files | Step 5 |
| One worker, shared users | Suite cannot run in parallel | Step 10 |
| Some flows have no test (expired requests and holds, booking history, reviews on their own, form validation) | Regressions there go unnoticed | Step 8 |
| No tags | Cannot run a quick smoke subset | Step 9 |
| Not in CI | Breakages are found late, by hand | Step 13 |
| Chromium only, desktop only | Layout bugs on phones and Safari go unnoticed | Step 11 |

---

## 2. How the suite works

```
                 ┌────────────────────────────────────────────┐
                 │ Playwright test runner (e2e/)              │
                 │  global-setup → tests → global-teardown    │
                 └──────────┬───────────────────┬─────────────┘
                 browser    │                   │  direct HTTP (support/api.ts)
                            ▼                   ▼
 ┌─────────────────────────────┐   ┌──────────────────────────────────────┐
 │ Angular app  localhost:4200 │──►│ Spring Boot backend  localhost:8081  │
 │ (proxies /api to 8081)      │   │ profile "dev": /api/v1/dev/* tools   │
 └──────────────┬──────────────┘   └───┬──────────┬──────────┬────────────┘
                │ login redirect       │          │          │
                ▼                      ▼          ▼          ▼
 ┌─────────────────────────────┐   MySQL      MinIO      Mailpit
 │ Keycloak  localhost:8080    │   3306       9000       8025 (UI + API)
 │ realm marketplace-realm     │
 └─────────────────────────────┘
```

Two ways a test talks to the system:

1. **Through the browser**: the behaviour we are actually testing. Uses role/label locators and asserts what a user sees.
2. **Through the API** (`Api.as(email)`): setup and shortcuts. Gets a token from Keycloak with the password grant and calls the backend directly. Also calls the dev-only endpoints:

| Dev endpoint | Used for |
|---|---|
| `POST /api/v1/dev/seed/owner-listing` | Make a verified owner with an approved listing |
| `POST /api/v1/dev/bookings/{id}/shift-time?hours=N` | Move a booking's dates into the past to reach handover/return windows |
| `POST /api/v1/dev/scheduler/run` | Run the expiry and rental-state sweeps now, instead of waiting a minute |
| `POST /api/v1/dev/payments/{orderId}/simulate` | Fake Razorpay success or failure |
| `POST /api/v1/dev/seed/listing-photos` | Give test listings sample photos (teardown) |

> These endpoints exist only when the backend runs with the `dev` (or `test`) profile. `global-setup` fails with a clear message if they are missing.

---

## Step 1: Set up a machine to run the suite

**1.1 Start the services** (PowerShell, from the repository root):

```powershell
# MySQL80 runs as a Windows service on 3306 (database marketplace_dev).
# Keycloak runs in the keycloak-clean container on 8080.
docker start keycloak-clean
cd infrastructure; docker compose up -d minio minio-init mailpit; cd ..
```

> Never run a bare `docker compose up -d`. The Compose MySQL and Keycloak clash with MySQL80 and `keycloak-clean`.

**1.2 Start the backend and frontend** in two terminals:

```powershell
cd backend;  .\gradlew.bat bootRun --args='--spring.profiles.active=dev'
cd frontend; npm start
```

**1.3 Install the test tools** (once):

```powershell
cd e2e
npm ci
npx playwright install chromium   # later: npx playwright install (all browsers, Step 11)
```

**1.4 Check the environment variables.** Defaults in [support/env.ts](../../e2e/support/env.ts) match the local stack. Override only if your ports differ:

| Variable | Default |
|---|---|
| `E2E_APP_URL` | `http://localhost:4200` |
| `E2E_API_URL` | `http://localhost:8081` |
| `E2E_KEYCLOAK_URL` | `http://localhost:8080` |
| `E2E_MAILPIT_URL` | `http://localhost:8025` |
| `E2E_KEYCLOAK_ADMIN` / `_PASSWORD` | `admin` / `admin_password` (the docker-compose Keycloak) |
| `E2E_USER_PASSWORD` | `Password123!` |

**1.5 Install the VS Code extension** "Playwright Test for VSCode" (Microsoft). It lets you run one test from the gutter, pick locators by clicking, and record new steps.

**Done when:** `cd e2e; npm run typecheck` passes and the four checks in `global-setup` pass.

---

## Step 2: Run the existing suite and read the results

| Command (in `e2e/`) | What it does |
|---|---|
| `npm test` | Runs everything headless |
| `npm run test:headed` | Same, with the browser visible |
| `npm run test:ui` | Opens UI mode: pick tests, watch them, time-travel through steps |
| `npx playwright test tests/02-owner-rejects.spec.ts` | One file |
| `npx playwright test -g "owner declines"` | Tests whose title matches |
| `npx playwright test --debug` | Step through with the inspector |
| `npm run report` | Opens the HTML report from the last run |

**When a test fails:**
1. Open the report (`npm run report`) and click the failed test.
2. Open the **trace**. It shows every action, the DOM before and after, network calls and console logs.
3. Check the screenshot and video for what the user would have seen.
4. If the failure is a backend error, check the backend console for the request in the trace's Network tab.

**Done when:** the whole suite passes on your machine, and the run time is recorded as the baseline (used to measure Steps 4 and 10).

---

## Step 3: Agree the folder layout and naming rules

Target layout. New folders are marked ✚; existing files move only when they are next touched.

```
e2e/
├── playwright.config.ts
├── global-setup.ts / global-teardown.ts
├── .auth/                     ✚ saved sign-in state per role (git-ignored, Step 4)
├── support/
│   ├── env.ts                 URLs, passwords, fixed users
│   ├── api.ts                 REST client + dev tools
│   ├── keycloak-admin.ts      user and role management
│   ├── mailpit.ts             email checks
│   ├── files.ts               tiny test files (PDF, PNG, photos)
│   ├── fixtures.ts            data builders: freshListing(), bookingIn(state)
│   ├── test.ts                ✚ extended `test` with page objects and role pages (Step 5)
│   └── ui.ts                  shared UI actions (shrinks as page objects grow)
├── pages/                     ✚ page objects (Step 5)
│   ├── header.ts
│   ├── catalogue.page.ts
│   ├── equipment-details.page.ts
│   ├── booking-request.page.ts
│   ├── booking-details.page.ts
│   ├── booking-history.page.ts
│   ├── profile.page.ts
│   ├── owner/ onboarding.page.ts, listings.page.ts, owner-bookings.page.ts
│   └── admin/ owners.page.ts, listings.page.ts, disputes.page.ts, metrics.page.ts
└── tests/
    ├── auth.setup.ts          ✚ signs each role in once (Step 4)
    ├── journeys/              ✚ long flows across roles (01-happy-path, 06-dispute …)
    ├── renter/                ✚ catalogue, booking request, history, cancel, pay
    ├── owner/                 ✚ onboarding, listings, bookings, delivery
    ├── admin/                 ✚ KYC, listings, disputes, metrics
    ├── access/                ✚ role checks, signed-out redirects
    └── notifications/         ✚ emails
```

**Naming rules**
- File: `<feature>.spec.ts`, for example `booking-history.spec.ts`.
- Test title: a sentence describing behaviour from the user's side: `'renter sees a refund quote before cancelling'`, not `'test cancel 1'`.
- Group with `test.describe('<feature>')` when a file has more than three tests.
- Use `test.step('…')` inside long journeys so the report reads like the scenario.

**Done when:** the team agrees the layout, and new tests follow it.

---

## Step 4: Sign in once per role, not once per test

**Problem:** `signedInPage()` fills in the Keycloak form for every page in every test. That is the slowest part of the run.

**Approach:** a `setup` project signs each fixed user in once and saves the browser state to `e2e/.auth/<role>.json`. Tests start already signed in.

> **Note on this app:** `angular-oauth2-oidc` keeps its tokens in `sessionStorage`, which Playwright's `storageState` does **not** save. What it does save is Keycloak's SSO session cookie. So a test that loads saved state still has to click **Sign In** once, but Keycloak sends it straight back without showing the form. That is one fast redirect instead of filling in a form. Verify this in 4.4 before rolling it out.

**4.1 Add the setup test** `tests/auth.setup.ts`:

```ts
import { test as setup, expect } from '@playwright/test';
import { env, users } from '../support/env';

for (const [role, user] of Object.entries(users)) {
  setup(`sign in as ${role}`, async ({ page }) => {
    await page.goto('/');
    await page.locator('#btn-header-login').click();
    await page.locator('#username').fill(user.email);
    await page.locator('#password').fill(env.password);
    await page.locator('#kc-login').click();
    await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible();
    await page.context().storageState({ path: `.auth/${role}.json` });
  });
}
```

**4.2 Wire it into the config:**

```ts
projects: [
  { name: 'setup', testMatch: /auth\.setup\.ts/ },
  { name: 'chromium', use: { ...devices['Desktop Chrome'] }, dependencies: ['setup'] }
]
```

**4.3 Use the saved state** in a new `signedInAs(browser, role, path)` helper (replacing `signedInPage`):
1. `browser.newContext({ storageState: '.auth/<role>.json' })`
2. Go to `/`, click **Sign In**, wait for **Sign Out** (no form appears).
3. Go to `path`.

**4.4 Verify:** sign in with saved state and check the form does not appear; time the suite and compare with the Step 2 baseline.

**4.5 Keep these tests on the full form,** because the form is what they test: sign-up (`register`), `signInAgain` after a role change, and signed-out redirects in the role-check tests.

**4.6 Add `.auth/` to [e2e/.gitignore](../../e2e/.gitignore).**

**Done when:** only the tests in 4.5 fill in the Keycloak form, and the suite is noticeably faster.

---

## Step 5: Introduce page objects

**Why:** today a label such as `'Daily rate (₹)'` or a button such as `'Submit for verification'` is written in several specs. A page object puts each page's locators and actions in one place, so a UI change is one edit.

**Rules**
- One class per page or major component, in `e2e/pages/`.
- Expose **locators** as read-only properties and **user actions** as methods (`requestBooking(start, end)`).
- Assertions stay in the test, except a `expectLoaded()` method that waits for the page to be ready.
- No `waitForTimeout`; rely on Playwright's auto-waiting and web-first assertions.

**5.1 Example**: `pages/booking-details.page.ts`:

```ts
import { expect, Locator, Page } from '@playwright/test';

export class BookingDetailsPage {
  readonly status: Locator;
  readonly cancelButton: Locator;

  constructor(private readonly page: Page) {
    this.status = page.locator('header app-status-badge');
    this.cancelButton = page.getByRole('button', { name: 'Cancel booking' });
  }

  async open(bookingId: string) {
    await this.page.goto(`/bookings/${bookingId}`);
    await expect(this.page.getByText(/^Booking BKG-/)).toBeVisible();
  }

  async generateCode(): Promise<string> {
    await this.page.getByRole('button', { name: /Generate (my|a new) code/ }).click();
    const code = this.page.getByText(/Development only/).locator('strong');
    await expect(code).toHaveText(/^\d{6}$/);
    return (await code.textContent())!.trim();
  }
}
```

**5.2 Expose them through fixtures** in `support/test.ts`, so tests ask for what they need:

```ts
import { test as base } from '@playwright/test';
import { BookingDetailsPage } from '../pages/booking-details.page';

export const test = base.extend<{ bookingPage: BookingDetailsPage }>({
  bookingPage: async ({ page }, use) => use(new BookingDetailsPage(page))
});
export { expect } from '@playwright/test';
```

**5.3 Migrate gradually.** Move the helpers in [support/ui.ts](../../e2e/support/ui.ts) (`openBooking`, `expectStatus`, `generateCode`, `uploadInspectionPhotos`, `confirmInspection`, `cancelFromBookingPage`) into `BookingDetailsPage` first, because almost every spec uses them. Then convert other pages as their specs are next edited.

**Done when:** every page in the route table ([app.routes.ts](../../frontend/src/app/app.routes.ts)) has a page object, and no spec repeats a locator that a page object already has.

---

## Step 6: Make test data predictable

**Principles**
1. **Each test creates what it needs.** Use `freshListing()` and `bookingIn('requested' | 'accepted' | 'paid')` so tests never depend on each other or on run order.
2. **Unique names.** Titles include `Date.now()`; new users use `uniqueEmail(prefix)`. Two runs never collide.
3. **Set up through the API, test through the UI.** Only the step under test is clicked. This keeps tests fast and failures pointed.
4. **Control time, don't wait for it.** Use `shiftTime` and `runScheduler`. Never `waitForTimeout` for a scheduler.
5. **No reliance on manual-testing data.** The fixed `e2e-*` users are separate from the accounts people use by hand.

**6.1 Extend the data builders** in [support/fixtures.ts](../../e2e/support/fixtures.ts) so every state in `BookingStatus` can be reached in one call:

| Builder | Reaches |
|---|---|
| `bookingIn('requested' / 'accepted' / 'paid')` | exists |
| `bookingIn('in-progress')` ✚ | paid → shift time → handover done by API |
| `bookingIn('returned')` ✚ | in-progress → return done by API |
| `bookingIn('completed')` ✚ | returned → owner approves condition |
| `expiredRequest()` ✚ | requested → shift past hold → run scheduler |
| `pendingKycApplicant()` ✚ | new Keycloak user with a submitted owner application |
| `pendingListing()` ✚ | listing submitted for admin approval |

**6.2 Clean up.** Tests leave rows in `marketplace_dev`. That is acceptable locally (teardown already adds photos so the catalogue looks normal). In CI the database is created fresh for each run (Step 13), so no cleanup is needed there. If local data grows too large, add a dev endpoint `POST /api/v1/dev/cleanup/e2e` that deletes data owned by `e2e-*` users, and call it from `global-teardown`.

**Done when:** any single test can be run on its own (`-g "<title>"`) and passes.

---

## Step 7: Follow one locator strategy

Use the first option that works, in this order:

| Priority | Locator | Example |
|---|---|---|
| 1 | Role + accessible name | `getByRole('button', { name: 'Accept' })` |
| 2 | Label | `getByLabel('Daily rate (₹)')` |
| 3 | Placeholder / text | `getByPlaceholder('Name')`, `getByText('Draft saved')` |
| 4 | Scoped to a container | `page.locator('article', { hasText: title }).getByRole('button', { name: 'Approve' })` |
| 5 | Test id (last resort) | `getByTestId('booking-total')` |

**Rules**
- Don't use CSS classes, XPath, or `nth()` without a reason in a comment. Classes change with styling.
- If role or label can't find an element, it is often an accessibility bug in the app. Fix the markup (add a `<label>`, `aria-label`, or proper heading) rather than reaching for a test id.
- Add `data-testid` only for values with no accessible name, such as a computed total in a table cell. The frontend has none today; keep it that way unless needed.
- Use `{ exact: true }` when one label is a prefix of another (`'Model'` vs `'Model year'`).

**Done when:** a review of new specs finds no class or XPath selectors.

---

## Step 8: Fill the coverage gaps

The table maps every route and booking state to a test. ✅ covered · 🟡 partly · ❌ missing. Priorities: **P1** money, access or data correctness; **P2** core user flows; **P3** polish.

### 8.1 Pages

| Route | Status | What to add | Priority |
|---|---|---|---|
| `/equipment` (catalogue) | 🟡 | Filters, sort, empty results, paging if present | P2 |
| `/equipment/category/:id` | 🟡 | Only listings of that category are shown | P3 |
| `/equipment/:id` | ✅ | Unapproved or deleted listing shows a not-found message | P2 |
| `/equipment/:id/book` | 🟡 | Validation: end before start, past dates, dates that clash with another booking | **P1** |
| `/bookings` (history) | ❌ | Lists the renter's bookings with correct statuses; opens a booking | P2 |
| `/bookings/:id` | ✅ | — | — |
| `/profile` | ✅ | Invalid phone number is rejected | P3 |
| `/owner/onboarding` | ✅ | Invalid PAN, Aadhaar checksum and IFSC are rejected | P2 |
| `/owner/equipment` | ✅ | Required-field errors; owner can't edit an approved listing's locked fields (if that rule exists) | P2 |
| `/owner/bookings` | 🟡 | Tabs or filters by status; owner sees only their own equipment's bookings | **P1** |
| `/admin/owners` | ✅ | Reject KYC with a reason (renter side is in 11-owner-reapply) | — |
| `/admin/listings` | ✅ | — | — |
| `/admin/disputes`, `/admin/disputes/:id` | ✅ | Each resolution outcome changes payouts correctly | **P1** |
| `/admin` (metrics) | 🟡 | Numbers change after a booking is created and paid | P3 |
| `/unauthorized` | ✅ | — | — |

### 8.2 Booking states (`BookingStatus`)

| State | Status | What to add | Priority |
|---|---|---|---|
| `REQUESTED`, `PAYMENT_PENDING`, `PAYMENT_FAILED`, `CONFIRMED` | ✅ | — | — |
| `HANDOVER_PENDING`, `IN_PROGRESS`, `RETURN_PENDING`, `RETURNED` | ✅ | — | — |
| `INSPECTION_PASSED`, `COMPLETED`, `CLOSED` | ✅ | — | — |
| `DISPUTED` | ✅ | — | — |
| `CANCELLED_BY_RENTER`, `CANCELLED_BY_OWNER`, `OWNER_REJECTED` | ✅ | — | — |
| `REQUEST_EXPIRED` | ❌ | Owner doesn't answer in time → scheduler expires it → renter sees it and dates are free again | **P1** |
| `HOLD_EXPIRED` | ❌ | Renter doesn't pay in time → hold released → another renter can book the same dates | **P1** |

### 8.3 Cross-cutting

| Area | What to add | Priority |
|---|---|---|
| Double booking | Two renters request overlapping dates; only one can be confirmed | **P1** |
| Payment amounts | Total shown at request = amount on the payment dialog = amount in the email | **P1** |
| Privacy | Owner's full bank and Aadhaar numbers never appear in any renter-visible page or API response | **P1** |
| Session expiry | Token expires mid-session → user is asked to sign in again and returns to the same page | P2 |
| Error handling | Backend returns 500 (use `page.route` to fake it) → user sees a friendly message, not a blank page | P2 |
| Reviews | Each side can review once; ratings appear on the listing | P2 |
| Emails | Every email template is sent at least once in some test (extend [08-emails.spec.ts](../../e2e/tests/08-emails.spec.ts)) | P2 |

**How to write each new test:** follow the template below.

```ts
import { test, expect } from '../../support/test';
import { bookingIn } from '../../support/fixtures';

test.describe('renter cancels', () => {
  test('sees the refund quote before confirming @regression', async ({ renterPage, bookingPage }) => {
    // Arrange: build state through the API
    const { bookingId } = await bookingIn('paid', 24 * 10);

    // Act: only the behaviour under test goes through the UI
    await bookingPage.open(bookingId);
    await bookingPage.cancelButton.click();

    // Assert: what the user sees
    await expect(renterPage.getByRole('dialog').getByText('Total refund')).toBeVisible();
  });
});
```

**Done when:** every row marked P1 has a passing test; P2 within the next phase.

---

## Step 9: Tag tests into smoke and regression suites

Put tags in the test title (or `{ tag: '@smoke' }` in the test details):

| Tag | Meaning | Target run time |
|---|---|---|
| `@smoke` | One test per critical flow: sign-in, search, request, pay, handover, return, cancel, admin approve | under 5 min |
| `@regression` | Everything else | under 20 min |
| `@slow` | Long journeys (01-happy-path) | — |
| `@email` | Needs Mailpit | — |

Add scripts to [e2e/package.json](../../e2e/package.json):

```json
"test:smoke": "playwright test --grep @smoke",
"test:regression": "playwright test --grep-invert @smoke"
```

**Done when:** `npm run test:smoke` finishes in under 5 minutes locally.

---

## Step 10: Run tests in parallel

**Why the suite runs on one worker today:** all tests share the four fixed users. Two tests acting as `e2e-renter` at once can see each other's bookings and break assertions on lists.

**10.1 Give each worker its own users.** In `env.ts`, make the fixed users depend on the worker index:

```ts
export function usersFor(workerIndex: number) {
  return {
    renter: { email: `e2e-renter-w${workerIndex}@example.com`, roles: ['ROLE_RENTER'] /* … */ },
    owner:  { email: `e2e-owner-w${workerIndex}@example.com`,  roles: ['ROLE_RENTER', 'ROLE_OWNER'] /* … */ },
    // admin can stay shared if admin tests only act on data they created
  };
}
```

Use a **worker-scoped fixture** to create these users and seed the owner once per worker, and save a storage state per worker (Step 4).

**10.2 Find tests that still can't run in parallel**: tests that assert on global lists (admin queues, metrics). Either scope assertions to the item the test created (`locator('article', { hasText: title })`), or put those tests in a `test.describe.configure({ mode: 'serial' })` block.

**10.3 Turn it on** gradually: `workers: 2`, run the suite five times, fix anything flaky, then raise to 4.

```ts
fullyParallel: true,
workers: process.env.CI ? 2 : 4,
```

**Done when:** the suite passes five times in a row with 4 workers, and run time is under half the Step 2 baseline.

---

## Step 11: Add more browsers and a phone-size viewport

Add projects to the config. Run the full suite on Chromium, and only `@smoke` on the others to keep run time down:

```ts
projects: [
  { name: 'setup', testMatch: /auth\.setup\.ts/ },
  { name: 'chromium', use: { ...devices['Desktop Chrome'] }, dependencies: ['setup'] },
  { name: 'firefox',  use: { ...devices['Desktop Firefox'] }, dependencies: ['setup'], grep: /@smoke/ },
  { name: 'webkit',   use: { ...devices['Desktop Safari'] },  dependencies: ['setup'], grep: /@smoke/ },
  { name: 'mobile',   use: { ...devices['Pixel 7'] },          dependencies: ['setup'], grep: /@smoke/ }
]
```

On the phone project, also check the header menu opens and every page has no horizontal scroll.

**Done when:** smoke passes on all four projects.

---

## Step 12: Add accessibility and visual checks

**12.1 Accessibility** with `@axe-core/playwright`:

```powershell
cd e2e; npm i -D @axe-core/playwright
```

```ts
import AxeBuilder from '@axe-core/playwright';

test('catalogue has no serious accessibility issues @a11y', async ({ page }) => {
  await page.goto('/equipment');
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  expect(results.violations.filter(v => ['serious', 'critical'].includes(v.impact!))).toEqual([]);
});
```

Run it on each main page. Start by recording existing violations, fix them, then make the test strict.

**12.2 Visual snapshots** (optional, later): `await expect(page).toHaveScreenshot()` on a few stable pages (catalogue header, empty states). Mask dynamic content (dates, prices, photos) with the `mask` option. Generate baselines in CI's Linux container, not on Windows, or fonts will differ.

**Done when:** each main page has an axe check with no serious or critical violations.

---

## Step 13: Run the suite in GitHub Actions

Add an `e2e` job to [ci.yml](../../.github/workflows/ci.yml) that starts the whole stack in the runner.

**13.1 Services the job needs**

| Service | How in CI |
|---|---|
| MySQL 8 | `services: mysql` container, database `marketplace_dev` |
| Keycloak | `services: keycloak` (or `docker run`) importing [infrastructure/keycloak/marketplace-realm.json](../../infrastructure/keycloak/marketplace-realm.json) |
| MinIO | `services: minio`, then create the bucket (as `minio-init` does) |
| Mailpit | `services: mailpit` |
| Backend | `./gradlew bootRun` with profile `dev`, in the background |
| Frontend | `npm start` (or build and serve with a `/api` proxy), in the background |

**13.2 Job outline**

```yaml
  e2e:
    runs-on: ubuntu-latest
    needs: [backend, frontend]
    timeout-minutes: 45
    services:
      mysql:
        image: mysql:8.0
        env: { MYSQL_ROOT_PASSWORD: root, MYSQL_DATABASE: marketplace_dev }
        ports: ["3306:3306"]
        options: --health-cmd="mysqladmin ping" --health-interval=5s --health-retries=20
      mailpit:
        image: axllent/mailpit
        ports: ["1025:1025", "8025:8025"]
      minio:
        image: minio/minio   # pin an exact tag
        # start command and bucket creation as in infrastructure/docker-compose.yml
    steps:
      - uses: actions/checkout@v4
      - name: Start Keycloak with the realm import
        run: docker run -d --name keycloak -p 8080:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin
             -v ${{ github.workspace }}/infrastructure/keycloak:/opt/keycloak/data/import
             quay.io/keycloak/keycloak:26.5.4 start-dev --import-realm
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "21" }
      - uses: actions/setup-node@v4
        with: { node-version: "24" }
      - name: Start backend
        working-directory: backend
        run: (chmod +x gradlew && ./gradlew bootRun --args='--spring.profiles.active=dev' > ../backend.log 2>&1 &)
      - name: Start frontend
        working-directory: frontend
        run: (npm ci && npm start > ../frontend.log 2>&1 &)
      - name: Wait for the stack
        run: npx --yes wait-on -t 300000 http://localhost:8081/api/v1/health http://localhost:4200 http://localhost:8080/realms/marketplace-realm
      - name: Install Playwright
        working-directory: e2e
        run: npm ci && npx playwright install --with-deps chromium
      - name: Run e2e
        working-directory: e2e
        run: npx playwright test
        env: { CI: "true" }
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: playwright-report
          path: |
            e2e/playwright-report
            e2e/test-results
            backend.log
            frontend.log
          retention-days: 14
```

**13.3 Config changes for CI**

```ts
forbidOnly: !!process.env.CI,          // a stray test.only fails the build
retries: process.env.CI ? 1 : 0,       // one retry, and flaky tests are reported as such
reporter: process.env.CI
  ? [['github'], ['html', { open: 'never' }], ['junit', { outputFile: 'results.xml' }]]
  : [['list'], ['html', { open: 'never' }]],
```

**13.4 What runs when**

| Trigger | Suite |
|---|---|
| Every pull request | `@smoke` on Chromium |
| Push to `main` | Full suite on Chromium |
| Nightly (`schedule: cron`) | Full suite, all projects (Step 11), plus `@a11y` |

**13.5 Things to check while building the job**
- The backend's dev profile points at `localhost:3306`, `localhost:8080`, `localhost:9000`, `localhost:1025`. If not, override with environment variables in the backend step.
- The Keycloak realm import must include the `marketplace-frontend` client with `http://localhost:4200/*` as a redirect URI and direct-access grants enabled (the API helper uses the password grant).
- The backend's Keycloak service account (used to grant `ROLE_OWNER` on KYC approval) must exist in the import, or the happy path will take its manual fallback.

**Done when:** a pull request shows a green `e2e` check, and a failing test uploads a report you can open.

---

## Step 14: Reporting and handling flaky tests

**Reports**
- Locally: `npm run report`.
- CI: download the `playwright-report` artifact, unzip, run `npx playwright show-report <folder>`. Traces open at [trace.playwright.dev](https://trace.playwright.dev) too.
- The `github` reporter puts failures as annotations on the pull request's changed lines.

**Flaky test policy**
1. A test that passes only on retry is **flaky**. The HTML report lists them separately.
2. The person who notices opens an issue with the trace and labels it `flaky-test`.
3. If it can't be fixed within two working days, mark it `test.fixme()` with the issue link, so it stops blocking others. Never leave it silently retried.
4. Common causes in this suite, and fixes:

| Cause | Fix |
|---|---|
| Asserting on a list another test also changes | Scope the locator to the item this test created |
| Reading text before the API call finished | Use `expect(locator).toHaveText()`, not `textContent()` then compare |
| Scheduler not yet run | Call `runScheduler()` then poll with `expect.poll()` |
| Email not yet delivered | Poll Mailpit with `expect.poll()` (see [support/mailpit.ts](../../e2e/support/mailpit.ts)) |
| Date near midnight or a time-zone edge | Build dates with `setMinutes(0,0,0)` and fixed offsets, as 01-happy-path does |

**Done when:** the flaky count in CI stays at zero for two weeks.

---

## Step 15: Keep the suite healthy

**Definition of done for any frontend or booking-flow change**
- [ ] Existing e2e tests updated if labels, buttons or flows changed.
- [ ] New user-facing behaviour has at least one e2e test, or a note in the PR explaining why unit tests are enough.
- [ ] `npm run typecheck` and `npm run test:smoke` pass locally.

**Review checklist for e2e code**
- [ ] Setup through the API, behaviour through the UI.
- [ ] Locators follow Step 7.
- [ ] No `waitForTimeout`, no `test.only`, no hard-coded IDs from someone's database.
- [ ] Test can run alone and in any order.
- [ ] Title describes behaviour.

**Housekeeping**
- Upgrade `@playwright/test` monthly; run `npx playwright install` after.
- Every quarter, remove tests that duplicate each other and check run time against targets.
- Keep [manual-smoke-tests.md](manual-smoke-tests.md) section 14 in step with the test count.

---

## Delivery phases and checklist

| Phase | Steps | Outcome | Rough effort |
|---|---|---|---|
| **A. Baseline** | 1, 2, 3 | Everyone can run the suite; layout agreed; baseline time recorded | 1 day |
| **B. Speed and structure** | 4, 5, 7 | Sign-in once per role; booking page object; locator rules | 3–4 days |
| **C. Coverage** | 6, 8 (P1 rows) | Expired states, double booking, amounts, privacy tested | 4–5 days |
| **D. CI** | 9, 13, 14 | Smoke on every PR, full suite on `main`, reports uploaded | 2–3 days |
| **E. Scale** | 10, 11 | Parallel workers, Firefox/WebKit/mobile smoke | 3 days |
| **F. Quality extras** | 8 (P2/P3), 12, 15 | Accessibility checks, remaining coverage, team rules | ongoing |

Review after each phase before starting the next, the same as the completion plan.

**Master checklist**

- [ ] A1 Stack runs locally; `npm run typecheck` passes
- [ ] A2 Full suite passes; baseline run time recorded: ______ min
- [ ] A3 Folder layout and naming rules agreed
- [ ] B1 `auth.setup.ts` saves state per role; SSO-cookie sign-in verified
- [ ] B2 Specs use `signedInAs`; only sign-up/role-change/redirect tests fill in the form
- [ ] B3 `BookingDetailsPage` created; `ui.ts` booking helpers moved into it
- [ ] B4 Page objects for every route
- [ ] C1 Builders for `in-progress`, `returned`, `completed`, expired states
- [ ] C2 `REQUEST_EXPIRED` test
- [ ] C3 `HOLD_EXPIRED` test
- [ ] C4 Double-booking test
- [ ] C5 Amounts consistent across request, payment and email
- [ ] C6 Privacy: no full bank or Aadhaar numbers reach renters
- [ ] C7 Booking request validation; owner bookings scoped to owner; dispute payouts
- [ ] D1 Tags added; `test:smoke` under 5 minutes
- [ ] D2 `e2e` job in CI with realm import; smoke on PRs
- [ ] D3 Full suite on `main`; nightly all-browser run
- [ ] D4 Reports and logs uploaded on failure; flaky policy agreed
- [ ] E1 Per-worker users; suite stable at 4 workers
- [ ] E2 Firefox, WebKit and mobile smoke passing
- [ ] F1 Axe checks on main pages
- [ ] F2 P2 and P3 coverage rows done
- [ ] F3 Definition of done added to the PR template
