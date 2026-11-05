import { Browser, expect, test } from '@playwright/test';
import { AdminDisputesPage } from '../pages/admin.pages';
import { Api } from '../support/api';
import { users } from '../support/env';
import { bookingIn, inr, ownerApi, renterApi } from '../support/fixtures';
import { dialog, expectStatus, openBooking, signedInPage } from '../support/ui';

test('owner raises a damage claim and an admin resolves it', async ({ browser }) => {
  const { bookingId, title } = await bookingIn('paid');
  const renter = renterApi();
  const owner = ownerApi();

  // Run the rental to its return through the API, using time travel.
  await renter.shiftTime(bookingId, -73);
  await owner.completeInspection(bookingId, 'HANDOVER', renter);
  await renter.shiftTime(bookingId, -49);
  await owner.completeInspection(bookingId, 'RETURN', renter);
  expect(await renter.status(bookingId)).toBe('RETURNED');

  // The owner raises the claim in the UI.
  const ownerPage = await signedInPage(browser, users.owner.email);
  await openBooking(ownerPage, bookingId);
  await ownerPage.getByRole('button', { name: 'Raise damage claim' }).click();
  await ownerPage.getByRole('combobox', { name: 'Category' }).selectOption('DAMAGE');
  await ownerPage.getByLabel('Claim amount (₹)').fill('500');
  await ownerPage.getByLabel('What happened').fill('Cracked handle housing noticed at return.');
  await ownerPage.getByRole('button', { name: 'Submit claim' }).click();
  await expect(ownerPage.getByText('Damage claim submitted.')).toBeVisible();
  await expectStatus(ownerPage, 'Disputed');

  // The admin reviews the evidence and decides.
  const adminPage = await signedInPage(browser, users.admin.email, '/admin/disputes');
  await adminPage.getByRole('link', { name: new RegExp(title) }).click();
  await expect(adminPage.getByText('Return evidence')).toBeVisible();
  await expect(adminPage.locator('.photo-grid img')).toHaveCount(8);
  await adminPage.getByLabel('Deduction from deposit (₹)').fill('400');
  await adminPage.getByLabel('Decision notes (shared with both parties)').fill('Photos support part of the claim.');
  await adminPage.getByRole('button', { name: 'Resolve claim' }).click();
  await dialog(adminPage).getByRole('button', { name: 'Resolve' }).click();
  await expect(adminPage.getByText('Claim resolved.')).toBeVisible();
  await expect(adminPage.locator('header app-status-badge')).toHaveText('Resolved');

  const details = await renter.details(bookingId);
  expect(details.status).toBe('INSPECTION_PASSED');
  expect(details.dispute.approvedDeductionAmount).toBe(400);
  const balance = details.payment.refunds.find((refund: { type: string }) => refund.type === 'DISPUTE_BALANCE');
  expect(balance.amount).toBe(details.pricing.securityDepositAmount - 400);

  const renterPage = await signedInPage(browser, users.renter.email);
  await openBooking(renterPage, bookingId);
  await expect(renterPage.locator('dt:has-text("Approved deduction") + dd')).toHaveText(inr(400));
});

/** A paid booking run to its return through the API, with the owner's claim raised. */
async function returnedWithClaim(claimAmount: number) {
  const { bookingId, title } = await bookingIn('paid');
  const renter = renterApi();
  const owner = ownerApi();
  await renter.shiftTime(bookingId, -73);
  await owner.completeInspection(bookingId, 'HANDOVER', renter);
  await renter.shiftTime(bookingId, -49);
  await owner.completeInspection(bookingId, 'RETURN', renter);
  await owner.raiseDispute(bookingId, claimAmount);
  return { bookingId, title };
}

async function adminDisputes(browser: Browser): Promise<AdminDisputesPage> {
  return new AdminDisputesPage(await signedInPage(browser, users.admin.email));
}

const disputeRefunds = (details: Record<string, any>) =>
  (details.payment.refunds as { type: string; amount: number }[]).filter(refund => refund.type === 'DISPUTE_BALANCE')
    .reduce((sum, refund) => sum + Number(refund.amount), 0);

test('admin rejects the claim: the whole deposit goes back to the renter', async ({ browser }) => {
  const { bookingId, title } = await returnedWithClaim(500);
  const disputes = await adminDisputes(browser);
  await disputes.openClaim(title);
  await disputes.resolve(0, 'E2E: claim rejected.');

  const details = await renterApi().details(bookingId);
  expect(Number(details.dispute.approvedDeductionAmount)).toBe(0);
  expect(disputeRefunds(details)).toBe(Number(details.pricing.securityDepositAmount));
});

test('admin approves the full claim: the renter gets the rest of the deposit', async ({ browser }) => {
  const { bookingId, title } = await returnedWithClaim(500);
  const deposit = Number((await renterApi().details(bookingId)).pricing.securityDepositAmount);
  const disputes = await adminDisputes(browser);
  await disputes.openClaim(title);
  await disputes.resolve(500, 'E2E: full claim approved.');

  const details = await renterApi().details(bookingId);
  expect(Number(details.dispute.approvedDeductionAmount)).toBe(500);
  expect(disputeRefunds(details)).toBe(deposit - 500);
});

test('admin cannot deduct more than the claim, in the form or through the API', async ({ browser }) => {
  const { bookingId, title } = await returnedWithClaim(300);

  const disputes = await adminDisputes(browser);
  await disputes.openClaim(title);
  await disputes.askToResolve(301, 'E2E: too much.');
  await expect(disputes.page.getByText('The deduction cannot be more than ₹300.')).toBeVisible();
  await expect(dialog(disputes.page)).toHaveCount(0);

  const disputeId = (await renterApi().details(bookingId)).dispute.disputeId;
  await expect(Api.as(users.admin.email).patch(`/api/v1/admin/disputes/${disputeId}/resolve`,
    { approvedDeductionAmount: 301, resolutionNotes: 'E2E: too much.' }))
    .rejects.toThrow(/HTTP 409.*cannot exceed the damage claim or security deposit/);
  expect(await renterApi().status(bookingId)).toBe('DISPUTED');
});
