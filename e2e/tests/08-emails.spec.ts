import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { freshListing, ownerApi, renterApi } from '../support/fixtures';
import { mailpit } from '../support/mailpit';

/** Needs the backend running with NOTIFICATIONS_ENABLED=true and MAIL_HOST/MAIL_PORT pointing at Mailpit. */
test('booking emails reach Mailpit, and the handover code goes to the other party', async () => {
  await mailpit.clear();
  const renter = renterApi();
  const owner = ownerApi();
  const { equipmentId, title } = await freshListing();

  const bookingId = await renter.requestBooking(equipmentId, 72);
  const reference = (await renter.details(bookingId)).bookingReference as string;
  const requested = await mailpit.waitFor(users.owner.email, `New booking request ${reference}`);
  expect(requested.subject).toContain(title);
  expect(requested.html).toContain(users.renter.firstName);

  await owner.accept(bookingId);
  await mailpit.waitFor(users.renter.email, `Booking ${reference} accepted`);

  await renter.pay(bookingId);
  await mailpit.waitFor(users.renter.email, `Payment received - booking ${reference}`);
  await mailpit.waitFor(users.owner.email, `Payment received - booking ${reference}`);

  await renter.shiftTime(bookingId, -73);
  const ownerCode = await owner.issueOtp(bookingId, 'HANDOVER');
  const codeMail = await mailpit.waitFor(users.renter.email, `Your handover code for booking ${reference}`);
  expect(codeMail.html).toContain(ownerCode);
  expect(codeMail.html).toContain(users.owner.firstName);
});
