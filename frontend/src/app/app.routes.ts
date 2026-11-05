import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { PilotMetricsComponent } from './features/admin/pilot-metrics.component';

export const routes: Routes = [
  {
    path: 'equipment',
    loadComponent: () => import('./features/catalogue/equipment-catalogue.component').then(m => m.EquipmentCatalogueComponent),
    title: 'EquipRent India | Commercial Equipment Rental Marketplace'
  },
  {
    path: 'equipment/category/:categoryId',
    loadComponent: () => import('./features/catalogue/equipment-category.component').then(m => m.EquipmentCategoryComponent),
    title: 'Equipment by Category'
  },
  {
    path: 'equipment/:equipmentId/book',
    canActivate: [authGuard],
    data: { role: 'ROLE_RENTER' },
    loadComponent: () => import('./features/bookings/booking-request.component').then(m => m.BookingRequestComponent),
    title: 'Request Equipment Booking'
  },
  {
    path: 'equipment/:equipmentId',
    loadComponent: () => import('./features/catalogue/equipment-details.component').then(m => m.EquipmentDetailsComponent),
    title: 'Equipment Details'
  },
  {
    path: '',
    redirectTo: 'equipment',
    pathMatch: 'full'
  },
  {
    path: 'bookings',
    canActivate: [authGuard],
    data: { role: 'ROLE_RENTER' },
    loadComponent: () => import('./features/bookings/booking-history.component').then(m => m.BookingHistoryComponent),
    title: 'My Bookings'
  },
  {
    path: 'bookings/:bookingId',
    canActivate: [authGuard],
    data: { roles: ['ROLE_RENTER', 'ROLE_OWNER', 'ROLE_ADMIN'] },
    loadComponent: () => import('./features/bookings/booking-details.component').then(m => m.BookingDetailsComponent),
    title: 'Booking'
  },
  {
    path: 'profile',
    canActivate: [authGuard],
    loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent),
    title: 'My Profile'
  },
  {
    path: 'owner/onboarding',
    canActivate: [authGuard],
    data: { roles: ['ROLE_RENTER', 'ROLE_OWNER'] },
    loadComponent: () => import('./features/owner/owner-onboarding.component').then(m => m.OwnerOnboardingComponent),
    title: 'Owner Verification'
  },
  {
    path: 'owner',
    canActivate: [authGuard],
    data: { role: 'ROLE_OWNER' },
    loadChildren: () => Promise.resolve([
      {
        path: 'equipment',
        loadComponent: () => import('./features/owner/equipment-listing.component').then(m => m.EquipmentListingComponent),
        title: 'My Listings'
      },
      {
        path: 'bookings',
        loadComponent: () => import('./features/owner/owner-bookings.component').then(m => m.OwnerBookingsComponent),
        title: 'Bookings for My Equipment'
      }
    ])
  },
  {
    path: 'admin/listings',
    canActivate: [authGuard],
    data: { role: 'ROLE_ADMIN' },
    loadComponent: () => import('./features/admin/listing-review.component').then(m => m.ListingReviewComponent),
    title: 'Listing Approval Queue'
  },
  {
    path: 'admin/owners',
    canActivate: [authGuard],
    data: { role: 'ROLE_ADMIN' },
    loadComponent: () => import('./features/admin/owner-review.component').then(m => m.OwnerReviewComponent),
    title: 'Owner KYC'
  },
  {
    path: 'admin/disputes',
    canActivate: [authGuard],
    data: { role: 'ROLE_ADMIN' },
    loadComponent: () => import('./features/admin/dispute-queue.component').then(m => m.DisputeQueueComponent),
    title: 'Damage Claims'
  },
  {
    path: 'admin/disputes/:disputeId',
    canActivate: [authGuard],
    data: { role: 'ROLE_ADMIN' },
    loadComponent: () => import('./features/admin/dispute-detail.component').then(m => m.DisputeDetailComponent),
    title: 'Damage Claim'
  },
  {
    path: 'admin',
    canActivate: [authGuard],
    data: { role: 'ROLE_ADMIN' },
    component: PilotMetricsComponent,
    title: 'Marketplace Operations'
  },
  {
    path: 'unauthorized',
    loadComponent: () => import('./features/unauthorized/unauthorized.component').then(m => m.UnauthorizedComponent),
    title: 'No Access'
  },
  {
    path: '**',
    redirectTo: ''
  }
];
