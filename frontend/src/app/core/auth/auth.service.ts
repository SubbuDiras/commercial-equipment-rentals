import { Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';

export interface UserProfile {
  sub: string;
  email: string;
  name: string;
  roles: string[];
}

const RETURN_URL_KEY = 'auth.returnUrl';

const isLocalDevelopment =['localhost', '127.0.0.1'].includes(window.location.hostname);

export const authCodeFlowConfig: AuthConfig = {
  issuer: isLocalDevelopment
    ? 'http://localhost:8080/realms/marketplace-realm'
    : `${window.location.origin}/auth/realms/marketplace-realm`,
  redirectUri: window.location.origin,
  clientId: 'marketplace-frontend',
  responseType: 'code',
  scope: 'openid profile email',
  showDebugInformation: isLocalDevelopment,
  requireHttps: !isLocalDevelopment
};

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private userSignal = signal<UserProfile | null>(null);
  public readonly currentUser = this.userSignal.asReadonly();
  public readonly isAuthenticated = computed(() => !!this.userSignal());
  public readonly isOwner = computed(() => this.userSignal()?.roles.includes('ROLE_OWNER') ?? false);
  public readonly isAdmin = computed(() => this.userSignal()?.roles.includes('ROLE_ADMIN') ?? false);

  /**
   * Resolves once the login check after page load has finished (successfully or not). Route guards wait on it,
   * so deep links and page refreshes are judged with the restored session instead of bouncing to sign-in.
   */
  public readonly ready: Promise<void>;

  constructor(private oauthService: OAuthService, private router: Router) {
    this.ready = this.configureKeycloak();
  }

  private async configureKeycloak(): Promise<void> {
    this.oauthService.configure(authCodeFlowConfig);
    this.oauthService.events.subscribe(() => {
      if (this.oauthService.hasValidAccessToken()) {
        this.extractUserClaims();
      } else {
        this.userSignal.set(null);
      }
    });

    try {
      await this.oauthService.loadDiscoveryDocumentAndTryLogin();
      // An expired access token can still be renewed with the stored refresh token after a page reload.
      if (!this.oauthService.hasValidAccessToken() && this.oauthService.getRefreshToken()) {
        await this.oauthService.refreshToken().catch(() => undefined);
      }
      if (this.oauthService.hasValidAccessToken()) {
        this.extractUserClaims();
        this.oauthService.setupAutomaticSilentRefresh();
      }
      this.settleStartUrl(this.oauthService.hasValidAccessToken());
    } catch (err) {
      console.warn('Keycloak discovery or login check failed:', err);
    }
  }

  /**
   * Runs before the router's first navigation (see the app initializer in app.config.ts), so rewriting the address
   * bar here decides where the app starts: the page requested before sign-in, or the current page without
   * Keycloak's response parameters. Using the router instead would race its own first navigation and lose.
   */
  private settleStartUrl(signedIn: boolean): void {
    const returnUrl = sessionStorage.getItem(RETURN_URL_KEY);
    const url = new URL(window.location.href);
    const isLoginCallback = ['code', 'state', 'session_state', 'iss'].some(param => url.searchParams.has(param));
    if (signedIn && returnUrl && isLoginCallback) {
      sessionStorage.removeItem(RETURN_URL_KEY);
      window.history.replaceState(window.history.state, '', returnUrl);
      return;
    }
    if (isLoginCallback) {
      for (const param of ['code', 'state', 'session_state', 'iss']) {
        url.searchParams.delete(param);
      }
      window.history.replaceState(window.history.state, '', url.pathname + url.search + url.hash);
    }
  }

  private extractUserClaims(): void {
    const claims: any = this.oauthService.getIdentityClaims();
    if (!claims) return;

    const realmAccess = claims['realm_access'] || {};
    // Keep only application roles; Keycloak also adds internal ones such as
    // default-roles-marketplace-realm, offline_access and uma_authorization.
    const identityRoles = this.applicationRoles(realmAccess['roles']);
    const roles = identityRoles.length > 0 ? identityRoles : this.accessTokenRoles();

    this.userSignal.set({
      sub: claims['sub'],
      email: claims['email'] || '',
      name: claims['name'] || claims['preferred_username'] || 'User',
      roles: roles
    });
  }

  private applicationRoles(value: unknown): string[] {
    return Array.isArray(value)
      ? value.filter((role): role is string => typeof role === 'string' && role.startsWith('ROLE_'))
      : [];
  }

  private accessTokenRoles(): string[] {
    const encodedPayload = this.oauthService.getAccessToken().split('.')[1];
    if (!encodedPayload) return [];

    try {
      const base64 = encodedPayload
        .replace(/-/g, '+')
        .replace(/_/g, '/')
        .padEnd(Math.ceil(encodedPayload.length / 4) * 4, '=');
      const payload: unknown = JSON.parse(atob(base64));
      if (typeof payload !== 'object' || payload === null) return [];

      const realmAccess = (payload as Record<string, unknown>)['realm_access'];
      if (typeof realmAccess !== 'object' || realmAccess === null) return [];

      return this.applicationRoles((realmAccess as Record<string, unknown>)['roles']);
    } catch (error) {
      console.warn('Could not read roles from the Keycloak access token:', error);
      return [];
    }
  }

  /** @param returnUrl in-app URL to open after signing in (defaults to the current page) */
  public login(returnUrl?: string): void {
    sessionStorage.setItem(RETURN_URL_KEY, returnUrl ?? this.router.url);
    // initCodeFlow() silently does nothing until the discovery document is loaded, which can
    // happen if Keycloak was unreachable or misconfigured when the app first started.
    if (this.oauthService.discoveryDocumentLoaded) {
      this.oauthService.initCodeFlow();
      return;
    }
    this.oauthService.loadDiscoveryDocument()
      .then(() => this.oauthService.initCodeFlow())
      .catch(err => {
        console.error('Keycloak discovery failed; cannot start login:', err);
        alert('Sign-in is unavailable: the login server (Keycloak) could not be reached. Please try again shortly.');
      });
  }

  public logout(): void {
    this.oauthService.logOut();
    this.userSignal.set(null);
  }

  public getAccessToken(): string | null {
    return this.oauthService.hasValidAccessToken() ? this.oauthService.getAccessToken() : null;
  }

  public hasRole(role: string): boolean {
    return this.userSignal()?.roles.includes(role) ?? false;
  }
}
