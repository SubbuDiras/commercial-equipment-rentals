package com.marketplace.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.InvalidStateTransitionException;
import com.marketplace.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private static final Duration OWNER_REVIEW_WINDOW = Duration.ofHours(4);
    private static final Duration PAYMENT_WINDOW = Duration.ofMinutes(30);
    private static final List<BookingStatus> PERMANENT_INVENTORY_BLOCKING_STATUSES = List.of(
            BookingStatus.CONFIRMED, BookingStatus.HANDOVER_PENDING, BookingStatus.IN_PROGRESS,
            BookingStatus.RETURN_PENDING, BookingStatus.RETURNED, BookingStatus.DISPUTED,
            BookingStatus.INSPECTION_PASSED);
    private static final List<BookingStatus> HOLD_INVENTORY_BLOCKING_STATUSES = List.of(
            BookingStatus.REQUESTED, BookingStatus.PAYMENT_PENDING, BookingStatus.PAYMENT_FAILED);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BookingPricingService pricingService;

    public BookingService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            BookingPricingService pricingService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.pricingService = pricingService;
    }

    @Transactional
    public BookingResponse create(Jwt renterJwt, BookingCreateRequest request) {
        validatePeriod(request.startTime(), request.endTime());
        EquipmentPrice equipment = lockEquipment(request.equipmentId());
        String renterId = resolveUser(renterJwt);
        validateDelivery(request, equipment);

        if (hasOverlappingBooking(equipment.id(), request.startTime(), request.endTime())) {
            throw new ConflictException("The equipment is already reserved for an overlapping period.");
        }

        BookingPricingService.Quote quote = pricingService.calculate(
                request.startTime(), request.endTime(), equipment.dailyRate(), equipment.weeklyRate(),
                request.deliveryOption() == DeliveryOption.OWNER_DELIVERY
                        ? equipment.deliveryCharge()
                        : BigDecimal.ZERO,
                equipment.securityDeposit(), equipment.minimumRentalDays());
        Instant holdExpiresAt = Instant.now().plus(OWNER_REVIEW_WINDOW);
        String bookingId = UUID.randomUUID().toString();
        String bookingReference = "BKG-" + DateTimeFormatter.ofPattern("yyyyMM")
                .withZone(ZoneOffset.UTC).format(Instant.now()) + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try {
            jdbcTemplate.update("""
                    INSERT INTO bookings
                        (id, booking_reference, renter_user_id, equipment_id, start_time, end_time,
                         status, hold_expires_at, delivery_option, delivery_address_json, currency,
                         base_rental_amount, delivery_charge, security_deposit_amount, platform_fee_amount,
                         owner_commission_amount, tax_amount, total_payable_amount, owner_earnings_estimated)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'INR', ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    bookingId, bookingReference, renterId, equipment.id(),
                    Timestamp.from(request.startTime()), Timestamp.from(request.endTime()),
                    BookingStatus.REQUESTED.name(), Timestamp.from(holdExpiresAt),
                    request.deliveryOption().name(), serializeAddress(request.deliveryAddress()),
                    quote.baseRentalAmount(), quote.deliveryCharge(), quote.securityDepositAmount(),
                    quote.platformFeeAmount(), quote.ownerCommissionAmount(), quote.taxAmount(),
                    quote.totalPayableAmount(), quote.ownerEarningsEstimated());

            insertPriceItem(bookingId, "BASE_RENTAL", "Rental (" + quote.rentalDays() + " days)", quote.baseRentalAmount());
            insertPriceItem(bookingId, "DELIVERY", "Delivery", quote.deliveryCharge());
            insertPriceItem(bookingId, "PLATFORM_FEE", "Platform convenience fee", quote.platformFeeAmount());
            insertPriceItem(bookingId, "OWNER_COMMISSION", "Owner commission (12%)", quote.ownerCommissionAmount());
            insertPriceItem(bookingId, "GST_ESTIMATE", "Provisional GST estimate (18%)", quote.taxAmount());
            insertPriceItem(bookingId, "SECURITY_DEPOSIT", "Refundable security deposit", quote.securityDepositAmount());
            insertStatusHistory(bookingId, null, BookingStatus.REQUESTED, renterId, null);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("The booking could not be created because its data conflicts with existing records.");
        }

        return new BookingResponse(bookingId, bookingReference, equipment.id(), request.startTime(),
                request.endTime(), request.deliveryOption().name(), BookingStatus.REQUESTED.name(),
                holdExpiresAt, quote.toResponse());
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(String equipmentId, Instant startTime, Instant endTime) {
        validatePeriod(startTime, endTime);
        Integer activeListingCount = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN equipment_categories c ON c.id = e.category_id
                        WHERE e.id = ? AND e.status = 'ACTIVE'
                          AND op.verification_status = 'VERIFIED'
                          AND c.is_active = TRUE
                        """,
                Integer.class, equipmentId);
        if (activeListingCount == null || activeListingCount == 0) {
            throw new ResourceNotFoundException("Active equipment listing not found.");
        }
        return !hasOverlappingBooking(equipmentId, startTime, endTime);
    }

    @Transactional
    public BookingResponse accept(String bookingId, Jwt ownerJwt) {
        BookingRow booking = lockBooking(bookingId);
        String ownerUserId = resolveUser(ownerJwt);
        assertEquipmentOwner(booking.equipmentId(), ownerUserId);
        requireStatus(booking, BookingStatus.REQUESTED, BookingStatus.PAYMENT_PENDING);
        if (booking.holdExpiresAt() == null || !booking.holdExpiresAt().isAfter(Instant.now())) {
            throw new InvalidStateTransitionException(BookingStatus.REQUESTED.name(), BookingStatus.PAYMENT_PENDING.name());
        }

        Instant holdExpiresAt = Instant.now().plus(PAYMENT_WINDOW);
        jdbcTemplate.update("""
                UPDATE bookings SET status = ?, hold_expires_at = ?
                WHERE id = ? AND status = ?
                """,
                BookingStatus.PAYMENT_PENDING.name(), Timestamp.from(holdExpiresAt),
                booking.id(), BookingStatus.REQUESTED.name());
        insertStatusHistory(booking.id(), BookingStatus.REQUESTED, BookingStatus.PAYMENT_PENDING, ownerUserId, null);
        return booking.withStatus(BookingStatus.PAYMENT_PENDING, holdExpiresAt);
    }

    @Transactional
    public BookingResponse reject(String bookingId, Jwt ownerJwt, String reason) {
        BookingRow booking = lockBooking(bookingId);
        String ownerUserId = resolveUser(ownerJwt);
        assertEquipmentOwner(booking.equipmentId(), ownerUserId);
        requireStatus(booking, BookingStatus.REQUESTED, BookingStatus.OWNER_REJECTED);
        jdbcTemplate.update("""
                UPDATE bookings SET status = ?, hold_expires_at = NULL
                WHERE id = ? AND status = ?
                """,
                BookingStatus.OWNER_REJECTED.name(), booking.id(), BookingStatus.REQUESTED.name());
        insertStatusHistory(booking.id(), BookingStatus.REQUESTED, BookingStatus.OWNER_REJECTED, ownerUserId, reason);
        return booking.withStatus(BookingStatus.OWNER_REJECTED, null);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listForRenter(Jwt renterJwt, int page, int size) {
        validatePage(page, size);
        String renterId = findExistingUser(renterJwt.getSubject());
        return jdbcTemplate.query("""
                        SELECT b.*
                        FROM bookings b
                        WHERE b.renter_user_id = ?
                        ORDER BY b.created_at DESC
                        LIMIT ? OFFSET ?
                        """,
                (resultSet, rowNumber) -> mapBooking(resultSet),
                renterId, size, (long) page * size);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(
            String bookingId,
            Jwt jwt,
            Collection<? extends GrantedAuthority> authorities
    ) {
        BookingRow booking = findBooking(bookingId);
        boolean admin = authorities.stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!admin) {
            String userId = findExistingUser(jwt.getSubject());
            boolean renter = booking.renterUserId().equals(userId);
            boolean owner = jdbcTemplate.queryForObject("""
                            SELECT COUNT(*) FROM equipment e
                            JOIN owner_profiles op ON op.id = e.owner_id
                            WHERE e.id = ? AND op.user_id = ?
                            """,
                    Integer.class, booking.equipmentId(), userId) > 0;
            if (!renter && !owner) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "You do not have access to this booking.");
            }
        }
        return booking.toResponse();
    }

    @Transactional
    public int expireHolds() {
        Instant now = Instant.now();
        List<BookingRow> expired = jdbcTemplate.query("""
                        SELECT b.*
                        FROM bookings b
                        WHERE b.status IN (?, ?, ?) AND b.hold_expires_at <= ?
                        ORDER BY b.hold_expires_at
                        LIMIT 100
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> mapBookingRow(resultSet),
                BookingStatus.REQUESTED.name(), BookingStatus.PAYMENT_PENDING.name(),
                BookingStatus.PAYMENT_FAILED.name(), Timestamp.from(now));
        int count = 0;
        for (BookingRow booking : expired) {
            BookingStatus target = booking.status() == BookingStatus.REQUESTED
                    ? BookingStatus.REQUEST_EXPIRED
                    : BookingStatus.HOLD_EXPIRED;
            int updated = jdbcTemplate.update("""
                    UPDATE bookings SET status = ?, hold_expires_at = NULL
                    WHERE id = ? AND status = ? AND hold_expires_at <= ?
                    """,
                    target.name(), booking.id(), booking.status().name(), Timestamp.from(now));
            if (updated == 1) {
                insertStatusHistory(booking.id(), booking.status(), target, null, "Booking hold expired.");
                count++;
            }
        }
        return count;
    }

    private String resolveUser(Jwt jwt) {
        String subject = jwt.getSubject();
        String userId = findExistingUserOrNull(subject);
        if (userId != null) {
            return userId;
        }
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new ConflictException("The authenticated account must provide a subject and email address.");
        }
        String preferredName = jwt.getClaimAsString("preferred_username");
        String givenName = firstNonBlank(jwt.getClaimAsString("given_name"), preferredName, "Marketplace");
        String familyName = firstNonBlank(jwt.getClaimAsString("family_name"), "User");
        userId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO users (id, keycloak_subject_id, email, phone, first_name, last_name)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    userId, subject, email, jwt.getClaimAsString("phone_number"), givenName, familyName);
            return userId;
        } catch (DataIntegrityViolationException exception) {
            String existingId = findExistingUserOrNull(subject);
            if (existingId != null) {
                return existingId;
            }
            throw new ConflictException("The authenticated account conflicts with an existing user profile.");
        }
    }

    private String findExistingUser(String subject) {
        String userId = findExistingUserOrNull(subject);
        if (userId == null) {
            throw new ResourceNotFoundException("User profile not found.");
        }
        return userId;
    }

    private String findExistingUserOrNull(String subject) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM users WHERE keycloak_subject_id = ?", String.class, subject);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private EquipmentPrice lockEquipment(String equipmentId) {
        List<EquipmentPrice> equipment = jdbcTemplate.query("""
                        SELECT id, owner_id, base_daily_rate, base_weekly_rate, security_deposit,
                               minimum_rental_days, delivery_supported, pickup_supported,
                               COALESCE(delivery_charge_flat, 0.00) AS delivery_charge_flat
                        FROM equipment
                        WHERE id = ? AND status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1 FROM owner_profiles op
                              WHERE op.id = equipment.owner_id AND op.verification_status = 'VERIFIED'
                          )
                          AND EXISTS (
                              SELECT 1 FROM equipment_categories c
                              WHERE c.id = equipment.category_id AND c.is_active = TRUE
                          )
                        FOR UPDATE
                        """,
                (resultSet, rowNumber) -> new EquipmentPrice(
                        resultSet.getString("id"),
                        resultSet.getString("owner_id"),
                        resultSet.getBigDecimal("base_daily_rate"),
                        resultSet.getBigDecimal("base_weekly_rate"),
                        resultSet.getBigDecimal("security_deposit"),
                        resultSet.getInt("minimum_rental_days"),
                        resultSet.getBoolean("delivery_supported"),
                        resultSet.getBoolean("pickup_supported"),
                        resultSet.getBigDecimal("delivery_charge_flat")),
                equipmentId);
        if (equipment.isEmpty()) {
            throw new ResourceNotFoundException("Active equipment listing not found.");
        }
        EquipmentPrice listing = equipment.get(0);
        return listing;
    }

    private boolean hasOverlappingBooking(String equipmentId, Instant startTime, Instant endTime) {
        String availabilitySql = """
                SELECT COUNT(*) FROM bookings
                WHERE equipment_id = ?
                  AND start_time < ?
                  AND end_time > ?
                  AND (
                      status IN (%s)
                      OR (status IN (%s) AND hold_expires_at > CURRENT_TIMESTAMP(6))
                  )
                """.formatted(
                String.join(",", PERMANENT_INVENTORY_BLOCKING_STATUSES.stream().map(status -> "?").toList()),
                String.join(",", HOLD_INVENTORY_BLOCKING_STATUSES.stream().map(status -> "?").toList()));
        List<Object> parameters = new ArrayList<>();
        parameters.add(equipmentId);
        parameters.add(Timestamp.from(endTime));
        parameters.add(Timestamp.from(startTime));
        PERMANENT_INVENTORY_BLOCKING_STATUSES.forEach(status -> parameters.add(status.name()));
        HOLD_INVENTORY_BLOCKING_STATUSES.forEach(status -> parameters.add(status.name()));
        Integer conflicts = jdbcTemplate.queryForObject(availabilitySql, Integer.class, parameters.toArray());
        return conflicts != null && conflicts > 0;
    }

    private void validateDelivery(BookingCreateRequest request, EquipmentPrice equipment) {
        if (request.deliveryOption() == DeliveryOption.OWNER_DELIVERY) {
            if (!equipment.deliverySupported()) {
                throw new ConflictException("Owner delivery is not available for this equipment.");
            }
            if (request.deliveryAddress() == null) {
                throw new ConflictException("A delivery address is required for owner delivery.");
            }
        } else if (!equipment.pickupSupported()) {
            throw new ConflictException("Renter pickup is not available for this equipment.");
        }
    }

    private void assertEquipmentOwner(String equipmentId, String ownerUserId) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*) FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        WHERE e.id = ? AND op.user_id = ?
                          AND op.verification_status = 'VERIFIED'
                        """,
                Integer.class, equipmentId, ownerUserId);
        if (count == null || count == 0) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the verified owner of this equipment may review the booking.");
        }
    }

    private BookingRow lockBooking(String bookingId) {
        List<BookingRow> rows = jdbcTemplate.query("""
                        SELECT b.*
                        FROM bookings b WHERE b.id = ? FOR UPDATE
                        """,
                (resultSet, rowNumber) -> mapBookingRow(resultSet),
                bookingId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        return rows.get(0);
    }

    private BookingRow findBooking(String bookingId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT b.*
                            FROM bookings b WHERE b.id = ?
                            """,
                    (resultSet, rowNumber) -> mapBookingRow(resultSet),
                    bookingId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException("Booking not found.");
        }
    }

    private void requireStatus(BookingRow booking, BookingStatus expected, BookingStatus attempted) {
        if (booking.status() != expected) {
            throw new InvalidStateTransitionException(booking.status().name(), attempted.name());
        }
    }

    private void insertPriceItem(String bookingId, String itemType, String description, BigDecimal amount) {
        jdbcTemplate.update("""
                INSERT INTO booking_price_items (id, booking_id, item_type, description, amount)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), bookingId, itemType, description, amount);
    }

    private void insertStatusHistory(
            String bookingId,
            BookingStatus from,
            BookingStatus to,
            String actorUserId,
            String reason
    ) {
        jdbcTemplate.update("""
                INSERT INTO booking_status_history
                    (id, booking_id, from_status, to_status, changed_by_user_id, reason_or_notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID().toString(), bookingId, from == null ? null : from.name(),
                to.name(), actorUserId, reason);
    }

    private String serializeAddress(DeliveryAddressRequest address) {
        if (address == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(address);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize the delivery address.", exception);
        }
    }

    private static void validatePeriod(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ConflictException("Rental end time must be later than its start time.");
        }
        if (!start.isAfter(Instant.now())) {
            throw new ConflictException("Rental start time must be in the future.");
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "Marketplace";
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new com.marketplace.exception.BadRequestException(
                    "Page must be non-negative and size must be between 1 and 100.");
        }
    }

    private static BookingResponse mapBooking(ResultSet resultSet) throws SQLException {
        return mapBookingRow(resultSet).toResponse();
    }

    private static BookingRow mapBookingRow(ResultSet resultSet) throws SQLException {
        Timestamp hold = resultSet.getTimestamp("hold_expires_at");
        return new BookingRow(
                resultSet.getString("id"),
                resultSet.getString("booking_reference"),
                resultSet.getString("renter_user_id"),
                resultSet.getString("equipment_id"),
                resultSet.getTimestamp("start_time").toInstant(),
                resultSet.getTimestamp("end_time").toInstant(),
                DeliveryOption.valueOf(resultSet.getString("delivery_option")),
                BookingStatus.valueOf(resultSet.getString("status")),
                hold == null ? null : hold.toInstant(),
                rentalDays(
                        resultSet.getTimestamp("start_time").toInstant(),
                        resultSet.getTimestamp("end_time").toInstant()),
                resultSet.getBigDecimal("base_rental_amount"),
                resultSet.getBigDecimal("delivery_charge"),
                resultSet.getBigDecimal("platform_fee_amount"),
                resultSet.getBigDecimal("tax_amount"),
                resultSet.getBigDecimal("security_deposit_amount"),
                resultSet.getBigDecimal("total_payable_amount"));
    }

    private static int rentalDays(Instant start, Instant end) {
        Duration duration = Duration.between(start, end);
        long seconds = duration.getSeconds();
        return Math.toIntExact(Math.floorDiv(seconds, 86_400)
                + (seconds % 86_400 == 0 && duration.getNano() == 0 ? 0 : 1));
    }

    private record EquipmentPrice(
            String id,
            String ownerId,
            BigDecimal dailyRate,
            BigDecimal weeklyRate,
            BigDecimal securityDeposit,
            int minimumRentalDays,
            boolean deliverySupported,
            boolean pickupSupported,
            BigDecimal deliveryCharge
    ) {
    }

    private record BookingRow(
            String id,
            String bookingReference,
            String renterUserId,
            String equipmentId,
            Instant startTime,
            Instant endTime,
            DeliveryOption deliveryOption,
            BookingStatus status,
            Instant holdExpiresAt,
            int rentalDays,
            BigDecimal baseRentalAmount,
            BigDecimal deliveryCharge,
            BigDecimal platformFeeAmount,
            BigDecimal taxAmount,
            BigDecimal securityDepositAmount,
            BigDecimal totalPayableAmount
    ) {
        private BookingResponse toResponse() {
            BookingPricing pricing = new BookingPricing("INR", rentalDays, baseRentalAmount,
                    deliveryCharge, platformFeeAmount, taxAmount, securityDepositAmount, totalPayableAmount);
            return new BookingResponse(id, bookingReference, equipmentId, startTime, endTime,
                    deliveryOption.name(), status.name(), holdExpiresAt, pricing);
        }

        private BookingResponse withStatus(BookingStatus nextStatus, Instant nextHoldExpiresAt) {
            BookingPricing pricing = new BookingPricing("INR", rentalDays, baseRentalAmount,
                    deliveryCharge, platformFeeAmount, taxAmount, securityDepositAmount, totalPayableAmount);
            return new BookingResponse(id, bookingReference, equipmentId, startTime, endTime,
                    deliveryOption.name(), nextStatus.name(), nextHoldExpiresAt, pricing);
        }
    }
}
