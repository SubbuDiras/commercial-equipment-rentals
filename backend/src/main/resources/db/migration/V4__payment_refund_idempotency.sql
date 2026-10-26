ALTER TABLE refunds
    ADD CONSTRAINT uk_refund_booking_type UNIQUE (booking_id, refund_type);
