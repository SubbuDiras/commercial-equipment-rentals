import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  EquipmentCategory,
  EquipmentCatalogueService,
  EquipmentListing
} from './equipment-catalogue.service';

@Component({
  selector: 'app-equipment-catalogue',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <main class="container" style="padding: 3rem 1rem;">
      <header style="margin-bottom: 2rem;">
        <p style="color: var(--color-primary-light); font-weight: 700;">BENGALURU PILOT</p>
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
          <input name="city" [(ngModel)]="city" maxlength="100" placeholder="Bengaluru" style="display: block; width: 100%; padding: .65rem; margin-top: .35rem;" />
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
        <button class="btn btn-primary" type="submit" [disabled]="loading()">Search equipment</button>
      </form>

      <p *ngIf="categoryError()" role="alert" style="margin: 1rem 0; color: var(--color-danger);">{{ categoryError() }}</p>
      <p *ngIf="error()" role="alert" style="margin: 1rem 0; color: var(--color-danger);">{{ error() }}</p>
      <p *ngIf="loading()" role="status" style="margin: 1.5rem 0;">Loading listings…</p>
      <p *ngIf="!loading() && !error() && listings().length === 0" style="margin: 1.5rem 0; color: var(--color-text-muted);">
        No approved listings match these filters yet.
      </p>

      <section *ngIf="listings().length" aria-label="Available equipment" class="grid grid-cols-3" style="gap: 1rem; margin-top: 1.5rem;">
        <article *ngFor="let listing of listings()" class="card" style="padding: 1.25rem;">
          <p style="color: var(--color-primary-light); font-size: .8rem; font-weight: 700;">{{ listing.categoryName }}</p>
          <h2 style="font-size: 1.2rem; margin: .4rem 0;">{{ listing.title }}</h2>
          <p style="color: var(--color-text-muted);">{{ listing.manufacturer }} {{ listing.modelNumber }} · {{ listing.conditionGrade }}</p>
          <p style="margin: 1rem 0;"><strong>{{ listing.baseDailyRate | currency:'INR':'symbol':'1.0-2' }}</strong> / day</p>
          <p style="color: var(--color-text-muted);">{{ listing.city }} · {{ listing.minimumRentalDays }} day minimum</p>
          <a class="btn btn-primary" [routerLink]="['/equipment', listing.id]" style="margin-top: 1rem;">View details</a>
        </article>
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
  readonly listings = signal<EquipmentListing[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly categoryError = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);

  categoryId = '';
  city = '';
  minRate: number | null = null;
  maxRate: number | null = null;
  startDateTime = '';
  endDateTime = '';

  ngOnInit(): void {
    this.catalogue.categories().subscribe({
      next: categories => this.categories.set(categories),
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
    this.page.set(0);
    this.loadPage();
  }

  changePage(page: number): void {
    this.page.set(page);
    this.loadPage();
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
      size: 12
    }).subscribe({
      next: result => {
        this.listings.set(result.content);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: () => {
        this.listings.set([]);
        this.totalPages.set(0);
        this.error.set('Equipment listings could not be loaded. Please try again.');
        this.loading.set(false);
      }
    });
  }
}
