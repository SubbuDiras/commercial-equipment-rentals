import { expect, Locator, Page } from '@playwright/test';

export interface ListingDetails {
  title: string;
  description?: string;
  manufacturer?: string;
  model?: string;
  dailyRate?: string;
  deposit?: string;
  address?: string;
  city?: string;
  postalCode?: string;
  latitude?: string;
  longitude?: string;
}

/** /owner/equipment — the owner's listing form and their list of listings. */
export class OwnerListingsPage {
  readonly saveDraft: Locator;

  constructor(readonly page: Page) {
    this.saveDraft = page.getByRole('button', { name: 'Save draft' });
  }

  async open(): Promise<void> {
    await this.page.goto('/owner/equipment');
    await this.waitUntilLoaded();
  }

  /**
   * Waits until the owner check has finished and the listings have loaded. Filling the form earlier is lost when
   * the page re-renders it as that data arrives.
   */
  async waitUntilLoaded(): Promise<void> {
    await expect(this.page.getByRole('heading', { name: /^Your listings \(\d+\)$/ })).toBeVisible();
  }

  async chooseFirstCategory(): Promise<void> {
    await this.waitUntilLoaded();
    const category = this.page.getByRole('combobox', { name: 'Category' });
    await category.selectOption({ index: 1 });
    await expect(category).not.toHaveValue('');
  }

  async fill(listing: ListingDetails): Promise<void> {
    await this.chooseFirstCategory();
    await this.page.getByLabel('Listing title').fill(listing.title);
    await this.page.getByLabel('Description').fill(listing.description ?? 'Listed by the E2E suite.');
    await this.page.getByLabel('Manufacturer', { exact: true }).fill(listing.manufacturer ?? 'Bosch');
    await this.page.getByLabel('Model', { exact: true }).fill(listing.model ?? 'GSH 11 E');
    await this.page.getByLabel('Daily rate (₹)').fill(listing.dailyRate ?? '1000');
    await this.page.getByLabel('Refundable deposit (₹)').fill(listing.deposit ?? '2000');
    await this.page.getByLabel('Address', { exact: true }).fill(listing.address ?? 'Plot 1, HITEC City');
    await this.page.getByLabel('City', { exact: true }).fill(listing.city ?? 'Hyderabad');
    await this.page.getByLabel('Postal code').fill(listing.postalCode ?? '500081');
    await this.page.getByLabel('Latitude').fill(listing.latitude ?? '17.4474');
    await this.page.getByLabel('Longitude').fill(listing.longitude ?? '78.3762');
  }

  /** The card for one of the owner's listings, found by its unique title. */
  card(title: string): Locator {
    return this.page.locator('section', { hasText: title });
  }

  async submitForApproval(title: string): Promise<void> {
    await this.card(title).getByRole('button', { name: 'Submit for approval' }).click();
    await expect(this.page.getByText('Listing submitted for admin approval.')).toBeVisible();
  }
}
