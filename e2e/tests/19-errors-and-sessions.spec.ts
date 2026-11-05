import { users } from '../support/env';
import { expect, test } from '../support/test';
import { signedInPage } from '../support/ui';

test('an unknown listing shows a clear message, not a blank page', async ({ page }) => {
  await page.goto('/equipment/00000000-0000-4000-8000-00000000dead');
  await expect(page.getByRole('alert')).toHaveText('This listing is unavailable or is no longer approved.');
});

test('when the server fails, the catalogue says so and offers no stale results', async ({ page }) => {
  await page.route(/\/api\/v1\/equipment(\?|$)/, route => route.fulfill({
    status: 500, contentType: 'application/problem+json', body: JSON.stringify({ title: 'Internal Server Error', status: 500 })
  }));
  await page.goto('/equipment');
  await expect(page.getByRole('alert')).toHaveText('Equipment listings could not be loaded. Please try again.');
});

test('a user whose app session is lost is signed back in and stays on the page they asked for', async ({ browser }) => {
  const page = await signedInPage(browser, users.renter.email);
  // Drop the app's tokens, as when the tab's session ends; Keycloak's own session is still alive.
  await page.evaluate(() => sessionStorage.clear());
  await page.goto('/bookings');
  await expect(page).toHaveURL(/\/bookings$/);
  await expect(page.getByRole('heading', { name: 'My bookings' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible();
});
