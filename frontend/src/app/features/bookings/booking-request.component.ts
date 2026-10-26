import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Booking, BookingApiService, BookingRequest } from './booking-api.service';

@Component({
  selector: 'app-booking-request',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <section class="container" style="max-width: 760px; padding: 3rem 1rem;">
      <a routerLink="/" style="display: inline-block; margin-bottom: 1.5rem;">← Equipment catalogue</a>
      <h1>Request a rental</h1>
      <p style="color: var(--color-text-muted);">The owner will review your request. No payment is collected in this step.</p>

      <form [formGroup]="form" (ngSubmit)="submit()" class="card" style="padding: 1.5rem; display: grid; gap: 1rem;">
        <label>
          <span>Equipment ID</span>
          <input formControlName="equipmentId" readonly style="width: 100%; padding: .65rem; margin-top: .35rem;" />
        </label>
        <div class="grid grid-cols-2" style="gap: 1rem;">
          <label>
            <span>Start</span>
            <input type="datetime-local" formControlName="startTime" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
          </label>
          <label>
            <span>End</span>
            <input type="datetime-local" formControlName="endTime" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
          </label>
        </div>
        <label>
          <span>Collection / delivery</span>
          <select formControlName="deliveryOption" style="width: 100%; padding: .65rem; margin-top: .35rem;">
            <option value="PICKUP">I will collect the equipment</option>
            <option value="OWNER_DELIVERY">Request owner delivery</option>
          </select>
        </label>
        <ng-container *ngIf="form.controls.deliveryOption.value === 'OWNER_DELIVERY'">
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>
              <span>Site contact name</span>
              <input formControlName="siteContactName" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
            </label>
            <label>
              <span>Site contact phone</span>
              <input formControlName="siteContactPhone" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
            </label>
          </div>
          <label>
            <span>Delivery address</span>
            <input formControlName="addressLine" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
          </label>
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>
              <span>City</span>
              <input formControlName="city" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
            </label>
            <label>
              <span>Postal code</span>
              <input formControlName="postalCode" style="width: 100%; padding: .65rem; margin-top: .35rem;" />
            </label>
          </div>
        </ng-container>
        <p *ngIf="error()" role="alert" style="color: var(--color-danger);">{{ error() }}</p>
        <button class="btn btn-primary" type="submit" [disabled]="form.invalid || submitting()">
          {{ submitting() ? 'Submitting…' : 'Request booking' }}
        </button>
      </form>
    </section>

    <div *ngIf="quote() as booking" class="booking-modal-backdrop" role="presentation">
      <section class="booking-modal card" role="dialog" aria-modal="true" aria-labelledby="quote-title">
        <button type="button" (click)="quote.set(null)" aria-label="Close price quote" class="booking-modal-close">×</button>
        <h2 id="quote-title">Request sent</h2>
        <p>Reference {{ booking.bookingReference }} · Status {{ booking.status }}</p>
        <p>Owner response due {{ booking.holdExpiresAt | date:'medium' }}</p>
        <h3>Price estimate</h3>
          <p style="color:var(--color-text-muted);">The renter convenience fee is 3% of rental plus delivery. GST is a provisional 18% estimate, not a tax invoice. The refundable deposit is shown separately and excluded from both fee bases.</p>
          <dl class="quote-lines">
            <dt>Rental ({{ booking.pricing.rentalDays }} days)</dt><dd>{{ booking.pricing.baseRentalAmount | currency:'INR' }}</dd>
            <dt>Delivery</dt><dd>{{ booking.pricing.deliveryCharge | currency:'INR' }}</dd>
            <dt>Renter convenience fee (3%)</dt><dd>{{ booking.pricing.platformFeeAmount | currency:'INR' }}</dd>
            <dt>Provisional GST estimate (18%)</dt><dd>{{ booking.pricing.taxAmount | currency:'INR' }}</dd>
          <dt>Refundable deposit</dt><dd>{{ booking.pricing.securityDepositAmount | currency:'INR' }}</dd>
          <dt class="quote-total">Total payable</dt><dd class="quote-total">{{ booking.pricing.totalPayableAmount | currency:'INR' }}</dd>
        </dl>
        <a routerLink="/bookings" class="btn btn-primary" (click)="quote.set(null)">View booking history</a>
      </section>
    </div>
  `,
  styles: [`
    .booking-modal-backdrop { position: fixed; inset: 0; z-index: 100; display: grid; place-items: center; padding: 1rem; background: rgb(15 23 42 / 55%); }
    .booking-modal { position: relative; width: min(100%, 540px); padding: 2rem; background: white; }
    .booking-modal-close { position: absolute; top: .75rem; right: .75rem; border: 0; background: transparent; font-size: 1.75rem; cursor: pointer; }
    .quote-lines { display: grid; grid-template-columns: 1fr auto; gap: .6rem 1rem; margin: 1.25rem 0; }
    .quote-lines dd { margin: 0; text-align: right; }
    .quote-total { border-top: 1px solid var(--color-border); padding-top: .75rem; font-weight: 700; }
  `]
})
export class BookingRequestComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly bookingApi = inject(BookingApiService);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly quote = signal<Booking | null>(null);

  readonly form = this.formBuilder.group({
    equipmentId: [this.route.snapshot.paramMap.get('equipmentId') ?? '', Validators.required],
    startTime: ['', Validators.required],
    endTime: ['', Validators.required],
    deliveryOption: ['PICKUP' as 'PICKUP' | 'OWNER_DELIVERY', Validators.required],
    siteContactName: [''],
    siteContactPhone: [''],
    addressLine: [''],
    city: [''],
    postalCode: ['']
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: BookingRequest = {
      equipmentId: value.equipmentId!,
      startTime: new Date(value.startTime!).toISOString(),
      endTime: new Date(value.endTime!).toISOString(),
      deliveryOption: value.deliveryOption!
    };
    if (value.deliveryOption === 'OWNER_DELIVERY') {
      const address = {
        siteContactName: value.siteContactName ?? '',
        siteContactPhone: value.siteContactPhone ?? '',
        addressLine: value.addressLine ?? '',
        city: value.city ?? '',
        postalCode: value.postalCode ?? ''
      };
      if (Object.values(address).some(field => !field.trim())) {
        this.error.set('Complete all delivery contact and address fields.');
        return;
      }
      request.deliveryAddress = address;
    }

    this.error.set(null);
    this.submitting.set(true);
    this.bookingApi.create(request).subscribe({
      next: booking => {
        this.quote.set(booking);
        this.submitting.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message);
        this.submitting.set(false);
      }
    });
  }
}
