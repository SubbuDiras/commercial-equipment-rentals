import { Api } from './support/api';
import { users } from './support/env';

/**
 * Tests create listings without photos (they only check the flow), and those listings stay in the shared dev
 * database. Give them sample photos so the catalogue people browse afterwards has no blank cards.
 */
export default async function globalTeardown(): Promise<void> {
  try {
    await Api.as(users.admin.email).addSamplePhotos();
  } catch (error) {
    console.warn(`Sample photos were not added to new test listings: ${(error as Error).message}`);
  }
}
