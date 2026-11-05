import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { freshListing } from '../support/fixtures';
import { dialog, hoursFromNow, localDateTime, signedInPage } from '../support/ui';

test('the booking form explains bad dates and creates nothing', async ({ browser }) => {
  const { equipmentId } = await freshListing();
  const page = await signedInPage(browser, users.renter.email, `/equipment/${equipmentId}/book`);
  const alert = page.getByRole('alert');
  const submit = page.getByRole('button', { name: 'Request booking' });
  const start = hoursFromNow(72);
  const at = (from: Date, days: number) => localDateTime(new Date(from.getTime() + days * 86_400_000));

  await test.step('empty dates', async () => {
    await submit.click();
    await expect(alert).toContainText('Start');
    await expect(alert).toContainText('End');
  });

  await test.step('end before start, and end equal to start', async () => {
    await page.getByLabel('Start').fill(localDateTime(start));
    await page.getByLabel('End').fill(at(start, -1));
    await submit.click();
    await expect(alert).toHaveText('The end must be later than the start.');
    await page.getByLabel('End').fill(localDateTime(start));
    await submit.click();
    await expect(alert).toHaveText('The end must be later than the start.');
  });

  await test.step('start in the past (refused by the backend)', async () => {
    const past = hoursFromNow(-24);
    await page.getByLabel('Start').fill(localDateTime(past));
    await page.getByLabel('End').fill(at(past, 2));
    await submit.click();
    await expect(alert).toContainText('Rental start time must be in the future.');
  });

  await test.step('owner delivery without an address', async () => {
    await page.getByLabel('Start').fill(localDateTime(start));
    await page.getByLabel('End').fill(at(start, 2));
    await page.getByLabel('Collection / delivery').selectOption('OWNER_DELIVERY');
    await submit.click();
    await expect(alert).toHaveText('Complete all delivery contact and address fields.');
  });

  await expect(dialog(page)).toHaveCount(0);
});
