import { test as base } from '@playwright/test';
import { AdminDisputesPage, AdminListingsPage, AdminOwnersPage } from '../pages/admin.pages';
import { BookingDetailsPage } from '../pages/booking-details.page';
import { BookingHistoryPage } from '../pages/booking-history.page';
import { BookingRequestPage } from '../pages/booking-request.page';
import { CataloguePage } from '../pages/catalogue.page';
import { OwnerBookingsPage } from '../pages/owner-bookings.page';
import { OwnerListingsPage } from '../pages/owner-listings.page';
import { OwnerOnboardingPage } from '../pages/owner-onboarding.page';
import { ProfilePage } from '../pages/profile.page';

/**
 * `test` with page objects bound to the default `page`. Tests that sign in as several users create their own with
 * `new BookingDetailsPage(signedInPage)`.
 */
export const test = base.extend<{
  bookingPage: BookingDetailsPage;
  requestPage: BookingRequestPage;
  historyPage: BookingHistoryPage;
  cataloguePage: CataloguePage;
  profilePage: ProfilePage;
  ownerBookings: OwnerBookingsPage;
  ownerListings: OwnerListingsPage;
  onboardingPage: OwnerOnboardingPage;
  adminOwners: AdminOwnersPage;
  adminListings: AdminListingsPage;
  adminDisputes: AdminDisputesPage;
}>({
  bookingPage: async ({ page }, use) => use(new BookingDetailsPage(page)),
  requestPage: async ({ page }, use) => use(new BookingRequestPage(page)),
  historyPage: async ({ page }, use) => use(new BookingHistoryPage(page)),
  cataloguePage: async ({ page }, use) => use(new CataloguePage(page)),
  profilePage: async ({ page }, use) => use(new ProfilePage(page)),
  ownerBookings: async ({ page }, use) => use(new OwnerBookingsPage(page)),
  ownerListings: async ({ page }, use) => use(new OwnerListingsPage(page)),
  onboardingPage: async ({ page }, use) => use(new OwnerOnboardingPage(page)),
  adminOwners: async ({ page }, use) => use(new AdminOwnersPage(page)),
  adminListings: async ({ page }, use) => use(new AdminListingsPage(page)),
  adminDisputes: async ({ page }, use) => use(new AdminDisputesPage(page))
});

export { expect } from '@playwright/test';
