import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    const requiredRole = route.data?.['role'] as string | undefined;
    if (requiredRole && !authService.hasRole(requiredRole)) {
      router.navigate(['/unauthorized']);
      return false;
    }
    return true;
  }

  // Not authenticated, redirect to Keycloak login
  authService.login();
  return false;
};
