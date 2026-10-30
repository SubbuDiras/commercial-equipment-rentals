import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { documentTypeLabel, OwnerApiService, OwnerDocument, OwnerReview } from '../owner/owner-api.service';

const TABS = [
  { status: 'UNDER_REVIEW', label: 'Waiting for review' },
  { status: 'VERIFIED', label: 'Verified' },
  { status: 'REJECTED', label: 'Rejected' }
];

@Component({
  selector: 'app-owner-review',
  standalone: true,
  imports: [DatePipe, FormsModule, ConfirmDialogComponent, StatusBadgeComponent],
  template: `
    <main class="container" style="max-width: 1000px; padding: 3rem 1rem;">
      <h1>Owner KYC</h1>
      <p class="muted">Review owner applications and their documents. Approving grants the owner role automatically.</p>

      <div class="tabs" role="tablist">
        @for (tab of tabs; track tab.status) {
          <button type="button" class="tab" role="tab" [class.active]="status() === tab.status"
                  [attr.aria-selected]="status() === tab.status" (click)="select(tab.status)">{{ tab.label }}</button>
        }
      </div>

      @if (notice(); as message) {
        <div class="alert" [class.alert-success]="!noticeIsWarning()" [class.alert-warning]="noticeIsWarning()" role="status">{{ message }}</div>
      }
      @if (error()) {
        <div class="alert alert-error" role="alert">{{ error() }}</div>
      }
      @if (loading()) {
        <p role="status">Loading owners…</p>
      } @else if (owners().length === 0) {
        <p class="muted">No owners in this list.</p>
      }

      @for (owner of owners(); track owner.ownerProfileId) {
        <article class="card" style="padding: 1.25rem; margin: 1rem 0;">
          <div style="display: flex; justify-content: space-between; gap: 1rem; flex-wrap: wrap;">
            <div>
              <h2 style="font-size: 1.15rem;">{{ owner.firstName }} {{ owner.lastName }}</h2>
              <div class="muted">{{ owner.email }}</div>
              <div>{{ owner.businessName || 'No business name' }} · {{ owner.businessType }}</div>
              <div class="muted" style="font-size: .875rem;">Submitted {{ owner.submittedAt | date:'medium' }}</div>
            </div>
            <app-status-badge [status]="owner.status" />
          </div>

          @if (owner.kyc; as kyc) {
            <dl [attr.aria-label]="'KYC details of ' + owner.firstName + ' ' + owner.lastName"
                style="display: grid; grid-template-columns: minmax(130px, auto) 1fr; gap: .3rem 1rem; margin-top: 1rem; font-size: .9375rem;">
              <dt class="muted">Phone</dt><dd>{{ kyc.phone || '—' }}</dd>
              <dt class="muted">Aadhaar</dt><dd>{{ kyc.aadhaarLast4 ? 'XXXX XXXX ' + kyc.aadhaarLast4 : 'Not provided' }}</dd>
              <dt class="muted">PAN</dt><dd>{{ kyc.pan || '—' }}</dd>
              <dt class="muted">GSTIN</dt><dd>{{ kyc.gstin || '—' }}</dd>
              <dt class="muted">Bank</dt><dd>{{ kyc.bankName || 'Not provided' }}{{ kyc.bankAccountType ? ' · ' + (kyc.bankAccountType === 'CURRENT' ? 'Current' : 'Savings') : '' }}</dd>
              <dt class="muted">Account number</dt><dd>{{ kyc.bankAccountNumber }}</dd>
              <dt class="muted">IFSC</dt><dd>{{ kyc.bankIfsc }}</dd>
              <dt class="muted">Account holder</dt><dd>{{ kyc.bankAccountHolderName }}</dd>
            </dl>
          }

          <div style="margin-top: 1rem;">
            <button type="button" class="btn btn-secondary" (click)="toggleDocuments(owner.ownerProfileId)">
              {{ documents()[owner.ownerProfileId] ? 'Hide documents' : 'View documents' }}
            </button>
          </div>
          @if (documents()[owner.ownerProfileId]; as list) {
            @if (list.length === 0) {
              <p class="muted" style="margin-top: .75rem;">No documents uploaded.</p>
            }
            <div style="display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 1rem; margin-top: .75rem;">
              @for (document of list; track document.documentId) {
                @let preview = previews()[document.documentId];
                <figure [attr.aria-label]="typeLabel(document.documentType)"
                        style="margin: 0; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; background: var(--color-bg-surface);">
                  <div style="height: 220px; display: grid; place-items: center; background: #f1f5f9;">
                    @if (!document.uploadedAt) {
                      <span class="muted" style="font-size: .875rem;">Upload not finished</span>
                    } @else if (!preview) {
                      <span class="muted" style="font-size: .875rem;">Loading…</span>
                    } @else if (document.contentType === 'application/pdf') {
                      <object [data]="preview.safe" type="application/pdf" [attr.aria-label]="document.originalFilename"
                              style="width: 100%; height: 100%;">
                        <a [href]="preview.url" target="_blank" rel="noopener">Open PDF</a>
                      </object>
                    } @else {
                      <a [href]="preview.url" target="_blank" rel="noopener" style="display: block; width: 100%; height: 100%;"
                         [attr.aria-label]="'Open ' + typeLabel(document.documentType) + ' full size'">
                        <img [src]="preview.url" [alt]="typeLabel(document.documentType) + ': ' + document.originalFilename"
                             style="width: 100%; height: 100%; object-fit: contain;" />
                      </a>
                    }
                  </div>
                  <figcaption style="padding: .6rem .75rem; font-size: .875rem;">
                    <strong>{{ typeLabel(document.documentType) }}</strong>
                    <div class="muted" style="overflow-wrap: anywhere;">{{ document.originalFilename }} · {{ document.status }}</div>
                    @if (preview) {
                      <a [href]="preview.url" target="_blank" rel="noopener" style="font-size: .8125rem;">Open full size</a>
                    }
                  </figcaption>
                </figure>
              }
            </div>
          }

          @if (owner.status === 'UNDER_REVIEW') {
            <div style="display: flex; gap: .75rem; margin-top: 1rem;">
              <button type="button" class="btn btn-primary" (click)="pending.set({ owner, action: 'approve' })">Approve</button>
              <button type="button" class="btn btn-secondary" (click)="pending.set({ owner, action: 'reject' })">Reject</button>
            </div>
          }
          @if (owner.status === 'VERIFIED') {
            <form style="display: flex; gap: .75rem; align-items: flex-end; margin-top: 1rem; flex-wrap: wrap;"
                  (ngSubmit)="linkAccount(owner)">
              <label style="flex: 1; min-width: 260px;">Razorpay linked account ID (for payouts)
                <input class="form-control" [name]="'account-' + owner.ownerProfileId"
                       [(ngModel)]="accountIds[owner.ownerProfileId]" pattern="acc_[A-Za-z0-9]{1,60}" />
              </label>
              <button type="button" class="btn btn-secondary" (click)="fakeAccount(owner.ownerProfileId)">Generate fake ID</button>
              <button type="submit" class="btn btn-primary" [disabled]="saving()">Save</button>
            </form>
          }
        </article>
      }

      @if (pending(); as request) {
        <app-confirm-dialog
          [title]="request.action === 'approve' ? 'Approve owner?' : 'Reject owner?'"
          [message]="request.action === 'approve'
            ? request.owner.firstName + ' ' + request.owner.lastName + ' will be able to publish listings.'
            : 'The applicant will see the reason you give.'"
          [confirmLabel]="request.action === 'approve' ? 'Approve' : 'Reject'"
          [reasonLabel]="request.action === 'reject' ? 'Reason for rejection' : ''"
          [danger]="request.action === 'reject'"
          [busy]="saving()"
          (confirmed)="decide(request.owner, request.action, $event)"
          (cancelled)="pending.set(null)" />
      }
    </main>
  `
})
export class OwnerReviewComponent implements OnInit {
  private readonly api = inject(OwnerApiService);

  readonly tabs = TABS;
  readonly status = signal('UNDER_REVIEW');
  readonly owners = signal<OwnerReview[]>([]);
  readonly typeLabel = documentTypeLabel;
  readonly documents = signal<Record<string, OwnerDocument[]>>({});
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly noticeIsWarning = signal(false);
  readonly pending = signal<{ owner: OwnerReview; action: 'approve' | 'reject' } | null>(null);
  private readonly sanitizer = inject(DomSanitizer);
  /** Viewable links for the documents on screen, by document id. */
  readonly previews = signal<Record<string, { url: string; safe: SafeResourceUrl }>>({});
  accountIds: Record<string, string> = {};

  ngOnInit(): void {
    this.load();
  }

  select(status: string): void {
    this.status.set(status);
    this.notice.set(null);
    this.load();
  }

  toggleDocuments(ownerProfileId: string): void {
    if (this.documents()[ownerProfileId]) {
      this.documents.update(current => {
        const { [ownerProfileId]: _removed, ...rest } = current;
        return rest;
      });
      return;
    }
    this.api.adminDocuments(ownerProfileId).subscribe({
      next: list => {
        this.documents.update(current => ({ ...current, [ownerProfileId]: list }));
        list.filter(document => document.uploadedAt).forEach(document => this.loadPreview(ownerProfileId, document));
      },
      error: (failure: Error) => this.error.set(failure.message)
    });
  }

  /** A short-lived link that shows the file in the page; every view is recorded in the audit log. */
  private loadPreview(ownerProfileId: string, document: OwnerDocument): void {
    this.api.adminDocumentDownload(ownerProfileId, document.documentId, true).subscribe({
      next: ({ downloadUrl }) => this.previews.update(current => ({
        ...current,
        // The PDF viewer needs a trusted resource URL; the link is our own storage's signed address.
        [document.documentId]: { url: downloadUrl, safe: this.sanitizer.bypassSecurityTrustResourceUrl(downloadUrl) }
      })),
      error: (failure: Error) => this.error.set(failure.message)
    });
  }

  decide(owner: OwnerReview, action: 'approve' | 'reject', reason: string): void {
    this.saving.set(true);
    this.error.set(null);
    const request = action === 'approve' ? this.api.approve(owner.ownerProfileId) : this.api.reject(owner.ownerProfileId, reason);
    request.subscribe({
      next: result => {
        this.saving.set(false);
        this.pending.set(null);
        this.noticeIsWarning.set(!!result.warning);
        this.notice.set(result.warning
          ?? (action === 'approve'
            ? `${owner.firstName} ${owner.lastName} is approved and has the owner role. They need to sign in again to use it.`
            : `${owner.firstName} ${owner.lastName} was rejected.`));
        this.load();
      },
      error: (failure: Error) => {
        this.saving.set(false);
        this.error.set(failure.message);
      }
    });
  }

  fakeAccount(ownerProfileId: string): void {
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    const suffix = Array.from({ length: 10 }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('');
    this.accountIds[ownerProfileId] = `acc_FAKE${suffix}`;
  }

  validAccount(ownerProfileId: string): boolean {
    return /^acc_[A-Za-z0-9]{1,60}$/.test((this.accountIds[ownerProfileId] ?? '').trim());
  }

  linkAccount(owner: OwnerReview): void {
    const accountId = (this.accountIds[owner.ownerProfileId] ?? '').trim();
    if (!this.validAccount(owner.ownerProfileId)) {
      this.notice.set(null);
      this.error.set('Enter a Razorpay linked account ID such as acc_ABC123 (letters and digits after "acc_"), or click Generate fake ID.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.linkRazorpayAccount(owner.ownerProfileId, accountId).subscribe({
      next: () => {
        this.saving.set(false);
        this.noticeIsWarning.set(false);
        this.notice.set(`Payout account ${accountId} saved for ${owner.firstName} ${owner.lastName}.`);
      },
      error: (failure: Error) => {
        this.saving.set(false);
        this.error.set(failure.message);
      }
    });
  }

  private load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.documents.set({});
    this.api.ownersForReview(this.status()).subscribe({
      next: owners => {
        this.owners.set(owners);
        this.loading.set(false);
      },
      error: (failure: Error) => {
        this.error.set(failure.message);
        this.loading.set(false);
      }
    });
  }
}
