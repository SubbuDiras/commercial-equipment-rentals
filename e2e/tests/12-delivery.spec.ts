import { expect, test } from '@playwright/test';
import { users } from '../support/env';
import { freshListing, ownerApi, renterApi } from '../support/fixtures';
import { mailpit } from '../support/mailpit';
import { expectStatus, openBooking, signedInPage } from '../support/ui';

/** The value a datetime-local input takes, in the browser's time zone. */
function localInput(date: Date): string {
  return new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

test('owner marks a delivery booking as out for delivery; the renter sees the driver and is emailed', async ({ browser }) => {
  const { equipmentId } = await freshListing();
  const bookingId = await renterApi().requestBooking(equipmentId, 72, 2, {
    siteContactName: 'Ravi Kumar', siteContactPhone: '+919876543210',
    addressLine: 'Plot 21, Sector 18, Udyog Vihar', city: 'Gurugram', postalCode: '122015'
  });
  await ownerApi().accept(bookingId);
  await renterApi().pay(bookingId);

  // The owner sees where to deliver, and records the driver when it leaves.
  const ownerPage = await signedInPage(browser, users.owner.email);
  await openBooking(ownerPage, bookingId);
  const delivery = ownerPage.getByRole('region', { name: 'Delivery' });
  await expect(delivery).toContainText('Ravi Kumar');
  await expect(delivery).toContainText('Plot 21, Sector 18, Udyog Vihar, Gurugram 122015');
  await delivery.getByRole('button', { name: 'Mark as out for delivery' }).click();
  await delivery.getByRole('button', { name: 'Send to renter' }).click();
  await expect(delivery.getByRole('alert')).toContainText('Driver name');

  const arrival = new Date(Date.now() + 70 * 3_600_000);
  arrival.setSeconds(0, 0);
  await delivery.getByLabel('Driver name').fill('Suresh Driver');
  await delivery.getByLabel('Driver mobile number').fill('+91 98123 45678');
  await delivery.getByLabel('Vehicle number').fill('ts 09 ab 1234');
  await delivery.getByLabel('Expected arrival').fill(localInput(arrival));
  await delivery.getByRole('button', { name: 'Send to renter' }).click();
  await expect(ownerPage.getByRole('status').filter({ hasText: 'Marked as out for delivery' })).toBeVisible();
  await expectStatus(ownerPage, 'Out for delivery');
  await expect(delivery).toContainText('TS09AB1234');
  await expect(delivery.getByRole('button', { name: 'Update delivery details' })).toBeVisible();

  // The renter sees who is coming and when, and gets an email with the same details.
  const renterPage = await signedInPage(browser, users.renter.email);
  await openBooking(renterPage, bookingId);
  await expectStatus(renterPage, 'Out for delivery');
  const renterDelivery = renterPage.getByRole('region', { name: 'Delivery' });
  await expect(renterDelivery).toContainText('Suresh Driver');
  await expect(renterDelivery.getByRole('link', { name: '+919812345678' })).toHaveAttribute('href', 'tel:+919812345678');
  await expect(renterDelivery.getByRole('button')).toHaveCount(0);
  const mail = await mailpit.waitFor(users.renter.email, 'is out for delivery');
  expect(mail.html).toContain('TS09AB1234');
  expect(mail.html).toContain('Suresh Driver');
});
