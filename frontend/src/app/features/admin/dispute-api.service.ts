import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { InspectionReport } from '../bookings/booking-api.service';

export interface DisputeSummary {
  disputeId: string;
  bookingId: string;
  bookingReference: string;
  equipmentTitle: string;
  category: string;
  claimAmount: number;
  securityDeposit: number;
  status: 'OPEN' | 'RESOLVED';
  raisedByName: string;
  createdAt: string;
}

export interface DisputeDetail {
  disputeId: string;
  status: 'OPEN' | 'RESOLVED';
  category: string;
  claimAmount: number;
  approvedDeductionAmount: number | null;
  description: string;
  resolutionNotes: string | null;
  createdAt: string;
  resolvedAt: string | null;
  bookingId: string;
  bookingReference: string;
  bookingStatus: string;
  equipmentId: string;
  equipmentTitle: string;
  startTime: string;
  endTime: string;
  securityDeposit: number;
  owner: { name: string; email: string };
  renter: { name: string; email: string };
  inspections: InspectionReport[];
}

@Injectable({ providedIn: 'root' })
export class DisputeApiService {
  private readonly http = inject(HttpClient);

  list(status: string | null): Observable<DisputeSummary[]> {
    return this.http.get<DisputeSummary[]>('/api/v1/admin/disputes', { params: status ? { status } : {} });
  }

  get(disputeId: string): Observable<DisputeDetail> {
    return this.http.get<DisputeDetail>(`/api/v1/admin/disputes/${encodeURIComponent(disputeId)}`);
  }

  resolve(disputeId: string, approvedDeductionAmount: number, resolutionNotes: string): Observable<unknown> {
    return this.http.patch(`/api/v1/admin/disputes/${encodeURIComponent(disputeId)}/resolve`,
      { approvedDeductionAmount, resolutionNotes });
  }
}
