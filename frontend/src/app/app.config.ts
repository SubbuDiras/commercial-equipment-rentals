import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideOAuthClient } from 'angular-oauth2-oidc';
import { routes } from './app.routes';
import { AuthService } from './core/auth/auth.service';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { errorInterceptor } from './core/interceptors/error.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([jwtInterceptor, errorInterceptor])
    ),
    provideOAuthClient(),
    // Finish the sign-in check (including Keycloak's redirect back) before the router's first navigation,
    // so the app opens on the page that was requested before signing in.
    provideAppInitializer(() => inject(AuthService).ready)
  ]
};
