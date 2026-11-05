import { Component, OnInit, inject, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ApiError } from '../../core/interceptors/error.interceptor';
import { OwnerApiService, OwnerProfile } from './owner-api.service';

/**
 * Why an account cannot list equipment yet, or null once its owner profile is verified. The owner role comes from
 * sign-in (Keycloak) while the profile lives in the marketplace, so an account can have the role without a profile.
 */
export function ownerSetupMessage(profile: OwnerProfile | null): string | null {
  if (!profile) {
    return 'Your account has owner access, but your business and payout details are not on file yet. '
      + 'Complete them; once our team verifies them you can list equipment and take bookings.';
  }
  switch (profile.status) {
    case 'VERIFIED':
      return null;
    case 'UNDER_REVIEW':
      return 'Your owner details are with our team for verification. You can list equipment as soon as they are approved.';
    case 'REJECTED':
      return 'Your owner application was not approved'
        + (profile.rejectionReason ? `: ${profile.rejectionReason}` : '.') + ' Contact support to update your details.';
    default:
      return `Your owner account is ${profile.status.toLowerCase().replace(/_/g, ' ')}. Please contact support.`;
  }
}

/**
 * Checks the signed-in owner's profile and, unless it is verified, explains what is missing with a link to fix it.
 * Emits `verified` once known, so the page can show its owner tools only when they will work.
 */
@Component({
  selector: 'app-owner-setup-notice',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (message(); as text) {
      <section class="card" role="status" style="padding: 1.5rem; margin: 1.5rem 0; border-left: 4px solid var(--color-accent-amber);">
        <h2 style="font-size: 1.15rem;">{{ hasProfile() ? 'Owner verification' : 'Finish setting up your owner account' }}</h2>
        <p style="margin: .5rem 0 1rem;">{{ text }}</p>
        <a class="btn btn-primary" routerLink="/owner/onboarding">{{ hasProfile() ? 'View owner application' : 'Complete owner details' }}</a>
      </section>
    }
  `
})
export class OwnerSetupNoticeComponent implements OnInit {
  private readonly api = inject(OwnerApiService);

  readonly verified = output<boolean>();
  readonly message = signal<string | null>(null);
  readonly hasProfile = signal(false);

  ngOnInit(): void {
    this.api.myProfile().subscribe({
      next: profile => this.settle(profile),
      error: (failure: ApiError) => {
        if (failure.status === 404) {
          this.settle(null);
        } else {
          // Cannot tell; let the page load and report its own errors rather than hide it.
          this.verified.emit(true);
        }
      }
    });
  }

  private settle(profile: OwnerProfile | null): void {
    this.hasProfile.set(!!profile);
    this.message.set(ownerSetupMessage(profile));
    this.verified.emit(profile?.status === 'VERIFIED');
  }
}
