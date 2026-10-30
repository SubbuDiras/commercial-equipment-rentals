-- Owner delivery: once the equipment leaves for the renter's site, the owner records who is bringing it and when it
-- should arrive. One row per booking; the owner may correct it until the handover.
CREATE TABLE booking_deliveries (
    booking_id CHAR(36) NOT NULL PRIMARY KEY,
    driver_name VARCHAR(100) NOT NULL,
    driver_phone VARCHAR(20) NOT NULL,
    vehicle_number VARCHAR(20) NOT NULL,
    expected_arrival_at TIMESTAMP(6) NOT NULL,
    dispatched_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    dispatched_by_user_id CHAR(36) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_delivery_booking FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_delivery_user FOREIGN KEY (dispatched_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
