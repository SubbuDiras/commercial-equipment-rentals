import { Locator, Page } from '@playwright/test';
import { localDateTime } from '../support/ui';

/** /equipment/:id/book — the renter's request form and the price quote it opens. */
export class BookingRequestPage {
  readonly alert: Locator;
  readonly quote: Locator;
  readonly submit: Locator;

  constructor(readonly page: Page) {
    this.alert = page.getByRole('alert');
    this.quote = page.getByRole('dialog');
    this.submit = page.getByRole('button', { name: 'Request booking' });
  }

  async open(equipmentId: string): Promise<void> {
    await this.page.goto(`/equipment/${equipmentId}/book`);
  }

  async fillPeriod(start: Date, days: number): Promise<void> {
    await this.page.getByLabel('Start').fill(localDateTime(start));
    await this.page.getByLabel('End').fill(localDateTime(new Date(start.getTime() + days * 86_400_000)));
  }

  async request(start: Date, days: number): Promise<void> {
    await this.fillPeriod(start, days);
    await this.submit.click();
  }

  /** The quote's "Total payable" as a number of rupees. */
  async quotedTotal(): Promise<number> {
    return Number(((await this.quote.locator('dd.quote-total').textContent()) ?? '').replace(/[^\d.]/g, ''));
  }

  /** Follows "Open booking" from the quote and returns the new booking's id. */
  async openBooking(): Promise<string> {
    await this.quote.getByRole('link', { name: 'Open booking' }).click();
    await this.page.waitForURL(/\/bookings\/[\w-]+$/);
    return this.page.url().split('/').pop()!;
  }
}
