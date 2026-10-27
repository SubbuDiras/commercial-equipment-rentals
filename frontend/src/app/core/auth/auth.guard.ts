import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

/**
 * Route data: `role` (one required role) or `roles` (any one of them). Waits for the login check that runs on
 * page load, so a refresh or deep link keeps its route instead of being sent to sign-in.
 */
export const authGuard: CanActivateFn = async (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  await authService.ready;

  if (!authService.isAuthenticated()) {
    authService.login(state.url);
    return false;
  }

  const roles: string[] = route.data?.['roles'] ?? (route.data?.['role'] ? [route.data['role']] : []);
  if (roles.length > 0 && !roles.some(role => authService.hasRole(role))) {
    return router.createUrlTree(['/unauthorized']);
  }
  return true;
};
