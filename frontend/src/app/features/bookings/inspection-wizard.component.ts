import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { invalidFieldsMessage } from '../../shared/form-feedback';
import { BookingApiService, BookingDetails, InspectionType, OtpChallenge } from './booking-api.service';

const MIN_PHOTOS = 4;
const MAX_PHOTOS = 20;
const CHECKLIST = [
  { key: 'accessories_present', label: 'All accessories present' },
  { key: 'no_visible_damage', label: 'No visible damage' },
  { key: 'operational_test_passed', label: 'Operational test passed' },
  { key: 'condition_acceptable', label: 'Overall condition acceptable' }
];

/**
 * Handover or return inspection. Each side generates a one-time code, which is emailed to the other side; when they
 * meet they read the codes to each other. Photos go straight to private storage through presigned URLs.
 */
@Component({
  selector: 'app-inspection-wizard',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule],
  template: `
    <section class="card" style="padding: 1.5rem; margin-top: 1.5rem;">
      <h2 style="font-size: 1.2rem;">{{ label() }} inspection</h2>
      <p class="muted">Do this together, with the equipment in front of you.</p>

      <h3 style="font-size: 1rem; margin-top: 1.25rem;">1. One-time codes</h3>
      <ul style="padding-left: 1.25rem; margin: .5rem 0;">
        <li>Owner code: {{ booking().otpStatus?.ownerRequested ? 'generated' : 'not generated yet' }}
          @if (booking().otpStatus?.ownerExpiresAt; as expires) { (valid until {{ expires | date:'shortTime' }}) }</li>
        <li>Renter code: {{ booking().otpStatus?.renterRequested ? 'generated' : 'not generated yet' }}
          @if (booking().otpStatus?.renterExpiresAt; as expires) { (valid until {{ expires | date:'shortTime' }}) }</li>
      </ul>
      @if (can('GENERATE')) {
        <button type="button" class="btn btn-secondary" (click)="generateCode()" [disabled]="busy()">
          {{ myCodeGenerated() ? 'Generate a new code' : 'Generate my code' }}
        </button>
      }
      @if (otp(); as issued) {
        <div class="alert alert-info">
          @if (issued.emailedToCounterparty) {
            Your code was emailed to the {{ otherSide() }}. When you meet, they read it to you.
          } @else {
            Email is off in this environment, so the {{ otherSide() }} did not receive your code.
          }
          @if (issued.code) {
            <div style="margin-top: .35rem;">Development only — your code: <strong style="letter-spacing: .15em;">{{ issued.code }}</strong></div>
          }
        </div>
      }

      <h3 style="font-size: 1rem; margin-top: 1.25rem;">2. Photos ({{ photoCount() }} of at least {{ minPhotos }})</h3>
      <p class="muted" style="font-size: .875rem;">JPEG, PNG or WebP up to 10 MB, at most 20. Either of you can upload; photos stay private.
        Confirm within 10 minutes of uploading, after that photos must be uploaded again.</p>
      @if (can('UPLOAD')) {
        <label class="btn btn-secondary" style="margin-top: .5rem;">
          {{ busy() ? 'Uploading…' : 'Add photos' }}
          <input type="file" accept="image/jpeg,image/png,image/webp" multiple (change)="uploadPhotos($event)"
                 [disabled]="busy()" style="display: none;" />
        </label>
      }

      <h3 style="font-size: 1rem; margin-top: 1.25rem;">3. Confirm the {{ label().toLowerCase() }}</h3>
      @if (!can('SUBMIT')) {
        <p class="muted">Available once both codes are generated and at least {{ minPhotos }} photos are uploaded.</p>
      } @else {
        <form [formGroup]="form" (ngSubmit)="submit()" style="display: grid; gap: .85rem; margin-top: .5rem;">
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>Hour meter reading
              <input type="number" min="0" step="0.1" formControlName="hourMeterReading" class="form-control" />
            </label>
            <label>Fuel / battery level
              <select formControlName="fuelOrBatteryLevel" class="form-control">
                <option value="">Not applicable</option>
                <option value="FULL">Full</option>
                <option value="THREE_QUARTERS">Three quarters</option>
                <option value="HALF">Half</option>
                <option value="QUARTER">Quarter</option>
                <option value="EMPTY">Empty</option>
              </select>
            </label>
          </div>
          <fieldset style="border: none; display: grid; gap: .35rem;">
            <legend style="font-weight: 600; margin-bottom: .35rem;">Checklist</legend>
            @for (item of checklist; track item.key) {
              <label><input type="checkbox" [formControlName]="item.key" /> {{ item.label }}</label>
            }
          </fieldset>
          <label>Condition notes (optional)
            <textarea rows="3" maxlength="2000" formControlName="conditionSummary" class="form-control"></textarea>
          </label>
          <div class="grid grid-cols-2" style="gap: 1rem;">
            <label>Your code
              <input formControlName="myCode" inputmode="numeric" maxlength="6" class="form-control" />
              <small class="muted">The {{ otherSide() }} reads it from their email.</small>
            </label>
            <label>{{ otherSideTitle() }}'s code
              <input formControlName="otherCode" inputmode="numeric" maxlength="6" class="form-control" />
              <small class="muted">Sent to your email when they generated it.</small>
            </label>
          </div>
          <button type="submit" class="btn btn-primary" [disabled]="busy()">
            {{ busy() ? 'Submitting…' : 'Confirm ' + label().toLowerCase() }}
          </button>
        </form>
      }

      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
    </section>
  `
})
export class InspectionWizardComponent {
  private readonly api = inject(BookingApiService);
  private readonly formBuilder = inject(FormBuilder);

  readonly booking = input.required<BookingDetails>();
  readonly type = input.required<InspectionType>();
  /** Booking data changed (code generated, photos added); the page should reload it. */
  readonly changed = output<void>();
  readonly submitted = output<void>();

  readonly minPhotos = MIN_PHOTOS;
  readonly checklist = CHECKLIST;
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);
  readonly otp = signal<OtpChallenge | null>(null);

  readonly label = computed(() => this.type() === 'HANDOVER' ? 'Handover' : 'Return');
  readonly isOwner = computed(() => this.booking().viewerRole === 'OWNER');
  readonly otherSide = computed(() => this.isOwner() ? 'renter' : 'owner');
  readonly otherSideTitle = computed(() => this.isOwner() ? 'Renter' : 'Owner');
  readonly photoCount = computed(() => this.booking().inspectionUploads.length);
  readonly myCodeGenerated = computed(() => this.isOwner()
    ? !!this.booking().otpStatus?.ownerRequested
    : !!this.booking().otpStatus?.renterRequested);

  readonly form = this.formBuilder.nonNullable.group({
    hourMeterReading: [null as number | null, [Validators.required, Validators.min(0)]],
    fuelOrBatteryLevel: [''],
    accessories_present: [false],
    no_visible_damage: [false],
    operational_test_passed: [false],
    condition_acceptable: [false],
    conditionSummary: [''],
    myCode: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]],
    otherCode: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
  });

  can(step: 'GENERATE' | 'UPLOAD' | 'SUBMIT'): boolean {
    const suffix = this.type() === 'HANDOVER' ? 'HANDOVER' : 'RETURN';
    const action = step === 'GENERATE' ? `GENERATE_${suffix}_OTP` : step === 'UPLOAD' ? `UPLOAD_${suffix}_PHOTOS` : `SUBMIT_${suffix}`;
    return this.booking().availableActions.includes(action as never);
  }

  generateCode(): void {
    this.run(async () => {
      this.otp.set(await firstValueFrom(this.api.issueOtp(this.booking().bookingId, this.type())));
      this.changed.emit();
    });
  }

  uploadPhotos(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    const invalid = files.find(file => !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)
      || file.size < 1 || file.size > 10 * 1024 * 1024);
    if (invalid) {
      this.error.set(`"${invalid.name}" is not a JPEG, PNG or WebP image under 10 MB.`);
      return;
    }
    this.run(async () => {
      for (const file of files) {
        const upload = await firstValueFrom(this.api.presignInspectionPhoto(this.booking().bookingId, this.type(), file));
        const response = await fetch(upload.uploadUrl, {
          method: 'PUT',
          headers: { 'Content-Type': upload.contentType },
          body: file
        });
        if (!response.ok) {
          throw new Error(`Uploading "${file.name}" failed (HTTP ${response.status}).`);
        }
      }
      this.changed.emit();
    });
  }

  submit(): void {
    const codes = this.form.getRawValue();
    this.form.patchValue({ myCode: codes.myCode.replace(/\s/g, ''), otherCode: codes.otherCode.replace(/\s/g, '') });
    const invalid = invalidFieldsMessage(this.form, {
      hourMeterReading: 'Hour meter reading',
      myCode: 'Your code (6 digits)',
      otherCode: `${this.otherSideTitle()}'s code (6 digits)`
    });
    if (invalid) {
      this.error.set(invalid);
      return;
    }
    const value = this.form.getRawValue();
    const ownerOtp = this.isOwner() ? value.myCode : value.otherCode;
    const renterOtp = this.isOwner() ? value.otherCode : value.myCode;
    this.run(async () => {
      await firstValueFrom(this.api.submitInspection(this.booking().bookingId, this.type(), {
        hourMeterReading: value.hourMeterReading ?? 0,
        fuelOrBatteryLevel: value.fuelOrBatteryLevel || null,
        checklist: Object.fromEntries(CHECKLIST.map(item => [item.key, !!value[item.key as keyof typeof value]])),
        conditionSummary: value.conditionSummary.trim() || null,
        ownerOtp,
        renterOtp,
        photos: this.booking().inspectionUploads.slice(0, MAX_PHOTOS).map(upload => ({
          storageObjectKey: upload.storageObjectKey,
          mediaTag: 'CONDITION',
          latitude: null,
          longitude: null
        }))
      }));
      this.form.reset();
      this.submitted.emit();
    });
  }

  private async run(work: () => Promise<void>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await work();
    } catch (failure) {
      this.error.set((failure as Error).message);
    } finally {
      this.busy.set(false);
    }
  }
}
