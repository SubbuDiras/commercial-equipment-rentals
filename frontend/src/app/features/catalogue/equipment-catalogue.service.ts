import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { Page } from '../bookings/booking-api.service';

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
  /** Filled in on the public details endpoint only. */
  averageRating: number | null;
  reviewCount: number | null;
  /** Short-lived URL of the cover photo; filled in on search results only, null when the listing has no photos. */
  primaryImageUrl: string | null;
}

/** The backend accepts listing photos of these types up to 10 MB. */
export function isListingPhoto(file: File): boolean {
  return ['image/jpeg', 'image/png', 'image/webp'].includes(file.type) && file.size > 0 && file.size <= 10 * 1024 * 1024;
}

/** Parents first, each followed by its subcategories. */
export function flattenCategories(categories: EquipmentCategory[]): EquipmentCategory[] {
  return categories.flatMap(category => [category, ...flattenCategories(category.subcategories ?? [])]);
}

export interface EquipmentListingPage {
  content: EquipmentListing[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface EquipmentReview {
  reviewId: string;
  rating: number;
  comments: string | null;
  reviewerName: string;
  createdAt: string;
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

  reviews(equipmentId: string, page = 0, size = 10): Observable<Page<EquipmentReview>> {
    return this.http.get<Page<EquipmentReview>>(
      `/api/v1/equipment/${encodeURIComponent(equipmentId)}/reviews`, { params: { page, size } });
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

  /** Presigns, uploads the file straight to private storage, then confirms it; resolves to the new photo. */
  async uploadImage(equipmentId: string, file: File): Promise<EquipmentImage> {
    const upload = await firstValueFrom(this.presignImage(equipmentId, file));
    const response = await fetch(upload.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': upload.contentType },
      body: file
    });
    if (!response.ok) {
      throw new Error(`Storage upload failed with HTTP ${response.status}.`);
    }
    return firstValueFrom(this.completeImage(equipmentId, upload.imageId));
  }

  /** Resolves to the listing's remaining photos. */
  deleteImage(equipmentId: string, imageId: string): Observable<EquipmentImage[]> {
    return this.http.delete<EquipmentImage[]>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}/images/${encodeURIComponent(imageId)}`
    );
  }

  /** Resolves to the listing's photos, the new cover first. */
  makeCoverImage(equipmentId: string, imageId: string): Observable<EquipmentImage[]> {
    return this.http.patch<EquipmentImage[]>(
      `/api/v1/owner/equipment/${encodeURIComponent(equipmentId)}/images/${encodeURIComponent(imageId)}/primary`,
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
