import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface BookingPricing {
  currency: string;
  rentalDays: number;
  baseRentalAmount: number;
  deliveryCharge: number;
  platformFeeAmount: number;
  taxAmount: number;
  securityDepositAmount: number;
  totalPayableAmount: number;
}

export interface Booking {
  bookingId: string;
  bookingReference: string;
  equipmentId: string;
  startTime: string;
  endTime: string;
  deliveryOption: 'PICKUP' | 'OWNER_DELIVERY';
  status: string;
  holdExpiresAt: string | null;
  pricing: BookingPricing;
}

export interface BookingRequest {
  equipmentId: string;
  startTime: string;
  endTime: string;
  deliveryOption: 'PICKUP' | 'OWNER_DELIVERY';
  deliveryAddress?: {
    siteContactName: string;
    siteContactPhone: string;
    addressLine: string;
    city: string;
    postalCode: string;
  };
}

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/bookings';

  create(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(this.apiUrl, request);
  }

  list(page = 0, size = 20): Observable<Booking[]> {
    return this.http.get<Booking[]>(this.apiUrl, { params: { page, size } });
  }
}
