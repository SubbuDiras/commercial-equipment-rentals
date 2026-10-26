import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval } from 'rxjs';
import { PilotMetrics, PilotMetricsService } from './pilot-metrics.service';

@Component({
  selector: 'app-pilot-metrics',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <main class="container" style="max-width: 1100px; padding: 3rem 1rem;">
      <header style="display:flex; justify-content:space-between; align-items:flex-end; gap:1rem; flex-wrap:wrap;">
        <div>
          <h1>Marketplace operations</h1>
          <p>Booking conversion, owner response, disputes, and settlement take rate.</p>
        </div>
        <div style="display:flex; align-items:flex-end; gap:.75rem; flex-wrap:wrap;">
          <label>From
            <input type="date" [(ngModel)]="from" (change)="refresh()" />
          </label>
          <label>To
            <input type="date" [(ngModel)]="to" (change)="refresh()" />
          </label>
          <button class="btn btn-secondary" type="button" (click)="refresh()">Refresh</button>
        </div>
      </header>

      <p *ngIf="loading()" role="status">Loading pilot metrics…</p>
      <p *ngIf="error()" role="alert" style="color:var(--color-danger);">{{ error() }}</p>

      <section *ngIf="metrics() as data" aria-label="Pilot performance metrics">
        <p>Period: {{ data.from | date:'mediumDate' }} – {{ data.to | date:'mediumDate' }}.
          Refreshes every 30 seconds.</p>
        <div class="grid grid-cols-3" style="gap:1rem;">
          <article class="card" style="padding:1.25rem;">
            <h2>Booking conversion</h2>
            <strong>{{ data.bookingConversionPercent | number:'1.0-2' }}%</strong>
            <p>{{ data.capturedBookings }} captured / {{ data.bookingRequests }} requests</p>
          </article>
          <article class="card" style="padding:1.25rem;">
            <h2>Owner response time</h2>
            <strong>{{ data.averageOwnerResponseMinutes === null ? '—' : (data.averageOwnerResponseMinutes | number:'1.0-1') + ' min' }}</strong>
            <p>Average to first accept or reject response</p>
          </article>
          <article class="card" style="padding:1.25rem;">
            <h2>Disputes</h2>
            <strong>{{ data.openDisputes }} open</strong>
            <p>{{ data.disputes }} created in period</p>
          </article>
          <article class="card" style="padding:1.25rem;">
            <h2>Captured payments</h2>
            <strong>{{ data.capturedGrossAmount | currency:'INR':'symbol':'1.0-2' }}</strong>
            <p>Successful booking payments in cohort</p>
          </article>
          <article class="card" style="padding:1.25rem;">
            <h2>Settlement take rate</h2>
            <strong>{{ data.takeRatePercent | number:'1.0-2' }}%</strong>
            <p>{{ data.platformCommissionAmount | currency:'INR':'symbol':'1.0-2' }} commission on
              {{ data.rentalGrossAmount | currency:'INR':'symbol':'1.0-2' }} rental gross</p>
          </article>
          <article class="card" style="padding:1.25rem;">
            <h2>Settlement queue</h2>
            <strong>{{ data.pendingSettlements }} pending</strong>
            <p>{{ data.releasedSettlements }} released in period</p>
          </article>
          <article class="card" style="padding:1.25rem;">
            <h2>Supply</h2>
            <strong>{{ data.verifiedOwners }} verified owners</strong>
            <p>{{ data.activeListings }} active equipment listings</p>
          </article>
        </div>
      </section>
    </main>
  `
})
export class PilotMetricsComponent implements OnInit {
  private readonly metricsService = inject(PilotMetricsService);
  private readonly destroyRef = inject(DestroyRef);
  readonly today = new Date().toISOString().slice(0, 10);
  from = this.defaultFrom();
  to = this.today;
  readonly metrics = signal<PilotMetrics | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.refresh();
    interval(30_000).pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.refresh());
  }

  refresh(): void {
    if (!this.from || !this.to || this.from > this.to) {
      this.error.set('Choose a valid date range.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.metricsService.get(this.from, this.to).pipe(
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: metrics => {
        this.metrics.set(metrics);
        this.loading.set(false);
      },
      error: (failure: HttpErrorResponse) => {
        this.error.set(failure.error?.detail ?? failure.message);
        this.loading.set(false);
      }
    });
  }

  private defaultFrom(): string {
    const date = new Date();
    date.setDate(date.getDate() - 29);
    return date.toISOString().slice(0, 10);
  }
}
