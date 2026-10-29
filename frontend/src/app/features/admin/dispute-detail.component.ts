import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { invalidFieldsMessage } from '../../shared/form-feedback';
import { InrPipe } from '../../shared/inr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { DisputeApiService, DisputeDetail } from './dispute-api.service';

@Component({
  selector: 'app-dispute-detail',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, ConfirmDialogComponent, InrPipe, StatusBadgeComponent],
  template: `
    <main class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <a routerLink="/admin/disputes" style="display: inline-block; margin-bottom: 1rem;">← All claims</a>
      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (notice()) {
        <div class="alert alert-success" role="status">{{ notice() }}</div>
      }
      @if (!dispute() && !error()) {
        <p role="status">Loading claim…</p>
      }

      @if (dispute(); as d) {
        <header style="display: flex; justify-content: space-between; gap: 1rem; align-items: flex-start;">
          <div>
            <h1 style="font-size: 1.6rem;">{{ d.equipmentTitle }}</h1>
            <div class="muted">
              <a [routerLink]="['/bookings', d.bookingId]">Booking {{ d.bookingReference }}</a>
              · {{ d.startTime | date:'mediumDate' }} – {{ d.endTime | date:'mediumDate' }}
            </div>
          </div>
          <app-status-badge [status]="d.status" />
        </header>

        <section class="grid grid-cols-2" style="margin-top: 1.5rem;">
          <div class="card" style="padding: 1.25rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Claim</h2>
            <dl class="kv">
              <dt>Category</dt><dd>{{ d.category }}</dd>
              <dt>Claimed</dt><dd>{{ d.claimAmount | inr }}</dd>
              <dt>Deposit held</dt><dd>{{ d.securityDeposit | inr }}</dd>
              <dt>Raised</dt><dd>{{ d.createdAt | date:'medium' }}</dd>
            </dl>
            <p style="margin-top: .75rem;">{{ d.description }}</p>
          </div>
          <div class="card" style="padding: 1.25rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Parties</h2>
            <dl class="kv">
              <dt>Owner</dt><dd>{{ d.owner.name }} · {{ d.owner.email }}</dd>
              <dt>Renter</dt><dd>{{ d.renter.name }} · {{ d.renter.email }}</dd>
            </dl>
          </div>
        </section>

        @if (d.status === 'RESOLVED') {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Decision</h2>
            <dl class="kv">
              <dt>Deducted</dt><dd>{{ d.approvedDeductionAmount | inr }}</dd>
              <dt>Resolved</dt><dd>{{ d.resolvedAt | date:'medium' }}</dd>
              <dt>Notes</dt><dd>{{ d.resolutionNotes }}</dd>
            </dl>
          </section>
        } @else {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem;">Resolve</h2>
            <p class="muted">The deduction goes to the owner; the rest of the deposit is refunded to the renter.
              It cannot exceed {{ maxDeduction() | inr }}.</p>
            <form [formGroup]="form" (ngSubmit)="askToResolve()" style="display: grid; gap: .85rem; margin-top: .75rem;">
              <label>Deduction from deposit (₹)
                <input type="number" min="0" step="0.01" formControlName="approvedDeductionAmount" class="form-control" />
              </label>
              <label>Decision notes (shared with both parties)
                <textarea rows="3" maxlength="2000" formControlName="resolutionNotes" class="form-control"></textarea>
              </label>
              @if (formError()) {
                <div class="alert alert-error" role="alert">{{ formError() }}</div>
              }
              <button type="submit" class="btn btn-primary" style="justify-self: start;" [disabled]="busy()">Resolve claim</button>
            </form>
          </section>
        }

        @for (report of d.inspections; track report.inspectionId) {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem;">{{ report.type === 'HANDOVER' ? 'Handover' : 'Return' }} evidence</h2>
            <p class="muted">{{ report.inspectedAt | date:'medium' }} · hour meter {{ report.hourMeterReading }}</p>
            @if (report.conditionSummary) {
              <p>{{ report.conditionSummary }}</p>
            }
            <div class="photo-grid" style="margin-top: .75rem;">
              @for (photo of report.photos; track photo.url) {
                <a [href]="photo.url" target="_blank" rel="noopener"><img [src]="photo.url" [alt]="report.type + ' photo'" /></a>
              }
            </div>
          </section>
        }

        @if (confirming()) {
          <app-confirm-dialog title="Resolve this claim?" confirmLabel="Resolve" [busy]="busy()"
                              [message]="'Deduct ' + (form.controls.approvedDeductionAmount.value ?? 0) + ' INR; the rest of the deposit is refunded.'"
                              (confirmed)="resolve()" (cancelled)="confirming.set(false)" />
        }
      }
    </main>
  `
})
export class DisputeDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(DisputeApiService);
  private readonly formBuilder = inject(FormBuilder);

  readonly dispute = signal<DisputeDetail | null>(null);
  readonly busy = signal(false);
  readonly confirming = signal(false);
  readonly error = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);

  readonly maxDeduction = computed(() => {
    const d = this.dispute();
    return d ? Math.min(d.claimAmount, d.securityDeposit) : 0;
  });

  readonly form = this.formBuilder.nonNullable.group({
    approvedDeductionAmount: [null as number | null, [Validators.required, Validators.min(0)]],
    resolutionNotes: ['', [Validators.required, Validators.maxLength(2000)]]
  });

  ngOnInit(): void {
    this.load();
  }

  /** Checks the decision, then opens the confirmation; problems are explained beside the button. */
  askToResolve(): void {
    const invalid = invalidFieldsMessage(this.form,
      { approvedDeductionAmount: 'Deduction from deposit (₹)', resolutionNotes: 'Decision notes' });
    const deduction = this.form.controls.approvedDeductionAmount.value ?? 0;
    this.formError.set(invalid ?? (deduction > this.maxDeduction()
      ? `The deduction cannot be more than ₹${this.maxDeduction().toLocaleString('en-IN')}.`
      : null));
    if (!this.formError()) {
      this.confirming.set(true);
    }
  }

  resolve(): void {
    const value = this.form.getRawValue();
    this.busy.set(true);
    this.error.set(null);
    this.api.resolve(this.disputeId(), value.approvedDeductionAmount ?? 0, value.resolutionNotes.trim()).subscribe({
      next: () => {
        this.busy.set(false);
        this.confirming.set(false);
        this.notice.set('Claim resolved. The remaining deposit is being refunded to the renter.');
        this.load();
      },
      error: (failure: Error) => {
        this.busy.set(false);
        this.confirming.set(false);
        this.error.set(failure.message);
      }
    });
  }

  private disputeId(): string {
    return this.route.snapshot.paramMap.get('disputeId') ?? '';
  }

  private load(): void {
    this.api.get(this.disputeId()).subscribe({
      next: dispute => this.dispute.set(dispute),
      error: (failure: Error) => this.error.set(failure.message || 'This claim could not be loaded.')
    });
  }
}
