import { expect, test } from '@playwright/test';
import { Api } from '../support/api';
import { uniqueEmail, users } from '../support/env';
import { tinyPdf, tinyPng } from '../support/files';
import { KeycloakAdmin } from '../support/keycloak-admin';
import {
  confirmInspection, dialog, expectStatus, generateCode, openBooking, register, signInAgain, signedInPage,
  uploadInspectionPhotos
} from '../support/ui';

/**
 * The whole marketplace through the UI: a new user signs up, becomes a verified owner, lists equipment; a renter
 * books and pays; both sides do handover and return; the owner approves; settlement completes; both review.
 */
test('sign-up to closed booking, through the UI', async ({ browser }) => {
  const owner = { email: uniqueEmail('e2e-new-owner'), firstName: 'Nina', lastName: 'Newowner' };
  const title = `E2E plate compactor ${Date.now()}`;

  // 1. Sign up and apply to become an owner (KYC + document).
  const ownerPage = await register(browser, owner);
  await ownerPage.getByRole('link', { name: 'Become an Owner' }).click();
  await ownerPage.getByLabel('First name').fill(owner.firstName);
  await ownerPage.getByLabel('Last name').fill(owner.lastName);
  await ownerPage.getByLabel('Mobile number').fill(`98${Date.now().toString().slice(-8)}`);
  await ownerPage.getByLabel('PAN').fill('ABCDE1234F');
  await ownerPage.getByLabel('Aadhaar number').fill('2341 2341 2346');
  await ownerPage.getByLabel('Account holder name').fill(`${owner.firstName} ${owner.lastName}`);
  await ownerPage.getByLabel('Bank name').fill('HDFC Bank');
  await ownerPage.getByLabel('Current').check();
  await ownerPage.getByLabel('Bank account number').fill('1234567890');
  await ownerPage.getByLabel('Confirm account number').fill('1234567890');
  await ownerPage.getByLabel('IFSC code').fill('HDFC0001234');
  // Missing documents are named; the files are chosen in the form and upload with it.
  await ownerPage.getByRole('button', { name: 'Submit for verification' }).click();
  await expect(ownerPage.getByText('Choose a file for: Aadhaar card, PAN card, Signature.')).toBeVisible();
  await ownerPage.getByLabel('Choose Aadhaar card file').setInputFiles({ name: 'aadhaar.pdf', mimeType: 'application/pdf', buffer: tinyPdf });
  await ownerPage.getByLabel('Choose PAN card file').setInputFiles({ name: 'pan.png', mimeType: 'image/png', buffer: tinyPng });
  await ownerPage.getByLabel('Choose Signature file').setInputFiles({ name: 'signature.png', mimeType: 'image/png', buffer: tinyPng });
  await ownerPage.getByRole('button', { name: 'Submit for verification' }).click();
  await expect(ownerPage.getByText('Under review')).toBeVisible();
  for (const name of ['aadhaar.pdf', 'pan.png', 'signature.png']) {
    await expect(ownerPage.getByText(name)).toBeVisible();
  }
  // Only the last 4 Aadhaar digits come back; the account number is masked for the owner.
  const kyc = ownerPage.getByRole('definition');
  await expect(ownerPage.getByText('XXXX XXXX 2346')).toBeVisible();
  await expect(kyc.filter({ hasText: '••••••7890 · HDFC0001234' })).toBeVisible();
  await expect(kyc.filter({ hasText: 'HDFC Bank · Current' })).toBeVisible();

  // 2. Admin approves the KYC and sets a fake payout account.
  const adminPage = await signedInPage(browser, users.admin.email, '/admin/owners');
  const application = adminPage.locator('article', { hasText: owner.email });
  await application.getByRole('button', { name: 'Approve' }).click();
  await dialog(adminPage).getByRole('button', { name: 'Approve' }).click();
  const notice = adminPage.locator('.alert[role=status]');
  await expect(notice).toContainText(/approved|could not be assigned/);
  if ((await notice.textContent())?.includes('could not be assigned')) {
    // The backend's Keycloak service account is not configured: assign the role as the admin would by hand.
    await (await KeycloakAdmin.connect()).grantRole(owner.email, 'ROLE_OWNER');
  }
  await adminPage.getByRole('tab', { name: 'Verified' }).click();
  const verified = adminPage.locator('article', { hasText: owner.email });
  await verified.getByRole('button', { name: 'Generate fake ID' }).click();
  await verified.getByRole('button', { name: 'Save' }).click();
  await expect(adminPage.getByText(/Payout account acc_FAKE/)).toBeVisible();

  // 3. The owner signs in again to pick up ROLE_OWNER, creates a listing and submits it.
  await signInAgain(ownerPage, owner.email);
  await ownerPage.getByRole('link', { name: 'My Listings' }).click();
  await ownerPage.getByRole('combobox', { name: 'Category' }).selectOption({ index: 1 });
  await ownerPage.getByLabel('Listing title').fill(title);
  await ownerPage.getByLabel('Description').fill('Forward plate compactor for sub-base and paving work.');
  await ownerPage.getByLabel('Manufacturer', { exact: true }).fill('Wacker Neuson');
  await ownerPage.getByLabel('Model', { exact: true }).fill('VP1550');
  await ownerPage.getByLabel('Daily rate (₹)').fill('1500');
  await ownerPage.getByLabel('Refundable deposit (₹)').fill('3000');
  await ownerPage.getByPlaceholder('Name').first().fill('Plate size');
  await ownerPage.getByPlaceholder('Value').first().fill('50 x 58 cm');
  await ownerPage.getByLabel('Address', { exact: true }).fill('Plot 7, IDA Jeedimetla');
  await ownerPage.getByLabel('City', { exact: true }).fill('Hyderabad');
  await ownerPage.getByLabel('Postal code').fill('500055');
  await ownerPage.getByLabel('Latitude').fill('17.5110');
  await ownerPage.getByLabel('Longitude').fill('78.4580');
  await ownerPage.getByRole('button', { name: 'Save draft' }).click();
  await expect(ownerPage.getByText('Draft saved')).toBeVisible();
  await ownerPage.locator('section', { hasText: title }).getByRole('button', { name: 'Submit for approval' }).click();
  await expect(ownerPage.getByText('Listing submitted for admin approval.')).toBeVisible();

  // 4. Admin approves the listing.
  await adminPage.goto('/admin/listings');
  await adminPage.locator('article', { hasText: title }).getByRole('button', { name: 'Approve' }).click();
  await expect(adminPage.locator('article', { hasText: title })).toHaveCount(0);

  // 5. The renter finds the listing and requests a two-day booking starting in three days.
  const ownerApi = Api.as(owner.email);
  const listing = (await ownerApi.get('/api/v1/owner/equipment') as { id: string; title: string }[])
    .find(candidate => candidate.title === title)!;
  const renterPage = await signedInPage(browser, users.renter.email, `/equipment/${listing.id}`);
  await expect(renterPage.getByText('Plate size')).toBeVisible();
  await renterPage.getByRole('link', { name: 'Add to booking', exact: true }).click();
  const start = new Date(Date.now() + 3 * 86_400_000);
  start.setMinutes(0, 0, 0);
  await renterPage.getByLabel('Start').fill(localDateTime(start));
  await renterPage.getByLabel('End').fill(localDateTime(new Date(start.getTime() + 2 * 86_400_000)));
  await renterPage.getByRole('button', { name: 'Request booking' }).click();
  await renterPage.getByRole('link', { name: 'Open booking' }).click();
  await expect(renterPage).toHaveURL(/\/bookings\/[\w-]+$/);
  const bookingId = renterPage.url().split('/').pop()!;
  await expectStatus(renterPage, 'Requested');

  // 6. The owner accepts; the renter pays with the fake Razorpay.
  await ownerPage.goto('/owner/bookings');
  await ownerPage.locator('article', { hasText: title }).getByRole('button', { name: 'Accept' }).click();
  await expect(ownerPage.getByText(/Accepted BKG-/)).toBeVisible();
  await renterPage.reload();
  await renterPage.getByRole('button', { name: 'Pay now' }).click();
  await dialog(renterPage).getByRole('button', { name: 'Pay successfully' }).click();
  await expect(renterPage.getByText('Payment received. Your booking is confirmed.')).toBeVisible();
  await expectStatus(renterPage, 'Confirmed');

  // 7. Time travel to the start, then hand over: each side generates a code, the renter adds photos, the owner confirms.
  const renterApi = Api.as(users.renter.email);
  await renterApi.shiftTime(bookingId, -73);
  await inspect('handover');
  await expectStatus(ownerPage, 'In progress');

  // 8. Time travel past the end and return the equipment the same way.
  await renterApi.shiftTime(bookingId, -49);
  await inspect('return');
  await expectStatus(ownerPage, 'Returned');

  // 9. The owner approves the condition; settlement runs (transfer held, then released) and the booking completes.
  await ownerPage.getByRole('button', { name: 'Approve condition' }).click();
  await dialog(ownerPage).getByRole('button', { name: 'Approve condition' }).click();
  await expectStatus(ownerPage, 'Inspection passed');
  await renterApi.runScheduler();
  await renterApi.shiftTime(bookingId, -1);
  await renterApi.runScheduler();
  await ownerPage.reload();
  await expectStatus(ownerPage, 'Completed');

  // 10. Both review; the second review closes the booking.
  await review(ownerPage, 5, 'Returned clean and on time.');
  await renterPage.reload();
  await review(renterPage, 4, 'Compacted well, a little noisy.');
  await expectStatus(renterPage, 'Closed');
  await renterPage.goto(`/equipment/${listing.id}`);
  await expect(renterPage.getByText('Compacted well, a little noisy.')).toBeVisible();
  await expect(renterPage.getByText('Returned clean and on time.')).toHaveCount(0);

  async function inspect(type: 'handover' | 'return'): Promise<void> {
    await openBooking(ownerPage, bookingId);
    await openBooking(renterPage, bookingId);
    const ownerCode = await generateCode(ownerPage);
    const renterCode = await generateCode(renterPage);
    await uploadInspectionPhotos(renterPage);
    await ownerPage.reload();
    await confirmInspection(ownerPage, ownerCode, renterCode, type);
    await expect(ownerPage.getByText(type === 'handover' ? 'Handover confirmed' : 'Return confirmed')).toBeVisible();
  }
});

async function review(page: import('@playwright/test').Page, stars: number, comments: string): Promise<void> {
  await page.getByRole('button', { name: `${stars} stars` }).click();
  await page.getByLabel('Comments (optional)').fill(comments);
  await page.getByRole('button', { name: 'Submit review' }).click();
  await expect(page.getByText('Thank you for your review.')).toBeVisible();
}

/** Value for an <input type="datetime-local">, in the browser's local time. */
function localDateTime(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
