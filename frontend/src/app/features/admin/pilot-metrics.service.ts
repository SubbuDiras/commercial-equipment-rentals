import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface PilotMetrics {
  from: string;
  to: string;
  bookingRequests: number;
  capturedBookings: number;
  bookingConversionPercent: number;
  capturedGrossAmount: number;
  rentalGrossAmount: number;
  platformCommissionAmount: number;
  takeRatePercent: number;
  averageOwnerResponseMinutes: number | null;
  disputes: number;
  openDisputes: number;
  verifiedOwners: number;
  activeListings: number;
  pendingSettlements: number;
  releasedSettlements: number;
}

@Injectable({ providedIn: 'root' })
export class PilotMetricsService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/admin/pilot-metrics';

  get(from: string, to: string): Observable<PilotMetrics> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<PilotMetrics>(this.apiUrl, { params });
  }
}
