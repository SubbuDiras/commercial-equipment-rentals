import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { Api } from '../support/api';
import { bookingIn, ownerApi, renterApi } from '../support/fixtures';
import { signedInPage } from '../support/ui';

const NO_ACCESS = "You don't have access to this page";

test('a renter cannot open owner or admin pages', { tag: '@smoke' }, async ({ browser }) => {
  const page = await signedInPage(browser, users.renter.email);
  for (const path of ['/owner/bookings', '/owner/equipment', '/admin/owners', '/admin/listings', '/admin/disputes', '/admin']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: NO_ACCESS }), path).toBeVisible();
  }
  await expect(page.getByRole('link', { name: 'Owner KYC' })).toHaveCount(0);
});

test('an owner cannot open admin pages', async ({ browser }) => {
  const page = await signedInPage(browser, users.owner.email);
  for (const path of ['/admin/owners', '/admin/disputes']) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: NO_ACCESS }), path).toBeVisible();
  }
});

test("another renter cannot open someone else's booking, but an admin can", async ({ browser }) => {
  const { bookingId } = await bookingIn('requested');

  const outsider = await signedInPage(browser, users.outsider.email);
  await outsider.goto(`/bookings/${bookingId}`);
  await expect(outsider.getByRole('alert')).toBeVisible();
  await expect(outsider.getByText(/^Booking BKG-/)).toHaveCount(0);

  const admin = await signedInPage(browser, users.admin.email);
  await admin.goto(`/bookings/${bookingId}`);
  await expect(admin.getByText(/^Booking BKG-/)).toBeVisible();
});

test('signed-out visitors are sent to sign-in and come back to the page they asked for', async ({ page }) => {
  await page.goto('/bookings');
  await expect(page.locator('#username')).toBeVisible();
  await page.locator('#username').fill(users.renter.email);
  await page.locator('#password').fill(process.env.E2E_USER_PASSWORD ?? 'Password123!');
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(/\/bookings$/);
  await expect(page.getByRole('heading', { name: 'My bookings' })).toBeVisible();
});

test("an owner cannot see or accept bookings for another owner's equipment", async ({ browser }) => {
  const { equipmentId, title } = await Api.as(users.otherOwner.email).seedOwnerListing();
  const bookingId = await renterApi().requestBooking(equipmentId, 72);

  const page = await signedInPage(browser, users.owner.email, '/owner/bookings');
  await page.getByRole('tab', { name: 'All' }).click();
  await expect(page.getByText('Loading bookings…')).toHaveCount(0);
  await expect(page.locator('article', { hasText: title })).toHaveCount(0);

  await page.goto(`/bookings/${bookingId}`);
  await expect(page.getByRole('alert')).toBeVisible();
  await expect(page.getByText(/^Booking BKG-/)).toHaveCount(0);

  await expect(ownerApi().accept(bookingId)).rejects.toThrow(/HTTP (403|404)/);
  expect(await renterApi().status(bookingId)).toBe('REQUESTED');
});

test("a renter cannot reach KYC review or another owner's documents through the API", async () => {
  const { ownerProfileId } = await ownerApi().seedOwnerListing();
  const renter = renterApi();
  for (const path of ['/api/v1/admin/owners', `/api/v1/admin/owners/${ownerProfileId}/documents`, '/api/v1/admin/pilot-metrics']) {
    await expect(renter.get(path), path).rejects.toThrow(/HTTP 403/);
  }
  await expect(renter.get(`/api/v1/owner/onboarding/${ownerProfileId}/documents`)).rejects.toThrow(/HTTP (403|404)/);
  await expect(renter.patch(`/api/v1/admin/owners/${ownerProfileId}/approve`)).rejects.toThrow(/HTTP 403/);
});
