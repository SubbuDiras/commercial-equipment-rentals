import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, renterApi } from '../support/fixtures';
import { dialog, expectStatus, openBooking, signedInPage } from '../support/ui';

test('owner declines a booking request with a reason', async ({ browser }) => {
  const { bookingId, title } = await bookingIn('requested');

  const ownerPage = await signedInPage(browser, users.owner.email, '/owner/bookings');
  const request = ownerPage.locator('article', { hasText: title });
  await expect(request.getByText('Respond by')).toBeVisible();
  await request.getByRole('button', { name: 'Decline' }).click();
  await dialog(ownerPage).getByLabel('Reason (shown to the renter)').fill('Machine is booked for servicing.');
  await dialog(ownerPage).getByRole('button', { name: 'Decline' }).click();
  await expect(ownerPage.getByText(/Declined BKG-/)).toBeVisible();

  expect(await renterApi().status(bookingId)).toBe('OWNER_REJECTED');
  const renterPage = await signedInPage(browser, users.renter.email);
  await openBooking(renterPage, bookingId);
  await expectStatus(renterPage, 'Declined');
  await expect(renterPage.getByRole('button', { name: 'Pay now' })).toHaveCount(0);
});
