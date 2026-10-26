import { Injectable, signal, computed } from '@angular/core';
import { Router } from '@angular/router';
import { AuthConfig, OAuthService } from 'angular-oauth2-oidc';

export interface UserProfile {
  sub: string;
  email: string;
  name: string;
  roles: string[];
}

const isLocalDevelopment = ['localhost', '127.0.0.1'].includes(window.location.hostname);

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

  constructor(private oauthService: OAuthService, private router: Router) {
    this.configureKeycloak();
  }

  private configureKeycloak(): void {
    this.oauthService.configure(authCodeFlowConfig);
    this.oauthService.loadDiscoveryDocumentAndTryLogin().then(() => {
      if (this.oauthService.hasValidAccessToken()) {
        this.extractUserClaims();
      }
      this.removeLoginResponseParams();
    }).catch(err => {
      console.warn('Keycloak discovery or login check failed:', err);
    });

    this.oauthService.events.subscribe(() => {
      if (this.oauthService.hasValidAccessToken()) {
        this.extractUserClaims();
      } else {
        this.userSignal.set(null);
      }
    });
  }

  // The library strips code/state/session_state after login but not Keycloak's `iss` parameter.
  private removeLoginResponseParams(): void {
    if (new URLSearchParams(window.location.search).has('iss')) {
      this.router.navigate([], { queryParams: { iss: null }, queryParamsHandling: 'merge', replaceUrl: true });
    }
  }

  private extractUserClaims(): void {
    const claims: any = this.oauthService.getIdentityClaims();
    if (!claims) return;

    const realmAccess = claims['realm_access'] || {};
    // Keep only application roles; Keycloak also adds internal ones such as
    // default-roles-marketplace-realm, offline_access and uma_authorization.
    const roles: string[] = (realmAccess['roles'] || []).filter((role: string) => role.startsWith('ROLE_'));

    this.userSignal.set({
      sub: claims['sub'],
      email: claims['email'] || '',
      name: claims['name'] || claims['preferred_username'] || 'User',
      roles: roles
    });
  }

  public login(): void {
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
