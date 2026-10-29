import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { ApiError } from '../../core/interceptors/error.interceptor';
import { invalidFieldsMessage } from '../../shared/form-feedback';
import { StatusBadgeComponent } from '../../shared/status-badge.component';
import { OwnerApiService, OwnerProfile } from '../owner/owner-api.service';
import { ownerSetupMessage } from '../owner/owner-setup-notice.component';
import { MyProfile, ProfileApiService } from './profile-api.service';

const ROLE_LABELS: Record<string, string> = {
  ROLE_RENTER: 'Renter',
  ROLE_OWNER: 'Owner',
  ROLE_ADMIN: 'Admin',
  ROLE_SUPPORT: 'Support'
};

/** The signed-in user's own details, for every role; name and phone are editable here. */
@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [DatePipe, ReactiveFormsModule, RouterLink, StatusBadgeComponent],
  template: `
    <main class="container-wide" style="max-width: 860px; margin: 0 auto; padding-top: 2rem; padding-bottom: 3rem;">
      <h1>My profile</h1>
      <p class="muted">Your details as owners, renters and our team see them.</p>

      @if (notice(); as message) {
        <div class="alert" [class.alert-success]="!noticeIsWarning()" [class.alert-warning]="noticeIsWarning()" role="status">{{ message }}</div>
      }
      @if (loadError()) {
        <div class="alert alert-error" role="alert">{{ loadError() }}</div>
      }

      @if (profile(); as me) {
        <section class="card" style="padding: 1.5rem; margin-top: 1.5rem;" aria-labelledby="personal-heading">
          <div style="display: flex; justify-content: space-between; align-items: center; gap: 1rem; flex-wrap: wrap;">
            <h2 id="personal-heading" style="font-size: 1.2rem;">Personal details</h2>
            @if (!editing()) {
              <button type="button" class="btn btn-secondary" (click)="startEdit(me)">Edit details</button>
            }
          </div>

          @if (!editing()) {
            <dl class="kv" style="margin-top: 1rem;">
              <dt>Name</dt><dd>{{ me.firstName }} {{ me.lastName }}</dd>
              <dt>Email</dt><dd>{{ me.email }}</dd>
              <dt>Phone</dt><dd>{{ me.phone || 'Not added' }}</dd>
              <dt>Account type</dt>
              <dd>
                @for (role of me.roles; track role) {
                  <span class="badge badge-role" style="margin-right: .35rem;">{{ roleLabel(role) }}</span>
                } @empty {
                  <span class="muted">No roles yet</span>
                }
              </dd>
              <dt>Member since</dt><dd>{{ me.memberSince | date:'mediumDate' }}</dd>
            </dl>
          } @else {
            <form [formGroup]="form" (ngSubmit)="save()" novalidate style="display: grid; gap: 1rem; margin-top: 1rem;">
              <div class="grid grid-cols-2" style="gap: 1rem;">
                <label>First name
                  <input formControlName="firstName" maxlength="100" autocomplete="given-name" class="form-control" />
                </label>
                <label>Last name
                  <input formControlName="lastName" maxlength="100" autocomplete="family-name" class="form-control" />
                </label>
              </div>
              <div>
                <label>Phone
                  <input formControlName="phone" maxlength="20" inputmode="tel" autocomplete="tel" placeholder="+919876543210"
                         aria-describedby="phone-hint" class="form-control" />
                </label>
                <small id="phone-hint" class="muted">8 to 15 digits, with the country code. Leave empty to remove it.</small>
              </div>
              <div>
                <label>Email
                  <input [value]="me.email" disabled aria-describedby="email-hint" class="form-control" />
                </label>
                <small id="email-hint" class="muted">Your email is how you sign in, so it is changed on the account page below.</small>
              </div>
              @if (formError()) {
                <div class="alert alert-error" role="alert">{{ formError() }}</div>
              }
              <div style="display: flex; gap: .75rem;">
                <button type="submit" class="btn btn-primary" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save changes' }}</button>
                <button type="button" class="btn btn-secondary" (click)="cancelEdit()" [disabled]="saving()">Cancel</button>
              </div>
            </form>
          }
        </section>

        <section class="card" style="padding: 1.5rem; margin-top: 1.5rem;" aria-labelledby="security-heading">
          <h2 id="security-heading" style="font-size: 1.2rem;">Sign-in and security</h2>
          <p class="muted" style="margin: .5rem 0 1rem;">Change your email address or password on your account page. It opens in a new tab.</p>
          <a class="btn btn-secondary" [href]="auth.accountConsoleUrl" target="_blank" rel="noopener">Change email or password ↗</a>
        </section>

        @if (showsOwnerSection(me)) {
          <section class="card" style="padding: 1.5rem; margin-top: 1.5rem;" aria-labelledby="owner-heading">
            <h2 id="owner-heading" style="font-size: 1.2rem;">Equipment owner</h2>
            @if (owner(); as current) {
              <dl class="kv" style="margin-top: 1rem;">
                <dt>Status</dt><dd><app-status-badge [status]="current.status" /></dd>
                <dt>Business</dt><dd>{{ current.businessName || 'Individual' }}</dd>
                <dt>Payout account</dt><dd>{{ current.razorpayAccountLinked ? 'Linked' : 'Not linked yet' }}</dd>
              </dl>
              @if (setupMessage(current); as text) {
                <div class="alert alert-warning" style="margin-top: .75rem;">{{ text }}</div>
              }
              <p class="muted" style="margin-top: .75rem;">Business and bank details are checked by our team, so they cannot be edited here.
                Contact support to change them.</p>
              <a routerLink="/owner/onboarding" style="display: inline-block; margin-top: .5rem;">View your owner application</a>
            } @else if (ownerChecked()) {
              @if (me.roles.includes('ROLE_OWNER')) {
                <div class="alert alert-warning" style="margin: .75rem 0 1rem;">{{ setupMessage(null) }}</div>
                <a class="btn btn-primary" routerLink="/owner/onboarding">Complete owner details</a>
              } @else {
                <p class="muted" style="margin: .5rem 0 1rem;">List your idle equipment and earn from it once our team verifies you.</p>
                <a class="btn btn-primary" routerLink="/owner/onboarding">Become an owner</a>
              }
            }
          </section>
        }
      } @else if (!loadError()) {
        <p role="status">Loading your profile…</p>
      }
    </main>
  `
})
export class ProfileComponent implements OnInit {
  private readonly api = inject(ProfileApiService);
  private readonly ownerApi = inject(OwnerApiService);
  private readonly formBuilder = inject(FormBuilder);
  readonly auth = inject(AuthService);

  readonly profile = signal<MyProfile | null>(null);
  readonly owner = signal<OwnerProfile | null>(null);
  readonly ownerChecked = signal(false);
  readonly editing = signal(false);
  readonly saving = signal(false);
  readonly loadError = signal<string | null>(null);
  readonly formError = signal<string | null>(null);
  readonly notice = signal<string | null>(null);
  readonly noticeIsWarning = signal(false);

  readonly form = this.formBuilder.nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    phone: ['', Validators.pattern(/^$|^\+?[0-9]{8,15}$/)]
  });

  ngOnInit(): void {
    this.api.get().subscribe({
      next: profile => {
        this.profile.set(profile);
        if (this.showsOwnerSection(profile)) {
          this.loadOwner();
        }
      },
      error: (failure: Error) => this.loadError.set(failure.message || 'Your profile could not be loaded.')
    });
  }

  readonly setupMessage = ownerSetupMessage;

  roleLabel(role: string): string {
    return ROLE_LABELS[role] ?? role.replace(/^ROLE_/, '').toLowerCase();
  }

  /** Renters can become owners and owners see their status; admin-only accounts have no owner side. */
  showsOwnerSection(profile: MyProfile): boolean {
    return profile.roles.includes('ROLE_RENTER') || profile.roles.includes('ROLE_OWNER');
  }

  startEdit(profile: MyProfile): void {
    this.form.reset({ firstName: profile.firstName, lastName: profile.lastName, phone: profile.phone ?? '' });
    this.formError.set(null);
    this.notice.set(null);
    this.editing.set(true);
  }

  cancelEdit(): void {
    this.editing.set(false);
    this.formError.set(null);
  }

  save(): void {
    const raw = this.form.getRawValue();
    this.form.patchValue({ firstName: raw.firstName.trim(), lastName: raw.lastName.trim(), phone: raw.phone.replace(/[\s-]/g, '') });
    const invalid = invalidFieldsMessage(this.form, {
      firstName: 'First name',
      lastName: 'Last name',
      phone: 'Phone (8 to 15 digits, e.g. +919876543210)'
    });
    this.formError.set(invalid);
    if (invalid) {
      return;
    }
    this.saving.set(true);
    this.api.update(this.form.getRawValue()).subscribe({
      next: async profile => {
        this.profile.set(profile);
        this.saving.set(false);
        this.editing.set(false);
        this.noticeIsWarning.set(profile.signInNameUpdated === false);
        this.notice.set(profile.signInNameUpdated === false
          ? 'Your details are saved. The name at the top of the page updates the next time an administrator syncs accounts.'
          : 'Your details are saved.');
        if (profile.signInNameUpdated) {
          await this.auth.refreshClaims().catch(() => undefined);
        }
      },
      error: (failure: ApiError) => {
        this.saving.set(false);
        this.formError.set(failure.message || 'Your details could not be saved. Please try again.');
      }
    });
  }

  private loadOwner(): void {
    this.ownerApi.myProfile().subscribe({
      next: owner => {
        this.owner.set(owner);
        this.ownerChecked.set(true);
      },
      // 404 means no application yet: the section then offers to start one.
      error: () => this.ownerChecked.set(true)
    });
  }
}
