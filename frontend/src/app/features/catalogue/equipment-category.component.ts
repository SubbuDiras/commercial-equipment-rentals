import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EquipmentCardComponent } from './equipment-card.component';
import { EquipmentCatalogueService, EquipmentListing, flattenCategories } from './equipment-catalogue.service';

/** Every approved listing in one category, reached from a catalogue tile or a details page. */
@Component({
  selector: 'app-equipment-category',
  standalone: true,
  imports: [CommonModule, RouterLink, EquipmentCardComponent],
  template: `
    <main class="container-wide" style="padding-top: 1.75rem; padding-bottom: 3rem;">
      <nav aria-label="Breadcrumb" style="font-size: .875rem; margin-bottom: .75rem;">
        <a routerLink="/equipment">Equipment catalogue</a>
        <span class="muted"> › {{ categoryName() || 'Category' }}</span>
      </nav>
      <header style="display: flex; flex-wrap: wrap; justify-content: space-between; align-items: baseline; gap: .5rem; margin-bottom: 1.25rem;">
        <h1>{{ categoryName() || 'Equipment' }}</h1>
        <span *ngIf="!loading() && !error()" class="muted">{{ totalElements() }} {{ totalElements() === 1 ? 'listing' : 'listings' }}</span>
      </header>

      <p *ngIf="error()" role="alert" style="margin: 1rem 0; color: var(--color-danger);">{{ error() }}</p>
      <p *ngIf="loading()" role="status" style="margin: 1.5rem 0;">Loading listings…</p>
      <p *ngIf="!loading() && !error() && listings().length === 0" style="margin: 1.5rem 0; color: var(--color-text-muted);">
        No approved listings in this category yet. <a routerLink="/equipment">Browse all equipment</a>
      </p>

      <section *ngIf="listings().length" [attr.aria-label]="categoryName() + ' listings'" class="equipment-grid">
        <app-equipment-card *ngFor="let listing of listings()" [listing]="listing" />
      </section>

      <nav *ngIf="totalPages() > 1" aria-label="Category pages" style="display: flex; gap: 1rem; justify-content: center; align-items: center; margin-top: 2rem;">
        <button class="btn btn-secondary" type="button" (click)="load(page() - 1)" [disabled]="page() === 0">Previous</button>
        <span>Page {{ page() + 1 }} of {{ totalPages() }}</span>
        <button class="btn btn-secondary" type="button" (click)="load(page() + 1)" [disabled]="page() + 1 >= totalPages()">Next</button>
      </nav>
    </main>
  `
})
export class EquipmentCategoryComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly catalogue = inject(EquipmentCatalogueService);
  private readonly destroyRef = inject(DestroyRef);

  readonly categoryName = signal('');
  readonly listings = signal<EquipmentListing[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);
  private categoryId = '';

  ngOnInit(): void {
    // The same component instance is reused when moving between categories, so follow the parameter.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      this.categoryId = params.get('categoryId') ?? '';
      this.categoryName.set('');
      this.catalogue.categories().subscribe({
        next: categories => this.categoryName.set(
          flattenCategories(categories).find(category => category.id === this.categoryId)?.name ?? ''),
        error: () => this.categoryName.set('')
      });
      this.load(0);
    });
  }

  load(page: number): void {
    this.loading.set(true);
    this.error.set(null);
    this.page.set(page);
    this.catalogue.search({ categoryId: this.categoryId, page, size: 24 }).subscribe({
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
