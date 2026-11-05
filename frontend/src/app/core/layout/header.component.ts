import { Component, effect, inject, untracked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ProfileApiService } from '../../features/profile/profile-api.service';
import { AuthService } from '../auth/auth.service';

const ROLE_LABELS: Record<string, string> = {
  ROLE_RENTER: 'Renter',
  ROLE_OWNER: 'Owner',
  ROLE_ADMIN: 'Admin',
  ROLE_SUPPORT: 'Support'
};

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule, RouterModule],
  styles: [`
    .nav-link {
      font-weight: 500;
      font-size: 0.9375rem;
      color: var(--color-text-muted);
      white-space: nowrap;
      text-decoration: none;
      padding: 0.25rem 0;
      border-bottom: 2px solid transparent;
    }
    .nav-link:hover { color: var(--color-text-main); }
    .nav-link.active {
      color: var(--color-text-main);
      font-weight: 600;
      border-bottom-color: var(--color-primary-light);
    }
    .nav-link.admin { color: var(--color-danger); }
  `],
  template: `
    <header class="glass-panel" style="position: sticky; top: 0; z-index: 50; border-bottom: 1px solid var(--color-border);">
      <div class="container-wide" style="display: flex; align-items: center; justify-content: space-between; gap: 1.5rem; height: 4.5rem;">

        <!-- Brand Logo -->
        <a routerLink="/equipment" style="display: flex; align-items: center; gap: 0.75rem; text-decoration: none; flex-shrink: 0;">
          <div style="background: linear-gradient(135deg, #1e3a8a, #2563eb); width: 2.5rem; height: 2.5rem; border-radius: var(--radius-md); display: flex; align-items: center; justify-content: center; color: white; font-weight: 800; font-size: 1.25rem;">
            ⚙️
          </div>
          <div style="white-space: nowrap;">
            <div style="font-family: var(--font-heading); font-weight: 800; font-size: 1.25rem; color: var(--color-primary-dark); letter-spacing: -0.02em;">
              EquipRent<span style="color: var(--color-primary-light);">India</span>
            </div>
            <div style="font-size: 0.6875rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.05em; color: #047857;">
              B2B Commercial Equipment
            </div>
          </div>
        </a>

        <!-- Center Nav Links -->
        <nav style="display: flex; align-items: center; gap: 1.75rem; flex: 1; justify-content: center; min-width: 0;">
          <a routerLink="/equipment" routerLinkActive="active" class="nav-link">Equipment Catalogue</a>
          <a *ngIf="auth.hasRole('ROLE_RENTER')" routerLink="/bookings" routerLinkActive="active" class="nav-link">My Bookings</a>
          <a *ngIf="auth.hasRole('ROLE_RENTER') && !auth.isOwner()" routerLink="/owner/onboarding" routerLinkActive="active" class="nav-link">Become an Owner</a>
          <a *ngIf="auth.isOwner()" routerLink="/owner/equipment" routerLinkActive="active" class="nav-link">My Listings</a>
          <a *ngIf="auth.isOwner()" routerLink="/owner/bookings" routerLinkActive="active" class="nav-link">Owner Bookings</a>
          <a *ngIf="auth.isAdmin()" routerLink="/admin/owners" routerLinkActive="active" class="nav-link">Owner KYC</a>
          <a *ngIf="auth.isAdmin()" routerLink="/admin/listings" routerLinkActive="active" class="nav-link">Listing Reviews</a>
          <a *ngIf="auth.isAdmin()" routerLink="/admin/disputes" routerLinkActive="active" class="nav-link">Disputes</a>
          <a *ngIf="auth.isAdmin()" routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" class="nav-link admin">Admin Portal</a>
        </nav>

        <!-- Right User Actions -->
        <div style="display: flex; align-items: center; gap: 1rem; flex-shrink: 0;">
          <ng-container *ngIf="auth.isAuthenticated(); else loginBtn">
            <a routerLink="/profile" title="My profile" aria-label="My profile" style="display: flex; align-items: center; gap: 0.6rem; text-align: right; white-space: nowrap; color: inherit; text-decoration: none;">
              <div>
                <div style="font-size: 0.875rem; font-weight: 600; line-height: 1.3;">{{ auth.currentUser()?.name }} <span aria-hidden="true" style="color: var(--color-text-muted);">›</span></div>
                <div style="display: flex; gap: 0.25rem; justify-content: flex-end; margin-top: 0.125rem;">
                  <span *ngIf="primaryRole() as role" class="badge badge-role">
                    {{ roleLabel(role) }}
                  </span>
                </div>
              </div>
              <img *ngIf="profileApi.photoUrl() as photo; else initialsAvatar" [src]="photo" alt="" width="38" height="38"
                   (error)="profileApi.photoUrl.set(null)"
                   style="width: 38px; height: 38px; border-radius: 50%; object-fit: cover; border: 1px solid var(--color-border);" />
              <ng-template #initialsAvatar>
                <span aria-hidden="true" style="width: 38px; height: 38px; border-radius: 50%; display: grid; place-items: center; font-size: 0.8125rem; font-weight: 700; color: #fff; background: var(--color-primary-light);">{{ initials() }}</span>
              </ng-template>
            </a>
            <button (click)="auth.logout()" class="btn btn-secondary" style="padding: 0.5rem 0.875rem; font-size: 0.8125rem; white-space: nowrap;">
              Sign Out
            </button>
          </ng-container>

          <ng-template #loginBtn>
            <button (click)="auth.login()" class="btn btn-primary" id="btn-header-login" style="white-space: nowrap;">
              Sign In / Register
            </button>
          </ng-template>
        </div>

      </div>
    </header>
  `
})
export class HeaderComponent {
  auth = inject(AuthService);
  readonly profileApi = inject(ProfileApiService);

  constructor() {
    // Load the photo once per sign-in; the profile page keeps it current after that.
    effect(() => {
      if (this.auth.isAuthenticated()) {
        untracked(() => this.profileApi.get().subscribe({ error: () => this.profileApi.photoUrl.set(null) }));
      } else {
        this.profileApi.photoUrl.set(null);
      }
    });
  }

  initials(): string {
    const name = this.auth.currentUser()?.name ?? '';
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase() || '?';
  }

  primaryRole(): string | null {
    const roles = this.auth.currentUser()?.roles ?? [];
    return ['ROLE_ADMIN', 'ROLE_OWNER', 'ROLE_SUPPORT', 'ROLE_RENTER']
      .find(role => roles.includes(role)) ?? null;
  }

  roleLabel(role: string): string {
    return ROLE_LABELS[role] ?? role.replace('ROLE_', '');
  }
}
