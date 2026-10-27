import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { InrPipe } from '../../shared/inr.pipe';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { BookingAction, BookingApiService, BookingDetails, InspectionType } from './booking-api.service';
import { CancelDialogComponent } from './cancel-dialog.component';
import { InspectionWizardComponent } from './inspection-wizard.component';
import { PaymentDialogComponent } from './payment-dialog.component';

/** What happens next, shown under the status, phrased for the viewer. */
const NEXT_STEP: Record<string, { RENTER: string; OWNER: string }> = {
  REQUESTED: { RENTER: 'Waiting for the owner to accept (up to 4 hours).', OWNER: 'Accept or decline this request.' },
  PAYMENT_PENDING: { RENTER: 'The owner accepted. Pay within 30 minutes to confirm.', OWNER: 'Waiting for the renter to pay.' },
  PAYMENT_FAILED: { RENTER: 'The payment failed. You can try again before the hold expires.', OWNER: 'The renter\'s payment failed; they can retry.' },
  CONFIRMED: { RENTER: 'Confirmed. Do the handover inspection together at the start time.', OWNER: 'Confirmed. Do the handover inspection together at the start time.' },
  HANDOVER_PENDING: { RENTER: 'Handover is due: meet the owner and complete the inspection.', OWNER: 'Handover is due: meet the renter and complete the inspection.' },
  IN_PROGRESS: { RENTER: 'The rental is under way. Return the equipment at the end time.', OWNER: 'The rental is under way.' },
  RETURN_PENDING: { RENTER: 'Return is due: complete the return inspection with the owner.', OWNER: 'Return is due: complete the return inspection with the renter.' },
  RETURNED: { RENTER: 'Returned. The owner has 24 hours to approve the condition or raise a claim.', OWNER: 'Approve the condition, or raise a damage claim within 24 hours.' },
  DISPUTED: { RENTER: 'The owner raised a damage claim. Our team is reviewing it.', OWNER: 'Your claim is with our team for review.' },
  INSPECTION_PASSED: { RENTER: 'Inspection passed. Your deposit refund is being processed.', OWNER: 'Inspection passed. Your payout is being processed.' },
  COMPLETED: { RENTER: 'Completed. Please leave a review.', OWNER: 'Completed. Please leave a review.' },
  CLOSED: { RENTER: 'This booking is closed.', OWNER: 'This booking is closed.' }
};

@Component({
  selector: 'app-booking-details',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, ConfirmDialogComponent, InrPipe, StatusBadgeComponent,
    CancelDialogComponent, InspectionWizardComponent, PaymentDialogComponent],
  template: `
    <main class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <a [routerLink]="backLink()" style="display: inline-block; margin-bottom: 1rem;">← Back</a>

      @if (loading() && !booking()) {
        <p role="status">Loading booking…</p>
      }
      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (notice()) {
        <div class="alert alert-success" role="status">{{ notice() }}</div>
      }

      @if (booking(); as b) {
        <header style="display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap; align-items: flex-start;">
          <div>
            <h1 style="font-size: 1.6rem;"><a [routerLink]="['/equipment', b.equipmentId]">{{ b.equipmentTitle }}</a></h1>
            <div class="muted">Booking {{ b.bookingReference }}</div>
          </div>
          <app-status-badge [status]="b.status" />
        </header>
        @if (nextStep()) {
          <div class="alert alert-info">{{ nextStep() }}</div>
        }

        @if (actionButtons().length) {
          <div style="display: flex; gap: .5rem; flex-wrap: wrap; margin: 1rem 0;">
            @for (button of actionButtons(); track button.action) {
              <button type="button" class="btn" [class.btn-primary]="button.primary" [class.btn-secondary]="!button.primary"
                      (click)="run(button.action)" [disabled]="busy()">{{ button.label }}</button>
            }
            @if (has('RESOLVE_DISPUTE') && b.dispute) {
              <a class="btn btn-primary" [routerLink]="['/admin/disputes', b.dispute.disputeId]">Resolve dispute</a>
            }
          </div>
        }

        @if (inspectionType(); as type) {
          <app-inspection-wizard [booking]="b" [type]="type" (changed)="load()" (submitted)="inspectionDone(type)" />
        }

        @if (showDisputeForm()) {
          <section class="card" style="padding: 1.5rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.2rem;">Raise a damage claim</h2>
            <p class="muted">The claim is reviewed by our team using the return photos. It cannot exceed the deposit
              ({{ b.pricing.securityDepositAmount | inr }}).</p>
            <form [formGroup]="disputeForm" (ngSubmit)="raiseDispute()" style="display: grid; gap: .85rem; margin-top: .75rem;">
              <div class="grid grid-cols-2" style="gap: 1rem;">
                <label>Category
                  <select formControlName="category" class="form-control">
                    <option value="DAMAGE">Damage</option>
                    <option value="MISSING_PARTS">Missing parts</option>
                    <option value="EXCESSIVE_WEAR">Excessive wear</option>
                    <option value="CLEANING">Cleaning</option>
                    <option value="OTHER">Other</option>
                  </select>
                </label>
                <label>Claim amount (₹)
                  <input type="number" min="0" step="0.01" [max]="b.pricing.securityDepositAmount" formControlName="claimAmount" class="form-control" />
                </label>
              </div>
              <label>What happened
                <textarea rows="4" maxlength="2000" formControlName="description" class="form-control"></textarea>
              </label>
              <div style="display: flex; gap: .5rem;">
                <button type="submit" class="btn btn-danger"
                        [disabled]="disputeForm.invalid || busy() || (disputeForm.controls.claimAmount.value ?? 0) > b.pricing.securityDepositAmount">Submit claim</button>
                <button type="button" class="btn btn-secondary" (click)="showDisputeForm.set(false)">Cancel</button>
              </div>
            </form>
          </section>
        }

        @if (has('REVIEW')) {
          <section id="review" class="card" style="padding: 1.5rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.2rem;">Review {{ b.viewerRole === 'RENTER' ? 'the owner and equipment' : 'the renter' }}</h2>
            <form [formGroup]="reviewForm" (ngSubmit)="submitReview()" style="display: grid; gap: .85rem; margin-top: .75rem;">
              <fieldset style="border: none; display: flex; gap: .25rem;" aria-label="Rating">
                @for (star of [1, 2, 3, 4, 5]; track star) {
                  <button type="button" class="btn btn-secondary" style="padding: .35rem .6rem; font-size: 1.1rem;"
                          [attr.aria-pressed]="reviewForm.controls.rating.value === star" [attr.aria-label]="star + ' stars'"
                          (click)="reviewForm.controls.rating.setValue(star)">
                    {{ star <= (reviewForm.controls.rating.value ?? 0) ? '★' : '☆' }}
                  </button>
                }
              </fieldset>
              <label>Comments (optional)
                <textarea rows="3" maxlength="2000" formControlName="comments" class="form-control"></textarea>
              </label>
              <button type="submit" class="btn btn-primary" [disabled]="reviewForm.invalid || busy()" style="justify-self: start;">Submit review</button>
            </form>
          </section>
        }

        <section class="grid grid-cols-2" style="margin-top: 1.5rem;">
          <div class="card" style="padding: 1.25rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Rental</h2>
            <dl class="kv">
              <dt>Start</dt><dd>{{ b.startTime | date:'medium' }}</dd>
              <dt>End</dt><dd>{{ b.endTime | date:'medium' }}</dd>
              <dt>Collection</dt><dd>{{ b.deliveryOption === 'OWNER_DELIVERY' ? 'Owner delivers' : 'Renter collects' }}</dd>
              <dt>Owner</dt><dd>{{ b.ownerName }}</dd>
              <dt>Renter</dt><dd>{{ b.renterName }}</dd>
              @if (b.holdExpiresAt && (b.status === 'REQUESTED' || b.status === 'PAYMENT_PENDING' || b.status === 'PAYMENT_FAILED')) {
                <dt>Hold expires</dt><dd>{{ b.holdExpiresAt | date:'short' }}</dd>
              }
            </dl>
          </div>
          <div class="card" style="padding: 1.25rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Price ({{ b.pricing.rentalDays }} days)</h2>
            <dl class="kv">
              <dt>Rental</dt><dd>{{ b.pricing.baseRentalAmount | inr }}</dd>
              @if (b.pricing.deliveryCharge > 0) {
                <dt>Delivery</dt><dd>{{ b.pricing.deliveryCharge | inr }}</dd>
              }
              <dt>Platform fee</dt><dd>{{ b.pricing.platformFeeAmount | inr }}</dd>
              <dt>GST (estimate)</dt><dd>{{ b.pricing.taxAmount | inr }}</dd>
              <dt>Refundable deposit</dt><dd>{{ b.pricing.securityDepositAmount | inr }}</dd>
              <dt><strong>Total</strong></dt><dd><strong>{{ b.pricing.totalPayableAmount | inr }}</strong></dd>
            </dl>
          </div>
        </section>

        @if (b.payment; as payment) {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Payment</h2>
            <dl class="kv">
              <dt>Status</dt><dd>{{ payment.status }}</dd>
              <dt>Amount</dt><dd>{{ payment.amount | inr }}</dd>
              @if (payment.failureReason) {
                <dt>Failure</dt><dd>{{ payment.failureReason }}</dd>
              }
              @for (refund of payment.refunds; track refund.type) {
                <dt>Refund ({{ humanize(refund.type) }})</dt>
                <dd>{{ refund.amount | inr }} · {{ refund.createdAt | date:'short' }}</dd>
              }
            </dl>
          </section>
        }

        @if (b.cancellation; as cancellation) {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Cancellation</h2>
            <dl class="kv">
              <dt>Cancelled by</dt><dd>{{ cancellation.breakdown.cancelledBy === 'OWNER' ? 'Owner' : 'Renter' }} · {{ cancellation.cancelledAt | date:'medium' }}</dd>
              <dt>Reason</dt><dd>{{ cancellation.reason }}</dd>
              <dt>Total refund</dt><dd>{{ cancellation.breakdown.totalRefund | inr }}</dd>
              @if (cancellation.breakdown.gatewayFee > 0) {
                <dt>Gateway fee kept</dt><dd>{{ cancellation.breakdown.gatewayFee | inr }}</dd>
              }
              @if (cancellation.breakdown.ownerCompensation > 0) {
                <dt>Owner compensation</dt><dd>{{ cancellation.breakdown.ownerCompensation | inr }}</dd>
              }
            </dl>
          </section>
        }

        @if (b.dispute; as dispute) {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <h2 style="font-size: 1.1rem;">Damage claim</h2>
              <app-status-badge [status]="dispute.status" />
            </div>
            <dl class="kv" style="margin-top: .75rem;">
              <dt>Category</dt><dd>{{ dispute.category }}</dd>
              <dt>Claimed</dt><dd>{{ dispute.claimAmount | inr }}</dd>
              <dt>Description</dt><dd>{{ dispute.description }}</dd>
              @if (dispute.status === 'RESOLVED') {
                <dt>Approved deduction</dt><dd>{{ dispute.approvedDeductionAmount | inr }}</dd>
                <dt>Decision</dt><dd>{{ dispute.resolutionNotes }}</dd>
              }
            </dl>
          </section>
        }

        @for (report of b.inspections; track report.inspectionId) {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem;">{{ report.type === 'HANDOVER' ? 'Handover' : 'Return' }} inspection</h2>
            <p class="muted">{{ report.inspectedAt | date:'medium' }} · hour meter {{ report.hourMeterReading }}
              @if (report.fuelOrBatteryLevel) { · fuel/battery {{ report.fuelOrBatteryLevel.toLowerCase() }} }</p>
            <ul style="padding-left: 1.25rem; margin: .5rem 0;">
              @for (item of checklistEntries(report.checklist); track item[0]) {
                <li>{{ item[1] ? '✔' : '✘' }} {{ humanize(item[0]) }}</li>
              }
            </ul>
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

        @if (b.reviews.length) {
          <section class="card" style="padding: 1.25rem; margin-top: 1.5rem;">
            <h2 style="font-size: 1.1rem; margin-bottom: .75rem;">Reviews</h2>
            @for (review of b.reviews; track review.reviewId) {
              <div style="margin-bottom: .75rem;">
                <strong>{{ stars(review.rating) }}</strong> · {{ review.reviewerName }}
                ({{ review.reviewerRole === 'RENTER' ? 'renter' : 'owner' }})
                @if (review.comments) { <p>{{ review.comments }}</p> }
              </div>
            }
          </section>
        }

        @if (dialog() === 'PAY') {
          <app-payment-dialog [bookingId]="b.bookingId" [bookingReference]="b.bookingReference"
                              (paid)="afterPayment()" (failed)="load()" (closed)="closeDialog()" />
        }
        @if (dialog() === 'CANCEL') {
          <app-cancel-dialog [bookingId]="b.bookingId" [asOwner]="b.viewerRole === 'OWNER'"
                             (done)="afterCancel()" (closed)="closeDialog()" />
        }
        @if (dialog() === 'REJECT') {
          <app-confirm-dialog title="Decline this request?" confirmLabel="Decline" reasonLabel="Reason (shown to the renter)"
                              [danger]="true" [busy]="busy()" (confirmed)="reject($event)" (cancelled)="closeDialog()" />
        }
        @if (dialog() === 'APPROVE_CONDITION') {
          <app-confirm-dialog title="Approve the returned condition?"
                              message="This closes the 24-hour claim window: the renter's deposit is refunded and your payout released."
                              confirmLabel="Approve condition" [busy]="busy()" (confirmed)="approveCondition()" (cancelled)="closeDialog()" />
        }
      }
    </main>
  `
})
export class BookingDetailsComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(BookingApiService);
  private readonly formBuilder = inject(FormBuilder);

  readonly booking = signal<BookingDetails | null>(null);
  readonly loading = signal(true);
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly dialog = signal<BookingAction | null>(null);
  readonly showDisputeForm = signal(false);

  readonly disputeForm = this.formBuilder.nonNullable.group({
    category: ['DAMAGE', Validators.required],
    claimAmount: [null as number | null, [Validators.required, Validators.min(0)]],
    description: ['', [Validators.required, Validators.maxLength(2000)]]
  });
  readonly reviewForm = this.formBuilder.nonNullable.group({
    rating: [null as number | null, [Validators.required, Validators.min(1), Validators.max(5)]],
    comments: ['', Validators.maxLength(2000)]
  });

  readonly nextStep = computed(() => {
    const b = this.booking();
    return b && b.viewerRole !== 'ADMIN' ? NEXT_STEP[b.status]?.[b.viewerRole] ?? null : null;
  });

  readonly inspectionType = computed<InspectionType | null>(() => {
    const actions = this.booking()?.availableActions ?? [];
    if (actions.some(action => action.includes('HANDOVER'))) {
      return 'HANDOVER';
    }
    return actions.some(action => action.includes('RETURN')) ? 'RETURN' : null;
  });

  readonly actionButtons = computed(() => {
    const labels: Partial<Record<BookingAction, { label: string; primary: boolean }>> = {
      PAY: { label: 'Pay now', primary: true },
      ACCEPT: { label: 'Accept request', primary: true },
      REJECT: { label: 'Decline', primary: false },
      APPROVE_CONDITION: { label: 'Approve condition', primary: true },
      RAISE_DISPUTE: { label: 'Raise damage claim', primary: false },
      CANCEL: { label: 'Cancel booking', primary: false }
    };
    return (this.booking()?.availableActions ?? [])
      .filter(action => labels[action])
      .map(action => ({ action, ...labels[action]! }));
  });

  readonly backLink = computed(() => {
    switch (this.booking()?.viewerRole) {
      case 'OWNER': return '/owner/bookings';
      case 'ADMIN': return '/admin/disputes';
      default: return '/bookings';
    }
  });

  ngOnInit(): void {
    this.load();
  }

  has(action: BookingAction): boolean {
    return this.booking()?.availableActions.includes(action) ?? false;
  }

  run(action: BookingAction): void {
    this.notice.set(null);
    if (action === 'ACCEPT') {
      this.act(this.api.accept(this.bookingId()), 'Accepted. The renter has 30 minutes to pay.');
    } else if (action === 'RAISE_DISPUTE') {
      this.showDisputeForm.set(true);
    } else {
      this.dialog.set(action);
    }
  }

  closeDialog(): void {
    this.dialog.set(null);
  }

  reject(reason: string): void {
    this.act(this.api.reject(this.bookingId(), reason), 'Request declined.');
  }

  approveCondition(): void {
    this.act(this.api.approveCondition(this.bookingId()), 'Condition approved. Deposit refund and payout will follow.');
  }

  raiseDispute(): void {
    const value = this.disputeForm.getRawValue();
    this.act(this.api.raiseDispute(this.bookingId(), {
      category: value.category,
      claimAmount: value.claimAmount ?? 0,
      description: value.description.trim()
    }), 'Damage claim submitted. Our team will review it.', () => {
      this.showDisputeForm.set(false);
      this.disputeForm.reset();
    });
  }

  submitReview(): void {
    const value = this.reviewForm.getRawValue();
    this.act(this.api.review(this.bookingId(), value.rating ?? 0, value.comments.trim() || null),
      'Thank you for your review.', () => this.reviewForm.reset());
  }

  afterPayment(): void {
    this.dialog.set(null);
    this.notice.set('Payment received. Your booking is confirmed.');
    this.load();
  }

  afterCancel(): void {
    this.dialog.set(null);
    this.notice.set('The booking has been cancelled.');
    this.load();
  }

  inspectionDone(type: InspectionType): void {
    this.notice.set(type === 'HANDOVER' ? 'Handover confirmed. The rental has started.' : 'Return confirmed.');
    this.load();
  }

  checklistEntries(checklist: Record<string, boolean>): [string, boolean][] {
    return Object.entries(checklist);
  }

  humanize(text: string): string {
    return text.replace(/_/g, ' ').toLowerCase();
  }

  stars(rating: number): string {
    return '★'.repeat(rating) + '☆'.repeat(5 - rating);
  }

  load(): void {
    this.loading.set(true);
    this.api.details(this.bookingId()).subscribe({
      next: booking => {
        this.booking.set(booking);
        this.loading.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message || 'This booking could not be loaded.');
        this.loading.set(false);
      }
    });
  }

  private bookingId(): string {
    return this.route.snapshot.paramMap.get('bookingId') ?? '';
  }

  private act(request: Observable<unknown>, message: string, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        this.dialog.set(null);
        this.notice.set(message);
        after?.();
        this.load();
      },
      error: (failure: Error) => {
        this.busy.set(false);
        this.error.set(failure.message);
      }
    });
  }
}
