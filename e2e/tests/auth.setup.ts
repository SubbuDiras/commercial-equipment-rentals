import { expect, test as setup } from '@playwright/test';
import { allFixedUsers, authFile, env } from '../support/env';

/**
 * Signs each fixed user (of every worker slot) in once and saves the browser state. The app keeps its tokens in
 * sessionStorage, which is not saved, but Keycloak's session cookie is: a test that loads this state signs in with one
 * redirect and no form.
 */
for (const user of allFixedUsers()) {
  setup(`sign in as ${user.email}`, async ({ page }) => {
    await page.goto('/');
    await page.locator('#btn-header-login').click();
    await page.locator('#username').fill(user.email);
    await page.locator('#password').fill(env.password);
    await page.locator('#kc-login').click();
    await expect(page.getByRole('button', { name: 'Sign Out' })).toBeVisible();
    await page.context().storageState({ path: authFile(user.email) });
  });
}
