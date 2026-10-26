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
    path: 'owner/onboarding',
    canActivate: [authGuard],
    data: { role: 'ROLE_RENTER' },
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
        loadComponent: () => import('./features/owner/equipment-listing.component').then(m => m.EquipmentListingComponent)
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
    path: 'admin',
    canActivate: [authGuard],
    data: { role: 'ROLE_ADMIN' },
    component: PilotMetricsComponent,
    title: 'Marketplace Operations'
  },
  {
    path: '**',
    redirectTo: ''
  }
];
