import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { InrPipe } from '../../shared/inr.pipe';
import { BookingApiService, CancellationResponse } from './booking-api.service';

const TIER_TEXT: Record<string, string> = {
  NO_PAYMENT: 'No payment has been taken, so nothing is refunded.',
  FULL_REFUND: 'More than 48 hours before the start: rental refunded less the gateway fee, plus the full deposit.',
  PARTIAL_REFUND: '24–48 hours before the start: half the rental is refunded, plus the full deposit.',
  NO_RENTAL_REFUND: 'Less than 24 hours before the start: only the deposit is refunded.',
  OWNER_CANCELLED: 'Owner cancellation: the renter gets a full refund.'
};

/** Shows the server's cancellation quote, then cancels with a reason. Render with @if. */
@Component({
  selector: 'app-cancel-dialog',
  standalone: true,
  imports: [ConfirmDialogComponent, InrPipe],
  template: `
    <app-confirm-dialog title="Cancel this booking?" confirmLabel="Cancel booking" reasonLabel="Reason for cancelling"
                        [danger]="true" [busy]="busy()" (confirmed)="cancel($event)" (cancelled)="closed.emit()">
      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (quote(); as current) {
        <p>{{ tierText(current.breakdown.policyTier) }}</p>
        @if (current.breakdown.amountPaid > 0) {
          <dl class="kv" style="margin-top: .75rem;">
            <dt>Paid</dt><dd>{{ current.breakdown.amountPaid | inr }}</dd>
            <dt>Rental refund</dt><dd>{{ current.breakdown.rentalRefund | inr }}</dd>
            @if (current.breakdown.gatewayFee > 0) {
              <dt>Gateway fee kept</dt><dd>{{ current.breakdown.gatewayFee | inr }}</dd>
            }
            <dt>Deposit refund</dt><dd>{{ current.breakdown.depositRefund | inr }}</dd>
            <dt><strong>Total refund</strong></dt><dd><strong>{{ current.breakdown.totalRefund | inr }}</strong></dd>
            @if (current.breakdown.ownerCompensation > 0) {
              <dt>Owner compensation</dt><dd>{{ current.breakdown.ownerCompensation | inr }}</dd>
            }
            @if (current.breakdown.platformCredit > 0) {
              <dt>Platform credit to renter</dt><dd>{{ current.breakdown.platformCredit | inr }}</dd>
            }
          </dl>
          @if (asOwner()) {
            <div class="alert alert-warning">Cancelling a paid booking is recorded on your owner profile.</div>
          }
        }
        <p class="muted" style="font-size: .8125rem; margin-top: .75rem;">{{ current.breakdown.policyNotice }}</p>
      } @else if (!error()) {
        <p role="status">Calculating your refund…</p>
      }
    </app-confirm-dialog>
  `
})
export class CancelDialogComponent implements OnInit {
  private readonly api = inject(BookingApiService);

  readonly bookingId = input.required<string>();
  readonly asOwner = input(false);
  readonly done = output<CancellationResponse>();
  readonly closed = output<void>();

  readonly quote = signal<CancellationResponse | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.cancellationQuote(this.bookingId()).subscribe({
      next: quote => this.quote.set(quote),
      error: (failure: Error) => this.error.set(failure.message)
    });
  }

  tierText(tier: string): string {
    return TIER_TEXT[tier] ?? '';
  }

  cancel(reason: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.cancel(this.bookingId(), reason, this.asOwner()).subscribe({
      next: result => {
        this.busy.set(false);
        this.done.emit(result);
      },
      error: (failure: Error) => {
        this.busy.set(false);
        this.error.set(failure.message);
      }
    });
  }
}
