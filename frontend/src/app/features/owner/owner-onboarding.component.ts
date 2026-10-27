import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/interceptors/error.interceptor';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { OwnerApiService, OwnerDocument, OwnerProfile } from './owner-api.service';

const DOCUMENT_TYPES = [
  { value: 'IDENTITY', label: 'Identity' },
  { value: 'BUSINESS_REGISTRATION', label: 'Business registration' },
  { value: 'GST_CERTIFICATE', label: 'GST certificate' },
  { value: 'PAN_CARD', label: 'PAN card' },
  { value: 'BANK_PROOF', label: 'Bank proof' },
  { value: 'ADDRESS_PROOF', label: 'Address proof' },
  { value: 'OTHER', label: 'Other' }
];

@Component({
  selector: 'app-owner-onboarding',
  standalone: true,
  imports: [DatePipe, FormsModule, ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <main class="container" style="max-width:760px;padding:3rem 1rem;">
      <h1>Owner verification</h1>

      @if (loading()) {
        <p role="status">Loading your application…</p>
      } @else if (profile(); as current) {
        <section class="card" style="padding:1.5rem;margin-top:1.5rem;">
          <div style="display:flex;justify-content:space-between;align-items:center;gap:1rem;">
            <h2 style="font-size:1.2rem;">{{ current.businessName || 'Your owner application' }}</h2>
            <app-status-badge [status]="current.status" />
          </div>
          <p class="muted">Submitted {{ current.submittedAt | date:'medium' }}</p>

          @switch (current.status) {
            @case ('UNDER_REVIEW') {
              <div class="alert alert-info">Our team is reviewing your details. Upload your verification documents below
                to speed this up; we email you as soon as there is a decision.</div>
            }
            @case ('VERIFIED') {
              @if (auth.isOwner()) {
                <div class="alert alert-success">You are a verified owner.
                  <a routerLink="/owner/equipment">Manage your listings</a> or
                  <a routerLink="/owner/bookings">see your bookings</a>.</div>
              } @else {
                <div class="alert alert-success">
                  Your application is approved. <strong>Sign out and sign in again</strong> to get owner access.
                  <div style="margin-top:.75rem;">
                    <button type="button" class="btn btn-primary" (click)="auth.logout()">Sign out now</button>
                  </div>
                </div>
              }
            }
            @case ('REJECTED') {
              <div class="alert alert-error">
                Your application was not approved.
                @if (current.rejectionReason) {
                  <div style="margin-top:.5rem;">Reason: <em>{{ current.rejectionReason }}</em></div>
                }
                <div style="margin-top:.5rem;">Please contact support to update your details and request a new review.</div>
              </div>
            }
            @default {
              <div class="alert alert-warning">Your owner account is {{ current.status.toLowerCase() }}. Please contact support.</div>
            }
          }
        </section>

        @if (current.status !== 'VERIFIED') {
          <section class="card" style="padding:1.5rem;margin-top:1.5rem;">
            <h2 style="font-size:1.2rem;">Verification documents</h2>
            <p class="muted">Files go to private storage and are visible only to you and our reviewers. PDF, JPEG, or PNG up to 10 MB.</p>
            <div style="display:flex;gap:.75rem;align-items:flex-end;flex-wrap:wrap;">
              <label>Document type
                <select class="form-control" [(ngModel)]="documentType" name="documentType">
                  @for (type of documentTypes; track type.value) {
                    <option [value]="type.value">{{ type.label }}</option>
                  }
                </select>
              </label>
              <label class="btn btn-secondary">
                {{ uploading() ? 'Uploading…' : 'Choose and upload' }}
                <input type="file" accept="application/pdf,image/jpeg,image/png" (change)="upload(current.ownerProfileId, $event)"
                       [disabled]="uploading()" style="display:none;" />
              </label>
            </div>
            @if (documentError()) {
              <div class="alert alert-error" role="alert">{{ documentError() }}</div>
            }
            @if (documents().length) {
              <ul style="margin-top:1rem;padding-left:1.25rem;">
                @for (document of documents(); track document.documentId) {
                  <li>{{ typeLabel(document.documentType) }} · {{ document.originalFilename }}
                    · <app-status-badge [status]="document.status === 'PENDING' ? 'UNDER_REVIEW' : document.status" /></li>
                }
              </ul>
            } @else {
              <p class="muted" style="margin-top:1rem;">No documents uploaded yet.</p>
            }
          </section>
        }
      } @else {
        <p class="muted">Submit your business and payout details for manual review. You can publish listings once your owner profile is verified.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" class="card" style="display:grid;gap:1rem;padding:1.5rem;margin-top:1.5rem;">
          <label>Account type
            <select formControlName="businessType" class="form-control">
              <option value="INDIVIDUAL">Individual</option>
              <option value="PROPRIETORSHIP">Proprietorship</option>
              <option value="PARTNERSHIP">Partnership</option>
              <option value="PVT_LTD">Private limited company</option>
            </select>
          </label>
          <label>Business / trading name (optional)
            <input formControlName="companyName" maxlength="255" class="form-control" />
          </label>
          <div class="grid grid-cols-2" style="gap:1rem;">
            <label>First name<input formControlName="firstName" class="form-control" /></label>
            <label>Last name<input formControlName="lastName" class="form-control" /></label>
            <label>Phone<input formControlName="phone" maxlength="20" class="form-control" /></label>
            <label>GSTIN (optional)<input formControlName="gstin" maxlength="15" class="form-control" style="text-transform:uppercase;" /></label>
            <label>PAN (optional)<input formControlName="pan" maxlength="10" class="form-control" style="text-transform:uppercase;" /></label>
          </div>
          <h2 style="font-size:1.2rem;">Payout account details</h2>
          <label>Account holder name<input formControlName="bankAccountHolderName" class="form-control" /></label>
          <div class="grid grid-cols-2" style="gap:1rem;">
            <label>Bank account number<input formControlName="bankAccountNumber" inputmode="numeric" class="form-control" /></label>
            <label>IFSC code<input formControlName="bankIfsc" maxlength="11" class="form-control" style="text-transform:uppercase;" /></label>
          </div>
          @if (error()) {
            <div class="alert alert-error" role="alert">{{ error() }}</div>
          }
          <button class="btn btn-primary" type="submit" [disabled]="form.invalid || submitting()">
            {{ submitting() ? 'Submitting…' : 'Submit for verification' }}
          </button>
        </form>
      }
    </main>
  `
})
export class OwnerOnboardingComponent implements OnInit {
  private readonly formBuilder = inject(FormBuilder);
  private readonly api = inject(OwnerApiService);
  readonly auth = inject(AuthService);

  readonly documentTypes = DOCUMENT_TYPES;
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly profile = signal<OwnerProfile | null>(null);
  readonly documents = signal<OwnerDocument[]>([]);
  readonly documentError = signal<string | null>(null);
  readonly uploading = signal(false);
  documentType = 'IDENTITY';

  readonly form = this.formBuilder.nonNullable.group({
    businessType: ['INDIVIDUAL', Validators.required],
    companyName: ['', Validators.maxLength(255)],
    gstin: ['', Validators.pattern(/^$|^[0-9A-Z]{15}$/i)],
    pan: ['', Validators.pattern(/^$|^[A-Z]{5}[0-9]{4}[A-Z]$/i)],
    bankAccountNumber: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(34), Validators.pattern(/^[0-9]+$/)]],
    bankIfsc: ['', [Validators.required, Validators.pattern(/^[A-Z]{4}0[A-Z0-9]{6}$/i)]],
    bankAccountHolderName: ['', [Validators.required, Validators.maxLength(255)]],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', [Validators.required, Validators.maxLength(20)]]
  });

  ngOnInit(): void {
    this.loadProfile();
  }

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const value = this.form.getRawValue();
    this.api.submit({
      ...value,
      gstin: value.gstin.toUpperCase(),
      pan: value.pan.toUpperCase(),
      bankIfsc: value.bankIfsc.toUpperCase()
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.loadProfile();
      },
      error: (failure: Error) => {
        this.error.set(failure.message || 'Owner verification could not be submitted.');
        this.submitting.set(false);
      }
    });
  }

  async upload(ownerProfileId: string, event: Event): Promise<void> {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type) || file.size < 1 || file.size > 10 * 1024 * 1024) {
      this.documentError.set('Choose a PDF, JPEG, or PNG document no larger than 10 MB.');
      return;
    }
    this.documentError.set(null);
    this.uploading.set(true);
    try {
      await this.api.uploadDocument(ownerProfileId, this.documentType, file);
      this.loadDocuments(ownerProfileId);
    } catch {
      this.documentError.set('The document upload failed. Retry, or contact support if it keeps failing.');
    } finally {
      this.uploading.set(false);
    }
  }

  typeLabel(value: string): string {
    return DOCUMENT_TYPES.find(type => type.value === value)?.label ?? value;
  }

  private loadProfile(): void {
    this.loading.set(true);
    this.api.myProfile().subscribe({
      next: profile => {
        this.profile.set(profile);
        this.loading.set(false);
        this.loadDocuments(profile.ownerProfileId);
      },
      error: (failure: ApiError) => {
        this.profile.set(null);
        this.loading.set(false);
        // 404 means no application yet, so the form is shown; anything else is a real failure.
        if (failure.status !== 404) {
          this.error.set(failure.message || 'Your application status could not be loaded.');
        }
      }
    });
  }

  private loadDocuments(ownerProfileId: string): void {
    this.api.documents(ownerProfileId).subscribe({
      next: documents => this.documents.set(documents),
      error: () => this.documentError.set('Your uploaded documents could not be loaded.')
    });
  }
}
