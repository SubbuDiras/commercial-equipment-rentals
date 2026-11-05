import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, inr, renterApi } from '../support/fixtures';
import { dialog, expectStatus, openBooking, signedInPage } from '../support/ui';

/** One case per tier of the draft cancellation policy; the start time decides the tier. */
const TIERS = [
  { name: 'before payment', state: 'accepted', startInHours: 72, tier: 'NO_PAYMENT', text: 'No payment has been taken' },
  { name: 'more than 48 hours ahead', state: 'paid', startInHours: 72, tier: 'FULL_REFUND', text: 'More than 48 hours before the start' },
  { name: '24 to 48 hours ahead', state: 'paid', startInHours: 30, tier: 'PARTIAL_REFUND', text: '24–48 hours before the start' },
  { name: 'less than 24 hours ahead', state: 'paid', startInHours: 10, tier: 'NO_RENTAL_REFUND', text: 'Less than 24 hours before the start' }
] as const;

for (const tier of TIERS) {
  test(`renter cancels ${tier.name}`, { tag: tier.tier === 'FULL_REFUND' ? ['@smoke'] : [] }, async ({ browser }) => {
    const { bookingId } = await bookingIn(tier.state, tier.startInHours);
    const quote = await renterApi().get(`/api/v1/bookings/${bookingId}/cancellation-quote`);
    expect(quote.breakdown.policyTier).toBe(tier.tier);

    const page = await signedInPage(browser, users.renter.email);
    await openBooking(page, bookingId);
    await page.getByRole('button', { name: 'Cancel booking' }).click();
    const modal = dialog(page);
    await expect(modal.getByText(tier.text)).toBeVisible();
    if (tier.state === 'paid') {
      // The dialog shows the server's numbers, not its own arithmetic.
      await expect(modal.locator('dt:has-text("Total refund") + dd')).toHaveText(inr(quote.breakdown.totalRefund));
      await expect(modal.locator('dt:has-text("Deposit refund") + dd')).toHaveText(inr(quote.breakdown.depositRefund));
    }
    await modal.getByLabel('Reason for cancelling').fill('Site work postponed.');
    await modal.getByRole('button', { name: 'Cancel booking' }).click();

    await expect(page.getByText('The booking has been cancelled.')).toBeVisible();
    await expectStatus(page, 'Cancelled by renter');
    const details = await renterApi().details(bookingId);
    expect(details.cancellation.breakdown.policyTier).toBe(tier.tier);
    if (tier.state === 'paid') {
      expect(details.payment.refunds.map((refund: { type: string }) => refund.type)).toContain('CANCELLATION');
    }
  });
}
