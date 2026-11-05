import { expect, Locator, Page } from '@playwright/test';

type Tab = 'Requests' | 'Upcoming' | 'Active' | 'Past' | 'All';

/** /owner/bookings — requests and bookings for the owner's equipment, by tab. */
export class OwnerBookingsPage {
  constructor(readonly page: Page) {}

  async open(tab: Tab = 'Requests'): Promise<void> {
    await this.page.goto('/owner/bookings');
    await this.showTab(tab);
  }

  async showTab(tab: Tab): Promise<void> {
    await this.page.getByRole('tab', { name: tab }).click();
    await expect(this.page.getByText('Loading bookings…')).toHaveCount(0);
  }

  /** The card for one booking, found by its unique equipment title. */
  row(title: string): Locator {
    return this.page.locator('article', { hasText: title });
  }

  async accept(title: string): Promise<void> {
    await this.row(title).getByRole('button', { name: 'Accept' }).click();
    await expect(this.page.getByText(/Accepted BKG-/)).toBeVisible();
  }

  async decline(title: string, reason: string): Promise<void> {
    await this.row(title).getByRole('button', { name: 'Decline' }).click();
    const modal = this.page.getByRole('dialog');
    await modal.getByLabel('Reason (shown to the renter)').fill(reason);
    await modal.getByRole('button', { name: 'Decline' }).click();
    await expect(this.page.getByText(/Declined BKG-/)).toBeVisible();
  }
}
