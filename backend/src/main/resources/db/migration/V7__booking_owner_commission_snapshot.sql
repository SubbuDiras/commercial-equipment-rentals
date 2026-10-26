ALTER TABLE bookings
    ADD COLUMN owner_commission_amount DECIMAL(12, 2) NULL AFTER platform_fee_amount;
