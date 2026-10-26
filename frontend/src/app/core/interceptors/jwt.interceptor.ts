import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { OAuthService } from 'angular-oauth2-oidc';

const isApiRequest = (url: string) => url.startsWith('/api/') || url.includes(':8081/api/');

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  // Keycloak's own requests (discovery, JWKS, token exchange) also pass through here while
  // AuthService is still being constructed, so read the token from OAuthService directly:
  // injecting AuthService would be a circular dependency (NG0200).
  if (!isApiRequest(req.url)) {
    return next(req);
  }

  const oauthService = inject(OAuthService);
  const token = oauthService.hasValidAccessToken() ? oauthService.getAccessToken() : null;

  if (token) {
    const authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
    return next(authReq);
  }

  return next(req);
};
