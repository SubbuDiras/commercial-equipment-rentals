import { expect, test } from '@playwright/test';
import { uniqueEmail } from '../support/env';
import { KeycloakAdmin } from '../support/keycloak-admin';
import { register, signedInPage } from '../support/ui';

test('an account with the owner role but no owner details is told how to finish, not shown tools that fail', async ({ browser }) => {
  // Like the realm import's owner@example.com: ROLE_OWNER in Keycloak, but never applied in the marketplace.
  const email = uniqueEmail('role-only-owner');
  await (await KeycloakAdmin.connect()).ensureUser(
    { email, firstName: 'Rohan', lastName: 'Roleonly' }, ['ROLE_RENTER', 'ROLE_OWNER']);
  const page = await signedInPage(browser, email, '/owner/equipment');

  const notice = page.getByRole('status').filter({ hasText: 'Finish setting up your owner account' });
  await expect(notice).toContainText('business and payout details are not on file yet');
  await expect(page.getByRole('button', { name: 'Save draft' })).toHaveCount(0);

  await page.goto('/owner/bookings');
  await expect(page.getByRole('status').filter({ hasText: 'Finish setting up your owner account' })).toBeVisible();
  await expect(page.getByRole('tab')).toHaveCount(0);

  await page.goto('/profile');
  const owner = page.getByRole('region', { name: 'Equipment owner' });
  await expect(owner).toContainText('business and payout details are not on file yet');
  await expect(owner.getByRole('link', { name: 'Become an owner' })).toHaveCount(0);
  await owner.getByRole('link', { name: 'Complete owner details' }).click();
  await expect(page).toHaveURL(/\/owner\/onboarding$/);
  await expect(page.getByRole('button', { name: 'Submit for verification' })).toBeVisible();
});

test('a user opens their profile from the header, and edits their name and phone', async ({ browser }) => {
  const email = uniqueEmail('profile');
  const page = await register(browser, { email, firstName: 'Pia', lastName: 'Profile' });

  await page.getByRole('link', { name: 'My profile' }).click();
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: 'My profile' })).toBeVisible();
  const details = page.getByRole('region', { name: 'Personal details' });
  await expect(details).toContainText(email);
  await expect(details).toContainText('Not added');
  await expect(details).toContainText('Renter');
  await expect(page.getByRole('region', { name: 'Equipment owner' }).getByRole('link', { name: 'Become an owner' })).toBeVisible();
  await expect(page.getByRole('link', { name: /Change email or password/ }))
    .toHaveAttribute('href', /\/realms\/marketplace-realm\/account$/);

  // Incomplete or wrong details are explained, not silently refused.
  await page.getByRole('button', { name: 'Edit details' }).click();
  await page.getByLabel('First name').fill('');
  await page.getByLabel('Phone').fill('12ab');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(details.getByRole('alert')).toHaveText(
    'Please check the highlighted fields: First name, Phone (8 to 15 digits, e.g. +919876543210).');

  // Cancel leaves everything as it was.
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(details).toContainText('Pia Profile');

  const phone = `+9197${Date.now().toString().slice(-8)}`;
  await page.getByRole('button', { name: 'Edit details' }).click();
  await page.getByLabel('First name').fill('Priya');
  await page.getByLabel('Last name').fill('Shah');
  await page.getByLabel('Phone').fill(phone.replace(/(\d{5})$/, ' $1'));
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByRole('status').filter({ hasText: 'Your details are saved' })).toBeVisible();
  await expect(details).toContainText('Priya Shah');
  await expect(details).toContainText(phone);

  // The header picks up the new name, and it is still there after a reload.
  await expect(page.getByRole('link', { name: 'My profile' })).toContainText('Priya Shah');
  await page.reload();
  await expect(page.getByRole('region', { name: 'Personal details' })).toContainText('Priya Shah');
});
