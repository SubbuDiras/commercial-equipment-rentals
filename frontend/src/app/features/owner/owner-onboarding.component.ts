import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AbstractControl, FormBuilder, FormsModule, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/interceptors/error.interceptor';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { aadhaarValidator } from './aadhaar';
import { DOCUMENT_TYPES, documentTypeLabel, OwnerApiService, OwnerDocument, OwnerProfile } from './owner-api.service';

type FieldName = 'firstName' | 'lastName' | 'phone' | 'aadhaarNumber' | 'gstin' | 'pan'
  | 'bankAccountHolderName' | 'bankName' | 'bankAccountNumber' | 'confirmBankAccountNumber' | 'bankIfsc';

interface FieldSpec {
  name: FieldName;
  label: string;
  hint: string;
  example?: string;
  maxLength?: number;
  uppercase?: boolean;
  numeric?: boolean;
  /** Only digits can be typed, and no more than maxLength of them. */
  digitsOnly?: boolean;
  /** Fixed text shown before the box, e.g. the +91 country code. */
  prefix?: string;
}

const PERSONAL_FIELDS: FieldSpec[] = [
  { name: 'firstName', label: 'First name', hint: '', maxLength: 100 },
  { name: 'lastName', label: 'Last name', hint: '', maxLength: 100 },
  { name: 'phone', label: 'Mobile number', hint: '10-digit Indian mobile number.', example: '9876543210', maxLength: 10,
    numeric: true, digitsOnly: true, prefix: '+91' },
  { name: 'aadhaarNumber', label: 'Aadhaar number', hint: '12 digits from your Aadhaar card. We keep only the last 4.',
    example: '2341 2341 2346', maxLength: 14, numeric: true },
  { name: 'gstin', label: 'GSTIN (optional)', hint: '15 letters and digits.', example: '29ABCDE1234F1Z5', maxLength: 15, uppercase: true },
  { name: 'pan', label: 'PAN', hint: '5 letters, 4 digits, 1 letter.', example: 'ABCDE1234F', maxLength: 10, uppercase: true }
];

const PAYOUT_FIELDS: FieldSpec[] = [
  { name: 'bankAccountHolderName', label: 'Account holder name', hint: 'As printed on the bank account.', maxLength: 255 },
  { name: 'bankName', label: 'Bank name', hint: '', example: 'HDFC Bank', maxLength: 100 },
  { name: 'bankAccountNumber', label: 'Bank account number', hint: '10 digits.', example: '1234567890', maxLength: 10,
    numeric: true, digitsOnly: true },
  { name: 'confirmBankAccountNumber', label: 'Confirm account number', hint: 'Type the account number again.', maxLength: 10,
    numeric: true, digitsOnly: true },
  { name: 'bankIfsc', label: 'IFSC code', hint: '4 letters, a zero, then 6 letters or digits.', example: 'HDFC0001234', maxLength: 11, uppercase: true }
];

const ACCOUNT_TYPES: Record<string, string> = { SAVINGS: 'Savings', CURRENT: 'Current' };

/** Extensions are listed too, so the file picker offers .jpg files on every system. */
const ANY_FORMAT_ACCEPT = '.jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf';
const ANY_CONTENT_TYPE = ['image/jpeg', 'image/png', 'application/pdf'];

/** The documents every applicant is asked for, each with its own upload button. */
const KEY_DOCUMENTS = [
  { type: 'AADHAAR_CARD', label: 'Aadhaar card', required: true, hint: 'Front and back, or the e-Aadhaar', formats: 'JPG, PNG or PDF',
    accept: ANY_FORMAT_ACCEPT, contentTypes: ANY_CONTENT_TYPE },
  { type: 'PAN_CARD', label: 'PAN card', required: true, hint: 'Photo of your PAN card', formats: 'JPG, PNG or PDF',
    accept: ANY_FORMAT_ACCEPT, contentTypes: ANY_CONTENT_TYPE },
  { type: 'SIGNATURE', label: 'Signature', required: true, hint: 'Sign on plain white paper and take a photo', formats: 'JPG or PNG',
    accept: '.jpg,.jpeg,.png,image/jpeg,image/png', contentTypes: ['image/jpeg', 'image/png'] },
  { type: 'BANK_PROOF', label: 'Bank proof', required: false, hint: 'Cancelled cheque or passbook first page', formats: 'JPG, PNG or PDF',
    accept: ANY_FORMAT_ACCEPT, contentTypes: ANY_CONTENT_TYPE }
];

/** The upload content type for an allowed file of this document type, or the reason it is refused. */
function checkFile(file: File, documentType: string): { contentType: string } | { error: string } {
  const slot = KEY_DOCUMENTS.find(candidate => candidate.type === documentType);
  const contentType = contentTypeOf(file);
  if (!contentType || !(slot?.contentTypes ?? ANY_CONTENT_TYPE).includes(contentType)
    || file.size < 1 || file.size > 10 * 1024 * 1024) {
    return { error: `Choose a ${slot?.formats ?? 'JPG, PNG or PDF'} file no larger than 10 MB.` };
  }
  return { contentType };
}

/** Some systems report no type for a .jpg; fall back to the file extension. */
function contentTypeOf(file: File): string | null {
  if (file.type) {
    return file.type === 'image/jpg' || file.type === 'image/pjpeg' ? 'image/jpeg' : file.type;
  }
  const extension = file.name.split('.').pop()?.toLowerCase();
  return ({ jpg: 'image/jpeg', jpeg: 'image/jpeg', png: 'image/png', pdf: 'application/pdf' } as Record<string, string>)[extension ?? '']
    ?? null;
}

/** The confirmation must repeat the account number; an empty value is left to Validators.required. */
function matchesAccountNumber(control: AbstractControl<string>): ValidationErrors | null {
  const accountNumber: string = control.parent?.get('bankAccountNumber')?.value ?? '';
  return control.value === '' || control.value.replace(/[\s-]/g, '') === accountNumber.replace(/[\s-]/g, '')
    ? null : { mismatch: true };
}

/** Shown when a filled-in field has the wrong format; the backend applies the same rules. */
const FIELD_MESSAGES: Record<FieldName, string> = {
  firstName: 'Use at most 100 characters.',
  lastName: 'Use at most 100 characters.',
  phone: 'Enter a 10-digit mobile number starting with 6, 7, 8 or 9, e.g. 9876543210.',
  aadhaarNumber: 'This is not a valid Aadhaar number. Check the 12 digits on your Aadhaar card.',
  bankName: 'Use at most 100 characters.',
  confirmBankAccountNumber: 'The account numbers do not match.',
  gstin: 'A GSTIN is 15 letters and digits, e.g. 29ABCDE1234F1Z5. Leave it blank if you have none.',
  pan: 'A PAN looks like ABCDE1234F: 5 letters, 4 digits, 1 letter.',
  bankAccountHolderName: 'Use at most 255 characters.',
  bankAccountNumber: 'Enter exactly 10 digits, e.g. 1234567890.',
  bankIfsc: 'An IFSC looks like HDFC0001234: 4 letters, a zero, then 6 letters or digits.'
};

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
          @if (current.kyc; as kyc) {
            <dl aria-label="Your KYC details" style="display:grid;grid-template-columns:minmax(140px,auto) 1fr;gap:.35rem 1rem;margin:1rem 0;font-size:.9375rem;">
              <dt class="muted">Phone</dt><dd>{{ kyc.phone || '—' }}</dd>
              <dt class="muted">Aadhaar</dt><dd>{{ kyc.aadhaarLast4 ? 'XXXX XXXX ' + kyc.aadhaarLast4 : 'Not provided' }}</dd>
              <dt class="muted">PAN</dt><dd>{{ kyc.pan || '—' }}</dd>
              <dt class="muted">GSTIN</dt><dd>{{ kyc.gstin || '—' }}</dd>
              <dt class="muted">Bank</dt><dd>{{ kyc.bankName || 'Not provided' }}{{ kyc.bankAccountType ? ' · ' + accountTypeLabel(kyc.bankAccountType) : '' }}</dd>
              <dt class="muted">Account</dt><dd>{{ kyc.bankAccountNumber }} · {{ kyc.bankIfsc }}</dd>
              <dt class="muted">Account holder</dt><dd>{{ kyc.bankAccountHolderName }}</dd>
            </dl>
          }

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

        @let canUpload = current.status === 'UNDER_REVIEW' || current.status === 'REJECTED';
          <section class="card" style="padding:1.5rem;margin-top:1.5rem;" aria-label="KYC documents">
            <h2 style="font-size:1.2rem;">KYC documents</h2>
            @if (canUpload) {
              <p class="muted">Upload a clear photo (JPG) or scan of each document, up to 10 MB. Files go to private storage and
                are visible only to you and our reviewers.</p>
            } @else {
              <p class="muted">The documents reviewed for your owner verification. Uploads close once you are verified; to
                replace a document, please contact support.</p>
            }
            @if (documentError()) {
              <div class="alert alert-error" role="alert">{{ documentError() }}</div>
            }

            <div style="display:grid;gap:.75rem;margin-top:1rem;">
              @for (slot of keyDocuments; track slot.type) {
                @let latest = latestDocument(slot.type);
                <div [attr.aria-label]="slot.label" role="group"
                     style="display:flex;justify-content:space-between;align-items:center;gap:1rem;flex-wrap:wrap;padding:.75rem 1rem;border:1px solid var(--color-border);border-radius:var(--radius-md);">
                  <div style="min-width:0;">
                    <strong>{{ slot.label }}</strong>
                    <div class="muted" style="font-size:.8125rem;">{{ slot.hint }} · {{ slot.formats }}</div>
                    @if (latest) {
                      <div style="font-size:.875rem;margin-top:.25rem;overflow-wrap:anywhere;">{{ latest.originalFilename }}
                        · <app-status-badge [status]="latest.status === 'PENDING' ? 'UNDER_REVIEW' : latest.status" /></div>
                    } @else {
                      <div style="font-size:.875rem;margin-top:.25rem;color:var(--color-text-muted);">Not uploaded yet</div>
                    }
                  </div>
                  @if (canUpload) {
                    <label class="btn" [class.btn-secondary]="!!latest" [class.btn-primary]="!latest">
                      {{ uploadingType() === slot.type ? 'Uploading…' : (latest ? 'Replace' : 'Upload JPG') }}
                      <input type="file" [attr.accept]="slot.accept" [attr.aria-label]="'Upload ' + slot.label"
                             (change)="upload(current.ownerProfileId, slot.type, $event)" [disabled]="!!uploadingType()" style="display:none;" />
                    </label>
                  }
                </div>
              }
            </div>

            <h3 style="font-size:1rem;margin-top:1.25rem;">Other documents</h3>
            @if (canUpload) {
              <div style="display:flex;gap:.75rem;align-items:flex-end;flex-wrap:wrap;margin-top:.5rem;">
                <label>Document type
                  <select class="form-control" [(ngModel)]="documentType" name="documentType">
                    @for (type of otherDocumentTypes; track type.value) {
                      <option [value]="type.value">{{ type.label }}</option>
                    }
                  </select>
                </label>
                <label class="btn btn-secondary">
                  {{ uploadingType() === documentType ? 'Uploading…' : 'Upload JPG, PNG or PDF' }}
                  <input type="file" [attr.accept]="anyFormat" aria-label="Upload other document"
                         (change)="upload(current.ownerProfileId, documentType, $event)" [disabled]="!!uploadingType()" style="display:none;" />
                </label>
              </div>
            }
            @if (otherDocuments().length) {
              <ul style="margin-top:.75rem;padding-left:1.25rem;">
                @for (document of otherDocuments(); track document.documentId) {
                  <li>{{ typeLabel(document.documentType) }} · {{ document.originalFilename }}
                    · <app-status-badge [status]="document.status === 'PENDING' ? 'UNDER_REVIEW' : document.status" /></li>
                }
              </ul>
            } @else {
              <p class="muted" style="margin-top:.5rem;font-size:.875rem;">Optional: address proof, business registration, GST certificate.</p>
            }
          </section>
      } @else {
        <p class="muted">Submit your business and payout details for manual review. You can publish listings once your owner profile is verified.</p>
        <form [formGroup]="form" (ngSubmit)="submit()" novalidate class="card" style="display:grid;gap:1rem;padding:1.5rem;margin-top:1.5rem;">
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
            @for (field of personalFields; track field.name) {
              <div>
                <label>{{ field.label }}
                  <span style="display:flex;align-items:stretch;gap:.4rem;">
                    @if (field.prefix) {
                      <span aria-hidden="true" class="form-control" style="flex:none;width:auto;background:var(--color-bg-muted);">{{ field.prefix }}</span>
                    }
                    <input [formControlName]="field.name" [attr.maxlength]="field.digitsOnly ? null : field.maxLength" [attr.placeholder]="field.example"
                           [attr.inputmode]="field.numeric ? 'numeric' : null" (input)="field.digitsOnly && keepDigits(field, $event)"
                           [attr.aria-invalid]="!!fieldError(field.name)" [attr.aria-describedby]="field.name + '-hint'"
                         class="form-control" [style.text-transform]="field.uppercase ? 'uppercase' : null"
                         [style.border-color]="fieldError(field.name) ? 'var(--color-danger)' : null" />
                  </span>
                </label>
                <small [id]="field.name + '-hint'" [style.color]="fieldError(field.name) ? 'var(--color-danger)' : 'var(--color-text-muted)'">
                  {{ fieldError(field.name) || field.hint }}
                </small>
              </div>
            }
          </div>
          <h2 style="font-size:1.2rem;">Bank account for payouts</h2>
          <div role="radiogroup" aria-label="Bank account type" style="display:flex;gap:1.5rem;">
            <span>Account type</span>
            <label><input type="radio" formControlName="bankAccountType" value="SAVINGS" /> Savings</label>
            <label><input type="radio" formControlName="bankAccountType" value="CURRENT" /> Current</label>
          </div>
          <div class="grid grid-cols-2" style="gap:1rem;">
            @for (field of payoutFields; track field.name) {
              <div>
                <label>{{ field.label }}
                  <span style="display:flex;align-items:stretch;gap:.4rem;">
                    @if (field.prefix) {
                      <span aria-hidden="true" class="form-control" style="flex:none;width:auto;background:var(--color-bg-muted);">{{ field.prefix }}</span>
                    }
                    <input [formControlName]="field.name" [attr.maxlength]="field.digitsOnly ? null : field.maxLength" [attr.placeholder]="field.example"
                           [attr.inputmode]="field.numeric ? 'numeric' : null" (input)="field.digitsOnly && keepDigits(field, $event)"
                           [attr.aria-invalid]="!!fieldError(field.name)" [attr.aria-describedby]="field.name + '-hint'"
                         class="form-control" [style.text-transform]="field.uppercase ? 'uppercase' : null"
                         [style.border-color]="fieldError(field.name) ? 'var(--color-danger)' : null" />
                  </span>
                </label>
                <small [id]="field.name + '-hint'" [style.color]="fieldError(field.name) ? 'var(--color-danger)' : 'var(--color-text-muted)'">
                  {{ fieldError(field.name) || field.hint }}
                </small>
              </div>
            }
          </div>
          <h2 style="font-size:1.2rem;">KYC documents</h2>
          <p class="muted" style="font-size:.875rem;margin-top:-.5rem;">A clear photo (JPG) or scan of each, up to 10 MB. They upload
            when you submit and are visible only to you and our reviewers.</p>
          <div style="display:grid;gap:.6rem;">
            @for (slot of keyDocuments; track slot.type) {
              @let chosen = chosenFiles()[slot.type];
              @let missing = documentsChecked() && slot.required && !chosen;
              <div role="group" [attr.aria-label]="slot.label"
                   style="display:flex;justify-content:space-between;align-items:center;gap:1rem;flex-wrap:wrap;padding:.65rem .9rem;border-radius:var(--radius-md);"
                   [style.border]="missing ? '1px solid var(--color-danger)' : '1px solid var(--color-border)'">
                <div style="min-width:0;">
                  <strong>{{ slot.label }}{{ slot.required ? '' : ' (optional)' }}</strong>
                  <div class="muted" style="font-size:.8125rem;">{{ slot.hint }} · {{ slot.formats }}</div>
                  <div style="font-size:.875rem;margin-top:.2rem;overflow-wrap:anywhere;"
                       [style.color]="missing ? 'var(--color-danger)' : (chosen ? null : 'var(--color-text-muted)')">
                    {{ chosen ? chosen.file.name : (missing ? 'Required.' : 'No file chosen') }}
                  </div>
                </div>
                <div style="display:flex;gap:.4rem;">
                  @if (chosen) {
                    <button type="button" class="btn btn-secondary" (click)="removeChosen(slot.type)"
                            [attr.aria-label]="'Remove ' + slot.label + ' file'">Remove</button>
                  }
                  <label class="btn btn-secondary">
                    {{ chosen ? 'Change' : 'Choose JPG' }}
                    <input type="file" [attr.accept]="slot.accept" [attr.aria-label]="'Choose ' + slot.label + ' file'"
                           (change)="choose(slot.type, $event)" [disabled]="submitting()" style="display:none;" />
                  </label>
                </div>
              </div>
            }
          </div>
          @if (documentError()) {
            <div class="alert alert-error" role="alert">{{ documentError() }}</div>
          }
          @if (error()) {
            <div class="alert alert-error" role="alert">{{ error() }}</div>
          }
          <button class="btn btn-primary" type="submit" [disabled]="submitting()">
            {{ submitting() ? (uploadingType() ? 'Uploading documents…' : 'Submitting…') : 'Submit for verification' }}
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

  readonly keyDocuments = KEY_DOCUMENTS;
  readonly otherDocumentTypes = DOCUMENT_TYPES.filter(type => !KEY_DOCUMENTS.some(slot => slot.type === type.value));
  readonly anyFormat = ANY_FORMAT_ACCEPT;
  readonly personalFields = PERSONAL_FIELDS;
  readonly payoutFields = PAYOUT_FIELDS;
  readonly loading = signal(true);
  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly profile = signal<OwnerProfile | null>(null);
  readonly documents = signal<OwnerDocument[]>([]);
  readonly documentError = signal<string | null>(null);
  /** The document type being uploaded, so only that row says "Uploading…". */
  readonly uploadingType = signal<string | null>(null);
  readonly otherDocuments = computed(() =>
    this.documents().filter(document => !KEY_DOCUMENTS.some(slot => slot.type === document.documentType)));
  documentType = 'ADDRESS_PROOF';
  /** Files picked in the application form, by document type; uploaded right after the details are submitted. */
  readonly chosenFiles = signal<Record<string, { file: File; contentType: string }>>({});
  /** Set on the first submit attempt, so missing documents are only flagged after the user tries. */
  readonly documentsChecked = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    businessType: ['INDIVIDUAL', Validators.required],
    companyName: ['', Validators.maxLength(255)],
    gstin: ['', Validators.pattern(/^$|^[0-9A-Z]{15}$/i)],
    pan: ['', [Validators.required, Validators.pattern(/^[A-Z]{5}[0-9]{4}[A-Z]$/i)]],
    bankAccountNumber: ['', [Validators.required, Validators.pattern(/^[0-9]{10}$/)]],
    confirmBankAccountNumber: ['', [Validators.required, matchesAccountNumber]],
    bankIfsc: ['', [Validators.required, Validators.pattern(/^[A-Z]{4}0[A-Z0-9]{6}$/i)]],
    bankAccountHolderName: ['', [Validators.required, Validators.maxLength(255)]],
    bankName: ['', [Validators.required, Validators.maxLength(100)]],
    bankAccountType: ['SAVINGS', Validators.required],
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', [Validators.required, Validators.pattern(/^[6-9][0-9]{9}$/)]],
    aadhaarNumber: ['', [Validators.required, aadhaarValidator]]
  });

  ngOnInit(): void {
    this.loadProfile();
  }

  /** The message for a field the user has left or tried to submit, or null when it is fine. */
  fieldError(name: FieldName): string | null {
    const control = this.form.controls[name];
    if (!control.invalid || !control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return 'Required.';
    }
    return FIELD_MESSAGES[name];
  }

  submit(): void {
    this.error.set(null);
    this.documentError.set(null);
    this.normalise();
    this.documentsChecked.set(true);
    const missing = KEY_DOCUMENTS.filter(slot => slot.required && !this.chosenFiles()[slot.type]).map(slot => slot.label);
    if (this.form.invalid || missing.length) {
      this.form.markAllAsTouched();
      this.error.set(missing.length
        ? `Please correct the highlighted fields. Choose a file for: ${missing.join(', ')}.`
        : 'Please correct the highlighted fields.');
      return;
    }
    this.submitting.set(true);
    const { confirmBankAccountNumber: _, ...value } = this.form.getRawValue();
    this.api.submit({
      ...value,
      phone: '+91' + value.phone,
      gstin: value.gstin.toUpperCase(),
      pan: value.pan.toUpperCase(),
      bankIfsc: value.bankIfsc.toUpperCase()
    }).subscribe({
      next: async submitted => {
        const failed = await this.uploadChosen(submitted.ownerProfileId);
        this.submitting.set(false);
        this.chosenFiles.set({});
        this.loadProfile();
        if (failed.length) {
          this.documentError.set(`Your details were submitted, but ${failed.join(', ')} could not be uploaded. ` +
            'Please upload them again below.');
        }
      },
      error: (failure: Error) => {
        this.error.set(failure.message || 'Owner verification could not be submitted.');
        this.submitting.set(false);
      }
    });
  }

  /** Keeps the file for the form; it is checked now and uploaded once the application exists. */
  choose(documentType: string, event: Event): void {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const checked = checkFile(file, documentType);
    if ('error' in checked) {
      this.documentError.set(checked.error);
      return;
    }
    this.documentError.set(null);
    this.chosenFiles.update(files => ({ ...files, [documentType]: { file, contentType: checked.contentType } }));
  }

  removeChosen(documentType: string): void {
    this.chosenFiles.update(({ [documentType]: _, ...rest }) => rest);
  }

  /** Uploads each chosen file in turn; returns the labels of those that failed. */
  private async uploadChosen(ownerProfileId: string): Promise<string[]> {
    const failed: string[] = [];
    for (const slot of KEY_DOCUMENTS) {
      const chosen = this.chosenFiles()[slot.type];
      if (!chosen) {
        continue;
      }
      this.uploadingType.set(slot.type);
      try {
        await this.api.uploadDocument(ownerProfileId, slot.type, chosen.file, chosen.contentType);
      } catch {
        failed.push(slot.label);
      }
    }
    this.uploadingType.set(null);
    return failed;
  }

  async upload(ownerProfileId: string, documentType: string, event: Event): Promise<void> {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    const checked = checkFile(file, documentType);
    if ('error' in checked) {
      this.documentError.set(checked.error);
      return;
    }
    const contentType = checked.contentType;
    this.documentError.set(null);
    this.uploadingType.set(documentType);
    try {
      await this.api.uploadDocument(ownerProfileId, documentType, file, contentType);
      this.loadDocuments(ownerProfileId);
    } catch {
      this.documentError.set('The document upload failed. Retry, or contact support if it keeps failing.');
    } finally {
      this.uploadingType.set(null);
    }
  }

  /** The most recent upload of a type; a replacement is a newer upload, and reviewers see every version. */
  latestDocument(type: string): OwnerDocument | undefined {
    return this.documents().filter(document => document.documentType === type).at(-1);
  }

  /** Drops anything but digits as it is typed or pasted; a pasted +91 or 91 country code is removed from a phone. */
  keepDigits(field: FieldSpec, event: Event): void {
    const input = event.target as HTMLInputElement;
    let digits = input.value.replace(/\D/g, '');
    if (field.prefix === '+91' && digits.length > 10 && digits.startsWith('91')) {
      digits = digits.slice(2);
    }
    digits = digits.slice(0, field.maxLength);
    if (digits !== input.value) {
      this.form.controls[field.name].setValue(digits);
    }
  }

  typeLabel(value: string): string {
    return documentTypeLabel(value);
  }

  accountTypeLabel(value: string | null): string {
    return value ? ACCOUNT_TYPES[value] ?? value : '';
  }

  /** Accepts what people usually type: stray spaces, spaced-out account numbers, lower-case codes. */
  private normalise(): void {
    const value = this.form.getRawValue();
    this.form.patchValue({
      companyName: value.companyName.trim(),
      firstName: value.firstName.trim(),
      lastName: value.lastName.trim(),
      phone: value.phone.replace(/\D/g, ''),
      aadhaarNumber: value.aadhaarNumber.replace(/[\s-]/g, ''),
      gstin: value.gstin.replace(/\s/g, '').toUpperCase(),
      pan: value.pan.replace(/\s/g, '').toUpperCase(),
      bankAccountHolderName: value.bankAccountHolderName.trim(),
      bankName: value.bankName.trim(),
      bankAccountNumber: value.bankAccountNumber.replace(/[\s-]/g, ''),
      confirmBankAccountNumber: value.confirmBankAccountNumber.replace(/[\s-]/g, ''),
      bankIfsc: value.bankIfsc.replace(/\s/g, '').toUpperCase()
    });
    // The confirmation is checked against the account number, which may have changed after it was typed.
    this.form.controls.confirmBankAccountNumber.updateValueAndValidity();
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
