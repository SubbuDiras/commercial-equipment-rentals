import { expect, Locator, Page } from '@playwright/test';
import { photos } from '../support/files';

/** /bookings/:id — the booking page every role works from: status, payment, handover, return, cancel. */
export class BookingDetailsPage {
  readonly status: Locator;
  readonly payNow: Locator;
  readonly cancelButton: Locator;

  constructor(readonly page: Page) {
    this.status = page.locator('header app-status-badge');
    this.payNow = page.getByRole('button', { name: 'Pay now' });
    this.cancelButton = page.getByRole('button', { name: 'Cancel booking' });
  }

  async open(bookingId: string): Promise<void> {
    await this.page.goto(`/bookings/${bookingId}`);
    await expect(this.page.getByText(/^Booking BKG-/)).toBeVisible();
  }

  async expectStatus(label: string): Promise<void> {
    await expect(this.status).toHaveText(label);
  }

  /** Generates this user's handover/return code and returns it (shown in development only). */
  async generateCode(): Promise<string> {
    await this.page.getByRole('button', { name: /Generate (my|a new) code/ }).click();
    const code = this.page.getByText(/Development only/).locator('strong');
    await expect(code).toHaveText(/^\d{6}$/);
    return (await code.textContent())!.trim();
  }

  async uploadInspectionPhotos(count = 4): Promise<void> {
    await this.page.locator('app-inspection-wizard input[type=file]').setInputFiles(photos(count));
    await expect(this.page.getByText(new RegExp(`Photos \\(${count} of at least 4\\)`))).toBeVisible();
  }

  /** Fills and submits the inspection confirmation, entering this user's code and the other side's code. */
  async confirmInspection(myCode: string, otherCode: string, type: 'handover' | 'return'): Promise<void> {
    await this.page.getByLabel('Hour meter reading').fill(type === 'handover' ? '100' : '112.5');
    await this.page.getByLabel('All accessories present').check();
    await this.page.getByLabel('Overall condition acceptable').check();
    await this.page.getByLabel('Your code').fill(myCode);
    await this.page.getByLabel(/^(Renter|Owner)'s code/).fill(otherCode);
    await this.page.getByRole('button', { name: `Confirm ${type}` }).click();
  }

  /** Pays through the fake Razorpay dialog. */
  async pay(outcome: 'success' | 'failure' = 'success'): Promise<void> {
    await this.payNow.click();
    const modal = this.page.getByRole('dialog');
    await modal.getByRole('button', { name: outcome === 'success' ? 'Pay successfully' : 'Fail payment' }).click();
  }

  /** Cancels and returns the quoted total refund text ('' when no payment was taken). */
  async cancel(reason: string): Promise<string> {
    await this.cancelButton.click();
    const modal = this.page.getByRole('dialog');
    await expect(modal.getByText('Total refund').or(modal.getByText('No payment has been taken'))).toBeVisible();
    const totalCell = modal.locator('dt:has-text("Total refund") + dd');
    const total = (await totalCell.count()) > 0 ? await totalCell.textContent() : null;
    await modal.getByLabel('Reason for cancelling').fill(reason);
    await modal.getByRole('button', { name: 'Cancel booking' }).click();
    await expect(this.page.getByText('The booking has been cancelled.')).toBeVisible();
    return (total ?? '').trim();
  }
}
