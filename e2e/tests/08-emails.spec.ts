import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { adminApi, bookingIn, freshListing, ownerApi, renterApi } from '../support/fixtures';
import { mailpit } from '../support/mailpit';

/** Needs the backend running with NOTIFICATIONS_ENABLED=true and MAIL_HOST/MAIL_PORT pointing at Mailpit. */
test('booking emails reach Mailpit, and the handover code goes to the other party', { tag: '@email' }, async () => {
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

test('declined, cancelled and damage-claim emails reach the right people', { tag: '@email' }, async () => {
  const renter = renterApi();
  const owner = ownerApi();
  const reference = async (bookingId: string) => (await renter.details(bookingId)).bookingReference as string;

  const declined = await bookingIn('requested');
  await owner.patch(`/api/v1/owner/bookings/${declined.bookingId}/reject`, { reason: 'Booked for servicing.' });
  await mailpit.waitFor(users.renter.email, `Booking ${await reference(declined.bookingId)} was declined`);

  const cancelled = await bookingIn('paid');
  await renter.post(`/api/v1/bookings/${cancelled.bookingId}/cancel`, { reason: 'Site work postponed.' });
  const cancelledRef = await reference(cancelled.bookingId);
  await mailpit.waitFor(users.renter.email, `Booking ${cancelledRef} cancelled`);
  await mailpit.waitFor(users.owner.email, `Booking ${cancelledRef} cancelled`);

  const claimed = await bookingIn('paid');
  await renter.shiftTime(claimed.bookingId, -73);
  await owner.completeInspection(claimed.bookingId, 'HANDOVER', renter);
  await renter.shiftTime(claimed.bookingId, -49);
  await owner.completeInspection(claimed.bookingId, 'RETURN', renter);
  const dispute = await owner.raiseDispute(claimed.bookingId, 200);
  const claimedRef = await reference(claimed.bookingId);
  await mailpit.waitFor(users.renter.email, `Damage claim opened on booking ${claimedRef}`);
  await mailpit.waitFor(users.owner.email, `Damage claim opened on booking ${claimedRef}`);

  const disputeId = dispute.disputeId ?? (await renter.details(claimed.bookingId)).dispute.disputeId;
  await adminApi().patch(`/api/v1/admin/disputes/${disputeId}/resolve`,
    { approvedDeductionAmount: 100, resolutionNotes: 'E2E: half the claim.' });
  await mailpit.waitFor(users.renter.email, `Damage claim on booking ${claimedRef} resolved`);
  await mailpit.waitFor(users.owner.email, `Damage claim on booking ${claimedRef} resolved`);
});
