import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface EquipmentListingRequest {
  categoryId: string;
  title: string;
  description: string;
  manufacturer: string;
  modelNumber: string;
  serialNumber: string | null;
  yearOfManufacture: number | null;
  conditionGrade: string;
  specifications: Record<string, unknown>;
  baseDailyRate: number;
  baseWeeklyRate: number | null;
  securityDeposit: number;
  minimumRentalDays: number;
  deliverySupported: boolean;
  pickupSupported: boolean;
  deliveryChargeFlat: number;
  addressLine: string;
  city: string;
  postalCode: string;
  latitude: number;
  longitude: number;
}

export interface EquipmentCategory {
  id: string;
  slug: string;
  name: string;
  description: string | null;
  subcategories: EquipmentCategory[];
}

export interface EquipmentListing {
  id: string;
  categoryId: string;
  categoryName: string;
  title: string;
  description: string;
  manufacturer: string;
  modelNumber: string;
  serialNumber: string | null;
  yearOfManufacture: number | null;
  conditionGrade: string;
  specifications: Record<string, unknown>;
  baseDailyRate: number;
  baseWeeklyRate: number | null;
  securityDeposit: number;
  minimumRentalDays: number;
  deliverySupported: boolean;
  pickupSupported: boolean;
  deliveryChargeFlat: number;
  addressLine: string | null;
  city: string;
  postalCode: string;
  latitude: number | null;
  longitude: number | null;
  status: string;
  createdAt: string;
}

export interface EquipmentListingPage {
  content: EquipmentListing[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EquipmentImage {
  imageId: string;
  imageUrl: string;
  primary: boolean;
  displayOrder: number;
}

export interface EquipmentImageUpload {
  imageId: string;
  uploadUrl: string;
  contentType: string;
  expiresAt: string;
}

@Injectable({ providedIn: 'root' })
export class EquipmentCatalogueService {
  private readonly http = inject(HttpClient);

  categories(): Observable<EquipmentCategory[]> {
    return this.http.get<EquipmentCategory[]>('/api/v1/categories');
  }

  search(filters: {
    categoryId?: string;
    city?: string;
    minRate?: number;
    maxRate?: number;
    startTime?: string;
    endTime?: string;
    page?: number;
    size?: number;
  }): Observable<EquipmentListingPage> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return this.http.get<EquipmentListingPage>('/api/v1/equipment', { params });
  }

  get(equipmentId: string): Observable<EquipmentListing> {
    return this.http.get<EquipmentListing>(`/api/v1/equipment/${encodeURIComponent(equipmentId)}`);
  }

  images(equipmentId: string): Observable<EquipmentImage[]> {
    return this.http.get<EquipmentImage[]>(
      `/api/v1/equipment/${encodeURIComponent(equipmentId)}/images`
    );
  }

  ownerImages(equipmentId: string): Observable<EquipmentImage[]> {
    return this.http.get<EquipmentImage[]>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}/images`
    );
  }

  presignImage(equipmentId: string, file: File): Observable<EquipmentImageUpload> {
    return this.http.post<EquipmentImageUpload>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}/images`,
      { contentType: file.type, contentLength: file.size }
    );
  }

  completeImage(equipmentId: string, imageId: string): Observable<EquipmentImage> {
    return this.http.post<EquipmentImage>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}/images/${encodeURIComponent(imageId)}/complete`,
      {}
    );
  }

  ownListings(): Observable<EquipmentListing[]> {
    return this.http.get<EquipmentListing[]>('/api/v1/owner/equipment');
  }

  createListing(request: EquipmentListingRequest): Observable<EquipmentListing> {
    return this.http.post<EquipmentListing>('/api/v1/owner/equipment', request);
  }

  updateListing(equipmentId: string, request: EquipmentListingRequest): Observable<EquipmentListing> {
    return this.http.put<EquipmentListing>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}`,
      request
    );
  }

  submitListing(equipmentId: string): Observable<EquipmentListing> {
    return this.http.post<EquipmentListing>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}/submit`,
      {}
    );
  }

  pendingListings(): Observable<EquipmentListing[]> {
    return this.http.get<EquipmentListing[]>('/api/v1/admin/listings/pending');
  }

  reviewListing(equipmentId: string, approve: boolean, reason?: string): Observable<EquipmentListing> {
    const action = approve ? 'approve' : 'reject';
    let params = new HttpParams();
    if (!approve && reason) {
      params = params.set('reason', reason);
    }
    return this.http.patch<EquipmentListing>(
      `/api/v1/admin/listings/${encodeURIComponent(equipmentId)}/${action}`,
      null,
      { params }
    );
  }
}
