ALTER TABLE payments
    ADD CONSTRAINT uk_payment_booking UNIQUE (booking_id);

CREATE INDEX idx_payment_webhook_status
    ON payment_webhook_events (status, created_at);

CREATE INDEX idx_inspection_booking_type
    ON rental_inspections (booking_id, inspection_type, inspected_at);

CREATE TABLE rental_inspection_otps (
    id CHAR(36) NOT NULL PRIMARY KEY,
    booking_id CHAR(36) NOT NULL,
    inspection_type VARCHAR(30) NOT NULL,
    participant_user_id CHAR(36) NOT NULL,
    code_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    verified_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_inspection_otp_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_otp_user FOREIGN KEY (participant_user_id) REFERENCES users(id),
    INDEX idx_inspection_otp_lookup (booking_id, inspection_type, participant_user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE rental_inspection_uploads (
    id CHAR(36) NOT NULL PRIMARY KEY,
    booking_id CHAR(36) NOT NULL,
    inspection_type VARCHAR(30) NOT NULL,
    uploaded_by_user_id CHAR(36) NOT NULL,
    storage_object_key VARCHAR(512) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    attached_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_inspection_upload_object UNIQUE (storage_object_key),
    CONSTRAINT fk_inspection_upload_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_upload_user FOREIGN KEY (uploaded_by_user_id) REFERENCES users(id),
    INDEX idx_inspection_upload_lookup (booking_id, uploaded_by_user_id, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
