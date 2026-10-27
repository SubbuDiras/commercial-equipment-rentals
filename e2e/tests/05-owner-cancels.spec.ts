import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, inr, renterApi } from '../support/fixtures';
import { dialog, signedInPage } from '../support/ui';

test('owner cancels a paid booking: full refund and a penalty notice', async ({ browser }) => {
  const { bookingId, title } = await bookingIn('paid', 30);
  const paid = (await renterApi().details(bookingId)).pricing.totalPayableAmount as number;

  const page = await signedInPage(browser, users.owner.email, '/owner/bookings');
  await page.getByRole('tab', { name: 'Upcoming' }).click();
  await page.locator('article', { hasText: title }).getByRole('button', { name: 'Cancel' }).click();
  const modal = dialog(page);
  await expect(modal.getByText('Owner cancellation: the renter gets a full refund.')).toBeVisible();
  await expect(modal.getByText('recorded on your owner profile')).toBeVisible();
  await expect(modal.locator('dt:has-text("Total refund") + dd')).toHaveText(inr(paid));
  await expect(modal.locator('dt:has-text("Platform credit to renter") + dd')).toHaveText(inr(500));
  await modal.getByLabel('Reason for cancelling').fill('Machine failed its pre-hire check.');
  await modal.getByRole('button', { name: 'Cancel booking' }).click();
  await expect(page.getByText('Booking cancelled.')).toBeVisible();

  const details = await renterApi().details(bookingId);
  expect(details.status).toBe('CANCELLED_BY_OWNER');
  expect(details.cancellation.breakdown.totalRefund).toBe(paid);
});
