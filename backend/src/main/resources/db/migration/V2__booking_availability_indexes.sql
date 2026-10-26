ALTER TABLE users
    MODIFY phone VARCHAR(20) NULL;

ALTER TABLE booking_status_history
    MODIFY changed_by_user_id CHAR(36) NULL;

CREATE INDEX idx_booking_availability
    ON bookings (equipment_id, status, start_time, end_time);

CREATE INDEX idx_booking_expiry
    ON bookings (status, hold_expires_at);
