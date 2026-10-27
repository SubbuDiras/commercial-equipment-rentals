import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, renterApi } from '../support/fixtures';
import { dialog, expectStatus, openBooking, signedInPage } from '../support/ui';

test('a failed payment can be retried and then confirms the booking', async ({ browser }) => {
  const { bookingId } = await bookingIn('accepted');
  const page = await signedInPage(browser, users.renter.email);
  await openBooking(page, bookingId);
  await expectStatus(page, 'Awaiting payment');

  await page.getByRole('button', { name: 'Pay now' }).click();
  await expect(dialog(page).getByText('Fake Razorpay')).toBeVisible();
  await dialog(page).getByRole('button', { name: 'Fail payment' }).click();
  await expect(dialog(page).getByRole('alert')).toContainText('declined');
  await dialog(page).getByRole('button', { name: 'Close' }).click();
  await expectStatus(page, 'Payment failed');
  expect(await renterApi().status(bookingId)).toBe('PAYMENT_FAILED');

  await page.getByRole('button', { name: 'Pay now' }).click();
  await dialog(page).getByRole('button', { name: 'Pay successfully' }).click();
  await expect(page.getByText('Payment received. Your booking is confirmed.')).toBeVisible();
  await expectStatus(page, 'Confirmed');
});
