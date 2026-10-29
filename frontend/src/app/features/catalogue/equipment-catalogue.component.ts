import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { EquipmentCardComponent } from './equipment-card.component';
import {
  EquipmentCategory,
  EquipmentCatalogueService,
  EquipmentListing,
  flattenCategories
} from './equipment-catalogue.service';

/** A category tile on the catalogue's front page: a few of its listings and a link to all of them. */
interface CategoryShelf {
  category: EquipmentCategory;
  listings: EquipmentListing[];
  total: number;
}

@Component({
  selector: 'app-equipment-catalogue',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, EquipmentCardComponent],
  template: `
    <main class="container-wide" style="padding-top: 1.75rem; padding-bottom: 3rem;">
      <header style="margin-bottom: 1.25rem;">
        <p style="color: var(--color-primary-light); font-weight: 700;">ACROSS INDIA</p>
        <h1>Equipment catalogue</h1>
        <p style="color: var(--color-text-muted);">Browse owner-approved equipment listings and compare rental rates.</p>
      </header>

      <form class="card" (ngSubmit)="search()" style="padding: 1.25rem; display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; align-items: end;">
        <label>
          <span>Category</span>
          <select name="category" [(ngModel)]="categoryId" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;">
            <option value="">All categories</option>
            <ng-container *ngFor="let category of categories()">
              <option [value]="category.id">{{ category.name }}</option>
              <option *ngFor="let child of category.subcategories" [value]="child.id">— {{ child.name }}</option>
            </ng-container>
          </select>
        </label>
        <label>
          <span>City</span>
          <input name="city" [(ngModel)]="city" maxlength="100" placeholder="e.g. Hyderabad" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;" />
        </label>
        <label>
          <span>Minimum daily rate (₹)</span>
          <input name="minRate" type="number" min="0" [(ngModel)]="minRate" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;" />
        </label>
        <label>
          <span>Maximum daily rate (₹)</span>
          <input name="maxRate" type="number" min="0" [(ngModel)]="maxRate" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;" />
        </label>
        <label>
          <span>Available from</span>
          <input name="startTime" type="datetime-local" [(ngModel)]="startDateTime" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;" />
        </label>
        <label>
          <span>Available until</span>
          <input name="endTime" type="datetime-local" [(ngModel)]="endDateTime" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;" />
        </label>
        <div style="display: flex; gap: .5rem;">
          <button class="btn btn-primary" type="submit" [disabled]="loading()" style="flex: 1;">Search equipment</button>
          <button *ngIf="filtered()" class="btn btn-secondary" type="button" (click)="clearFilters()">Clear</button>
        </div>
      </form>

      <p *ngIf="categoryError()" role="alert" style="margin: 1rem 0; color: var(--color-danger);">{{ categoryError() }}</p>

      <section *ngIf="!filtered() && shelves().length" aria-label="Browse by category"
               style="display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 1rem; margin-top: 1.5rem;">
        <article *ngFor="let shelf of shelves()" class="card" style="padding: 1rem 1rem 1.1rem; display: flex; flex-direction: column;">
          <a [routerLink]="['/equipment/category', shelf.category.id]" style="display: flex; justify-content: space-between; align-items: baseline; gap: .5rem; color: var(--color-text-main);">
            <h2 style="font-size: 1.1rem;">{{ shelf.category.name }}</h2>
            <span aria-hidden="true" style="font-size: 1.25rem;">›</span>
          </a>
          <div style="display: grid; grid-template-columns: 1fr 1fr; gap: .75rem; margin-top: .75rem;">
            <a *ngFor="let item of shelf.listings" [routerLink]="['/equipment', item.id]" style="color: var(--color-text-main);">
              <img *ngIf="item.primaryImageUrl; else tilePlaceholder" [src]="item.primaryImageUrl" [alt]="item.title" loading="lazy"
                   style="display: block; width: 100%; aspect-ratio: 1; object-fit: contain; background: #fff; border-radius: var(--radius-md); background: var(--color-bg-muted);" />
              <ng-template #tilePlaceholder>
                <div style="width: 100%; aspect-ratio: 1; border-radius: var(--radius-md); background: var(--color-bg-muted);"></div>
              </ng-template>
              <span class="line-clamp-2" style="font-size: .8rem; margin-top: .35rem;">{{ item.title }}</span>
              <strong style="font-size: .9rem;">{{ item.baseDailyRate | currency:'INR':'symbol':'1.0-0' }}/day</strong>
            </a>
          </div>
          <a [routerLink]="['/equipment/category', shelf.category.id]" style="margin-top: auto; padding-top: .9rem; font-size: .875rem; font-weight: 600;">
            See all {{ shelf.total }} {{ shelf.total === 1 ? 'listing' : 'listings' }} →
          </a>
        </article>
      </section>

      <h2 style="font-size: 1.35rem; margin: 2rem 0 1rem;">
        {{ filtered() ? 'Search results' : 'All equipment' }}
        <span *ngIf="!loading() && !error()" class="muted" style="font-size: 1rem; font-weight: 500;">({{ totalElements() }})</span>
      </h2>
      <p *ngIf="error()" role="alert" style="margin: 1rem 0; color: var(--color-danger);">{{ error() }}</p>
      <p *ngIf="loading()" role="status" style="margin: 1.5rem 0;">Loading listings…</p>
      <p *ngIf="!loading() && !error() && listings().length === 0" style="margin: 1.5rem 0; color: var(--color-text-muted);">
        No approved listings match these filters yet.
      </p>

      <section *ngIf="listings().length" aria-label="Available equipment" class="equipment-grid">
        <app-equipment-card *ngFor="let listing of listings()" [listing]="listing" />
      </section>

      <nav *ngIf="totalPages() > 1" aria-label="Catalogue pages" style="display: flex; gap: 1rem; justify-content: center; align-items: center; margin-top: 2rem;">
        <button class="btn btn-secondary" type="button" (click)="changePage(page() - 1)" [disabled]="page() === 0">Previous</button>
        <span>Page {{ page() + 1 }} of {{ totalPages() }}</span>
        <button class="btn btn-secondary" type="button" (click)="changePage(page() + 1)" [disabled]="page() + 1 >= totalPages()">Next</button>
      </nav>
    </main>
  `
})
export class EquipmentCatalogueComponent implements OnInit {
  private readonly catalogue = inject(EquipmentCatalogueService);

  readonly categories = signal<EquipmentCategory[]>([]);
  readonly shelves = signal<CategoryShelf[]>([]);
  readonly listings = signal<EquipmentListing[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly categoryError = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  /** True once a search with any filter has run; the category tiles then give way to the results. */
  readonly filtered = signal(false);

  categoryId = '';
  city = '';
  minRate: number | null = null;
  maxRate: number | null = null;
  startDateTime = '';
  endDateTime = '';

  ngOnInit(): void {
    this.catalogue.categories().subscribe({
      next: categories => {
        this.categories.set(categories);
        this.loadShelves(flattenCategories(categories));
      },
      error: () => this.categoryError.set('Categories could not be loaded. Please try again.')
    });
    this.search();
  }

  search(): void {
    if (this.minRate !== null && this.maxRate !== null && this.minRate > this.maxRate) {
      this.error.set('Minimum daily rate cannot exceed maximum daily rate.');
      return;
    }
    if ((this.startDateTime === '') !== (this.endDateTime === '')) {
      this.error.set('Choose both availability dates or leave both blank.');
      return;
    }
    if (this.startDateTime && new Date(this.startDateTime) >= new Date(this.endDateTime)) {
      this.error.set('The availability end must be later than the start.');
      return;
    }
    this.filtered.set(Boolean(this.categoryId || this.city.trim() || this.minRate !== null || this.maxRate !== null
      || this.startDateTime));
    this.page.set(0);
    this.loadPage();
  }

  clearFilters(): void {
    this.categoryId = '';
    this.city = '';
    this.minRate = null;
    this.maxRate = null;
    this.startDateTime = '';
    this.endDateTime = '';
    this.search();
  }

  changePage(page: number): void {
    this.page.set(page);
    this.loadPage();
  }

  /** Four listings per category, skipping categories with none; a failed category just has no tile. */
  private loadShelves(categories: EquipmentCategory[]): void {
    if (!categories.length) {
      return;
    }
    forkJoin(categories.map(category => this.catalogue.search({ categoryId: category.id, page: 0, size: 4 }).pipe(
      map(result => ({ category, listings: result.content, total: result.totalElements })),
      catchError(() => of({ category, listings: [], total: 0 }))
    ))).subscribe(shelves => this.shelves.set(shelves.filter(shelf => shelf.total > 0)));
  }

  private loadPage(): void {
    this.loading.set(true);
    this.error.set(null);
    this.catalogue.search({
      categoryId: this.categoryId || undefined,
      city: this.city.trim() || undefined,
      minRate: this.minRate ?? undefined,
      maxRate: this.maxRate ?? undefined,
      startTime: this.startDateTime ? new Date(this.startDateTime).toISOString() : undefined,
      endTime: this.endDateTime ? new Date(this.endDateTime).toISOString() : undefined,
      page: this.page(),
      size: 24
    }).subscribe({
      next: result => {
        this.listings.set(result.content);
        this.totalPages.set(result.totalPages);
        this.totalElements.set(result.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.listings.set([]);
        this.totalPages.set(0);
        this.totalElements.set(0);
        this.error.set('Equipment listings could not be loaded. Please try again.');
        this.loading.set(false);
      }
    });
  }
}
