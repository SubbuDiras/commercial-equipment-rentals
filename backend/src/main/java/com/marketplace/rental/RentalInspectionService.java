package com.marketplace.rental;

import com.marketplace.booking.BookingStatus;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.InvalidStateTransitionException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.storage.PrivateObjectStorage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RentalInspectionService {

    private static final int OTP_LIFETIME_MINUTES = 15;
    private static final Duration DISPUTE_WINDOW = Duration.ofHours(24);
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp");

    private final JdbcTemplate jdbcTemplate;
    private final PrivateObjectStorage objectStorage;
    private final OtpProperties otpProperties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public RentalInspectionService(
            JdbcTemplate jdbcTemplate,
            PrivateObjectStorage objectStorage,
            OtpProperties otpProperties,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectStorage = objectStorage;
        this.otpProperties = otpProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OtpChallengeResponse issueOtp(String bookingId, InspectionType type, Jwt jwt) {
        Booking row = lockBooking(bookingId);
        Participant participant = requireParticipant(row, jwt.getSubject());
        validateInspectionWindow(row, type);

        String code = "%06d".formatted(secureRandom.nextInt(1_000_000));
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(OTP_LIFETIME_MINUTES));
        jdbcTemplate.update("""
                DELETE FROM rental_inspection_otps
                WHERE booking_id = ? AND inspection_type = ? AND participant_user_id = ?
                """,
                bookingId, type.name(), participant.userId());
        jdbcTemplate.update("""
                INSERT INTO rental_inspection_otps
                    (id, booking_id, inspection_type, participant_user_id, code_hash, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), bookingId, type.name(), participant.userId(),
                hashOtp(bookingId, type, participant.userId(), code),
                Timestamp.from(expiresAt));
        return new OtpChallengeResponse(type.name(), expiresAt, code);
    }

    @Transactional
    public InspectionUploadResponse presignPhoto(
            String bookingId,
            InspectionType type,
            String contentType,
            long contentLength,
            Jwt jwt
    ) {
        Booking row = lockBooking(bookingId);
        Participant participant = requireParticipant(row, jwt.getSubject());
        validateInspectionWindow(row, type);
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);
        if (extension == null) {
            throw new ConflictException("Inspection images must use JPEG, PNG, or WebP.");
        }
        String key = "rentals/" + bookingId + "/" + type.name().toLowerCase() + "/" +
                UUID.randomUUID() + "." + extension;
        PrivateObjectStorage.PresignedUpload upload =
                objectStorage.presignPut(key, contentType, contentLength);
        jdbcTemplate.update("""
                INSERT INTO rental_inspection_uploads
                    (id, booking_id, inspection_type, uploaded_by_user_id, storage_object_key, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), bookingId, type.name(), participant.userId(), key,
                Timestamp.from(upload.expiresAt()));
        return new InspectionUploadResponse(key, upload.uploadUrl(), contentType, upload.expiresAt());
    }

    @Transactional
    public InspectionResponse submit(
            String bookingId,
            InspectionType type,
            InspectionSubmissionRequest request,
            Jwt jwt
    ) {
        Booking row = lockBooking(bookingId);
        Participant actor = requireParticipant(row, jwt.getSubject());
        validateInspectionWindow(row, type);
        Participant owner = new Participant(row.ownerUserId(), true);
        Participant renter = new Participant(row.renterUserId(), false);
        verifyOtp(row.id(), type, owner.userId(), request.ownerOtp());
        verifyOtp(row.id(), type, renter.userId(), request.renterOtp());

        Instant inspectedAt = Instant.now();
        String inspectionId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO rental_inspections
                    (id, booking_id, inspection_type, inspector_user_id, hour_meter_reading,
                     fuel_or_battery_level, checklist_json, condition_summary, otp_verified, inspected_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, ?)
                """,
                inspectionId, bookingId, type.name(), actor.userId(), request.hourMeterReading(),
                request.fuelOrBatteryLevel(), serializeChecklist(request.checklist()),
                request.conditionSummary(), Timestamp.from(inspectedAt));

        for (InspectionPhotoRequest photo : request.photos()) {
            validatePhoto(row.id(), type, photo.storageObjectKey(), inspectedAt);
            jdbcTemplate.update("""
                    INSERT INTO rental_inspection_media
                        (id, inspection_id, storage_object_key, media_tag, geo_latitude, geo_longitude, captured_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID().toString(), inspectionId, photo.storageObjectKey(), photo.mediaTag(),
                    photo.latitude(), photo.longitude(), Timestamp.from(inspectedAt));
            jdbcTemplate.update("""
                    UPDATE rental_inspection_uploads SET attached_at = ?
                    WHERE storage_object_key = ? AND attached_at IS NULL
                    """, Timestamp.from(inspectedAt), photo.storageObjectKey());
        }

        BookingStatus nextStatus = type == InspectionType.HANDOVER
                ? BookingStatus.IN_PROGRESS
                : BookingStatus.RETURNED;
        jdbcTemplate.update("UPDATE bookings SET status = ? WHERE id = ?",
                nextStatus.name(), bookingId);
        addStatusHistory(bookingId, row.status(), nextStatus, actor.userId(),
                type == InspectionType.HANDOVER ? "Mutual handover inspection verified."
                        : "Mutual return inspection verified.");
        return new InspectionResponse(inspectionId, bookingId, type.name(), nextStatus.name(),
                inspectedAt, type == InspectionType.RETURN ? inspectedAt.plus(DISPUTE_WINDOW) : null);
    }

    @Transactional
    public String raiseDamageClaim(String bookingId, DamageClaimRequest request, Jwt ownerJwt) {
        Booking row = lockBooking(bookingId);
        Participant owner = requireParticipant(row, ownerJwt.getSubject());
        if (!owner.owner()) {
            throw new AccessDeniedException("Only the equipment owner may submit a damage claim.");
        }
        if (row.status() != BookingStatus.RETURNED) {
            throw new InvalidStateTransitionException(row.status().name(), BookingStatus.DISPUTED.name());
        }
        Instant returnedAt = latestReturnTime(bookingId);
        if (Instant.now().isAfter(returnedAt.plus(DISPUTE_WINDOW))) {
            throw new ConflictException("The 24-hour post-return dispute window has expired.");
        }
        if (request.claimAmount().compareTo(row.securityDeposit()) > 0) {
            throw new ConflictException("The damage claim cannot exceed the security deposit.");
        }
        Integer evidenceCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM rental_inspection_media rim
                        JOIN rental_inspections ri ON ri.id = rim.inspection_id
                        WHERE ri.booking_id = ? AND ri.inspection_type = 'RETURN'
                        """,
                Integer.class, bookingId);
        if (evidenceCount == null || evidenceCount < 4) {
            throw new ConflictException("A damage claim requires return inspection photo evidence.");
        }
        String disputeId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO disputes
                    (id, booking_id, raised_by_user_id, dispute_category, claim_amount, description, status)
                VALUES (?, ?, ?, ?, ?, ?, 'OPEN')
                """,
                disputeId, bookingId, owner.userId(), request.category(), request.claimAmount(), request.description());
        jdbcTemplate.update("UPDATE bookings SET status = 'DISPUTED' WHERE id = ?", bookingId);
        addStatusHistory(bookingId, BookingStatus.RETURNED, BookingStatus.DISPUTED,
                owner.userId(), "Damage claim " + disputeId + " submitted.");
        return disputeId;
    }

    @Transactional
    public int advanceTimedStates() {
        Instant now = Instant.now();
        List<TimedBooking> due = jdbcTemplate.query("""
                        SELECT b.id, b.status,
                               CASE
                                 WHEN b.status = 'CONFIRMED' THEN 'HANDOVER_PENDING'
                                 WHEN b.status = 'IN_PROGRESS' THEN 'RETURN_PENDING'
                                 ELSE 'INSPECTION_PASSED'
                               END AS next_status
                        FROM bookings b
                        WHERE (b.status = 'CONFIRMED' AND b.start_time <= ?)
                           OR (b.status = 'IN_PROGRESS' AND b.end_time <= ?)
                           OR (b.status = 'RETURNED' AND EXISTS (
                               SELECT 1 FROM rental_inspections ri
                               WHERE ri.booking_id = b.id AND ri.inspection_type = 'RETURN'
                                 AND ri.inspected_at <= ?
                           ) AND NOT EXISTS (
                               SELECT 1 FROM disputes d WHERE d.booking_id = b.id AND d.status = 'OPEN'
                           ))
                        ORDER BY b.updated_at
                        LIMIT 100
                        FOR UPDATE
                        """,
                (rs, rowNum) -> new TimedBooking(
                        rs.getString("id"), BookingStatus.valueOf(rs.getString("status")),
                        BookingStatus.valueOf(rs.getString("next_status"))),
                Timestamp.from(now), Timestamp.from(now), Timestamp.from(now.minus(DISPUTE_WINDOW)));
        int transitioned = 0;
        for (TimedBooking booking : due) {
            int updated = jdbcTemplate.update("""
                    UPDATE bookings SET status = ? WHERE id = ? AND status = ?
                    """,
                    booking.nextStatus().name(), booking.id(), booking.status().name());
            if (updated == 1) {
                addStatusHistory(booking.id(), booking.status(), booking.nextStatus(), null,
                        booking.nextStatus() == BookingStatus.INSPECTION_PASSED
                                ? "24-hour dispute window elapsed without an open claim."
                                : "Rental scheduled transition.");
                transitioned++;
            }
        }
        return transitioned;
    }

    private void verifyOtp(String bookingId, InspectionType type, String userId, String code) {
        String storedHash;
        Timestamp expires;
        try {
            OtpRecord record = jdbcTemplate.queryForObject("""
                            SELECT code_hash, expires_at, verified_at
                            FROM rental_inspection_otps
                            WHERE booking_id = ? AND inspection_type = ? AND participant_user_id = ?
                            FOR UPDATE
                            """,
                    (rs, rowNum) -> new OtpRecord(
                            rs.getString("code_hash"), rs.getTimestamp("expires_at"),
                            rs.getTimestamp("verified_at") != null),
                    bookingId, type.name(), userId);
            storedHash = record.hash();
            expires = record.expiresAt();
            if (record.verified()) {
                throw new ConflictException("An inspection OTP has already been used.");
            }
        } catch (EmptyResultDataAccessException exception) {
            throw new ConflictException("Both owner and renter must request an inspection OTP.");
        }
        if (!expires.toInstant().isAfter(Instant.now())
                || !MessageDigest.isEqual(
                storedHash.getBytes(StandardCharsets.US_ASCII),
                hashOtp(bookingId, type, userId, code).getBytes(StandardCharsets.US_ASCII))) {
            throw new ConflictException("An inspection OTP is invalid or expired.");
        }
        jdbcTemplate.update("""
                UPDATE rental_inspection_otps SET verified_at = CURRENT_TIMESTAMP(6)
                WHERE booking_id = ? AND inspection_type = ? AND participant_user_id = ?
                """,
                bookingId, type.name(), userId);
    }

    private void validatePhoto(String bookingId, InspectionType type, String objectKey, Instant uploadedAt) {
        if (objectKey == null || !objectKey.startsWith(
                "rentals/" + bookingId + "/" + type.name().toLowerCase() + "/")) {
            throw new ConflictException("Inspection photo key is not valid for this booking and inspection.");
        }
        Integer uploadCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM rental_inspection_uploads
                        WHERE booking_id = ? AND inspection_type = ? AND storage_object_key = ?
                          AND expires_at > ? AND attached_at IS NULL
                        """,
                Integer.class, bookingId, type.name(), objectKey, Timestamp.from(uploadedAt));
        if (uploadCount == null || uploadCount != 1) {
            throw new ConflictException("Inspection photo upload is missing, expired, or already attached.");
        }
        if (!objectStorage.existsWithinLimit(objectKey)) {
            throw new ConflictException("Inspection photo is missing from private object storage or exceeds 10 MB.");
        }
    }

    private Booking lockBooking(String bookingId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT b.id, b.renter_user_id, b.equipment_id, b.status,
                                   b.security_deposit_amount, b.start_time, b.end_time,
                                   op.user_id AS owner_user_id
                            FROM bookings b
                            JOIN equipment e ON e.id = b.equipment_id
                            JOIN owner_profiles op ON op.id = e.owner_id
                            WHERE b.id = ? FOR UPDATE
                            """,
                    (rs, rowNum) -> new Booking(
                            rs.getString("id"), rs.getString("renter_user_id"), rs.getString("equipment_id"),
                            BookingStatus.valueOf(rs.getString("status")), rs.getBigDecimal("security_deposit_amount"),
                            rs.getTimestamp("start_time").toInstant(), rs.getTimestamp("end_time").toInstant(),
                            rs.getString("owner_user_id")),
                    bookingId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Booking not found.");
        }
    }

    private Participant requireParticipant(Booking booking, String subject) {
        String userId;
        try {
            userId = jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE keycloak_subject_id = ?", String.class, subject);
        } catch (EmptyResultDataAccessException exception) {
            throw new AccessDeniedException("Booking participant profile not found.");
        }
        if (userId.equals(booking.ownerUserId())) {
            return new Participant(userId, true);
        }
        if (userId.equals(booking.renterUserId())) {
            return new Participant(userId, false);
        }
        throw new AccessDeniedException("Only the renter or equipment owner may access rental operations.");
    }

    private void validateInspectionWindow(Booking booking, InspectionType type) {
        Instant now = Instant.now();
        if (type == InspectionType.HANDOVER) {
            if (!booking.startTime().isBefore(now.plusSeconds(1))) {
                throw new ConflictException("Handover inspection is available from the rental start time.");
            }
            if (booking.status() != BookingStatus.CONFIRMED && booking.status() != BookingStatus.HANDOVER_PENDING) {
                throw new InvalidStateTransitionException(booking.status().name(), BookingStatus.IN_PROGRESS.name());
            }
        } else {
            if (!booking.endTime().isBefore(now.plusSeconds(1))) {
                throw new ConflictException("Return inspection is available from the rental end time.");
            }
            if (booking.status() != BookingStatus.IN_PROGRESS && booking.status() != BookingStatus.RETURN_PENDING) {
                throw new InvalidStateTransitionException(booking.status().name(), BookingStatus.RETURNED.name());
            }
        }
    }

    private Instant latestReturnTime(String bookingId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT inspected_at FROM rental_inspections
                            WHERE booking_id = ? AND inspection_type = 'RETURN'
                            ORDER BY inspected_at DESC LIMIT 1
                            """,
                    (rs, rowNum) -> rs.getTimestamp("inspected_at").toInstant(), bookingId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ConflictException("Return inspection evidence is missing.");
        }
    }

    private void addStatusHistory(String bookingId, BookingStatus from, BookingStatus to,
                                  String actorId, String reason) {
        jdbcTemplate.update("""
                INSERT INTO booking_status_history
                    (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), bookingId, from.name(), to.name(), actorId, reason);
    }

    private String serializeChecklist(Map<String, Boolean> checklist) {
        try {
            return objectMapper.writeValueAsString(checklist);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize inspection checklist.", exception);
        }
    }

    private String hashOtp(String bookingId, InspectionType type, String userId, String code) {
        if (otpProperties.secret() == null || otpProperties.secret().isBlank()) {
            throw new IllegalStateException("Inspection OTP hashing secret is not configured.");
        }
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(otpProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String value = bookingId + "|" + type.name() + "|" + userId + "|" + code;
            return HexFormat.of().formatHex(hmac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to hash inspection OTP.", exception);
        }
    }

    private record Booking(
            String id,
            String renterUserId,
            String equipmentId,
            BookingStatus status,
            BigDecimal securityDeposit,
            Instant startTime,
            Instant endTime,
            String ownerUserId
    ) {
    }

    private record Participant(String userId, boolean owner) {
    }

    private record OtpRecord(String hash, Timestamp expiresAt, boolean verified) {
    }

    private record TimedBooking(String id, BookingStatus status, BookingStatus nextStatus) {
    }
}
