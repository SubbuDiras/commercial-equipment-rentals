import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { InrPipe } from '../../shared/inr.pipe';
import { EquipmentCatalogueService, EquipmentImage, EquipmentListing, EquipmentReview } from './equipment-catalogue.service';

@Component({
  selector: 'app-equipment-details',
  standalone: true,
  imports: [CommonModule, RouterLink, InrPipe],
  template: `
    <main class="container" style="max-width: 900px; padding: 3rem 1rem;">
      <a routerLink="/" style="display: inline-block; margin-bottom: 1rem;">← Back to catalogue</a>
      <p *ngIf="loading()" role="status">Loading listing…</p>
      <p *ngIf="error()" role="alert" style="color: var(--color-danger);">{{ error() }}</p>
      <article *ngIf="listing() as item" class="card" style="padding: 2rem;">
        <section *ngIf="images().length" aria-label="Equipment photos" style="display:flex;gap:1rem;overflow:auto;margin-bottom:1.5rem;">
          <img *ngFor="let image of images()" [src]="image.imageUrl" [alt]="item.title" style="width:min(100%, 420px);max-height:320px;object-fit:contain;border-radius:.5rem;" />
        </section>
        <p *ngIf="imageError()" role="status" style="color:var(--color-text-muted);">Equipment photos are temporarily unavailable.</p>
        <p style="color: var(--color-primary-light); font-weight: 700;">{{ item.categoryName }}</p>
        <h1>{{ item.title }}</h1>
        <p style="color: var(--color-text-muted);">{{ item.manufacturer }} {{ item.modelNumber }} · {{ item.conditionGrade }}</p>
        <p style="margin-top: .35rem;">
          <span *ngIf="item.averageRating !== null; else noRating">
            <strong style="color: var(--color-accent-amber);">{{ stars(item.averageRating) }}</strong>
            {{ item.averageRating }} / 5 · {{ item.reviewCount }} {{ item.reviewCount === 1 ? 'review' : 'reviews' }}
          </span>
          <ng-template #noRating><span class="muted">No reviews yet</span></ng-template>
        </p>
        <p style="margin: 1.5rem 0;">{{ item.description }}</p>
        <dl style="display: grid; grid-template-columns: 1fr 1fr; gap: .75rem;">
          <dt>Daily rate</dt><dd>{{ item.baseDailyRate | inr }}</dd>
          <dt>Weekly rate</dt><dd>{{ item.baseWeeklyRate ? (item.baseWeeklyRate | inr) : 'Not provided' }}</dd>
          <dt>Refundable security deposit</dt><dd>{{ item.securityDeposit | inr }}</dd>
          <dt>Minimum rental period</dt><dd>{{ item.minimumRentalDays }} days</dd>
          <dt>Delivery</dt><dd>{{ item.deliverySupported ? (item.deliveryChargeFlat | inr) : 'Not offered' }}</dd>
          <dt>Pickup</dt><dd>{{ item.pickupSupported ? 'Available' : 'Not available' }}</dd>
          <dt>Location</dt><dd>{{ item.city }} {{ item.postalCode }}</dd>
          <ng-container *ngFor="let spec of specifications(item)">
            <dt>{{ spec[0] }}</dt><dd>{{ spec[1] }}</dd>
          </ng-container>
        </dl>
        <a *ngIf="item.pickupSupported || item.deliverySupported" class="btn btn-primary" [routerLink]="['/equipment', item.id, 'book']" style="margin-top: 1.5rem;">
          Request booking
        </a>
      </article>

      <section *ngIf="listing()" class="card" style="padding: 1.5rem; margin-top: 1.5rem;">
        <h2 style="font-size: 1.2rem; margin-bottom: .75rem;">Renter reviews</h2>
        <p *ngIf="reviews().length === 0" class="muted">No reviews yet.</p>
        <div *ngFor="let review of reviews()" style="padding: .75rem 0; border-bottom: 1px solid var(--color-border);">
          <strong style="color: var(--color-accent-amber);">{{ stars(review.rating) }}</strong>
          · {{ review.reviewerName }} · <span class="muted">{{ review.createdAt | date:'mediumDate' }}</span>
          <p *ngIf="review.comments" style="margin-top: .35rem;">{{ review.comments }}</p>
        </div>
        <button *ngIf="reviewPage() + 1 < reviewPages()" type="button" class="btn btn-secondary" style="margin-top: 1rem;"
                (click)="loadReviews(reviewPage() + 1)">Show more reviews</button>
      </section>
    </main>
  `
})
export class EquipmentDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly catalogue = inject(EquipmentCatalogueService);

  readonly listing = signal<EquipmentListing | null>(null);
  readonly images = signal<EquipmentImage[]>([]);
  readonly imageError = signal(false);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly reviews = signal<EquipmentReview[]>([]);
  readonly reviewPage = signal(0);
  readonly reviewPages = signal(0);

  stars(rating: number): string {
    const rounded = Math.round(rating);
    return '★'.repeat(rounded) + '☆'.repeat(5 - rounded);
  }

  specifications(item: EquipmentListing): [string, unknown][] {
    return Object.entries(item.specifications ?? {});
  }

  loadReviews(page: number): void {
    const listing = this.listing();
    if (!listing) {
      return;
    }
    this.catalogue.reviews(listing.id, page).subscribe({
      next: result => {
        this.reviews.update(current => page === 0 ? result.content : [...current, ...result.content]);
        this.reviewPage.set(result.page);
        this.reviewPages.set(result.totalPages);
      },
      error: () => this.reviews.set([])
    });
  }

  ngOnInit(): void {
    const equipmentId = this.route.snapshot.paramMap.get('equipmentId');
    if (!equipmentId) {
      this.error.set('Equipment listing was not specified.');
      this.loading.set(false);
      return;
    }
    this.catalogue.get(equipmentId).subscribe({
      next: listing => {
        this.listing.set(listing);
        this.loadReviews(0);
        this.loading.set(false);
        this.catalogue.images(equipmentId).subscribe({
          next: images => this.images.set(images),
          error: () => this.imageError.set(true)
        });
      },
      error: () => {
        this.error.set('This listing is unavailable or is no longer approved.');
        this.loading.set(false);
      }
    });
  }
}
