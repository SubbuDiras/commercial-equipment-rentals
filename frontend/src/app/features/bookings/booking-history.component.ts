import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Booking, BookingApiService } from './booking-api.service';

@Component({
  selector: 'app-booking-history',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <section class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <h1>My bookings</h1>
      <p *ngIf="loading()">Loading your booking history…</p>
      <p *ngIf="error()" role="alert" style="color: var(--color-danger);">{{ error() }}</p>
      <p *ngIf="!loading() && !error() && bookings().length === 0">You haven’t requested a rental yet.</p>
      <div *ngFor="let booking of bookings()" class="card" style="padding: 1.25rem; margin: 1rem 0;">
        <div style="display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap;">
          <div>
            <strong>{{ booking.bookingReference }}</strong>
            <div>Equipment {{ booking.equipmentId }}</div>
            <div>{{ booking.startTime | date:'medium' }} – {{ booking.endTime | date:'medium' }}</div>
          </div>
          <div style="text-align: right;">
            <span class="badge badge-role">{{ booking.status }}</span>
            <div style="font-weight: 700; margin-top: .5rem;">{{ booking.pricing.totalPayableAmount | currency:'INR' }}</div>
          </div>
        </div>
      </div>
      <a routerLink="/" class="btn btn-secondary">Browse equipment</a>
    </section>
  `
})
export class BookingHistoryComponent implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  readonly bookings = signal<Booking[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.bookingApi.list().subscribe({
      next: bookings => {
        this.bookings.set(bookings);
        this.loading.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message);
        this.loading.set(false);
      }
    });
  }
}
