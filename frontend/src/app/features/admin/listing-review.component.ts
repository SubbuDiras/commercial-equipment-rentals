import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EquipmentCatalogueService, EquipmentListing } from '../catalogue/equipment-catalogue.service';

@Component({
  selector: 'app-listing-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <main class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <h1>Listing approval queue</h1>
      <p style="color:var(--color-text-muted);">Only approved listings are visible in the public catalogue.</p>
      <p *ngIf="loading()" role="status">Loading pending listings…</p>
      <p *ngIf="error()" role="alert" style="color:var(--color-danger);">{{ error() }}</p>
      <p *ngIf="!loading() && !error() && pending().length === 0">No listings are waiting for review.</p>
      <article *ngFor="let listing of pending()" class="card" style="padding:1.25rem;margin:1rem 0;">
        <p style="color:var(--color-primary-light);font-weight:700;">{{ listing.categoryName }}</p>
        <h2 style="font-size:1.25rem;">{{ listing.title }}</h2>
        <p>{{ listing.manufacturer }} {{ listing.modelNumber }} · {{ listing.conditionGrade }}</p>
        <p>{{ listing.description }}</p>
        <p>{{ listing.city }} · {{ listing.baseDailyRate | currency:'INR' }}/day</p>
        <label *ngIf="rejectingId() === listing.id">
          Rejection reason
          <input [(ngModel)]="rejectionReason" [name]="'reason-' + listing.id" maxlength="500" required aria-required="true"
                 [attr.aria-invalid]="reasonMissing()" (ngModelChange)="reasonMissing.set(false)"
                 [style.border-color]="reasonMissing() ? 'var(--color-danger)' : null"
                 style="display:block;width:100%;padding:.6rem;margin:.35rem 0 .25rem;" />
        </label>
        <small *ngIf="rejectingId() === listing.id" [attr.role]="reasonMissing() ? 'alert' : null"
               [style.color]="reasonMissing() ? 'var(--color-danger)' : 'var(--color-text-muted)'" style="display:block;margin-bottom:1rem;">
          {{ reasonMissing() ? 'Please enter a reason before you confirm the rejection.' : 'Required. The owner sees this reason.' }}
        </small>
        <div style="display:flex;gap:.75rem;">
          <button class="btn btn-primary" type="button" (click)="review(listing, true)" [disabled]="saving()">Approve</button>
          <button *ngIf="rejectingId() !== listing.id" class="btn btn-secondary" type="button" (click)="startReject(listing.id)">Reject</button>
          <button *ngIf="rejectingId() === listing.id" class="btn btn-secondary" type="button" (click)="review(listing, false)" [disabled]="saving()">Confirm rejection</button>
          <button *ngIf="rejectingId() === listing.id" class="btn btn-secondary" type="button" (click)="cancelReject()">Cancel</button>
        </div>
      </article>
    </main>
  `
})
export class ListingReviewComponent implements OnInit {
  private readonly catalogue = inject(EquipmentCatalogueService);

  readonly pending = signal<EquipmentListing[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly rejectingId = signal<string | null>(null);
  rejectionReason = '';
  readonly reasonMissing = signal(false);

  ngOnInit(): void {
    this.load();
  }

  startReject(id: string): void {
    this.rejectingId.set(id);
    this.rejectionReason = '';
    this.reasonMissing.set(false);
  }

  cancelReject(): void {
    this.rejectingId.set(null);
    this.rejectionReason = '';
    this.reasonMissing.set(false);
  }

  review(listing: EquipmentListing, approve: boolean): void {
    const reason = this.rejectionReason.trim();
    if (!approve && !reason) {
      this.reasonMissing.set(true);
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.catalogue.reviewListing(listing.id, approve, reason).subscribe({
      next: () => {
        this.pending.update(current => current.filter(item => item.id !== listing.id));
        this.cancelReject();
        this.saving.set(false);
      },
      error: () => {
        this.error.set('The listing review could not be saved. Refresh the queue and try again.');
        this.saving.set(false);
      }
    });
  }

  private load(): void {
    this.catalogue.pendingListings().subscribe({
      next: listings => {
        this.pending.set(listings);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Pending listings could not be loaded.');
        this.loading.set(false);
      }
    });
  }
}
