import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog.component';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { OwnerApiService, OwnerDocument, OwnerReview } from '../owner/owner-api.service';

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

          <div style="margin-top: 1rem;">
            <button type="button" class="btn btn-secondary" (click)="toggleDocuments(owner.ownerProfileId)">
              {{ documents()[owner.ownerProfileId] ? 'Hide documents' : 'View documents' }}
            </button>
          </div>
          @if (documents()[owner.ownerProfileId]; as list) {
            @if (list.length === 0) {
              <p class="muted" style="margin-top: .75rem;">No documents uploaded.</p>
            }
            <ul style="margin-top: .75rem; padding-left: 1.25rem;">
              @for (document of list; track document.documentId) {
                <li style="margin: .35rem 0;">
                  {{ document.documentType }} · {{ document.originalFilename }} · {{ document.status }}
                  @if (document.uploadedAt) {
                    <button type="button" class="btn btn-secondary" style="padding: .2rem .6rem; margin-left: .5rem;"
                            (click)="download(owner.ownerProfileId, document)">Download</button>
                  }
                </li>
              }
            </ul>
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
              <button type="submit" class="btn btn-primary" [disabled]="saving() || !validAccount(owner.ownerProfileId)">Save</button>
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
  readonly documents = signal<Record<string, OwnerDocument[]>>({});
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly noticeIsWarning = signal(false);
  readonly pending = signal<{ owner: OwnerReview; action: 'approve' | 'reject' } | null>(null);
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
      next: list => this.documents.update(current => ({ ...current, [ownerProfileId]: list })),
      error: (failure: Error) => this.error.set(failure.message)
    });
  }

  download(ownerProfileId: string, document: OwnerDocument): void {
    this.api.adminDocumentDownload(ownerProfileId, document.documentId).subscribe({
      next: ({ downloadUrl }) => window.open(downloadUrl, '_blank', 'noopener'),
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
    return /^acc_[A-Za-z0-9]{1,60}$/.test(this.accountIds[ownerProfileId] ?? '');
  }

  linkAccount(owner: OwnerReview): void {
    const accountId = this.accountIds[owner.ownerProfileId];
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
