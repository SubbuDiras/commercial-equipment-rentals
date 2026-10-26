ALTER TABLE owner_settlements
    ADD COLUMN release_after TIMESTAMP(6) NULL,
    ADD COLUMN owner_damage_award_amount DECIMAL(12, 2) NOT NULL DEFAULT 0.00;

UPDATE owner_settlements s
JOIN bookings b ON b.id = s.booking_id
SET s.release_after = COALESCE(
        (SELECT MAX(ri.inspected_at)
         FROM rental_inspections ri
         WHERE ri.booking_id = s.booking_id AND ri.inspection_type = 'RETURN'),
        b.end_time
    ) + INTERVAL 24 HOUR
WHERE s.razorpay_transfer_id IS NOT NULL
  AND s.release_after IS NULL;
