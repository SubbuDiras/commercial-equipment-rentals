import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

interface OwnerProfileResponse {
  ownerProfileId: string;
  status: string;
  submittedAt: string;
}

interface OwnerDocumentResponse {
  documentId: string;
  documentType: string;
  originalFilename: string;
  status: string;
  uploadedAt: string | null;
}

interface OwnerDocumentUploadResponse {
  documentId: string;
  uploadUrl: string;
  contentType: string;
  expiresAt: string;
}

@Component({
  selector: 'app-owner-onboarding',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  template: `
    <main class="container" style="max-width:760px;padding:3rem 1rem;">
      <h1>Owner verification application</h1>
      <p style="color:var(--color-text-muted);">Submit your business and payout details for manual review. Listings can be published after your owner profile is verified.</p>
      <form [formGroup]="form" (ngSubmit)="submit()" class="card" style="display:grid;gap:1rem;padding:1.5rem;margin-top:1.5rem;">
        <label>Account type
          <select formControlName="businessType" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;">
            <option value="INDIVIDUAL">Individual</option>
            <option value="PROPRIETORSHIP">Proprietorship</option>
            <option value="PARTNERSHIP">Partnership</option>
            <option value="PVT_LTD">Private limited company</option>
          </select>
        </label>
        <label>Business / trading name (optional)
          <input formControlName="companyName" maxlength="255" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;" />
        </label>
        <div class="grid grid-cols-2" style="gap:1rem;">
          <label>First name<input formControlName="firstName" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;" /></label>
          <label>Last name<input formControlName="lastName" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;" /></label>
          <label>Phone<input formControlName="phone" maxlength="20" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;" /></label>
          <label>GSTIN (optional)<input formControlName="gstin" maxlength="15" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;text-transform:uppercase;" /></label>
          <label>PAN (optional)<input formControlName="pan" maxlength="10" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;text-transform:uppercase;" /></label>
        </div>
        <h2 style="font-size:1.2rem;">Payout account details</h2>
        <label>Account holder name<input formControlName="bankAccountHolderName" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;" /></label>
        <div class="grid grid-cols-2" style="gap:1rem;">
          <label>Bank account number<input formControlName="bankAccountNumber" inputmode="numeric" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;" /></label>
          <label>IFSC code<input formControlName="bankIfsc" maxlength="11" style="display:block;width:100%;padding:.65rem;margin-top:.35rem;text-transform:uppercase;" /></label>
        </div>
        <p *ngIf="error()" role="alert" style="color:var(--color-danger);">{{ error() }}</p>
        <section *ngIf="submitted() as profile" role="status" class="card" style="padding:1rem;background:var(--color-bg-muted);">
          Application submitted · Status: <strong>{{ profile.status }}</strong>
        </section>
        <button class="btn btn-primary" type="submit" [disabled]="form.invalid || submitting() || submitted()">
          {{ submitting() ? 'Submitting…' : 'Submit for verification' }}
        </button>
      </form>
      <section *ngIf="submitted() as profile" class="card" style="padding:1.5rem;margin-top:1.5rem;">
        <h2 style="font-size:1.2rem;">Upload verification documents</h2>
        <p style="color:var(--color-text-muted);">Files are uploaded to private storage and are available only to you and authorized reviewers. PDF, JPEG, or PNG; up to 10 MB each.</p>
        <div style="display:flex;gap:.75rem;align-items:center;flex-wrap:wrap;">
          <label>Document type
            <select [(ngModel)]="documentType" name="documentType" style="display:block;padding:.6rem;margin-top:.3rem;">
              <option value="IDENTITY">Identity</option>
              <option value="BUSINESS_REGISTRATION">Business registration</option>
              <option value="GST_CERTIFICATE">GST certificate</option>
              <option value="PAN_CARD">PAN card</option>
              <option value="BANK_PROOF">Bank proof</option>
              <option value="ADDRESS_PROOF">Address proof</option>
              <option value="OTHER">Other</option>
            </select>
          </label>
          <label class="btn btn-secondary" style="margin-top:1.25rem;">
            Choose and upload
            <input type="file" accept="application/pdf,image/jpeg,image/png" (change)="uploadDocument(profile.ownerProfileId, $event)" [disabled]="uploadingDocument()" style="display:none;" />
          </label>
        </div>
        <p *ngIf="documentError()" role="alert" style="color:var(--color-danger);">{{ documentError() }}</p>
        <p *ngIf="uploadingDocument()" role="status">Uploading document…</p>
        <ul *ngIf="documents().length" style="margin-top:1rem;">
          <li *ngFor="let document of documents()">{{ document.documentType }} · {{ document.originalFilename }} · {{ document.status }}</li>
        </ul>
      </section>
    </main>
  `
})
export class OwnerOnboardingComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly http = inject(HttpClient);

  readonly submitting = signal(false);
  readonly error = signal<string | null>(null);
  readonly submitted = signal<OwnerProfileResponse | null>(null);
  readonly documents = signal<OwnerDocumentResponse[]>([]);
  readonly documentError = signal<string | null>(null);
  readonly uploadingDocument = signal(false);
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

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const value = this.form.getRawValue();
    const request = {
      ...value,
      gstin: value.gstin.toUpperCase(),
      pan: value.pan.toUpperCase(),
      bankIfsc: value.bankIfsc.toUpperCase()
    };
    this.http.post<OwnerProfileResponse>('/api/v1/owner/onboarding', request).subscribe({
      next: profile => {
        this.submitted.set(profile);
        this.submitting.set(false);
        this.loadDocuments(profile.ownerProfileId);
      },
      error: () => {
        this.error.set('Owner verification could not be submitted. Check your details or contact support.');
        this.submitting.set(false);
      }
    });
  }

  async uploadDocument(ownerProfileId: string, event: Event): Promise<void> {
    const input = event.currentTarget as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) {
      return;
    }
    if (!['application/pdf', 'image/jpeg', 'image/png'].includes(file.type)
        || file.size < 1 || file.size > 10 * 1024 * 1024) {
      this.documentError.set('Choose a PDF, JPEG, or PNG document no larger than 10 MB.');
      return;
    }
    this.documentError.set(null);
    this.uploadingDocument.set(true);
    try {
      const upload = await firstValueFrom(this.http.post<OwnerDocumentUploadResponse>(
        `/api/v1/owner/onboarding/${encodeURIComponent(ownerProfileId)}/documents`,
        {
          documentType: this.documentType,
          originalFilename: file.name,
          contentType: file.type,
          contentLength: file.size
        }
      ));
      const response = await fetch(upload.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': upload.contentType },
        body: file
      });
      if (!response.ok) {
        throw new Error(`Storage upload failed with HTTP ${response.status}.`);
      }
      await firstValueFrom(this.http.post(
        `/api/v1/owner/onboarding/${encodeURIComponent(ownerProfileId)}/documents/${encodeURIComponent(upload.documentId)}/complete`,
        {}
      ));
      this.loadDocuments(ownerProfileId);
    } catch {
      this.documentError.set('The document upload failed. Retry or contact support if the problem continues.');
    } finally {
      this.uploadingDocument.set(false);
    }
  }

  private loadDocuments(ownerProfileId: string): void {
    this.http.get<OwnerDocumentResponse[]>(
      `/api/v1/owner/onboarding/${encodeURIComponent(ownerProfileId)}/documents`
    ).subscribe({
      next: documents => this.documents.set(documents),
      error: () => this.documentError.set('Your uploaded documents could not be loaded.')
    });
  }
}
