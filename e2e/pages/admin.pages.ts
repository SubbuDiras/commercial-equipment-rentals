import { expect, Locator, Page } from '@playwright/test';

/** /admin/owners — the KYC queue, by tab. */
export class AdminOwnersPage {
  constructor(readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/admin/owners');
    await expect(this.page.getByText('Loading owners…')).toHaveCount(0);
  }

  async showTab(name: 'Waiting for review' | 'Verified' | 'Rejected'): Promise<void> {
    await this.page.getByRole('tab', { name }).click();
    await expect(this.page.getByRole('tab', { name })).toHaveAttribute('aria-selected', 'true');
    await expect(this.page.getByText('Loading owners…')).toHaveCount(0);
  }

  /** One application, found by the applicant's email. */
  application(email: string): Locator {
    return this.page.locator('article', { hasText: email });
  }
}

/** /admin/listings — listings waiting for approval. */
export class AdminListingsPage {
  constructor(readonly page: Page) {}

  async open(): Promise<void> {
    await this.page.goto('/admin/listings');
  }

  pending(title: string): Locator {
    return this.page.locator('article', { hasText: title });
  }

  async approve(title: string): Promise<void> {
    await this.pending(title).getByRole('button', { name: 'Approve' }).click();
    await expect(this.pending(title)).toHaveCount(0);
  }

  async reject(title: string, reason: string): Promise<void> {
    const listing = this.pending(title);
    await listing.getByRole('button', { name: 'Reject' }).click();
    await listing.getByLabel('Rejection reason').fill(reason);
    await listing.getByRole('button', { name: 'Confirm rejection' }).click();
    await expect(listing).toHaveCount(0);
  }
}

/** /admin/disputes and /admin/disputes/:id — damage claims and their resolution. */
export class AdminDisputesPage {
  readonly deduction: Locator;
  readonly notes: Locator;

  constructor(readonly page: Page) {
    this.deduction = page.getByLabel('Deduction from deposit (₹)');
    this.notes = page.getByLabel('Decision notes (shared with both parties)');
  }

  /** Opens the claim for the listing with this unique title. */
  async openClaim(title: string): Promise<void> {
    await this.page.goto('/admin/disputes');
    await this.page.getByRole('link', { name: new RegExp(title) }).click();
  }

  /** Fills in the decision and asks to resolve; the confirm dialog opens only if the form accepts it. */
  async askToResolve(deduction: number, notes: string): Promise<void> {
    await this.deduction.fill(String(deduction));
    await this.notes.fill(notes);
    await this.page.getByRole('button', { name: 'Resolve claim' }).click();
  }

  async resolve(deduction: number, notes: string): Promise<void> {
    await this.askToResolve(deduction, notes);
    await this.page.getByRole('dialog').getByRole('button', { name: 'Resolve' }).click();
    await expect(this.page.getByText('Claim resolved.')).toBeVisible();
  }
}
