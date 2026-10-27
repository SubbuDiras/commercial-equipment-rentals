import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
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

/** A row in the renter's booking history. */
export interface RenterBookingSummary extends Booking {
  equipmentTitle: string;
}

/** A row in the owner's bookings list. */
export interface OwnerBookingSummary extends Booking {
  equipmentTitle: string;
  renterName: string;
  requestedAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type BookingAction =
  | 'PAY' | 'ACCEPT' | 'REJECT'
  | 'GENERATE_HANDOVER_OTP' | 'UPLOAD_HANDOVER_PHOTOS' | 'SUBMIT_HANDOVER'
  | 'GENERATE_RETURN_OTP' | 'UPLOAD_RETURN_PHOTOS' | 'SUBMIT_RETURN'
  | 'APPROVE_CONDITION' | 'RAISE_DISPUTE' | 'RESOLVE_DISPUTE' | 'CANCEL' | 'REVIEW';

export type InspectionType = 'HANDOVER' | 'RETURN';

export interface InspectionReport {
  inspectionId: string;
  type: InspectionType;
  inspectedAt: string;
  hourMeterReading: number | null;
  fuelOrBatteryLevel: string | null;
  conditionSummary: string | null;
  checklist: Record<string, boolean>;
  photos: { mediaTag: string; url: string; capturedAt: string }[];
}

export interface CancellationBreakdown {
  cancelledBy: 'RENTER' | 'OWNER';
  policyTier: string;
  hoursBeforeStart: number;
  currency: string;
  amountPaid: number;
  rentalCharges: number;
  rentalRefund: number;
  gatewayFee: number;
  depositRefund: number;
  totalRefund: number;
  ownerCompensation: number;
  platformCredit: number;
  policyNotice: string;
}

export interface CancellationResponse {
  bookingId: string;
  bookingStatus: string;
  cancelledAt: string | null;
  razorpayRefundId: string | null;
  breakdown: CancellationBreakdown;
}

export interface BookingReview {
  reviewId: string;
  bookingId: string;
  reviewerRole: 'RENTER' | 'OWNER';
  reviewerName: string;
  rating: number;
  comments: string | null;
  createdAt: string;
  bookingStatus: string | null;
}

export interface BookingDetails extends Booking {
  equipmentTitle: string;
  ownerName: string;
  renterName: string;
  viewerRole: 'RENTER' | 'OWNER' | 'ADMIN';
  payment: {
    paymentId: string;
    status: string;
    amount: number;
    currency: string;
    razorpayOrderId: string;
    failureReason: string | null;
    refunds: { type: string; amount: number; status: string; createdAt: string }[];
  } | null;
  inspections: InspectionReport[];
  inspectionUploads: { inspectionType: InspectionType; storageObjectKey: string; uploadedByMe: boolean; expiresAt: string }[];
  otpStatus: {
    inspectionType: InspectionType;
    ownerRequested: boolean;
    ownerExpiresAt: string | null;
    renterRequested: boolean;
    renterExpiresAt: string | null;
  } | null;
  dispute: {
    disputeId: string;
    status: string;
    category: string;
    claimAmount: number;
    approvedDeductionAmount: number | null;
    description: string;
    resolutionNotes: string | null;
    createdAt: string;
    resolvedAt: string | null;
  } | null;
  disputeDeadline: string | null;
  cancellation: {
    reason: string;
    cancelledAt: string;
    razorpayRefundId: string | null;
    breakdown: CancellationBreakdown;
  } | null;
  reviews: BookingReview[];
  availableActions: BookingAction[];
}

export interface PaymentOrder {
  paymentId: string;
  razorpayOrderId: string;
  amountInPaise: number;
  currency: string;
  razorpayKeyId: string;
  provider: 'FAKE' | 'RAZORPAY';
}

export interface CheckoutResult {
  razorpay_order_id: string;
  razorpay_payment_id: string;
  razorpay_signature: string | null;
  outcome?: 'SUCCESS' | 'FAILURE';
  error_description?: string | null;
}

export interface OtpChallenge {
  inspectionType: InspectionType;
  expiresAt: string;
  /** Present only in dev/test; elsewhere the code is emailed to the other party. */
  code: string | null;
  emailedToCounterparty: boolean;
}

export interface InspectionUpload {
  storageObjectKey: string;
  uploadUrl: string;
  contentType: string;
  expiresAt: string;
}

export interface InspectionSubmission {
  hourMeterReading: number;
  fuelOrBatteryLevel: string | null;
  checklist: Record<string, boolean>;
  conditionSummary: string | null;
  ownerOtp: string;
  renterOtp: string;
  photos: { storageObjectKey: string; mediaTag: string; latitude: number | null; longitude: number | null }[];
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

const id = encodeURIComponent;

@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = '/api/v1/bookings';

  create(request: BookingRequest): Observable<Booking> {
    return this.http.post<Booking>(this.apiUrl, request);
  }

  list(page = 0, size = 20): Observable<Page<RenterBookingSummary>> {
    return this.http.get<Page<RenterBookingSummary>>(this.apiUrl, { params: { page, size } });
  }

  details(bookingId: string): Observable<BookingDetails> {
    return this.http.get<BookingDetails>(`${this.apiUrl}/${id(bookingId)}`);
  }

  ownerBookings(statuses: string[], page = 0, size = 20): Observable<Page<OwnerBookingSummary>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (statuses.length) {
      params = params.set('status', statuses.join(','));
    }
    return this.http.get<Page<OwnerBookingSummary>>('/api/v1/owner/bookings', { params });
  }

  accept(bookingId: string): Observable<Booking> {
    return this.http.patch<Booking>(`/api/v1/owner/bookings/${id(bookingId)}/accept`, null);
  }

  reject(bookingId: string, reason: string): Observable<Booking> {
    return this.http.patch<Booking>(`/api/v1/owner/bookings/${id(bookingId)}/reject`, { reason });
  }

  cancellationQuote(bookingId: string): Observable<CancellationResponse> {
    return this.http.get<CancellationResponse>(`${this.apiUrl}/${id(bookingId)}/cancellation-quote`);
  }

  cancel(bookingId: string, reason: string, asOwner: boolean): Observable<CancellationResponse> {
    const url = asOwner ? `/api/v1/owner/bookings/${id(bookingId)}/cancel` : `${this.apiUrl}/${id(bookingId)}/cancel`;
    return this.http.post<CancellationResponse>(url, { reason });
  }

  createPaymentOrder(bookingId: string): Observable<PaymentOrder> {
    return this.http.post<PaymentOrder>(`${this.apiUrl}/${id(bookingId)}/payment-order`, null);
  }

  simulateFakePayment(orderId: string, outcome: 'SUCCESS' | 'FAILURE'): Observable<CheckoutResult> {
    return this.http.post<CheckoutResult>(`/api/v1/dev/payments/${id(orderId)}/simulate`, { outcome });
  }

  verifyPayment(paymentId: string, result: CheckoutResult): Observable<unknown> {
    return this.http.post(`/api/v1/payments/${id(paymentId)}/verify`, {
      razorpayOrderId: result.razorpay_order_id,
      razorpayPaymentId: result.razorpay_payment_id,
      razorpaySignature: result.razorpay_signature
    });
  }

  issueOtp(bookingId: string, type: InspectionType): Observable<OtpChallenge> {
    return this.http.post<OtpChallenge>(`/api/v1/rentals/${id(bookingId)}/otp`, null, { params: { type } });
  }

  presignInspectionPhoto(bookingId: string, type: InspectionType, file: File): Observable<InspectionUpload> {
    return this.http.post<InspectionUpload>(`/api/v1/rentals/${id(bookingId)}/inspection-uploads`,
      { contentType: file.type, contentLength: file.size }, { params: { type } });
  }

  submitInspection(bookingId: string, type: InspectionType, submission: InspectionSubmission): Observable<unknown> {
    const path = type === 'HANDOVER' ? 'handover' : 'return';
    return this.http.post(`/api/v1/rentals/${id(bookingId)}/${path}`, submission);
  }

  approveCondition(bookingId: string): Observable<unknown> {
    return this.http.post(`/api/v1/owner/rentals/${id(bookingId)}/approve-condition`, null);
  }

  raiseDispute(bookingId: string, claim: { category: string; claimAmount: number; description: string }): Observable<unknown> {
    return this.http.post(`/api/v1/rentals/${id(bookingId)}/disputes`, claim);
  }

  review(bookingId: string, rating: number, comments: string | null): Observable<BookingReview> {
    return this.http.post<BookingReview>(`${this.apiUrl}/${id(bookingId)}/reviews`, { rating, comments });
  }
}
