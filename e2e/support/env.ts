import path from 'node:path';

/** Every URL and credential can be overridden with an environment variable. */
export const env = {
  appUrl: process.env.E2E_APP_URL ?? 'http://localhost:4200',
  apiUrl: process.env.E2E_API_URL ?? 'http://localhost:8081',
  keycloakUrl: process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8080',
  realm: process.env.E2E_KEYCLOAK_REALM ?? 'marketplace-realm',
  clientId: process.env.E2E_KEYCLOAK_CLIENT_ID ?? 'marketplace-frontend',
  /** Keycloak master-realm administrator, used to create the test users and assign roles (defaults match docker-compose). */
  keycloakAdminUser: process.env.E2E_KEYCLOAK_ADMIN ?? 'admin',
  keycloakAdminPassword: process.env.E2E_KEYCLOAK_ADMIN_PASSWORD ?? 'admin_password',
  mailpitUrl: process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025',
  password: process.env.E2E_USER_PASSWORD ?? 'Password123!'
};

/** Parallel workers (E2E_WORKERS, default 1). Each worker gets its own renter and owners; the admin is shared. */
export const workerCount = Math.max(1, Number(process.env.E2E_WORKERS ?? 1));

/**
 * The fixed test accounts for one worker slot, created (or reset) by global-setup. Slot 0 keeps the plain addresses,
 * so a one-worker run uses the same accounts as before. Separate from the manual-testing accounts.
 */
export function usersForSlot(slot: number) {
  const at = (name: string) => `e2e-${name}${slot === 0 ? '' : `-w${slot}`}@example.com`;
  return {
    renter: { email: at('renter'), firstName: 'Eva', lastName: 'Renter', roles: ['ROLE_RENTER'] },
    owner: { email: at('owner'), firstName: 'Omar', lastName: 'Owner', roles: ['ROLE_RENTER', 'ROLE_OWNER'] },
    admin: { email: 'e2e-admin@example.com', firstName: 'Ada', lastName: 'Admin', roles: ['ROLE_ADMIN'] },
    /** A second renter with no bookings, for access checks. */
    outsider: { email: at('outsider'), firstName: 'Otto', lastName: 'Outsider', roles: ['ROLE_RENTER'] },
    /** A second verified owner, to check owners only see their own bookings. */
    otherOwner: { email: at('other-owner'), firstName: 'Olga', lastName: 'Otherowner', roles: ['ROLE_RENTER', 'ROLE_OWNER'] }
  } as const;
}

/** This worker's accounts (Playwright sets TEST_PARALLEL_INDEX in each worker). */
export const users = usersForSlot(Number(process.env.TEST_PARALLEL_INDEX ?? 0));

/** Every fixed account across all worker slots, without duplicates. */
export function allFixedUsers() {
  const byEmail = new Map<string, ReturnType<typeof usersForSlot>[keyof ReturnType<typeof usersForSlot>]>();
  for (let slot = 0; slot < workerCount; slot++) {
    for (const user of Object.values(usersForSlot(slot))) {
      byEmail.set(user.email, user);
    }
  }
  return [...byEmail.values()];
}

/** Where auth.setup.ts saves a fixed user's signed-in browser state. */
export function authFile(email: string): string {
  return path.join(__dirname, '..', '.auth', `${email}.json`);
}

export type TestUser ={ email: string; firstName: string; lastName: string };

/** A fresh address for sign-up tests, unique per run. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e4)}@example.com`;
}
