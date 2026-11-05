import { CommonModule } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { InrPipe } from '../../shared/inr.pipe';
import { PhotoEditorComponent } from '../../shared/photo-editor.component';
import { EquipmentCardComponent } from './equipment-card.component';
import {
  EquipmentCatalogueService,
  EquipmentImage,
  EquipmentListing,
  EquipmentReview,
  isListingPhoto
} from './equipment-catalogue.service';

@Component({
  selector: 'app-equipment-details',
  standalone: true,
  imports: [CommonModule, RouterLink, InrPipe, EquipmentCardComponent, ConfirmDialogComponent, PhotoEditorComponent],
  template: `
    <main class="container-wide" style="padding-top: 1.75rem; padding-bottom: 3rem;">
      <nav aria-label="Breadcrumb" style="font-size: .875rem; margin-bottom: 1rem;">
        <a routerLink="/equipment">Equipment catalogue</a>
        <ng-container *ngIf="listing() as item">
          <span class="muted"> › </span><a [routerLink]="['/equipment/category', item.categoryId]">{{ item.categoryName }}</a>
        </ng-container>
      </nav>
      <p *ngIf="loading()" role="status">Loading listing…</p>
      <p *ngIf="error()" role="alert" style="color: var(--color-danger);">{{ error() }}</p>

      <article *ngIf="listing() as item" class="card" style="padding: 1.5rem; display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 2rem;">
        <section aria-label="Equipment photos">
          <img *ngIf="selectedImage() as image; else noPhotos" [src]="image.imageUrl" [alt]="item.title"
               style="display: block; width: 100%; aspect-ratio: 4 / 3; object-fit: contain; border-radius: var(--radius-md); background: #fff; border: 1px solid var(--color-border);" />
          <ng-template #noPhotos>
            <div style="display: flex; align-items: center; justify-content: center; width: 100%; aspect-ratio: 4 / 3; border-radius: var(--radius-md); background: var(--color-bg-muted); color: var(--color-text-subtle);">
              No photo yet
            </div>
          </ng-template>
          <div *ngIf="images().length > 1" style="display: flex; gap: .5rem; overflow-x: auto; margin-top: .75rem;">
            <button *ngFor="let image of images(); let i = index" type="button" (click)="selectedImage.set(image)"
                    [attr.aria-label]="'Show photo ' + (i + 1)"
                    [style.outline]="selectedImage() === image ? '2px solid var(--color-primary-light)' : 'none'"
                    style="padding: 0; border: 1px solid var(--color-border); border-radius: var(--radius-sm); background: none; cursor: pointer;">
              <img [src]="image.imageUrl" alt="" style="display: block; width: 72px; height: 54px; object-fit: contain; background: #fff; border-radius: var(--radius-sm);" />
            </button>
          </div>
          <p *ngIf="imageError()" role="status" style="color:var(--color-text-muted); margin-top: .5rem;">Equipment photos are temporarily unavailable.</p>

          <section *ngIf="isMine()" aria-label="Manage photos"
                   style="margin-top: 1rem; padding: 1rem; border: 1px dashed var(--color-border-hover); border-radius: var(--radius-md); background: var(--color-bg-base);">
            <div style="display: flex; justify-content: space-between; align-items: baseline; gap: .5rem; flex-wrap: wrap;">
              <h2 style="font-size: 1rem;">Manage photos</h2>
              <span class="muted" style="font-size: .75rem;">Only you, the owner, see this</span>
            </div>
            <p class="muted" style="font-size: .8125rem; margin: .25rem 0 .75rem;">The cover is the photo on catalogue cards. Changes show to renters
              straight away. JPEG, PNG or WebP up to 10 MB.</p>
            <div *ngIf="images().length" style="display: flex; flex-wrap: wrap; gap: .75rem;">
              <figure *ngFor="let image of images(); let i = index" style="margin: 0; width: 132px;">
                <img [src]="image.imageUrl" [alt]="'Photo ' + (i + 1)"
                     [style.outline]="image.primary ? '2px solid var(--color-accent)' : 'none'"
                     style="display: block; width: 132px; height: 99px; object-fit: contain; background: #fff; border-radius: var(--radius-sm);" />
                <figcaption style="display: flex; gap: .35rem; margin-top: .35rem; align-items: center; flex-wrap: wrap;">
                  <span *ngIf="image.primary" class="badge badge-verified">Cover</span>
                  <button *ngIf="!image.primary" type="button" class="btn btn-secondary" style="padding: .2rem .5rem; font-size: .75rem;"
                          [disabled]="photoBusy()" (click)="makeCover(image)" [attr.aria-label]="'Make photo ' + (i + 1) + ' the cover'">Make cover</button>
                  <button type="button" class="btn btn-secondary" style="padding: .2rem .5rem; font-size: .75rem; color: var(--color-danger);"
                          [disabled]="photoBusy()" (click)="photoToDelete.set(image)" [attr.aria-label]="'Delete photo ' + (i + 1)">Delete</button>
                </figcaption>
              </figure>
            </div>
            <label class="btn btn-primary" style="margin-top: .85rem;" [style.opacity]="photoBusy() ? .6 : 1">
              {{ photoBusy() ? 'Working…' : 'Add photos' }}
              <input type="file" accept="image/jpeg,image/png,image/webp" multiple (change)="addPhotos($event)"
                     [disabled]="photoBusy()" style="display: none;" />
            </label>
            <p *ngIf="photoError()" role="alert" style="color: var(--color-danger); margin-top: .5rem; font-size: .875rem;">{{ photoError() }}</p>
            <p *ngIf="photoNotice()" role="status" style="color: var(--color-accent); margin-top: .5rem; font-size: .875rem;">{{ photoNotice() }}</p>
          </section>
          <app-photo-editor *ngIf="photoQueue()[0] as next" [file]="next"
                            [position]="photoQueueTotal() > 1 ? (photoQueueTotal() - photoQueue().length + 1) + ' of ' + photoQueueTotal() : ''"
                            (saved)="photoEdited($event)" (cancelled)="photoSkipped()" />
          <app-confirm-dialog *ngIf="photoToDelete() as doomed" title="Delete this photo?"
                              message="Renters will no longer see it. This cannot be undone." confirmLabel="Delete photo"
                              [danger]="true" [busy]="photoBusy()" (confirmed)="deletePhoto(doomed)" (cancelled)="photoToDelete.set(null)" />
        </section>

        <section>
          <p style="color: var(--color-primary-light); font-weight: 700;">{{ item.categoryName }}</p>
          <h1 style="font-size: 1.75rem; line-height: 1.25;">{{ item.title }}</h1>
          <p style="color: var(--color-text-muted);">{{ item.manufacturer }} {{ item.modelNumber }} · {{ item.conditionGrade }}</p>
          <p style="margin-top: .35rem;">
            <span *ngIf="item.averageRating !== null; else noRating">
              <strong style="color: var(--color-accent-amber);">{{ stars(item.averageRating) }}</strong>
              {{ item.averageRating }} / 5 · {{ item.reviewCount }} {{ item.reviewCount === 1 ? 'review' : 'reviews' }}
            </span>
            <ng-template #noRating><span class="muted">No reviews yet</span></ng-template>
          </p>
          <p style="margin: 1rem 0 .25rem;"><strong style="font-size: 1.6rem;">{{ item.baseDailyRate | inr }}</strong> / day</p>
          <a *ngIf="item.pickupSupported || item.deliverySupported" class="btn btn-primary" [routerLink]="['/equipment', item.id, 'book']" style="margin: .75rem 0 1.25rem;">
            Add to booking
          </a>
          <p style="margin-bottom: 1.25rem;">{{ item.description }}</p>
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
        </section>
      </article>

      <section *ngIf="listing() as item" class="card" style="padding: 1.5rem; margin-top: 1.5rem;">
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

      <section *ngIf="listing() && related().length" aria-label="Related equipment" style="margin-top: 2rem;">
        <div style="display: flex; justify-content: space-between; align-items: baseline; gap: 1rem; margin-bottom: 1rem;">
          <h2 style="font-size: 1.35rem;">More in {{ listing()!.categoryName }}</h2>
          <a [routerLink]="['/equipment/category', listing()!.categoryId]" style="font-weight: 600;">See all →</a>
        </div>
        <div class="equipment-grid">
          <app-equipment-card *ngFor="let other of related()" [listing]="other" />
        </div>
      </section>
    </main>
  `
})
export class EquipmentDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly catalogue = inject(EquipmentCatalogueService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly auth = inject(AuthService);

  readonly listing = signal<EquipmentListing | null>(null);
  readonly images = signal<EquipmentImage[]>([]);
  readonly selectedImage = signal<EquipmentImage | null>(null);
  readonly imageError = signal(false);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly reviews = signal<EquipmentReview[]>([]);
  readonly reviewPage = signal(0);
  readonly reviewPages = signal(0);
  readonly related = signal<EquipmentListing[]>([]);

  /** True when the signed-in owner owns this listing; they then get the photo tools. */
  readonly isMine = signal(false);
  readonly photoBusy = signal(false);
  readonly photoError = signal<string | null>(null);
  readonly photoNotice = signal<string | null>(null);
  readonly photoToDelete = signal<EquipmentImage | null>(null);
  /** Chosen photos waiting for the editor; the first one is open. Each uploads as soon as it is saved. */
  readonly photoQueue = signal<File[]>([]);
  readonly photoQueueTotal = signal(0);
  private photoBatch = { added: 0, rejected: [] as string[], failed: [] as string[] };

  addPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    if (!this.listing() || !files.length) {
      return;
    }
    const accepted = files.filter(isListingPhoto);
    this.photoBatch = { added: 0, rejected: files.filter(file => !isListingPhoto(file)).map(file => file.name), failed: [] };
    this.photoError.set(null);
    this.photoNotice.set(null);
    this.photoQueue.set(accepted);
    this.photoQueueTotal.set(accepted.length);
    if (!accepted.length) {
      this.finishPhotoBatch();
    }
  }

  async photoEdited(file: File): Promise<void> {
    const listing = this.listing()!;
    this.photoBusy.set(true);
    try {
      await this.catalogue.uploadImage(listing.id, file);
      this.photoBatch.added++;
    } catch {
      this.photoBatch.failed.push(file.name);
    }
    this.photoBusy.set(false);
    this.nextPhoto();
  }

  photoSkipped(): void {
    this.nextPhoto();
  }

  private nextPhoto(): void {
    this.photoQueue.update(([, ...rest]) => rest);
    if (!this.photoQueue().length) {
      this.finishPhotoBatch();
    }
  }

  private finishPhotoBatch(): void {
    const { added, rejected, failed } = this.photoBatch;
    this.catalogue.ownerImages(this.listing()!.id).subscribe({
      next: images => this.showImages(images),
      error: () => undefined
    });
    this.photoNotice.set(added ? `${added} ${added === 1 ? 'photo' : 'photos'} added.` : null);
    const problems = [
      rejected.length ? `Not added (must be JPEG, PNG or WebP up to 10 MB): ${rejected.join(', ')}.` : '',
      failed.length ? `Upload failed, please try again: ${failed.join(', ')}.` : ''
    ].filter(Boolean).join(' ');
    this.photoError.set(problems || null);
  }

  makeCover(image: EquipmentImage): void {
    this.changePhotos(this.catalogue.makeCoverImage(this.listing()!.id, image.imageId), 'Cover photo updated.');
  }

  deletePhoto(image: EquipmentImage): void {
    this.changePhotos(this.catalogue.deleteImage(this.listing()!.id, image.imageId), 'Photo deleted.');
  }

  private changePhotos(request: Observable<EquipmentImage[]>, done: string): void {
    this.photoBusy.set(true);
    this.photoError.set(null);
    this.photoNotice.set(null);
    request.subscribe({
      next: images => {
        this.showImages(images);
        this.photoToDelete.set(null);
        this.photoBusy.set(false);
        this.photoNotice.set(done);
      },
      error: (failure: Error) => {
        this.photoToDelete.set(null);
        this.photoBusy.set(false);
        this.photoError.set(failure.message || 'The photo could not be changed. Please try again.');
      }
    });
  }

  /** Shows a photo list, keeping the large photo on the same image when it is still there. */
  private showImages(images: EquipmentImage[]): void {
    this.images.set(images);
    const current = this.selectedImage();
    this.selectedImage.set(images.find(image => image.imageId === current?.imageId) ?? images[0] ?? null);
  }

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
    // Related items link to this same route, so follow the parameter rather than reading it once.
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const equipmentId = params.get('equipmentId');
      if (!equipmentId) {
        this.error.set('Equipment listing was not specified.');
        this.loading.set(false);
        return;
      }
      this.load(equipmentId);
    });
  }

  private load(equipmentId: string): void {
    this.loading.set(true);
    this.error.set(null);
    this.listing.set(null);
    this.images.set([]);
    this.selectedImage.set(null);
    this.imageError.set(false);
    this.reviews.set([]);
    this.related.set([]);
    this.isMine.set(false);
    this.photoError.set(null);
    this.photoNotice.set(null);
    this.catalogue.get(equipmentId).subscribe({
      next: listing => {
        this.listing.set(listing);
        this.loadReviews(0);
        this.loading.set(false);
        this.catalogue.images(equipmentId).subscribe({
          next: images => this.showImages(images),
          error: () => this.imageError.set(true)
        });
        // The owner-only photo list answers just for this listing's owner, which is how the page knows it is theirs.
        if (this.auth.isOwner()) {
          this.catalogue.ownerImages(equipmentId).subscribe({
            next: images => {
              this.isMine.set(true);
              this.showImages(images);
            },
            error: () => this.isMine.set(false)
          });
        }
        this.catalogue.search({ categoryId: listing.categoryId, page: 0, size: 9 }).subscribe({
          next: result => this.related.set(result.content.filter(other => other.id !== equipmentId).slice(0, 8)),
          error: () => this.related.set([])
        });
      },
      error: () => {
        this.error.set('This listing is unavailable or is no longer approved.');
        this.loading.set(false);
      }
    });
  }
}
