import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { InrPipe } from '../../shared/inr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { DisputeApiService, DisputeSummary } from './dispute-api.service';

const TABS = [
  { status: 'OPEN', label: 'Open' },
  { status: 'RESOLVED', label: 'Resolved' },
  { status: null, label: 'All' }
];

@Component({
  selector: 'app-dispute-queue',
  standalone: true,
  imports: [DatePipe, RouterLink, InrPipe, StatusBadgeComponent],
  template: `
    <main class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <h1>Damage claims</h1>
      <p class="muted">Owners raise claims within 24 hours of a return. Review the inspection photos and decide the deduction.</p>

      <div class="tabs" role="tablist">
        @for (tab of tabs; track tab.label) {
          <button type="button" class="tab" role="tab" [class.active]="status() === tab.status"
                  [attr.aria-selected]="status() === tab.status" (click)="select(tab.status)">{{ tab.label }}</button>
        }
      </div>

      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (loading()) {
        <p role="status">Loading claims…</p>
      } @else if (disputes().length === 0) {
        <p class="muted">No claims here.</p>
      }

      @for (dispute of disputes(); track dispute.disputeId) {
        <a class="card" [routerLink]="['/admin/disputes', dispute.disputeId]"
           style="display: flex; justify-content: space-between; gap: 1rem; padding: 1.25rem; margin: .75rem 0; color: inherit;">
          <div>
            <strong>{{ dispute.equipmentTitle }}</strong>
            <div class="muted" style="font-size: .875rem;">{{ dispute.bookingReference }} · raised by {{ dispute.raisedByName }}
              · {{ dispute.createdAt | date:'medium' }}</div>
            <div>{{ dispute.category }} · claimed {{ dispute.claimAmount | inr }} of {{ dispute.securityDeposit | inr }} deposit</div>
          </div>
          <app-status-badge [status]="dispute.status" />
        </a>
      }
    </main>
  `
})
export class DisputeQueueComponent implements OnInit {
  private readonly api = inject(DisputeApiService);

  readonly tabs = TABS;
  readonly status = signal<string | null>('OPEN');
  readonly disputes = signal<DisputeSummary[]>([]);
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  select(status: string | null): void {
    this.status.set(status);
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.list(this.status()).subscribe({
      next: disputes => {
        this.disputes.set(disputes);
        this.loading.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message);
        this.loading.set(false);
      }
    });
  }
}
