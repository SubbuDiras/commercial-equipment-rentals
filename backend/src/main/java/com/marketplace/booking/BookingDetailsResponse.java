package com.marketplace.booking;

import com.marketplace.rental.InspectionReport;
import com.marketplace.review.ReviewResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingDetailsResponse(
        String bookingId,
        String bookingReference,
        String equipmentId,
        String equipmentTitle,
        Instant startTime,
        Instant endTime,
        String deliveryOption,
        String status,
        Instant holdExpiresAt,
        BookingPricing pricing,
        String ownerName,
        String renterName,
        String viewerRole,
        PaymentSummary payment,
        List<InspectionReport> inspections,
        List<InspectionUpload> inspectionUploads,
        OtpStatus otpStatus,
        DisputeSummary dispute,
        Instant disputeDeadline,
        Cancellation cancellation,
        List<ReviewResponse> reviews,
        List<BookingAction> availableActions,
        /** Short-lived links (null when there is no photo): the equipment's cover and each party's profile photo. */
        Photos photos,
        /** Owner delivery only: where to deliver, as the renter entered it. Null for pickup. */
        DeliveryAddressRequest deliveryAddress,
        /** Owner delivery only: the driver and expected arrival, once the owner has sent the equipment. */
        Delivery delivery
) {

    public record Delivery(
            String driverName,
            String driverPhone,
            String vehicleNumber,
            Instant expectedArrivalAt,
            Instant dispatchedAt,
            Instant updatedAt
    ) {
    }

    public record Photos(String equipment, String owner, String renter) {
    }

    /** Present only on cancelled bookings: who cancelled, why, and the refund breakdown that was applied. */
    public record Cancellation(
            String reason,
            Instant cancelledAt,
            String razorpayRefundId,
            CancellationBreakdown breakdown
    ) {
    }

    public record PaymentSummary(
            String paymentId,
            String status,
            BigDecimal amount,
            String currency,
            String razorpayOrderId,
            String failureReason,
            List<Refund> refunds
    ) {
    }

    public record Refund(String type, BigDecimal amount, String status, Instant createdAt) {
    }

    /** A photo uploaded for the inspection in progress that has not yet been submitted. */
    public record InspectionUpload(
            String inspectionType,
            String storageObjectKey,
            boolean uploadedByMe,
            Instant expiresAt
    ) {
    }

    /** Whether each party currently holds an unused, unexpired OTP for the inspection in progress. */
    public record OtpStatus(
            String inspectionType,
            boolean ownerRequested,
            Instant ownerExpiresAt,
            boolean renterRequested,
            Instant renterExpiresAt
    ) {
    }

    public record DisputeSummary(
            String disputeId,
            String status,
            String category,
            BigDecimal claimAmount,
            BigDecimal approvedDeductionAmount,
            String description,
            String resolutionNotes,
            Instant createdAt,
            Instant resolvedAt
    ) {
    }
}
