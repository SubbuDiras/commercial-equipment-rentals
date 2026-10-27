import { expect, test } from '@playwright/test';
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
