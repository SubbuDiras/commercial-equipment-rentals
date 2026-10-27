import { env } from './env';
import { tinyPng } from './files';

type Json = Record<string, any>;

/**
 * Calls the backend as one user, with a token from Keycloak's password grant (direct access grants must be on for
 * the frontend client). Used to put bookings into a state quickly so the UI test can focus on one step.
 */
export class Api {
  private token: string | null = null;

  constructor(readonly email: string) {}

  static as(email: string): Api {
    return new Api(email);
  }

  /** Forget the token, e.g. after a role change, so the next call picks up the new roles. */
  refresh(): this {
    this.token = null;
    return this;
  }

  // Dev tools (dev/test profile only)

  seedOwnerListing(): Promise<{ ownerProfileId: string; equipmentId: string; title: string; ownerRoleGranted: boolean }> {
    return this.post('/api/v1/dev/seed/owner-listing');
  }

  shiftTime(bookingId: string, hours: number): Promise<Json> {
    return this.post(`/api/v1/dev/bookings/${bookingId}/shift-time?hours=${hours}`);
  }

  runScheduler(): Promise<{ expiredHolds: number; advancedRentals: number; releasedSettlements: number; closedBookings: number }> {
    return this.post('/api/v1/dev/scheduler/run');
  }

  // Bookings

  /** Requests a pickup booking starting `startInHours` from now and lasting `days`. */
  async requestBooking(equipmentId: string, startInHours: number, days = 2): Promise<string> {
    const start = new Date(Date.now() + startInHours * 3_600_000);
    start.setSeconds(0, 0);
    const end = new Date(start.getTime() + days * 86_400_000);
    const booking = await this.post('/api/v1/bookings', {
      equipmentId,
      startTime: start.toISOString(),
      endTime: end.toISOString(),
      deliveryOption: 'PICKUP'
    });
    return booking.bookingId;
  }

  details(bookingId: string): Promise<Json> {
    return this.get(`/api/v1/bookings/${bookingId}`);
  }

  async status(bookingId: string): Promise<string> {
    return (await this.details(bookingId)).status;
  }

  accept(bookingId: string): Promise<Json> {
    return this.patch(`/api/v1/owner/bookings/${bookingId}/accept`);
  }

  /** Pays through the fake Razorpay simulator, exactly as the payment dialog does. */
  async pay(bookingId: string, outcome: 'SUCCESS' | 'FAILURE' = 'SUCCESS'): Promise<void> {
    const order = await this.post(`/api/v1/bookings/${bookingId}/payment-order`);
    if (order.provider !== 'FAKE') {
      throw new Error('The backend is not using the fake Razorpay gateway (set RAZORPAY_FAKE=true).');
    }
    const result = await this.post(`/api/v1/dev/payments/${order.razorpayOrderId}/simulate`, { outcome });
    if (outcome === 'SUCCESS') {
      await this.post(`/api/v1/payments/${order.paymentId}/verify`, {
        razorpayOrderId: result.razorpay_order_id,
        razorpayPaymentId: result.razorpay_payment_id,
        razorpaySignature: result.razorpay_signature
      });
    }
  }

  // Rental operations

  async issueOtp(bookingId: string, type: 'HANDOVER' | 'RETURN'): Promise<string> {
    const challenge = await this.post(`/api/v1/rentals/${bookingId}/otp?type=${type}`);
    if (!challenge.code) {
      throw new Error('The OTP code was not returned; the backend must run with the dev profile.');
    }
    return challenge.code;
  }

  /** Uploads `count` photos straight to private storage and returns their object keys. */
  async uploadInspectionPhotos(bookingId: string, type: 'HANDOVER' | 'RETURN', count = 4): Promise<string[]> {
    const keys: string[] = [];
    for (let index = 0; index < count; index++) {
      const upload = await this.post(`/api/v1/rentals/${bookingId}/inspection-uploads?type=${type}`,
        { contentType: 'image/png', contentLength: tinyPng.length });
      const response = await fetch(upload.uploadUrl, { method: 'PUT', headers: { 'Content-Type': 'image/png' }, body: tinyPng });
      if (!response.ok) {
        throw new Error(`Photo upload to storage failed: HTTP ${response.status}`);
      }
      keys.push(upload.storageObjectKey);
    }
    return keys;
  }

  /** Completes a handover or return: both sides' codes plus four photos, submitted by this user. */
  async completeInspection(bookingId: string, type: 'HANDOVER' | 'RETURN', other: Api): Promise<void> {
    const mine = await this.issueOtp(bookingId, type);
    const theirs = await other.issueOtp(bookingId, type);
    const keys = await this.uploadInspectionPhotos(bookingId, type);
    const iAmOwner = (await this.details(bookingId)).viewerRole === 'OWNER';
    await this.post(`/api/v1/rentals/${bookingId}/${type === 'HANDOVER' ? 'handover' : 'return'}`, {
      hourMeterReading: type === 'HANDOVER' ? 100 : 112.5,
      fuelOrBatteryLevel: 'FULL',
      checklist: { accessories_present: true, condition_acceptable: true },
      conditionSummary: 'Recorded by the E2E suite.',
      ownerOtp: iAmOwner ? mine : theirs,
      renterOtp: iAmOwner ? theirs : mine,
      photos: keys.map(storageObjectKey => ({ storageObjectKey, mediaTag: 'CONDITION', latitude: null, longitude: null }))
    });
  }

  approveCondition(bookingId: string): Promise<Json> {
    return this.post(`/api/v1/owner/rentals/${bookingId}/approve-condition`);
  }

  raiseDispute(bookingId: string, claimAmount: number): Promise<Json> {
    return this.post(`/api/v1/rentals/${bookingId}/disputes`,
      { category: 'DAMAGE', claimAmount, description: 'Cracked housing found at return (E2E).' });
  }

  // HTTP

  get(path: string): Promise<any> {
    return this.request('GET', path);
  }

  post(path: string, body?: unknown): Promise<any> {
    return this.request('POST', path, body);
  }

  patch(path: string, body?: unknown): Promise<any> {
    return this.request('PATCH', path, body);
  }

  private async request(method: string, path: string, body?: unknown): Promise<any> {
    const response = await fetch(`${env.apiUrl}${path}`, {
      method,
      headers: { Authorization: `Bearer ${await this.accessToken()}`, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const text = await response.text();
    if (!response.ok) {
      throw new Error(`${method} ${path} as ${this.email} failed: HTTP ${response.status} ${text}`);
    }
    return text ? JSON.parse(text) : undefined;
  }

  private async accessToken(): Promise<string> {
    if (this.token) {
      return this.token;
    }
    const response = await fetch(`${env.keycloakUrl}/realms/${env.realm}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'password',
        client_id: env.clientId,
        username: this.email,
        password: env.password,
        scope: 'openid profile email'
      })
    });
    if (!response.ok) {
      throw new Error(`Keycloak login for ${this.email} failed (HTTP ${response.status}); is "Direct access grants" on for ${env.clientId}?`);
    }
    this.token = (await response.json()).access_token as string;
    return this.token;
  }
}
