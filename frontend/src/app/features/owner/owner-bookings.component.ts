import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { InrPipe } from '../../shared/inr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { BookingApiService, OwnerBookingSummary } from '../bookings/booking-api.service';
import { CancelDialogComponent } from '../bookings/cancel-dialog.component';
import { OwnerSetupNoticeComponent } from './owner-setup-notice.component';

const TABS = [
  { key: 'requests', label: 'Requests', statuses: ['REQUESTED'] },
  { key: 'upcoming', label: 'Upcoming', statuses: ['PAYMENT_PENDING', 'PAYMENT_FAILED', 'CONFIRMED', 'HANDOVER_PENDING'] },
  { key: 'active', label: 'Active', statuses: ['IN_PROGRESS', 'RETURN_PENDING', 'RETURNED', 'DISPUTED', 'INSPECTION_PASSED'] },
  { key: 'past', label: 'Past', statuses: ['COMPLETED', 'CLOSED', 'CANCELLED_BY_RENTER', 'CANCELLED_BY_OWNER',
      'OWNER_REJECTED', 'REQUEST_EXPIRED', 'HOLD_EXPIRED'] },
  { key: 'all', label: 'All', statuses: [] as string[] }
];

/** Owner-cancellable statuses (a REQUESTED booking is declined instead). */
const CANCELLABLE = ['PAYMENT_PENDING', 'PAYMENT_FAILED', 'CONFIRMED', 'HANDOVER_PENDING'];
const PAGE_SIZE = 20;

@Component({
  selector: 'app-owner-bookings',
  standalone: true,
  imports: [DatePipe, RouterLink, ConfirmDialogComponent, InrPipe, StatusBadgeComponent, CancelDialogComponent, OwnerSetupNoticeComponent],
  template: `
    <main class="container" style="max-width: 1100px; padding: 3rem 1rem;">
      <h1>Bookings for my equipment</h1>
      <p class="muted">Accept or decline new requests within 4 hours. Open a booking to handle handover, return and payouts.</p>

      <app-owner-setup-notice (verified)="onOwnerChecked($event)" />
      @if (ownerVerified()) {

      <div class="tabs" role="tablist">
        @for (tab of tabs; track tab.key) {
          <button type="button" class="tab" role="tab" [class.active]="tab.key === activeTab()"
                  [attr.aria-selected]="tab.key === activeTab()" (click)="select(tab.key)">{{ tab.label }}</button>
        }
      </div>

      @if (notice()) {
        <div class="alert alert-success" role="status">{{ notice() }}</div>
      }
      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (loading()) {
        <p role="status">Loading bookings…</p>
      } @else if (bookings().length === 0) {
        <p class="muted">No bookings here.</p>
      }

      @for (booking of bookings(); track booking.bookingId) {
        <article class="card" style="padding: 1.25rem; margin: .75rem 0;">
          <div style="display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap;">
            <div>
              <a [routerLink]="['/bookings', booking.bookingId]" style="font-weight: 700;">{{ booking.equipmentTitle }}</a>
              <div class="muted" style="font-size: .875rem;">{{ booking.bookingReference }} · requested by {{ booking.renterName }}
                {{ booking.requestedAt | date:'short' }}</div>
              <div>{{ booking.startTime | date:'medium' }} – {{ booking.endTime | date:'medium' }}</div>
              <div class="muted" style="font-size: .875rem;">{{ booking.deliveryOption === 'OWNER_DELIVERY' ? 'You deliver' : 'Renter collects' }}</div>
            </div>
            <div style="text-align: right;">
              <app-status-badge [status]="booking.status" />
              <div style="font-weight: 700; margin-top: .5rem;">{{ booking.pricing.totalPayableAmount | inr }}</div>
              @if (booking.status === 'REQUESTED' && booking.holdExpiresAt) {
                <div class="muted" style="font-size: .8125rem;">Respond by {{ booking.holdExpiresAt | date:'short' }}</div>
              }
            </div>
          </div>
          <div style="display: flex; gap: .5rem; margin-top: 1rem; flex-wrap: wrap;">
            <a class="btn btn-secondary" [routerLink]="['/bookings', booking.bookingId]">Open</a>
            @if (booking.status === 'REQUESTED') {
              <button type="button" class="btn btn-primary" (click)="accept(booking)" [disabled]="busy()">Accept</button>
              <button type="button" class="btn btn-secondary" (click)="rejecting.set(booking)" [disabled]="busy()">Decline</button>
            }
            @if (cancellable(booking.status)) {
              <button type="button" class="btn btn-secondary" (click)="cancelling.set(booking)">Cancel</button>
            }
          </div>
        </article>
      }

      @if (totalPages() > 1) {
        <div style="display: flex; gap: .75rem; align-items: center; justify-content: center; margin-top: 1rem;">
          <button type="button" class="btn btn-secondary" (click)="go(page() - 1)" [disabled]="page() === 0">Previous</button>
          <span>Page {{ page() + 1 }} of {{ totalPages() }}</span>
          <button type="button" class="btn btn-secondary" (click)="go(page() + 1)" [disabled]="page() + 1 >= totalPages()">Next</button>
        </div>
      }

      @if (rejecting(); as booking) {
        <app-confirm-dialog title="Decline this request?" [message]="booking.renterName + ' will be told and the dates released.'"
                            confirmLabel="Decline" reasonLabel="Reason (shown to the renter)" [danger]="true" [busy]="busy()"
                            (confirmed)="reject(booking, $event)" (cancelled)="rejecting.set(null)" />
      }
      @if (cancelling(); as booking) {
        <app-cancel-dialog [bookingId]="booking.bookingId" [asOwner]="true"
                           (done)="cancelled()" (closed)="cancelling.set(null)" />
      }
      }
    </main>
  `
})
export class OwnerBookingsComponent {
  private readonly api = inject(BookingApiService);

  readonly tabs = TABS;
  readonly activeTab = signal('requests');
  readonly bookings = signal<OwnerBookingSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly rejecting = signal<OwnerBookingSummary | null>(null);
  readonly cancelling = signal<OwnerBookingSummary | null>(null);

  /** Null until the owner check finishes; bookings show only for a verified owner. */
  readonly ownerVerified = signal<boolean | null>(null);

  /** Called by the owner check instead of loading on init, so an unverified owner sees what to do instead. */
  onOwnerChecked(verified: boolean): void {
    this.ownerVerified.set(verified);
    if (verified) {
      this.load();
    } else {
      this.loading.set(false);
    }
  }

  select(tab: string): void {
    this.activeTab.set(tab);
    this.page.set(0);
    this.notice.set(null);
    this.load();
  }

  go(page: number): void {
    this.page.set(page);
    this.load();
  }

  cancellable(status: string): boolean {
    return CANCELLABLE.includes(status);
  }

  accept(booking: OwnerBookingSummary): void {
    this.act(this.api.accept(booking.bookingId),
      `Accepted ${booking.bookingReference}. ${booking.renterName} has 30 minutes to pay.`);
  }

  reject(booking: OwnerBookingSummary, reason: string): void {
    this.act(this.api.reject(booking.bookingId, reason), `Declined ${booking.bookingReference}.`);
  }

  cancelled(): void {
    this.cancelling.set(null);
    this.notice.set('Booking cancelled. The renter has been refunded according to the policy.');
    this.load();
  }

  private act(request: ReturnType<BookingApiService['accept']>, message: string): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.rejecting.set(null);
        this.notice.set(message);
        this.load();
      },
      error: (failure: Error) => {
        this.busy.set(false);
        this.error.set(failure.message);
      }
    });
  }

  private load(): void {
    const tab = TABS.find(candidate => candidate.key === this.activeTab()) ?? TABS[0];
    this.loading.set(true);
    this.error.set(null);
    this.api.ownerBookings(tab.statuses, this.page(), PAGE_SIZE).subscribe({
      next: page => {
        this.bookings.set(page.content);
        this.totalPages.set(page.totalPages);
        this.loading.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message);
        this.loading.set(false);
      }
    });
  }
}
