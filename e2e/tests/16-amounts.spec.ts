import { BookingDetailsPage } from '../pages/booking-details.page';
import { BookingRequestPage } from '../pages/booking-request.page';
import { users } from '../support/env';
import { freshListing, ownerApi, renterApi } from '../support/fixtures';
import { expect, test } from '../support/test';
import { dialog, hoursFromNow, signedInPage } from '../support/ui';

/** "₹4,512.00" → 4512. The quote and the booking page use different currency pipes, so compare numbers. */
const rupees = (text: string | null) => Number((text ?? '').replace(/[^\d.]/g, ''));

test('the quoted total is what the renter pays', { tag: '@smoke' }, async ({ browser }) => {
  const { equipmentId } = await freshListing();
  const page = await signedInPage(browser, users.renter.email);
  const request = new BookingRequestPage(page);
  const booking = new BookingDetailsPage(page);

  await request.open(equipmentId);
  await request.request(hoursFromNow(72), 2);
  const quoted = await request.quotedTotal();
  expect(quoted).toBeGreaterThan(0);
  const bookingId = await request.openBooking();

  const pricing = (await renterApi().details(bookingId)).pricing;
  expect(Number(pricing.totalPayableAmount)).toBeCloseTo(quoted, 2);
  const parts = [pricing.baseRentalAmount, pricing.deliveryCharge, pricing.platformFeeAmount,
    pricing.taxAmount, pricing.securityDepositAmount].map(Number);
  expect(parts.reduce((sum, part) => sum + part, 0)).toBeCloseTo(Number(pricing.totalPayableAmount), 2);

  await ownerApi().accept(bookingId);
  await page.reload();
  await booking.payNow.click();
  const charged = rupees(await dialog(page).getByText(/^Amount:/).locator('strong').textContent());
  expect(charged).toBeCloseTo(quoted, 2);

  await dialog(page).getByRole('button', { name: 'Pay successfully' }).click();
  await expect(page.getByText('Payment received. Your booking is confirmed.')).toBeVisible();
  await booking.expectStatus('Confirmed');
  expect(Number((await renterApi().details(bookingId)).payment.amount)).toBeCloseTo(quoted, 2);
});
