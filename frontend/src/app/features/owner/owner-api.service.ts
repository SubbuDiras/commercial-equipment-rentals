import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';

export const DOCUMENT_TYPES = [
  { value: 'AADHAAR_CARD', label: 'Aadhaar card' },
  { value: 'PAN_CARD', label: 'PAN card' },
  { value: 'SIGNATURE', label: 'Signature' },
  { value: 'BANK_PROOF', label: 'Bank proof (cancelled cheque or passbook)' },
  { value: 'IDENTITY', label: 'Other identity proof' },
  { value: 'ADDRESS_PROOF', label: 'Address proof' },
  { value: 'BUSINESS_REGISTRATION', label: 'Business registration' },
  { value: 'GST_CERTIFICATE', label: 'GST certificate' },
  { value: 'OTHER', label: 'Other' }
];

export function documentTypeLabel(type: string): string {
  return DOCUMENT_TYPES.find(candidate => candidate.value === type)?.label ?? type;
}

/** What the owner submitted. Aadhaar is the last 4 digits only; the owner's own view masks the account number. */
export interface OwnerKyc {
  phone: string | null;
  pan: string | null;
  gstin: string | null;
  aadhaarLast4: string | null;
  bankName: string | null;
  bankAccountType: string | null;
  bankAccountNumber: string | null;
  bankIfsc: string | null;
  bankAccountHolderName: string | null;
}

export interface OwnerProfile {
  ownerProfileId: string;
  status: 'UNDER_REVIEW' | 'VERIFIED' | 'REJECTED' | 'SUSPENDED' | string;
  businessType: string;
  businessName: string | null;
  rejectionReason: string | null;
  razorpayAccountLinked: boolean;
  submittedAt: string;
  verifiedAt: string | null;
  kyc: OwnerKyc;
}

export interface OwnerDocument {
  documentId: string;
  documentType: string;
  originalFilename: string;
  contentType: string;
  fileSizeBytes: number;
  status: string;
  uploadedAt: string | null;
  uploadExpiresAt: string | null;
}

export interface OwnerReview {
  ownerProfileId: string;
  firstName: string;
  lastName: string;
  email: string;
  businessName: string | null;
  businessType: string;
  status: string;
  submittedAt: string;
  kyc?: OwnerKyc;
  ownerRoleGranted?: boolean;
  warning?: string;
}

interface DocumentUpload {
  documentId: string;
  uploadUrl: string;
  contentType: string;
  expiresAt: string;
}

const id = encodeURIComponent;

@Injectable({ providedIn: 'root' })
export class OwnerApiService {
  private readonly http = inject(HttpClient);

  myProfile(): Observable<OwnerProfile> {
    return this.http.get<OwnerProfile>('/api/v1/owner/onboarding/me');
  }

  submit(request: Record<string, unknown>): Observable<{ ownerProfileId: string; status: string }> {
    return this.http.post<{ ownerProfileId: string; status: string }>('/api/v1/owner/onboarding', request);
  }

  documents(ownerProfileId: string): Observable<OwnerDocument[]> {
    return this.http.get<OwnerDocument[]>(`/api/v1/owner/onboarding/${id(ownerProfileId)}/documents`);
  }

  /** Presigns, uploads the file straight to private storage, then marks the document complete. */
  async uploadDocument(ownerProfileId: string, documentType: string, file: File, contentType = file.type): Promise<void> {
    const base = `/api/v1/owner/onboarding/${id(ownerProfileId)}/documents`;
    const upload = await firstValueFrom(this.http.post<DocumentUpload>(base, {
      documentType,
      originalFilename: file.name,
      contentType,
      contentLength: file.size
    }));
    const response = await fetch(upload.uploadUrl, {
      method: 'PUT',
      headers: { 'Content-Type': upload.contentType },
      body: file
    });
    if (!response.ok) {
      throw new Error(`Storage upload failed with HTTP ${response.status}.`);
    }
    await firstValueFrom(this.http.post(`${base}/${id(upload.documentId)}/complete`, {}));
  }

  // Admin

  ownersForReview(status: string): Observable<OwnerReview[]> {
    return this.http.get<OwnerReview[]>('/api/v1/admin/owners', { params: { status } });
  }

  approve(ownerProfileId: string): Observable<OwnerReview> {
    return this.http.patch<OwnerReview>(`/api/v1/admin/owners/${id(ownerProfileId)}/approve`, null);
  }

  reject(ownerProfileId: string, reason: string): Observable<OwnerReview> {
    return this.http.patch<OwnerReview>(`/api/v1/admin/owners/${id(ownerProfileId)}/reject`, { reason });
  }

  adminDocuments(ownerProfileId: string): Observable<OwnerDocument[]> {
    return this.http.get<OwnerDocument[]>(`/api/v1/admin/owners/${id(ownerProfileId)}/documents`);
  }

  /** With inline, the link displays the file in the page (for previews) rather than downloading it. */
  adminDocumentDownload(ownerProfileId: string, documentId: string, inline = false): Observable<{ downloadUrl: string }> {
    return this.http.get<{ downloadUrl: string }>(
      `/api/v1/admin/owners/${id(ownerProfileId)}/documents/${id(documentId)}/download`, { params: { inline } });
  }

  linkRazorpayAccount(ownerProfileId: string, linkedAccountId: string): Observable<unknown> {
    return this.http.patch(`/api/v1/admin/owners/${id(ownerProfileId)}/razorpay-account`, { linkedAccountId });
  }
}
