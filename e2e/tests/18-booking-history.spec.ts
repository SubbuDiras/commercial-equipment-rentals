import { BookingDetailsPage } from '../pages/booking-details.page';
import { BookingHistoryPage } from '../pages/booking-history.page';
import { users } from '../support/env';
import { bookingIn, renterApi } from '../support/fixtures';
import { expect, test } from '../support/test';
import { signedInPage } from '../support/ui';

test("my bookings lists the renter's bookings with their status and opens each one", async ({ browser }) => {
  const requested = await bookingIn('requested');
  const paid = await bookingIn('paid');
  const cancelled = await bookingIn('accepted');
  await renterApi().post(`/api/v1/bookings/${cancelled.bookingId}/cancel`, { reason: 'E2E: plans changed.' });

  const history = new BookingHistoryPage(await signedInPage(browser, users.renter.email));
  await history.open();
  // Newest first, so this run's bookings are on the first page.
  for (const [booking, label] of [[requested, 'Requested'], [paid, 'Confirmed'], [cancelled, 'Cancelled by renter']] as const) {
    await expect(history.card(booking.title), booking.title).toBeVisible();
    await expect(history.status(booking.title)).toHaveText(label);
  }

  await history.card(paid.title).click();
  await expect(history.page).toHaveURL(new RegExp(`/bookings/${paid.bookingId}$`));
  await new BookingDetailsPage(history.page).expectStatus('Confirmed');
});
