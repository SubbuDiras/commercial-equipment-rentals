import AxeBuilder from '@axe-core/playwright';
import { expect, Locator, Page, test } from '@playwright/test';
import { users } from '../support/env';
import { bookingIn, freshListing } from '../support/fixtures';
import { signedInPage } from '../support/ui';

/**
 * Fails on serious or critical WCAG 2 A/AA violations, listing each with the elements it was found on. `ready` is a
 * heading that shows once the page has loaded, or a locator for pages whose loaded state is not a heading.
 */
async function expectAccessible(page: Page, ready: string | RegExp | Locator): Promise<void> {
  const loaded = typeof ready === 'string' || ready instanceof RegExp ? page.getByRole('heading', { name: ready }) : ready;
  await expect(loaded.first()).toBeVisible();
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa']).analyze();
  const serious = results.violations
    .filter(violation => violation.impact === 'serious' || violation.impact === 'critical')
    .map(violation => `${violation.id} (${violation.impact}): ${violation.help}\n`
      + violation.nodes.slice(0, 5).map(node => `    ${node.html.slice(0, 160)}\n      ${node.failureSummary?.split('\n').pop()}`).join('\n'));
  expect(serious, serious.join('\n')).toEqual([]);
}

test.describe('accessibility', { tag: '@a11y' }, () => {
  test('catalogue', async ({ page }) => {
    await page.goto('/equipment');
    await expectAccessible(page, 'Equipment catalogue');
  });

  test('equipment details', async ({ page }) => {
    const { equipmentId, title } = await freshListing();
    await page.goto(`/equipment/${equipmentId}`);
    await expectAccessible(page, title);
  });

  test('booking request form', async ({ browser }) => {
    const { equipmentId } = await freshListing();
    const page = await signedInPage(browser, users.renter.email, `/equipment/${equipmentId}/book`);
    await expectAccessible(page, 'Request a rental');
  });

  test('booking page', async ({ browser }) => {
    const { bookingId } = await bookingIn('accepted');
    const page = await signedInPage(browser, users.renter.email, `/bookings/${bookingId}`);
    await expectAccessible(page, page.getByText(/^Booking BKG-/));
  });

  test('my bookings', async ({ browser }) => {
    const page = await signedInPage(browser, users.renter.email, '/bookings');
    await expectAccessible(page, 'My bookings');
  });

  test('owner listings and bookings', async ({ browser }) => {
    const page = await signedInPage(browser, users.owner.email, '/owner/equipment');
    await expectAccessible(page, /^Your listings \(\d+\)$/);
    await page.goto('/owner/bookings');
    await expectAccessible(page, 'Bookings for my equipment');
  });

  test('admin queues', async ({ browser }) => {
    const page = await signedInPage(browser, users.admin.email, '/admin/owners');
    await expectAccessible(page, 'Owner KYC');
    await page.goto('/admin/listings');
    await expectAccessible(page, /listing/i);
    await page.goto('/admin/disputes');
    await expectAccessible(page, /claim/i);
  });
});
