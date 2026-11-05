import { BookingDetailsPage } from '../pages/booking-details.page';
import { OwnerBookingsPage } from '../pages/owner-bookings.page';
import { users } from '../support/env';
import { bookingIn, renterApi } from '../support/fixtures';
import { expect, test } from '../support/test';
import { signedInPage } from '../support/ui';

test('owner declines a booking request with a reason', async ({ browser }) => {
  const { bookingId, title } = await bookingIn('requested');

  const ownerBookings = new OwnerBookingsPage(await signedInPage(browser, users.owner.email));
  await ownerBookings.open('Requests');
  await expect(ownerBookings.row(title).getByText('Respond by')).toBeVisible();
  await ownerBookings.decline(title, 'Machine is booked for servicing.');

  expect(await renterApi().status(bookingId)).toBe('OWNER_REJECTED');
  const booking = new BookingDetailsPage(await signedInPage(browser, users.renter.email));
  await booking.open(bookingId);
  await booking.expectStatus('Declined');
  await expect(booking.payNow).toHaveCount(0);
});
