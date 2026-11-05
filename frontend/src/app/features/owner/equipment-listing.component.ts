import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  EquipmentCategory,
  EquipmentCatalogueService,
  EquipmentImage,
  EquipmentListing,
  EquipmentListingRequest,
  isListingPhoto
} from '../catalogue/equipment-catalogue.service';
import { invalidFieldsMessage } from '../../shared/form-feedback';
import { InrPipe } from '../../shared/inr.pipe';
import { PhotoEditorComponent } from '../../shared/photo-editor.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { OwnerSetupNoticeComponent } from './owner-setup-notice.component';

/** Names used when telling the owner which fields to fix; they match the form's labels. */
const LISTING_FIELD_LABELS: Record<string, string> = {
  categoryId: 'Category', title: 'Listing title', description: 'Description', manufacturer: 'Manufacturer',
  modelNumber: 'Model', conditionGrade: 'Condition', baseDailyRate: 'Daily rate (₹)', baseWeeklyRate: 'Weekly rate (₹)',
  securityDeposit: 'Refundable deposit (₹)', minimumRentalDays: 'Minimum rental days', deliveryChargeFlat: 'Delivery charge (₹)',
  addressLine: 'Address', city: 'City', postalCode: 'Postal code', latitude: 'Latitude (between -90 and 90)',
  longitude: 'Longitude (between -180 and 180)', specifications: 'Specifications'
};

@Component({
  selector: 'app-equipment-listing',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, InrPipe, StatusBadgeComponent, OwnerSetupNoticeComponent, PhotoEditorComponent],
  template: `
    <main class="container-wide" style="padding-top: 2rem; padding-bottom: 3rem;">
      <h1>My equipment listings</h1>
      <p style="color: var(--color-text-muted);">Create a draft, then submit it for admin approval before it appears in public search.</p>

      <app-owner-setup-notice (verified)="onOwnerChecked($event)" />
      <div *ngIf="ownerVerified()" class="listing-workspace">

      <!-- No hover lift: a transform would make this card the containing block of the photo editor dialog and shake it. -->
      <section id="listing-form" class="card" style="padding: 1.5rem; transform: none;">
        <h2 style="font-size: 1.25rem;">{{ editingId() ? 'Edit listing' : 'Create listing' }}</h2>
        <form [formGroup]="form" (ngSubmit)="save()" style="display: grid; gap: 1rem; margin-top: 1rem;">
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
          <fieldset formArrayName="specifications" style="border: none; display: grid; gap: .5rem;">
            <legend style="font-weight: 600; margin-bottom: .35rem;">Specifications (e.g. Power: 1500 W, Weight: 11 kg)</legend>
            <div *ngFor="let spec of specifications.controls; let i = index" [formGroupName]="i"
                 style="display: grid; grid-template-columns: 1fr 1fr auto; gap: .5rem;">
              <input formControlName="name" placeholder="Name" maxlength="100" class="form-control" style="margin-top: 0;" [attr.aria-label]="'Specification ' + (i + 1) + ' name'" />
              <input formControlName="value" placeholder="Value" maxlength="255" class="form-control" style="margin-top: 0;" [attr.aria-label]="'Specification ' + (i + 1) + ' value'" />
              <button type="button" class="btn btn-secondary" (click)="removeSpecification(i)" aria-label="Remove specification">✕</button>
            </div>
            <button type="button" class="btn btn-secondary" (click)="addSpecification()" style="justify-self: start;">Add specification</button>
          </fieldset>
          <fieldset style="border: none; display: grid; gap: .5rem;">
            <legend style="font-weight: 600; margin-bottom: .35rem;">Photos</legend>
            <p class="muted" style="font-size: .875rem;">JPEG, PNG or WebP, up to 10 MB each. The first photo is the cover renters see in the
              catalogue. Photos are uploaded when you save.</p>
            <div *ngIf="savedPhotos().length || pendingPhotos().length" style="display: flex; flex-wrap: wrap; gap: .75rem;">
              <figure *ngFor="let image of savedPhotos(); let first = first" style="margin: 0; text-align: center;">
                <img [src]="image.imageUrl" alt="Saved photo" style="width: 120px; height: 90px; object-fit: contain; background: #fff; border-radius: var(--radius-md); border: 1px solid var(--color-border);" />
                <figcaption class="muted" style="font-size: .75rem;">{{ first ? 'Cover · saved' : 'Saved' }}</figcaption>
              </figure>
              <figure *ngFor="let photo of pendingPhotos(); let i = index" style="margin: 0; text-align: center; position: relative;">
                <img [src]="photo.previewUrl" [alt]="'New photo ' + photo.file.name" style="width: 120px; height: 90px; object-fit: contain; background: #fff; border-radius: var(--radius-md); border: 1px dashed var(--color-primary-light);" />
                <button type="button" (click)="removePendingPhoto(i)" [attr.aria-label]="'Remove photo ' + photo.file.name"
                        style="position: absolute; top: 4px; right: 4px; width: 24px; height: 24px; border-radius: 50%; border: none; background: rgba(15,23,42,.75); color: #fff; cursor: pointer; line-height: 1;">✕</button>
                <figcaption class="muted" style="font-size: .75rem;">
                  {{ savedPhotos().length === 0 && i === 0 ? 'Cover · new' : 'New' }} ·
                  <button type="button" (click)="reEditPhoto(i)" [attr.aria-label]="'Edit photo ' + photo.original.name"
                          style="border: none; background: none; padding: 0; color: var(--color-primary-light); cursor: pointer; font-size: .75rem;">Edit</button>
                </figcaption>
              </figure>
            </div>
            <app-photo-editor *ngIf="editQueue()[0] as next" [file]="next.file"
                              [position]="editQueue().length > 1 || editTotal() > 1 ? (editTotal() - editQueue().length + 1) + ' of ' + editTotal() : ''"
                              (saved)="photoEdited($event)" (cancelled)="photoSkipped()" />
            <label class="btn btn-secondary" style="justify-self: start;">
              Add photos
              <input type="file" accept="image/jpeg,image/png,image/webp" multiple (change)="choosePhotos($event)"
                     [disabled]="saving()" style="display: none;" />
            </label>
          </fieldset>
          <p *ngIf="error()" role="alert" style="color:var(--color-danger);">{{ error() }}</p>
          <p *ngIf="success()" role="status" style="color:var(--color-accent);">{{ success() }}</p>
          <div style="display: flex; gap: .75rem;">
            <button class="btn btn-primary" type="submit" [disabled]="saving()">{{ editingId() ? 'Save changes' : 'Save draft' }}</button>
            <button *ngIf="editingId()" class="btn btn-secondary" type="button" (click)="cancelEdit()">Cancel editing</button>
          </div>
        </form>
      </section>

      <aside class="listing-workspace-side" aria-label="Your listings">
      <h2 style="font-size: 1.25rem;">Your listings <span *ngIf="!loading()" class="muted" style="font-size: 1rem; font-weight: 500;">({{ listings().length }})</span></h2>
      <p *ngIf="loading()" role="status">Loading your listings…</p>
      <p *ngIf="!loading() && listings().length === 0" style="color:var(--color-text-muted);">You have no equipment listings yet.</p>
      <section *ngFor="let listing of listings()" class="card" style="padding:1rem;margin:.75rem 0;display:flex;flex-wrap:wrap;justify-content:space-between;gap:1rem;align-items:center;">
        <div style="flex: 1 1 220px; min-width: 0;">
          <strong>{{ listing.title }}</strong>
          <app-status-badge [status]="listing.status" style="margin-left:.5rem;" />
          <div style="color:var(--color-text-muted);">{{ listing.city }} · {{ listing.baseDailyRate | inr }}/day</div>
          <div *ngIf="images()[listing.id]?.length" style="display:flex;flex-wrap:wrap;gap:.5rem;margin-top:.75rem;">
            <img *ngFor="let image of images()[listing.id]" [src]="image.imageUrl" [alt]="listing.title" style="width:100px;height:75px;object-fit:contain;background:#fff;border-radius:.35rem;" />
          </div>
        </div>
        <div style="display:grid;gap:.5rem;">
          <button *ngIf="listing.status === 'DRAFT' || listing.status === 'REJECTED'" class="btn btn-secondary" type="button" (click)="edit(listing)" [disabled]="saving()">
            Edit
          </button>
          <label *ngIf="canManagePhotos(listing)" class="btn btn-secondary">
            Add image
            <input type="file" accept="image/jpeg,image/png,image/webp" (change)="uploadImage(listing, $event)" [disabled]="saving()" style="display:none;" />
          </label>
          <button *ngIf="listing.status === 'DRAFT' || listing.status === 'REJECTED'" class="btn btn-secondary" type="button" (click)="submit(listing)" [disabled]="saving()">
            Submit for approval
          </button>
          <a *ngIf="listing.status === 'ACTIVE'" class="btn btn-secondary" [routerLink]="['/equipment', listing.id]">Manage photos</a>
        </div>
      </section>
      </aside>
      </div>
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
    longitude: [null as number | null, [Validators.required, Validators.min(-180), Validators.max(180)]],
    specifications: this.formBuilder.nonNullable.array([this.specificationRow()])
  });

  /** Listing being edited, or null while creating a new one. */
  readonly editingId = signal<string | null>(null);

  /** Photos chosen in the form, uploaded when the listing is saved. */
  readonly pendingPhotos = signal<{ file: File; original: File; previewUrl: string }[]>([]);
  /** Photos waiting for the editor, first one open; `replace` is the form photo being re-edited, if any. */
  readonly editQueue = signal<{ file: File; replace: number | null }[]>([]);
  readonly editTotal = signal(0);
  /** Photos the listing being edited already has. */
  readonly savedPhotos = computed(() => {
    const id = this.editingId();
    return id ? this.images()[id] ?? [] : [];
  });

  get specifications(): FormArray<FormGroup<{ name: FormControl<string>; value: FormControl<string> }>> {
    return this.form.controls.specifications;
  }

  /** Null until the owner check finishes; the form and list show only for a verified owner. */
  readonly ownerVerified = signal<boolean | null>(null);

  ngOnInit(): void {
    this.catalogue.categories().subscribe({
      next: categories => this.categories.set(categories),
      error: () => this.error.set('Equipment categories could not be loaded.')
    });
  }

  onOwnerChecked(verified: boolean): void {
    this.ownerVerified.set(verified);
    if (verified) {
      this.loadListings();
    } else {
      this.loading.set(false);
    }
  }

  addSpecification(): void {
    this.specifications.push(this.specificationRow());
  }

  removeSpecification(index: number): void {
    this.specifications.removeAt(index);
    if (this.specifications.length === 0) {
      this.addSpecification();
    }
  }

  edit(listing: EquipmentListing): void {
    this.editingId.set(listing.id);
    this.error.set(null);
    this.success.set(null);
    this.resetForm();
    this.form.patchValue({
      categoryId: listing.categoryId,
      title: listing.title,
      description: listing.description,
      manufacturer: listing.manufacturer,
      modelNumber: listing.modelNumber,
      serialNumber: listing.serialNumber ?? '',
      yearOfManufacture: listing.yearOfManufacture,
      conditionGrade: listing.conditionGrade,
      baseDailyRate: listing.baseDailyRate,
      baseWeeklyRate: listing.baseWeeklyRate,
      securityDeposit: listing.securityDeposit,
      minimumRentalDays: listing.minimumRentalDays,
      deliverySupported: listing.deliverySupported,
      pickupSupported: listing.pickupSupported,
      deliveryChargeFlat: listing.deliveryChargeFlat,
      addressLine: listing.addressLine ?? '',
      city: listing.city,
      postalCode: listing.postalCode,
      latitude: listing.latitude,
      longitude: listing.longitude
    });
    const specs = Object.entries(listing.specifications ?? {});
    if (specs.length) {
      this.specifications.clear();
      for (const [name, value] of specs) {
        this.specifications.push(this.specificationRow(name, String(value ?? '')));
      }
    }
    document.getElementById('listing-form')?.scrollIntoView({ behavior: 'smooth' });
  }

  cancelEdit(): void {
    this.editingId.set(null);
    this.resetForm();
  }

  save(): void {
    this.error.set(null);
    this.success.set(null);
    const invalid = invalidFieldsMessage(this.form, LISTING_FIELD_LABELS);
    if (invalid) {
      this.error.set(invalid);
      return;
    }
    this.saving.set(true);
    const editingId = this.editingId();
    const request = editingId
      ? this.catalogue.updateListing(editingId, this.toRequest())
      : this.catalogue.createListing(this.toRequest());
    request.subscribe({
      next: async listing => {
        if (editingId) {
          this.replaceListing(listing);
        } else {
          this.listings.update(current => [listing, ...current]);
        }
        const photos = this.pendingPhotos().map(photo => photo.file);
        const failed: string[] = [];
        for (const file of photos) {
          try {
            await this.uploadFile(listing.id, file);
          } catch {
            failed.push(file.name);
          }
        }
        const uploaded = photos.length - failed.length;
        const photoNote = uploaded ? ` ${uploaded} ${uploaded === 1 ? 'photo' : 'photos'} uploaded.` : '';
        this.success.set((editingId ? 'Listing updated.' : 'Draft saved.') + photoNote
          + (editingId ? ' Submit it again when you are ready for review.' : ' Submit it when you are ready for review.'));
        if (failed.length) {
          this.error.set(`These photos could not be uploaded: ${failed.join(', ')}. Use "Add image" on the listing to try again.`);
        }
        this.editingId.set(null);
        this.resetForm();
        this.saving.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message || 'The listing could not be saved. Verify your owner account and try again.');
        this.saving.set(false);
      }
    });
  }

  private specificationRow(name = '', value = '') {
    return this.formBuilder.nonNullable.group({
      name: [name, Validators.maxLength(100)],
      value: [value, Validators.maxLength(255)]
    });
  }

  private resetForm(): void {
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
    this.specifications.clear();
    this.addSpecification();
    this.clearPendingPhotos();
  }

  /** Opens the editor for each chosen file in turn; edited photos join the form and upload when the listing is saved. */
  choosePhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    const rejected = files.filter(file => !isListingPhoto(file));
    const accepted = files.filter(isListingPhoto);
    this.editQueue.set(accepted.map(file => ({ file, replace: null })));
    this.editTotal.set(accepted.length);
    this.error.set(rejected.length
      ? `Not added (must be JPEG, PNG or WebP up to 10 MB): ${rejected.map(file => file.name).join(', ')}.`
      : null);
  }

  /** Re-frames a photo already in the form, starting again from the file as chosen. */
  reEditPhoto(index: number): void {
    const photo = this.pendingPhotos()[index];
    if (photo) {
      this.editQueue.set([{ file: photo.original, replace: index }]);
      this.editTotal.set(1);
    }
  }

  photoEdited(edited: File): void {
    const [current, ...rest] = this.editQueue();
    if (!current) {
      return;
    }
    const entry = { file: edited, original: current.file, previewUrl: URL.createObjectURL(edited) };
    if (current.replace === null) {
      this.pendingPhotos.update(photos => [...photos, entry]);
    } else {
      URL.revokeObjectURL(this.pendingPhotos()[current.replace].previewUrl);
      this.pendingPhotos.update(photos => photos.map((photo, i) => i === current.replace ? entry : photo));
    }
    this.editQueue.set(rest);
  }

  photoSkipped(): void {
    this.editQueue.update(([, ...rest]) => rest);
  }

  removePendingPhoto(index: number): void {
    const photo = this.pendingPhotos()[index];
    if (photo) {
      URL.revokeObjectURL(photo.previewUrl);
      this.pendingPhotos.update(current => current.filter((_, i) => i !== index));
    }
  }

  private clearPendingPhotos(): void {
    for (const photo of this.pendingPhotos()) {
      URL.revokeObjectURL(photo.previewUrl);
    }
    this.pendingPhotos.set([]);
    this.editQueue.set([]);
  }

  /** Presigns, uploads straight to private storage, then confirms; the image is added to the listing's gallery. */
  private async uploadFile(listingId: string, file: File): Promise<void> {
    const image = await this.catalogue.uploadImage(listingId, file);
    this.images.update(current => ({ ...current, [listingId]: [...(current[listingId] ?? []), image] }));
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

  /** Photos can change on drafts, pending, live and rejected listings (the backend allows the same). */
  canManagePhotos(listing: EquipmentListing): boolean {
    return ['DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'REJECTED'].includes(listing.status);
  }

  async uploadImage(listing: EquipmentListing, event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!isListingPhoto(file)) {
      this.error.set('Choose a JPEG, PNG, or WebP image no larger than 10 MB.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    try {
      await this.uploadFile(listing.id, file);
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
      specifications: Object.fromEntries(value.specifications
        .filter(spec => spec.name.trim() && spec.value.trim())
        .map(spec => [spec.name.trim(), spec.value.trim()])),
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
