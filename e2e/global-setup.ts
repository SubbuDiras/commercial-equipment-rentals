import { Api } from './support/api';
import { env, users } from './support/env';
import { KeycloakAdmin } from './support/keycloak-admin';

/**
 * Prepares the fixed test accounts in Keycloak and checks the stack is in the shape the suite needs, failing early
 * with a clear message instead of timing out inside a test.
 */
export default async function globalSetup(): Promise<void> {
  await expectReachable(`${env.appUrl}/`, 'Angular dev server (cd frontend && npm start)');
  await expectReachable(`${env.apiUrl}/api/v1/health`, 'backend (dev profile)');
  await expectReachable(`${env.mailpitUrl}/api/v1/messages`, 'Mailpit (docker compose up -d mailpit)');

  const keycloak = await KeycloakAdmin.connect();
  for (const user of Object.values(users)) {
    await keycloak.ensureUser(user, user.roles);
  }

  // The dev tools only exist under the dev/test profile, and the suite pays through the fake Razorpay.
  const admin = Api.as(users.admin.email);
  try {
    await admin.runScheduler();
  } catch (error) {
    throw new Error(`The backend dev tools are unavailable; run the backend with the dev profile. ${(error as Error).message}`);
  }
  // Make the owner a verified owner with a payout account (the listing it creates is not used).
  await Api.as(users.owner.email).seedOwnerListing();
}

async function expectReachable(url: string, what: string): Promise<void> {
  try {
    const response = await fetch(url);
    if (response.status >= 500) {
      throw new Error(`HTTP ${response.status}`);
    }
  } catch (error) {
    throw new Error(`${what} is not reachable at ${url}: ${(error as Error).message}`);
  }
}
