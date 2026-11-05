import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, outsiderApi, renterApi } from '../support/fixtures';
import { expectStatus, openBooking, signedInPage } from '../support/ui';

/**
 * Holds run out on the backend's clock: 4 hours for the owner to answer, 30 minutes for the renter to pay.
 * Shifting the booking back and running the scheduler stands in for waiting.
 */
test.describe('expiry', () => {
  test('a request the owner does not answer in 4 hours expires and frees the dates', { tag: '@smoke' }, async ({ browser }) => {
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

    // No money can be taken for an expired hold.
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
});
