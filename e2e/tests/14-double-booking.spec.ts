import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, freshListing, outsiderApi, renterApi } from '../support/fixtures';
import { dialog, hoursFromNow, requestInForm, signedInPage } from '../support/ui';

test('a second renter cannot book dates that overlap a held booking', { tag: '@smoke' }, async ({ browser }) => {
  const { equipmentId } = await bookingIn('requested', 72);
  const page = await signedInPage(browser, users.outsider.email, `/equipment/${equipmentId}/book`);

  await requestInForm(page, hoursFromNow(96), 2);
  await expect(page.getByRole('alert')).toContainText('already reserved for an overlapping period');
  await expect(dialog(page)).toHaveCount(0);

  // The first booking runs from 72 to 120 hours ahead; after that the dates are free.
  await requestInForm(page, hoursFromNow(144), 2);
  await expect(dialog(page).getByText('Request sent')).toBeVisible();
});

test('two requests for the same dates sent at the same moment: only one wins', async () => {
  const { equipmentId } = await freshListing();
  const results = await Promise.allSettled([
    renterApi().requestBooking(equipmentId, 72),
    outsiderApi().requestBooking(equipmentId, 72)
  ]);
  expect(results.filter(result => result.status === 'fulfilled')).toHaveLength(1);
  const failure = results.find(result => result.status === 'rejected') as PromiseRejectedResult;
  expect(String(failure.reason)).toMatch(/HTTP 409/);
});
