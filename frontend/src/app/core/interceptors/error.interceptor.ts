import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injector, inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../auth/auth.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  // Resolve AuthService lazily: injecting it eagerly is a circular dependency (NG0200), because
  // AuthService's own Keycloak requests pass through this interceptor during its construction.
  const injector = inject(Injector);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      let errorMessage = 'An unexpected error occurred.';

      if (error.error && typeof error.error === 'object') {
        // RFC 7807 Problem Details
        const problem = error.error;
        errorMessage = problem.detail || problem.title || errorMessage;

        if (error.status === 401) {
          console.warn('Session expired or unauthorized request, initiating re-login.');
          injector.get(AuthService).login();
        } else if (error.status === 403) {
          console.error('Access forbidden:', errorMessage);
        } else if (error.status === 409) {
          console.warn('Conflict or concurrent modification:', errorMessage);
        }
      } else {
        errorMessage = error.statusText || errorMessage;
      }

      console.error(`[API Error ${error.status}]:`, errorMessage);
      return throwError(() => new Error(errorMessage));
    })
  );
};
