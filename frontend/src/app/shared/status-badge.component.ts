import { Component, computed, input } from '@angular/core';

type Tone = 'success' | 'info' | 'warning' | 'danger' | 'neutral';

const STATUS: Record<string, { label: string; tone: Tone }> = {
  REQUESTED: { label: 'Requested', tone: 'warning' },
  PAYMENT_PENDING: { label: 'Awaiting payment', tone: 'warning' },
  PAYMENT_FAILED: { label: 'Payment failed', tone: 'danger' },
  CONFIRMED: { label: 'Confirmed', tone: 'success' },
  HANDOVER_PENDING: { label: 'Handover due', tone: 'info' },
  IN_PROGRESS: { label: 'In progress', tone: 'info' },
  RETURN_PENDING: { label: 'Return due', tone: 'info' },
  RETURNED: { label: 'Returned', tone: 'info' },
  DISPUTED: { label: 'Disputed', tone: 'danger' },
  INSPECTION_PASSED: { label: 'Inspection passed', tone: 'success' },
  COMPLETED: { label: 'Completed', tone: 'success' },
  CLOSED: { label: 'Closed', tone: 'neutral' },
  CANCELLED_BY_RENTER: { label: 'Cancelled by renter', tone: 'neutral' },
  CANCELLED_BY_OWNER: { label: 'Cancelled by owner', tone: 'neutral' },
  OWNER_REJECTED: { label: 'Declined', tone: 'neutral' },
  REQUEST_EXPIRED: { label: 'Request expired', tone: 'neutral' },
  HOLD_EXPIRED: { label: 'Payment window expired', tone: 'neutral' },
  // Owner KYC and listing statuses
  UNDER_REVIEW: { label: 'Under review', tone: 'warning' },
  VERIFIED: { label: 'Verified', tone: 'success' },
  REJECTED: { label: 'Rejected', tone: 'danger' },
  SUSPENDED: { label: 'Suspended', tone: 'danger' },
  DRAFT: { label: 'Draft', tone: 'neutral' },
  PENDING_APPROVAL: { label: 'Pending approval', tone: 'warning' },
  ACTIVE: { label: 'Live', tone: 'success' },
  // Disputes
  OPEN: { label: 'Open', tone: 'danger' },
  RESOLVED: { label: 'Resolved', tone: 'success' }
};

const TONE_CLASS: Record<Tone, string> = {
  success: 'badge-verified',
  info: 'badge-role',
  warning: 'badge-amber',
  danger: 'badge-danger',
  neutral: 'badge-neutral'
};

/** Human-readable, colour-coded badge for booking, KYC, listing and dispute statuses. */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  template: `<span [class]="'badge ' + toneClass()">{{ label() }}</span>`
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();

  readonly label = computed(() => STATUS[this.status()]?.label ?? this.status().replace(/_/g, ' ').toLowerCase());
  readonly toneClass = computed(() => TONE_CLASS[STATUS[this.status()]?.tone ?? 'neutral']);
}
