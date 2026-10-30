package com.marketplace.booking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.exception.BadRequestException;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.notification.NotificationEvent;
import com.marketplace.notification.NotificationType;
import com.marketplace.user.UserProfileService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner delivery: the owner records the driver, vehicle and expected arrival when the equipment leaves, and may
 * correct them until the handover. The renter sees them on the booking and is emailed each time. This is a record
 * kept beside the booking, not a booking status: the handover inspection still moves the booking on.
 */
@Service
public class DeliveryService {

    /** Delivery details can be recorded once the booking is paid and until the equipment is handed over. */
    static final Set<BookingStatus> DISPATCHABLE = Set.of(BookingStatus.CONFIRMED, BookingStatus.HANDOVER_PENDING);
    /** Allows for clocks being slightly apart, and for recording a delivery that is just arriving. */
    private static final Duration ARRIVAL_GRACE = Duration.ofMinutes(10);

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final ApplicationEventPublisher events;
    private final ObjectMapper objectMapper;

    public DeliveryService(JdbcTemplate jdbcTemplate, UserProfileService userProfileService,
                           ApplicationEventPublisher events, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BookingDetailsResponse.Delivery dispatch(String bookingId, Jwt ownerJwt, DeliveryDispatchRequest request) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                        SELECT b.status, b.delivery_option, b.end_time, op.user_id AS owner_user_id
                        FROM bookings b
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        WHERE b.id = ? FOR UPDATE
                        """, bookingId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        Map<String, Object> booking = rows.get(0);
        String ownerUserId = userProfileService.resolveOrCreate(ownerJwt);
        if (!ownerUserId.equals(booking.get("owner_user_id"))) {
            throw new AccessDeniedException("Only the owner of this equipment can record its delivery.");
        }
        if (!DeliveryOption.OWNER_DELIVERY.name().equals(booking.get("delivery_option"))) {
            throw new ConflictException("The renter collects the equipment for this booking, so there is no delivery to record.");
        }
        if (!DISPATCHABLE.contains(BookingStatus.valueOf((String) booking.get("status")))) {
            throw new ConflictException("Delivery details can be recorded after payment and until the equipment is handed over.");
        }
        Instant now = Instant.now();
        Instant endTime = ((Timestamp) booking.get("end_time")).toInstant();
        if (request.expectedArrivalAt().isBefore(now.minus(ARRIVAL_GRACE)) || !request.expectedArrivalAt().isBefore(endTime)) {
            throw new BadRequestException("The expected arrival must be from now until the end of the rental.");
        }

        boolean firstDispatch = jdbcTemplate.update("""
                UPDATE booking_deliveries
                SET driver_name = ?, driver_phone = ?, vehicle_number = ?, expected_arrival_at = ?
                WHERE booking_id = ?
                """,
                request.driverName().trim(), request.driverPhone(), request.vehicleNumber(),
                Timestamp.from(request.expectedArrivalAt()), bookingId) == 0;
        if (firstDispatch) {
            jdbcTemplate.update("""
                    INSERT INTO booking_deliveries
                        (booking_id, driver_name, driver_phone, vehicle_number, expected_arrival_at, dispatched_by_user_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    bookingId, request.driverName().trim(), request.driverPhone(), request.vehicleNumber(),
                    Timestamp.from(request.expectedArrivalAt()), ownerUserId);
        }
        jdbcTemplate.update("""
                INSERT INTO audit_logs (id, actor_user_id, action, entity_name, entity_id, details_json)
                VALUES (?, ?, ?, 'bookings', ?, ?)
                """, UUID.randomUUID().toString(), ownerUserId,
                firstDispatch ? "DELIVERY_DISPATCHED" : "DELIVERY_UPDATED", bookingId,
                "{\"vehicleNumber\":\"" + request.vehicleNumber() + "\"}");
        events.publishEvent(NotificationEvent.of(NotificationType.DELIVERY_DISPATCHED, bookingId, Map.of(
                "updated", !firstDispatch,
                "driverName", request.driverName().trim(),
                "driverPhone", request.driverPhone(),
                "vehicleNumber", request.vehicleNumber(),
                "expectedArrivalAt", request.expectedArrivalAt())));
        return find(bookingId);
    }

    /** The recorded delivery, or null when the equipment has not been sent yet. */
    @Transactional(readOnly = true)
    public BookingDetailsResponse.Delivery find(String bookingId) {
        List<BookingDetailsResponse.Delivery> deliveries = jdbcTemplate.query("""
                        SELECT driver_name, driver_phone, vehicle_number, expected_arrival_at, dispatched_at, updated_at
                        FROM booking_deliveries WHERE booking_id = ?
                        """,
                (rs, row) -> new BookingDetailsResponse.Delivery(
                        rs.getString("driver_name"),
                        rs.getString("driver_phone"),
                        rs.getString("vehicle_number"),
                        rs.getTimestamp("expected_arrival_at").toInstant(),
                        rs.getTimestamp("dispatched_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()),
                bookingId);
        return deliveries.isEmpty() ? null : deliveries.get(0);
    }

    /** Where the owner delivers to, as the renter entered it when booking; null for pickup bookings. */
    @Transactional(readOnly = true)
    public DeliveryAddressRequest address(String bookingId) {
        String json = jdbcTemplate.queryForObject(
                "SELECT delivery_address_json FROM bookings WHERE id = ?", String.class, bookingId);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DeliveryAddressRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored delivery address for booking " + bookingId + " is unreadable.", exception);
        }
    }
}
