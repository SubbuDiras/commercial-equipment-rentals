import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-unauthorized',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="container" style="max-width: 640px; padding: 4rem 1rem; text-align: center;">
      <h1>You don't have access to this page</h1>
      <p class="muted" style="margin: 1rem 0 2rem;">
        Your account is signed in, but it doesn't have the role this page needs.
        If you were just approved as an equipment owner, sign out and sign in again to pick up your new access.
      </p>
      <div style="display: flex; gap: .75rem; justify-content: center;">
        <a routerLink="/equipment" class="btn btn-primary">Back to the catalogue</a>
        <button type="button" class="btn btn-secondary" (click)="auth.logout()">Sign out</button>
      </div>
    </main>
  `
})
export class UnauthorizedComponent {
  readonly auth = inject(AuthService);
}
