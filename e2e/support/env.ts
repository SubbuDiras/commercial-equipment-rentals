/** Every URL and credential can be overridden with an environment variable. */
export const env = {
  appUrl: process.env.E2E_APP_URL ?? 'http://localhost:4200',
  apiUrl: process.env.E2E_API_URL ?? 'http://localhost:8081',
  keycloakUrl: process.env.E2E_KEYCLOAK_URL ?? 'http://localhost:8080',
  realm: process.env.E2E_KEYCLOAK_REALM ?? 'marketplace-realm',
  clientId: process.env.E2E_KEYCLOAK_CLIENT_ID ?? 'marketplace-frontend',
  /** Keycloak master-realm administrator, used to create the test users and assign roles. */
  keycloakAdminUser: process.env.E2E_KEYCLOAK_ADMIN ?? 'admin',
  keycloakAdminPassword: process.env.E2E_KEYCLOAK_ADMIN_PASSWORD ?? 'admin',
  mailpitUrl: process.env.E2E_MAILPIT_URL ?? 'http://localhost:8025',
  password: process.env.E2E_USER_PASSWORD ?? 'Password123!'
};

/** Fixed test accounts, created (or reset) by global-setup. Separate from the manual-testing accounts. */
export const users = {
  renter: { email: 'e2e-renter@example.com', firstName: 'Eva', lastName: 'Renter', roles: ['ROLE_RENTER'] },
  owner: { email: 'e2e-owner@example.com', firstName: 'Omar', lastName: 'Owner', roles: ['ROLE_RENTER', 'ROLE_OWNER'] },
  admin: { email: 'e2e-admin@example.com', firstName: 'Ada', lastName: 'Admin', roles: ['ROLE_ADMIN'] },
  /** A second renter with no bookings, for access checks. */
  outsider: { email: 'e2e-outsider@example.com', firstName: 'Otto', lastName: 'Outsider', roles: ['ROLE_RENTER'] }
} as const;

export type TestUser = { email: string; firstName: string; lastName: string };

/** A fresh address for sign-up tests, unique per run. */
export function uniqueEmail(prefix: string): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1e4)}@example.com`;
}
