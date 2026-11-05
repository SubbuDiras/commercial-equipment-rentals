import { expect, Locator, Page } from '@playwright/test';

/** /profile — the signed-in user's own details. */
export class ProfilePage {
  readonly details: Locator;

  constructor(readonly page: Page) {
    this.details = page.getByRole('region', { name: 'Personal details' });
  }

  async open(): Promise<void> {
    await this.page.goto('/profile');
    await expect(this.page.getByRole('heading', { name: 'My profile' })).toBeVisible();
  }

  async edit(fields: { firstName?: string; lastName?: string; phone?: string }): Promise<void> {
    await this.page.getByRole('button', { name: 'Edit details' }).click();
    if (fields.firstName !== undefined) await this.page.getByLabel('First name').fill(fields.firstName);
    if (fields.lastName !== undefined) await this.page.getByLabel('Last name').fill(fields.lastName);
    if (fields.phone !== undefined) await this.page.getByLabel('Mobile number').fill(fields.phone);
    await this.page.getByRole('button', { name: 'Save changes' }).click();
  }
}
