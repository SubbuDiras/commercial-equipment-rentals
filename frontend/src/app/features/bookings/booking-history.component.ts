import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InrPipe } from '../../shared/inr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { BookingApiService, RenterBookingSummary } from './booking-api.service';

const PAGE_SIZE = 20;

@Component({
  selector: 'app-booking-history',
  standalone: true,
  imports: [DatePipe, RouterLink, InrPipe, StatusBadgeComponent],
  template: `
    <section class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <h1>My bookings</h1>
      @if (loading()) {
        <p role="status">Loading your booking history…</p>
      }
      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (!loading() && !error() && bookings().length === 0) {
        <p class="muted">You haven’t requested a rental yet.</p>
      }
      @for (booking of bookings(); track booking.bookingId) {
        <a class="card" [routerLink]="['/bookings', booking.bookingId]"
           style="display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap; padding: 1.25rem; margin: 1rem 0; color: inherit;">
          <div>
            <strong>{{ booking.equipmentTitle }}</strong>
            <div class="muted" style="font-size: .875rem;">{{ booking.bookingReference }}</div>
            <div>{{ booking.startTime | date:'medium' }} – {{ booking.endTime | date:'medium' }}</div>
          </div>
          <div style="text-align: right;">
            <app-status-badge [status]="booking.status" />
            <div style="font-weight: 700; margin-top: .5rem;">{{ booking.pricing.totalPayableAmount | inr }}</div>
          </div>
        </a>
      }
      @if (totalPages() > 1) {
        <div style="display: flex; gap: .75rem; align-items: center; justify-content: center; margin: 1rem 0;">
          <button type="button" class="btn btn-secondary" (click)="load(page() - 1)" [disabled]="page() === 0">Previous</button>
          <span>Page {{ page() + 1 }} of {{ totalPages() }}</span>
          <button type="button" class="btn btn-secondary" (click)="load(page() + 1)" [disabled]="page() + 1 >= totalPages()">Next</button>
        </div>
      }
      <a routerLink="/" class="btn btn-secondary">Browse equipment</a>
    </section>
  `
})
export class BookingHistoryComponent implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  readonly bookings = signal<RenterBookingSummary[]>([]);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.loading.set(true);
    this.bookingApi.list(page, PAGE_SIZE).subscribe({
      next: result => {
        this.bookings.set(result.content);
        this.page.set(result.page);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message);
        this.loading.set(false);
      }
    });
  }
}
