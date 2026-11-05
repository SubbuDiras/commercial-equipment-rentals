import { expect, Locator, Page } from '@playwright/test';

/** /bookings — the renter's bookings, newest first. */
export class BookingHistoryPage {
  constructor(readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/bookings');
    await expect(this.page.getByRole('heading', { name: 'My bookings' })).toBeVisible();
    await expect(this.page.getByText('Loading your booking history…')).toHaveCount(0);
  }

  /** The card for one booking, found by its unique equipment title. */
  card(title: string): Locator {
    return this.page.getByRole('link').filter({ hasText: title });
  }

  status(title: string): Locator {
    return this.card(title).locator('app-status-badge');
  }
}
