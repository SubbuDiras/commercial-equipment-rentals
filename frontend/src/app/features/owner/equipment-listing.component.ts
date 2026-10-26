import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import {
  EquipmentCategory,
  EquipmentCatalogueService,
  EquipmentImage,
  EquipmentListing,
  EquipmentListingRequest
} from '../catalogue/equipment-catalogue.service';

@Component({
  selector: 'app-equipment-listing',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  template: `
    <main class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <h1>My equipment listings</h1>
      <p style="color: var(--color-text-muted);">Create a draft, then submit it for admin approval before it appears in public search.</p>

      <section class="card" style="padding: 1.5rem; margin: 1.5rem 0;">
        <h2 style="font-size: 1.25rem;">Create listing</h2>
        <form [formGroup]="form" (ngSubmit)="create()" style="display: grid; gap: 1rem; margin-top: 1rem;">
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>Category
              <select formControlName="categoryId" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;">
                <option value="">Select category</option>
                <ng-container *ngFor="let category of categories()">
                  <option [value]="category.id">{{ category.name }}</option>
                  <option *ngFor="let child of category.subcategories" [value]="child.id">— {{ child.name }}</option>
                </ng-container>
              </select>
            </label>
            <label>Condition
              <select formControlName="conditionGrade" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;">
                <option value="EXCELLENT">Excellent</option><option value="GOOD">Good</option>
                <option value="FAIR">Fair</option><option value="POOR">Poor</option>
              </select>
            </label>
          </div>
          <label>Listing title
            <input formControlName="title" maxlength="255" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" />
          </label>
          <label>Description
            <textarea formControlName="description" rows="4" maxlength="10000" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;"></textarea>
          </label>
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>Manufacturer<input formControlName="manufacturer" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Model<input formControlName="modelNumber" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Serial number (optional)<input formControlName="serialNumber" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Year made (optional)<input type="number" formControlName="yearOfManufacture" min="1900" max="2100" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
          </div>
          <div class="grid grid-cols-3" style="gap: 1rem;">
            <label>Daily rate (₹)<input type="number" formControlName="baseDailyRate" min="0.01" step="0.01" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Weekly rate (₹)<input type="number" formControlName="baseWeeklyRate" min="0" step="0.01" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Refundable deposit (₹)<input type="number" formControlName="securityDeposit" min="0" step="0.01" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
          </div>
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>Minimum rental days<input type="number" formControlName="minimumRentalDays" min="1" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Delivery charge (₹)<input type="number" formControlName="deliveryChargeFlat" min="0" step="0.01" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
          </div>
          <div style="display:flex;gap:1.5rem;">
            <label><input type="checkbox" formControlName="deliverySupported" /> Delivery offered</label>
            <label><input type="checkbox" formControlName="pickupSupported" /> Pickup offered</label>
          </div>
          <label>Address<input formControlName="addressLine" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>City<input formControlName="city" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Postal code<input formControlName="postalCode" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Latitude<input type="number" formControlName="latitude" min="-90" max="90" step="0.0000001" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
            <label>Longitude<input type="number" formControlName="longitude" min="-180" max="180" step="0.0000001" style="display:block;width:100%;padding:.6rem;margin-top:.3rem;" /></label>
          </div>
          <p *ngIf="error()" role="alert" style="color:var(--color-danger);">{{ error() }}</p>
          <p *ngIf="success()" role="status" style="color:var(--color-accent);">{{ success() }}</p>
          <button class="btn btn-primary" type="submit" [disabled]="form.invalid || saving()">Save draft</button>
        </form>
      </section>

      <p *ngIf="loading()" role="status">Loading your listings…</p>
      <p *ngIf="!loading() && listings().length === 0" style="color:var(--color-text-muted);">You have no equipment listings yet.</p>
      <section *ngFor="let listing of listings()" class="card" style="padding:1rem;margin:.75rem 0;display:flex;justify-content:space-between;gap:1rem;align-items:center;">
        <div>
          <strong>{{ listing.title }}</strong>
          <span class="badge badge-role" style="margin-left:.5rem;">{{ listing.status }}</span>
          <div style="color:var(--color-text-muted);">{{ listing.city }} · {{ listing.baseDailyRate | currency:'INR' }}/day</div>
          <div *ngIf="images()[listing.id]?.length" style="display:flex;gap:.5rem;margin-top:.75rem;">
            <img *ngFor="let image of images()[listing.id]" [src]="image.imageUrl" [alt]="listing.title" style="width:100px;height:75px;object-fit:cover;border-radius:.35rem;" />
          </div>
        </div>
        <div style="display:grid;gap:.5rem;">
          <label *ngIf="listing.status === 'DRAFT' || listing.status === 'REJECTED'" class="btn btn-secondary">
            Add image
            <input type="file" accept="image/jpeg,image/png,image/webp" (change)="uploadImage(listing, $event)" [disabled]="saving()" style="display:none;" />
          </label>
          <button *ngIf="listing.status === 'DRAFT' || listing.status === 'REJECTED'" class="btn btn-secondary" type="button" (click)="submit(listing)" [disabled]="saving()">
            Submit for approval
          </button>
        </div>
      </section>
    </main>
  `
})
export class EquipmentListingComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly catalogue = inject(EquipmentCatalogueService);

  readonly categories = signal<EquipmentCategory[]>([]);
  readonly listings = signal<EquipmentListing[]>([]);
  readonly images = signal<Record<string, EquipmentImage[]>>({});
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly success = signal<string | null>(null);

  readonly form = this.formBuilder.nonNullable.group({
    categoryId: ['', Validators.required],
    title: ['', [Validators.required, Validators.maxLength(255)]],
    description: ['', [Validators.required, Validators.maxLength(10000)]],
    manufacturer: ['', Validators.required],
    modelNumber: ['', Validators.required],
    serialNumber: [''],
    yearOfManufacture: [null as number | null],
    conditionGrade: ['GOOD', Validators.required],
    baseDailyRate: [null as number | null, [Validators.required, Validators.min(0.01)]],
    baseWeeklyRate: [null as number | null, Validators.min(0)],
    securityDeposit: [0, Validators.min(0)],
    minimumRentalDays: [1, [Validators.required, Validators.min(1)]],
    deliverySupported: [false],
    pickupSupported: [true],
    deliveryChargeFlat: [0, Validators.min(0)],
    addressLine: ['', Validators.required],
    city: ['', Validators.required],
    postalCode: ['', Validators.required],
    latitude: [null as number | null, [Validators.required, Validators.min(-90), Validators.max(90)]],
    longitude: [null as number | null, [Validators.required, Validators.min(-180), Validators.max(180)]]
  });

  ngOnInit(): void {
    this.catalogue.categories().subscribe({
      next: categories => this.categories.set(categories),
      error: () => this.error.set('Equipment categories could not be loaded.')
    });
    this.loadListings();
  }

  create(): void {
    this.error.set(null);
    this.success.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.catalogue.createListing(this.toRequest()).subscribe({
      next: listing => {
        this.listings.update(current => [listing, ...current]);
        this.form.reset({
          categoryId: '',
          title: '',
          description: '',
          manufacturer: '',
          modelNumber: '',
          serialNumber: '',
          yearOfManufacture: null,
          conditionGrade: 'GOOD',
          baseDailyRate: null,
          baseWeeklyRate: null,
          securityDeposit: 0,
          minimumRentalDays: 1,
          deliverySupported: false,
          pickupSupported: true,
          deliveryChargeFlat: 0,
          addressLine: '',
          city: '',
          postalCode: '',
          latitude: null,
          longitude: null
        });
        this.success.set('Draft saved. Submit it when you are ready for review.');
        this.saving.set(false);
      },
      error: () => {
        this.error.set('The listing could not be saved. Verify your owner account and try again.');
        this.saving.set(false);
      }
    });
  }

  submit(listing: EquipmentListing): void {
    this.saving.set(true);
    this.error.set(null);
    this.catalogue.submitListing(listing.id).subscribe({
      next: updated => {
        this.replaceListing(updated);
        this.success.set('Listing submitted for admin approval.');
        this.saving.set(false);
      },
      error: () => {
        this.error.set('The listing could not be submitted for approval.');
        this.saving.set(false);
      }
    });
  }

  async uploadImage(listing: EquipmentListing, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size < 1 || file.size > 10 * 1024 * 1024) {
      this.error.set('Choose a JPEG, PNG, or WebP image no larger than 10 MB.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      const upload = await firstValueFrom(this.catalogue.presignImage(listing.id, file));
      const response = await fetch(upload.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': upload.contentType },
        body: file
      });
      if (!response.ok) {
        throw new Error(`Storage upload failed with HTTP ${response.status}.`);
      }
      const image = await firstValueFrom(this.catalogue.completeImage(listing.id, upload.imageId));
      this.images.update(current => ({
        ...current,
        [listing.id]: [...(current[listing.id] ?? []), image]
      }));
      this.success.set('Equipment image uploaded.');
    } catch {
      this.error.set('The image upload failed. Retry the upload or check object-storage connectivity.');
    } finally {
      this.saving.set(false);
    }
  }

  private loadListings(): void {
    this.catalogue.ownListings().subscribe({
      next: listings => {
        this.listings.set(listings);
        for (const listing of listings) {
          this.catalogue.ownerImages(listing.id).subscribe({
            next: images => this.images.update(current => ({ ...current, [listing.id]: images })),
            error: () => this.error.set(`Images for "${listing.title}" could not be loaded.`)
          });
        }
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Your listings could not be loaded. Your owner profile may need verification.');
        this.loading.set(false);
      }
    });
  }

  private toRequest(): EquipmentListingRequest {
    const value = this.form.getRawValue();
    if (value.baseDailyRate === null || value.latitude === null || value.longitude === null) {
      throw new Error('The daily rate and listing coordinates are required.');
    }
    return {
      categoryId: value.categoryId,
      title: value.title,
      description: value.description,
      manufacturer: value.manufacturer,
      modelNumber: value.modelNumber,
      serialNumber: value.serialNumber || null,
      yearOfManufacture: value.yearOfManufacture,
      conditionGrade: value.conditionGrade,
      specifications: {},
      baseDailyRate: value.baseDailyRate,
      baseWeeklyRate: value.baseWeeklyRate,
      securityDeposit: value.securityDeposit,
      minimumRentalDays: value.minimumRentalDays,
      deliverySupported: value.deliverySupported,
      pickupSupported: value.pickupSupported,
      deliveryChargeFlat: value.deliveryChargeFlat,
      addressLine: value.addressLine,
      city: value.city,
      postalCode: value.postalCode,
      latitude: value.latitude,
      longitude: value.longitude
    };
  }

  private replaceListing(updated: EquipmentListing): void {
    this.listings.update(current => current.map(listing => listing.id === updated.id ? updated : listing));
  }
}
