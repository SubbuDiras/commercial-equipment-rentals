import { Api } from './api';
import { users } from './env';

export const renterApi = () => Api.as(users.renter.email);
export const ownerApi = () => Api.as(users.owner.email);
export const adminApi = () => Api.as(users.admin.email);

/** A new approved listing owned by the e2e owner, so each test books without date clashes. */
export async function freshListing(): Promise<{ equipmentId: string; title: string }> {
  const seeded = await ownerApi().seedOwnerListing();
  return { equipmentId: seeded.equipmentId, title: seeded.title };
}

/**
 * Creates a booking for the e2e renter on a fresh listing and moves it forward:
 * `requested` → owner accepts → `accepted` → renter pays → `paid`.
 */
export async function bookingIn(
  state: 'requested' | 'accepted' | 'paid',
  startInHours = 72
): Promise<{ bookingId: string; title: string }> {
  const { equipmentId, title } = await freshListing();
  const bookingId = await renterApi().requestBooking(equipmentId, startInHours);
  if (state !== 'requested') {
    await ownerApi().accept(bookingId);
  }
  if (state === 'paid') {
    await renterApi().pay(bookingId);
  }
  return { bookingId, title };
}

/** Formats an amount exactly like the frontend's ₹ pipe. */
export function inr(amount: number): string {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR', minimumFractionDigits: 2, maximumFractionDigits: 2
  }).format(amount);
}
