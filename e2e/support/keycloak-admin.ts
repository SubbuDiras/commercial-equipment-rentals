import { env, TestUser } from './env';

/** Minimal Keycloak Admin REST client, authenticated as the master-realm administrator. */
export class KeycloakAdmin {
  private constructor(private readonly token: string) {}

  static async connect(): Promise<KeycloakAdmin> {
    const response = await fetch(`${env.keycloakUrl}/realms/master/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: 'admin-cli',
        username: env.keycloakAdminUser,
        password: env.keycloakAdminPassword
      })
    });
    if (!response.ok) {
      throw new Error(`Keycloak admin login failed (HTTP ${response.status}). Set E2E_KEYCLOAK_ADMIN and E2E_KEYCLOAK_ADMIN_PASSWORD.`);
    }
    return new KeycloakAdmin((await response.json()).access_token);
  }

  /** Creates the user if missing, resets the password, and makes sure it has exactly the given application roles. */
  async ensureUser(user: TestUser, roles: readonly string[]): Promise<string> {
    let userId = await this.findUserId(user.email);
    if (!userId) {
      await this.call('POST', '/users', {
        username: user.email,
        email: user.email,
        firstName: user.firstName,
        lastName: user.lastName,
        enabled: true,
        emailVerified: true,
        requiredActions: []
      });
      userId = await this.findUserId(user.email);
    }
    if (!userId) {
      throw new Error(`Keycloak user ${user.email} could not be created.`);
    }
    await this.call('PUT', `/users/${userId}/reset-password`, { type: 'password', value: env.password, temporary: false });

    const current: { name: string }[] = await this.call('GET', `/users/${userId}/role-mappings/realm`);
    const unwanted = current.filter(role => role.name.startsWith('ROLE_') && !roles.includes(role.name));
    if (unwanted.length) {
      await this.call('DELETE', `/users/${userId}/role-mappings/realm`, unwanted);
    }
    for (const role of roles) {
      await this.grantRole(user.email, role);
    }
    return userId;
  }

  async grantRole(email: string, roleName: string): Promise<void> {
    const userId = await this.findUserId(email);
    if (!userId) {
      throw new Error(`Keycloak user ${email} not found.`);
    }
    const role = await this.call('GET', `/roles/${encodeURIComponent(roleName)}`);
    await this.call('POST', `/users/${userId}/role-mappings/realm`, [role]);
  }

  async hasRole(email: string, roleName: string): Promise<boolean> {
    const userId = await this.findUserId(email);
    if (!userId) {
      return false;
    }
    const roles: { name: string }[] = await this.call('GET', `/users/${userId}/role-mappings/realm`);
    return roles.some(role => role.name === roleName);
  }

  async findUserId(email: string): Promise<string | null> {
    const found: { id: string; email?: string }[] =
      await this.call('GET', `/users?email=${encodeURIComponent(email)}&exact=true`);
    return found.find(candidate => candidate.email?.toLowerCase() === email.toLowerCase())?.id ?? null;
  }

  private async call(method: string, path: string, body?: unknown): Promise<any> {
    const response = await fetch(`${env.keycloakUrl}/admin/realms/${env.realm}${path}`, {
      method,
      headers: { Authorization: `Bearer ${this.token}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    if (!response.ok) {
      throw new Error(`Keycloak ${method} ${path} failed: HTTP ${response.status} ${await response.text()}`);
    }
    const text = await response.text();
    return text ? JSON.parse(text) : undefined;
  }
}
