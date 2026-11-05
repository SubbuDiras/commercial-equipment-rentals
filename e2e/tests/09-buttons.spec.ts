import { expect, Page, test } from '@playwright/test';
import { users } from '../support/env';
import { photos } from '../support/files';
import { bookingIn, freshListing, ownerApi, renterApi } from '../support/fixtures';
import { dialog, openBooking, signedInPage } from '../support/ui';

/**
 * Buttons the journey tests do not reach, and forms submitted incomplete: every submit button stays clickable and
 * says what is missing instead of silently doing nothing.
 */

test('renter: catalogue search, clear, category page, and an incomplete booking request', async ({ browser }) => {
  const page = await signedInPage(browser, users.renter.email, '/equipment');

  await page.getByLabel('City').fill('Hyderabad');
  await page.getByRole('button', { name: 'Search equipment' }).click();
  await expect(page.getByRole('heading', { name: /Search results/ })).toBeVisible();
  await expect(page.getByRole('region', { name: 'Browse by category' })).toHaveCount(0);
  for (const city of await page.locator('app-equipment-card').getByText(/day minimum$/).allTextContents()) {
    expect(city).toMatch(/^Hyderabad/i); // city search ignores case, and owners type it either way
  }
  await page.getByRole('button', { name: 'Clear' }).click();
  await expect(page.getByRole('heading', { name: /All equipment/ })).toBeVisible();

  const tile = page.getByRole('region', { name: 'Browse by category' }).locator('article').first();
  const category = (await tile.getByRole('heading').textContent())!.trim();
  await tile.getByRole('link', { name: /See all/ }).click();
  await expect(page.getByRole('heading', { level: 1, name: category })).toBeVisible();
  await expect(page.locator('app-equipment-card').first()).toBeVisible();

  const { equipmentId } = await freshListing();
  await page.goto(`/equipment/${equipmentId}/book`);
  await page.getByRole('button', { name: 'Request booking' }).click();
  await expect(page.getByRole('alert')).toHaveText('Please check the highlighted fields: Start, End.');
  await page.getByLabel('Start').fill('2030-01-05T09:00');
  await page.getByLabel('End').fill('2030-01-04T09:00');
  await page.getByRole('button', { name: 'Request booking' }).click();
  await expect(page.getByRole('alert')).toHaveText('The end must be later than the start.');
});

test('owner: listing form, specifications, edit, image, submit; admin rejects it with a reason', async ({ browser }) => {
  const title = `Button check breaker ${Date.now()}`;
  const owner = await signedInPage(browser, users.owner.email, '/owner/equipment');

  await owner.getByRole('button', { name: 'Save draft' }).click();
  await expect(owner.getByRole('alert')).toContainText('Please check the highlighted fields: Category, Listing title');
  await expect(owner.getByRole('alert')).toContainText('Daily rate (₹)');

  const specRows = owner.getByPlaceholder('Name');
  await owner.getByRole('button', { name: 'Add specification' }).click();
  await expect(specRows).toHaveCount(2);
  await owner.getByRole('button', { name: 'Remove specification' }).last().click();
  await expect(specRows).toHaveCount(1);

  await fillListing(owner, title);
  await owner.getByRole('button', { name: 'Save draft' }).click();
  await expect(owner.getByText('Draft saved')).toBeVisible();

  const card = owner.locator('section', { hasText: title });
  await card.getByRole('button', { name: 'Edit' }).click();
  await expect(owner.getByLabel('Listing title')).toHaveValue(title);
  await owner.getByRole('button', { name: 'Cancel editing' }).click();
  await expect(owner.getByRole('button', { name: 'Save draft' })).toBeVisible();

  await card.locator('input[type=file]').setInputFiles(photos(1));
  await expect(card.locator('img')).toHaveCount(1);
  await card.getByRole('button', { name: 'Submit for approval' }).click();
  await expect(owner.getByText('Listing submitted for admin approval.')).toBeVisible();

  const admin = await signedInPage(browser, users.admin.email, '/admin/listings');
  const pending = admin.locator('article', { hasText: title });
  await pending.getByRole('button', { name: 'Reject' }).click();
  await pending.getByRole('button', { name: 'Confirm rejection' }).click();
  await expect(pending.getByRole('alert')).toHaveText(/Please enter a reason/);
  await pending.getByLabel('Rejection reason').fill('Add a photo of the serial plate.');
  await pending.getByRole('button', { name: 'Confirm rejection' }).click();
  await expect(pending).toHaveCount(0);

  await owner.reload();
  await expect(owner.locator('section', { hasText: title })).toContainText('Rejected');
});

test('owner: photos chosen in the create form upload with the draft, and editing adds more', async ({ browser }) => {
  const title = `Photo form breaker ${Date.now()}`;
  const owner = await signedInPage(browser, users.owner.email, '/owner/equipment');

  await fillListing(owner, title);
  const picker = owner.locator('#listing-form input[type=file]');
  await picker.setInputFiles(photos(4, 'form'));
  // Each chosen photo opens the editor in turn: frame and rotate the first, keep the next two, skip the last.
  const editor = owner.getByRole('dialog', { name: 'Edit photo' });
  await expect(editor).toContainText('form-1.png · photo 1 of 4');
  await editor.getByLabel('Fill the frame').check();
  await editor.getByLabel('Zoom').fill('2');
  await editor.getByRole('button', { name: 'Rotate right' }).click();
  await editor.getByRole('button', { name: 'Save photo' }).click();
  await expect(editor).toContainText('photo 2 of 4');
  await editor.getByRole('button', { name: 'Use original' }).click();
  await editor.getByRole('button', { name: 'Use original' }).click();
  await expect(editor).toContainText('form-4.png · photo 4 of 4');
  await editor.getByRole('button', { name: 'Skip this photo' }).click();
  await expect(editor).toHaveCount(0);
  await expect(owner.getByRole('img', { name: /^New photo / })).toHaveCount(3);
  await expect(owner.getByRole('img', { name: 'New photo form-1.jpg' })).toBeVisible(); // the edited copy is a JPEG

  // A photo in the form can be framed again later, from the original.
  await owner.getByRole('button', { name: 'Edit photo form-2.png' }).click();
  await expect(editor).toContainText('form-2.png');
  await editor.getByRole('button', { name: 'Save photo' }).click();
  await expect(owner.getByRole('img', { name: 'New photo form-2.jpg' })).toBeVisible();

  await owner.getByRole('button', { name: 'Remove photo form-3.png' }).click();
  await expect(owner.getByRole('img', { name: /^New photo / })).toHaveCount(2);
  await owner.getByRole('button', { name: 'Save draft' }).click();
  await expect(owner.getByText('Draft saved. 2 photos uploaded.')).toBeVisible();
  await expect(owner.getByRole('img', { name: /^New photo / })).toHaveCount(0);

  const card = owner.locator('section', { hasText: title });
  await expect(card.locator('img')).toHaveCount(2);

  await card.getByRole('button', { name: 'Edit' }).click();
  await expect(owner.getByRole('img', { name: 'Saved photo' })).toHaveCount(2);
  await picker.setInputFiles(photos(1, 'extra'));
  await owner.getByRole('dialog', { name: 'Edit photo' }).getByRole('button', { name: 'Save photo' }).click();
  await owner.getByRole('button', { name: 'Save changes' }).click();
  await expect(owner.getByText('Listing updated. 1 photo uploaded.')).toBeVisible();
  await expect(card.locator('img')).toHaveCount(3);
});

test('owner: manages photos on a live listing from its page; renters only see the photos', async ({ browser }) => {
  const { equipmentId } = await freshListing(); // live, with one sample photo as its cover
  const owner = await signedInPage(browser, users.owner.email, `/equipment/${equipmentId}`);
  const panel = owner.getByRole('region', { name: 'Manage photos' });
  await expect(panel.getByRole('img')).toHaveCount(1);
  await expect(panel.getByText('Cover', { exact: true })).toBeVisible();

  await panel.locator('input[type=file]').setInputFiles(photos(1, 'live'));
  await owner.getByRole('dialog', { name: 'Edit photo' }).getByRole('button', { name: 'Save photo' }).click();
  await expect(panel.getByRole('status')).toHaveText('1 photo added.');
  await expect(panel.getByRole('img')).toHaveCount(2);

  await panel.getByRole('button', { name: 'Make photo 2 the cover' }).click();
  await expect(panel.getByRole('status')).toHaveText('Cover photo updated.');
  await expect(panel.getByRole('button', { name: /Make photo \d the cover/ })).toHaveCount(1);

  await panel.getByRole('button', { name: 'Delete photo 2' }).click();
  await dialog(owner).getByRole('button', { name: 'Delete photo' }).click();
  await expect(panel.getByRole('status')).toHaveText('Photo deleted.');
  await expect(panel.getByRole('img')).toHaveCount(1);
  await expect(panel.getByText('Cover', { exact: true })).toBeVisible();

  const renter = await signedInPage(browser, users.renter.email, `/equipment/${equipmentId}`);
  await expect(renter.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(renter.getByRole('region', { name: 'Manage photos' })).toHaveCount(0);
});

test('admin: KYC tabs, documents, payout ID check, metrics and disputes pages', async ({ browser }) => {
  const admin = await signedInPage(browser, users.admin.email, '/admin/owners');

  for (const name of ['Verified', 'Rejected', 'Waiting for review']) {
    await admin.getByRole('tab', { name }).click();
    await expect(admin.getByRole('tab', { name })).toHaveAttribute('aria-selected', 'true');
    await expect(admin.getByText('Loading owners…')).toHaveCount(0);
  }

  await admin.getByRole('tab', { name: 'Verified' }).click();
  const verified = admin.locator('article').first();
  await verified.getByRole('button', { name: 'View documents' }).click();
  await expect(verified.getByRole('button', { name: 'Hide documents' })).toBeVisible();
  await verified.getByRole('button', { name: 'Hide documents' }).click();
  await expect(verified.getByRole('button', { name: 'View documents' })).toBeVisible();
  // An invalid ID is explained and nothing is saved.
  await verified.getByLabel(/Razorpay linked account ID/).fill('not-an-id');
  await verified.getByRole('button', { name: 'Save' }).click();
  await expect(admin.getByRole('alert')).toContainText('Enter a Razorpay linked account ID');

  await admin.goto('/admin');
  await admin.getByRole('button', { name: 'Refresh' }).click();
  await expect(admin.locator('.alert-error')).toHaveCount(0);

  await admin.goto('/admin/disputes');
  await expect(admin.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(admin.locator('.alert-error')).toHaveCount(0);
});

test('booking page: incomplete claim, inspection and review are explained', async ({ browser }) => {
  const renter = renterApi();
  const owner = ownerApi();

  // A rental run to its return, for the damage-claim form.
  const returned = (await bookingIn('paid')).bookingId;
  await renter.shiftTime(returned, -73);
  await owner.completeInspection(returned, 'HANDOVER', renter);
  await renter.shiftTime(returned, -49);
  await owner.completeInspection(returned, 'RETURN', renter);

  const ownerPage = await signedInPage(browser, users.owner.email);
  await openBooking(ownerPage, returned);
  // The equipment's photo is shown; the renter has no profile photo, so their initials are.
  const equipmentPhoto = ownerPage.getByRole('img', { name: /^Photo of / });
  await expect.poll(() => equipmentPhoto.evaluate((image: HTMLImageElement) => image.naturalWidth)).toBeGreaterThan(0);
  await expect(ownerPage.getByRole('definition').filter({ hasText: `${users.renter.firstName} ${users.renter.lastName}` }))
    .toContainText(`${users.renter.firstName[0]}${users.renter.lastName[0]}`);
  await ownerPage.getByRole('button', { name: 'Raise damage claim' }).click();
  await ownerPage.getByRole('button', { name: 'Submit claim' }).click();
  await expect(ownerPage.getByRole('alert')).toHaveText('Please check the highlighted fields: Claim amount (₹), What happened.');
  await ownerPage.getByLabel('Claim amount (₹)').fill('999999');
  await ownerPage.getByLabel('What happened').fill('Testing the limit.');
  await ownerPage.getByRole('button', { name: 'Submit claim' }).click();
  await expect(ownerPage.getByRole('alert')).toContainText('cannot be more than the deposit');
  await ownerPage.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(ownerPage.getByRole('button', { name: 'Submit claim' })).toHaveCount(0);

  // A handover ready to confirm (both codes, four photos), submitted empty.
  const handover = (await bookingIn('paid')).bookingId;
  await renter.shiftTime(handover, -73);
  await owner.issueOtp(handover, 'HANDOVER');
  await renter.issueOtp(handover, 'HANDOVER');
  await owner.uploadInspectionPhotos(handover, 'HANDOVER');
  await openBooking(ownerPage, handover);
  await ownerPage.getByRole('button', { name: 'Confirm handover' }).click();
  await expect(ownerPage.locator('app-inspection-wizard').getByRole('alert'))
    .toHaveText("Please check the highlighted fields: Hour meter reading, Your code (6 digits), Renter's code (6 digits).");

  // The returned rental completed, then reviewed without choosing stars.
  await owner.approveCondition(returned);
  await renter.runScheduler();
  await renter.shiftTime(returned, -1);
  await renter.runScheduler();
  expect(await renter.status(returned)).toBe('COMPLETED');
  const renterPage = await signedInPage(browser, users.renter.email);
  await openBooking(renterPage, returned);
  await renterPage.getByRole('button', { name: 'Submit review' }).click();
  await expect(renterPage.locator('#review').getByRole('alert')).toHaveText('Choose a star rating from 1 to 5.');
  await expect(dialog(renterPage)).toHaveCount(0);
});

async function fillListing(page: Page, title: string): Promise<void> {
  await page.getByRole('combobox', { name: 'Category' }).selectOption({ index: 1 });
  await page.getByLabel('Listing title').fill(title);
  await page.getByLabel('Description').fill('Checks that every listing button works.');
  await page.getByLabel('Manufacturer', { exact: true }).fill('Bosch');
  await page.getByLabel('Model', { exact: true }).fill('GSH 11 E');
  await page.getByLabel('Daily rate (₹)').fill('1000');
  await page.getByLabel('Refundable deposit (₹)').fill('2000');
  await page.getByLabel('Address', { exact: true }).fill('Plot 1, HITEC City');
  await page.getByLabel('City', { exact: true }).fill('Hyderabad');
  await page.getByLabel('Postal code').fill('500081');
  await page.getByLabel('Latitude').fill('17.4474');
  await page.getByLabel('Longitude').fill('78.3762');
}
