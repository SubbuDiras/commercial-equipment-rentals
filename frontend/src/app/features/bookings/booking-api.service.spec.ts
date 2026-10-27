import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { BookingApiService } from './booking-api.service';

describe('BookingApiService', () => {
  let service: BookingApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    service = TestBed.inject(BookingApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends the checkout result to verify with the backend field names', () => {
    service.verifyPayment('pay-1', {
      razorpay_order_id: 'order_FAKE1',
      razorpay_payment_id: 'pay_FAKE1',
      razorpay_signature: 'sig'
    }).subscribe();

    const request = http.expectOne('/api/v1/payments/pay-1/verify');
    expect(request.request.body).toEqual({
      razorpayOrderId: 'order_FAKE1',
      razorpayPaymentId: 'pay_FAKE1',
      razorpaySignature: 'sig'
    });
    request.flush({});
  });

  it('cancels through the renter or owner endpoint', () => {
    service.cancel('b-1', 'Plans changed.', false).subscribe();
    http.expectOne(request => request.method === 'POST' && request.url === '/api/v1/bookings/b-1/cancel').flush({});

    service.cancel('b-1', 'Machine broke.', true).subscribe();
    const owner = http.expectOne('/api/v1/owner/bookings/b-1/cancel');
    expect(owner.request.body).toEqual({ reason: 'Machine broke.' });
    owner.flush({});
  });

  it('filters owner bookings by a comma-separated status list', () => {
    service.ownerBookings(['REQUESTED', 'PAYMENT_PENDING'], 1, 10).subscribe();

    const request = http.expectOne(candidate => candidate.url === '/api/v1/owner/bookings');
    expect(request.request.params.get('status')).toBe('REQUESTED,PAYMENT_PENDING');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ content: [], page: 1, size: 10, totalElements: 0, totalPages: 0 });
  });

  it('posts inspection submissions to the handover or return endpoint', () => {
    const submission = {
      hourMeterReading: 1, fuelOrBatteryLevel: null, checklist: {}, conditionSummary: null,
      ownerOtp: '111111', renterOtp: '222222', photos: []
    };
    service.submitInspection('b-1', 'RETURN', submission).subscribe();
    http.expectOne('/api/v1/rentals/b-1/return').flush({});
  });
});
