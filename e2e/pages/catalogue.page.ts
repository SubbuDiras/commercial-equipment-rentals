import { expect, Locator, Page } from '@playwright/test';

/** /equipment and /equipment/category/:id — the public catalogue, its search, and category pages. */
export class CataloguePage {
  readonly cards: Locator;
  readonly categories: Locator;

  constructor(readonly page: Page) {
    this.cards = page.locator('app-equipment-card');
    this.categories = page.getByRole('region', { name: 'Browse by category' });
  }

  async open(): Promise<void> {
    await this.page.goto('/equipment');
    await expect(this.page.getByRole('heading', { name: 'Equipment catalogue' })).toBeVisible();
  }

  /** The result count appears only once the list has loaded; before that the previous results are still shown. */
  async searchCity(city: string): Promise<void> {
    await this.page.getByLabel('City').fill(city);
    await this.page.getByRole('button', { name: 'Search equipment' }).click();
    await expect(this.page.getByRole('heading', { name: /^Search results \(\d+\)$/ })).toBeVisible();
  }

  async clearSearch(): Promise<void> {
    await this.page.getByRole('button', { name: 'Clear' }).click();
    await expect(this.page.getByRole('heading', { name: /^All equipment \(\d+\)$/ })).toBeVisible();
  }

  /** Opens the first category tile's "See all" page and returns the category name. */
  async openFirstCategory(): Promise<string> {
    const tile = this.categories.locator('article').first();
    const name = (await tile.getByRole('heading').textContent())!.trim();
    await tile.getByRole('link', { name: /See all/ }).click();
    await expect(this.page.getByRole('heading', { level: 1, name })).toBeVisible();
    return name;
  }
}
