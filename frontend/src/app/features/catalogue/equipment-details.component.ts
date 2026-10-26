import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { EquipmentCatalogueService, EquipmentImage, EquipmentListing } from './equipment-catalogue.service';

@Component({
  selector: 'app-equipment-details',
  standalone: true,
  imports: [CommonModule, RouterLink],
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
        <p style="margin: 1.5rem 0;">{{ item.description }}</p>
        <dl style="display: grid; grid-template-columns: 1fr 1fr; gap: .75rem;">
          <dt>Daily rate</dt><dd>{{ item.baseDailyRate | currency:'INR' }}</dd>
          <dt>Weekly rate</dt><dd>{{ item.baseWeeklyRate ? (item.baseWeeklyRate | currency:'INR') : 'Not provided' }}</dd>
          <dt>Refundable security deposit</dt><dd>{{ item.securityDeposit | currency:'INR' }}</dd>
          <dt>Minimum rental period</dt><dd>{{ item.minimumRentalDays }} days</dd>
          <dt>Delivery</dt><dd>{{ item.deliverySupported ? (item.deliveryChargeFlat | currency:'INR') : 'Not offered' }}</dd>
          <dt>Pickup</dt><dd>{{ item.pickupSupported ? 'Available' : 'Not available' }}</dd>
          <dt>Location</dt><dd>{{ item.city }} {{ item.postalCode }}</dd>
        </dl>
        <a *ngIf="item.pickupSupported || item.deliverySupported" class="btn btn-primary" [routerLink]="['/equipment', item.id, 'book']" style="margin-top: 1.5rem;">
          Request booking
        </a>
      </article>
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
