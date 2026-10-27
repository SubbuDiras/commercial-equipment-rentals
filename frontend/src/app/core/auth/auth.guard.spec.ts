import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let resolveReady: () => void;
  let auth: { ready: Promise<void>; isAuthenticated: () => boolean; hasRole: (role: string) => boolean; login: ReturnType<typeof vi.fn> };
  let roles: string[];
  let signedIn: boolean;

  beforeEach(() => {
    roles = [];
    signedIn = false;
    auth = {
      ready: new Promise<void>(resolve => (resolveReady = resolve)),
      isAuthenticated: () => signedIn,
      hasRole: role => roles.includes(role),
      login: vi.fn()
    };
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }]
    });
  });

  function run(data: Record<string, unknown>, url = '/bookings/b-1'): Promise<boolean | UrlTree> {
    const route = { data } as unknown as ActivatedRouteSnapshot;
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() => authGuard(route, state)) as Promise<boolean | UrlTree>;
  }

  it('waits for the login check before deciding, so a page refresh keeps its route', async () => {
    const decision = run({ role: 'ROLE_RENTER' });
    signedIn = true;
    roles = ['ROLE_RENTER'];
    resolveReady();
    expect(await decision).toBe(true);
    expect(auth.login).not.toHaveBeenCalled();
  });

  it('starts sign-in with the requested URL when not signed in', async () => {
    resolveReady();
    expect(await run({ role: 'ROLE_RENTER' }, '/bookings/b-9')).toBe(false);
    expect(auth.login).toHaveBeenCalledWith('/bookings/b-9');
  });

  it('accepts any of several roles and sends others to /unauthorized', async () => {
    signedIn = true;
    roles = ['ROLE_ADMIN'];
    resolveReady();
    expect(await run({ roles: ['ROLE_RENTER', 'ROLE_ADMIN'] })).toBe(true);

    const denied = await run({ role: 'ROLE_OWNER' });
    expect(TestBed.inject(Router).serializeUrl(denied as UrlTree)).toBe('/unauthorized');
  });
});
