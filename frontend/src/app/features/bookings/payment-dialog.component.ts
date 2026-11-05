import { Component, NgZone, OnInit, inject, input, output, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { InrPipe } from '../../shared/inr.pipe';
import { BookingApiService, CheckoutResult, PaymentOrder } from './booking-api.service';

declare global {
  interface Window {
    Razorpay?: new (options: Record<string, unknown>) => { open(): void };
  }
}

const RAZORPAY_CHECKOUT = 'https://checkout.razorpay.com/v1/checkout.js';

/**
 * Pays for a booking. The backend decides the provider: FAKE shows a simulator with success/failure buttons
 * (local development), RAZORPAY opens Razorpay Checkout. Either way the result is verified server-side.
 */
@Component({
  selector: 'app-payment-dialog',
  standalone: true,
  imports: [InrPipe],
  template: `
    <div class="dialog-backdrop">
      <div class="dialog" role="dialog" aria-modal="true" aria-label="Payment">
        @if (order(); as current) {
          @if (current.provider === 'FAKE') {
            <h2>Fake Razorpay</h2>
            <div class="alert alert-warning">Development payment simulator. No real money moves.</div>
          } @else {
            <h2>Pay with Razorpay</h2>
          }
          <p>Amount: <strong>{{ current.amountInPaise / 100 | inr }}</strong></p>
        } @else if (!error()) {
          <h2>Payment</h2>
          <p role="status">Preparing your payment…</p>
        }

        @if (error()) {
          <div class="alert alert-error" role="alert">{{ error() }}</div>
        }
        @if (status()) {
          <p role="status">{{ status() }}</p>
        }

        <div class="dialog-actions">
          <button type="button" class="btn btn-secondary" (click)="closed.emit()" [disabled]="busy()">Close</button>
          @if (order(); as current) {
            @if (current.provider === 'FAKE') {
              <button type="button" class="btn btn-danger" (click)="simulate('FAILURE')" [disabled]="busy()">Fail payment</button>
              <button type="button" class="btn btn-primary" (click)="simulate('SUCCESS')" [disabled]="busy()">Pay successfully</button>
            } @else {
              <button type="button" class="btn btn-primary" (click)="openCheckout(current)" [disabled]="busy()">Pay now</button>
            }
          }
        </div>
      </div>
    </div>
  `
})
export class PaymentDialogComponent implements OnInit {
  private readonly api = inject(BookingApiService);
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);

  readonly bookingId = input.required<string>();
  readonly bookingReference = input<string>('');
  /** Emitted when the booking is paid and confirmed. */
  readonly paid = output<void>();
  /** Emitted after a failed attempt, so the page can refresh the booking status. */
  readonly failed = output<void>();
  readonly closed = output<void>();

  readonly order = signal<PaymentOrder | null>(null);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly status = signal<string | null>(null);

  ngOnInit(): void {
    this.api.createPaymentOrder(this.bookingId()).subscribe({
      next: order => this.order.set(order),
      error: (failure: Error) => this.error.set(failure.message)
    });
  }

  async simulate(outcome: 'SUCCESS' | 'FAILURE'): Promise<void> {
    const order = this.order();
    if (!order) {
      return;
    }
    this.start('Processing…');
    try {
      const result = await firstValueFrom(this.api.simulateFakePayment(order.razorpayOrderId, outcome));
      if (outcome === 'FAILURE') {
        this.fail(result.error_description ?? 'The payment failed. You can try again.');
        this.failed.emit();
        return;
      }
      await this.verify(order, result);
    } catch (failure) {
      this.fail((failure as Error).message);
    }
  }

  async openCheckout(order: PaymentOrder): Promise<void> {
    this.start('Opening Razorpay…');
    try {
      await loadScript(RAZORPAY_CHECKOUT);
      if (!window.Razorpay) {
        throw new Error('Razorpay Checkout could not be loaded.');
      }
      const user = this.auth.currentUser();
      new window.Razorpay({
        key: order.razorpayKeyId,
        order_id: order.razorpayOrderId,
        amount: order.amountInPaise,
        currency: order.currency,
        name: 'EquipRent India',
        description: this.bookingReference() ? `Booking ${this.bookingReference()}` : 'Equipment booking',
        prefill: { name: user?.name, email: user?.email },
        handler: (result: CheckoutResult) => this.zone.run(() => this.verify(order, result).catch(
          failure => this.fail((failure as Error).message))),
        modal: { ondismiss: () => this.zone.run(() => this.fail('Payment was not completed.')) }
      }).open();
    } catch (failure) {
      this.fail((failure as Error).message);
    }
  }

  private async verify(order: PaymentOrder, result: CheckoutResult): Promise<void> {
    this.status.set('Confirming your payment…');
    await firstValueFrom(this.api.verifyPayment(order.paymentId, result));
    this.busy.set(false);
    this.status.set(null);
    this.paid.emit();
  }

  private start(message: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.status.set(message);
  }

  private fail(message: string): void {
    this.busy.set(false);
    this.status.set(null);
    this.error.set(message);
  }
}

function loadScript(src: string): Promise<void> {
  if (document.querySelector(`script[src="${src}"]`)) {
    return Promise.resolve();
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = src;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Razorpay Checkout could not be loaded.'));
    document.body.appendChild(script);
  });
}
