package com.rental.Marketplace;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

/**
 * H2 copy of the rental, payment and settlement tables, seeded with one verified owner, one renter, one admin,
 * one equipment listing and one booking in {@code PAYMENT_PENDING} (rental window already started).
 */
final class RentalLifecycleSchema {

    static final String OWNER_ID = "phase4-owner-user";
    static final String RENTER_ID = "phase4-renter-user";
    static final String OWNER_PROFILE_ID = "phase4-owner-profile";
    static final String CATEGORY_ID = "phase4-category";
    static final String EQUIPMENT_ID = "phase4-equipment";
    static final String BOOKING_ID = "phase4-booking";

    private RentalLifecycleSchema() {
    }

    static void create(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS reviews");
        jdbcTemplate.execute("DROP TABLE IF EXISTS booking_cancellations");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspection_media");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspection_uploads");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspection_otps");
        jdbcTemplate.execute("DROP TABLE IF EXISTS rental_inspections");
        jdbcTemplate.execute("DROP TABLE IF EXISTS disputes");
        jdbcTemplate.execute("DROP TABLE IF EXISTS refunds");
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_settlements");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payment_webhook_events");
        jdbcTemplate.execute("DROP TABLE IF EXISTS payments");
        jdbcTemplate.execute("DROP TABLE IF EXISTS booking_status_history");
        jdbcTemplate.execute("DROP TABLE IF EXISTS audit_logs");
        jdbcTemplate.execute("DROP TABLE IF EXISTS bookings");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment");
        jdbcTemplate.execute("DROP TABLE IF EXISTS equipment_categories");
        jdbcTemplate.execute("DROP TABLE IF EXISTS owner_profiles");
        jdbcTemplate.execute("DROP TABLE IF EXISTS users");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    id CHAR(36) PRIMARY KEY,
                    keycloak_subject_id VARCHAR(64) NOT NULL UNIQUE,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    phone VARCHAR(20),
                    first_name VARCHAR(100) NOT NULL,
                    last_name VARCHAR(100) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_profiles (
                    id CHAR(36) PRIMARY KEY,
                    user_id CHAR(36) NOT NULL UNIQUE,
                    verification_status VARCHAR(30) NOT NULL,
                    razorpay_linked_account_id VARCHAR(64),
                    cancellation_penalty_count INT NOT NULL DEFAULT 0,
                    last_cancellation_penalty_at TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment_categories (
                    id CHAR(36) PRIMARY KEY,
                    slug VARCHAR(100) NOT NULL,
                    name VARCHAR(150) NOT NULL,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE equipment (
                    id CHAR(36) PRIMARY KEY,
                    owner_id CHAR(36) NOT NULL,
                    category_id CHAR(36) NOT NULL,
                    title VARCHAR(255) NOT NULL DEFAULT 'Phase four breaker',
                    status VARCHAR(30) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE bookings (
                    id CHAR(36) PRIMARY KEY,
                    booking_reference VARCHAR(32) NOT NULL,
                    renter_user_id CHAR(36) NOT NULL,
                    equipment_id CHAR(36) NOT NULL,
                    start_time TIMESTAMP(6) NOT NULL,
                    end_time TIMESTAMP(6) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    hold_expires_at TIMESTAMP(6),
                    total_payable_amount DECIMAL(12,2) NOT NULL,
                    base_rental_amount DECIMAL(12,2) NOT NULL,
                    owner_commission_amount DECIMAL(12,2),
                    security_deposit_amount DECIMAL(12,2) NOT NULL,
                    delivery_option VARCHAR(30) NOT NULL DEFAULT 'PICKUP',
                    delivery_charge DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    platform_fee_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    tax_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE payments (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL UNIQUE,
                    razorpay_order_id VARCHAR(64) NOT NULL UNIQUE,
                    razorpay_payment_id VARCHAR(64) UNIQUE,
                    razorpay_signature VARCHAR(255),
                    amount DECIMAL(12,2) NOT NULL,
                    currency VARCHAR(10) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    failure_reason CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    updated_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE payment_webhook_events (
                    id CHAR(36) PRIMARY KEY,
                    event_id VARCHAR(100) NOT NULL UNIQUE,
                    event_type VARCHAR(100) NOT NULL,
                    payload_hash VARCHAR(64) NOT NULL,
                    raw_payload CLOB NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    processed_at TIMESTAMP(6),
                    error_message CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_settlements (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL UNIQUE,
                    owner_profile_id CHAR(36) NOT NULL,
                    gross_rental_amount DECIMAL(12,2) NOT NULL,
                    platform_commission_amount DECIMAL(12,2) NOT NULL,
                    net_payout_amount DECIMAL(12,2) NOT NULL,
                    razorpay_transfer_id VARCHAR(64),
                    owner_damage_award_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
                    status VARCHAR(30) NOT NULL,
                    released_at TIMESTAMP(6),
                    release_after TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE booking_status_history (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    from_status VARCHAR(30),
                    to_status VARCHAR(30) NOT NULL,
                    changed_by_user_id CHAR(36),
                    reason_or_notes CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspections (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    inspection_type VARCHAR(30) NOT NULL,
                    inspector_user_id CHAR(36) NOT NULL,
                    hour_meter_reading DECIMAL(10,2),
                    fuel_or_battery_level VARCHAR(50),
                    checklist_json CLOB NOT NULL,
                    condition_summary CLOB,
                    otp_verified BOOLEAN NOT NULL,
                    inspected_at TIMESTAMP(6) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspection_media (
                    id CHAR(36) PRIMARY KEY,
                    inspection_id CHAR(36) NOT NULL,
                    storage_object_key VARCHAR(512) NOT NULL,
                    media_tag VARCHAR(50) NOT NULL,
                    geo_latitude DECIMAL(10,7),
                    geo_longitude DECIMAL(10,7),
                    captured_at TIMESTAMP(6) NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspection_otps (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    inspection_type VARCHAR(30) NOT NULL,
                    participant_user_id CHAR(36) NOT NULL,
                    code_hash CHAR(64) NOT NULL,
                    expires_at TIMESTAMP(6) NOT NULL,
                    verified_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE rental_inspection_uploads (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    inspection_type VARCHAR(30) NOT NULL,
                    uploaded_by_user_id CHAR(36) NOT NULL,
                    storage_object_key VARCHAR(512) NOT NULL UNIQUE,
                    expires_at TIMESTAMP(6) NOT NULL,
                    attached_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE disputes (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    raised_by_user_id CHAR(36) NOT NULL,
                    dispute_category VARCHAR(50) NOT NULL,
                    claim_amount DECIMAL(12,2) NOT NULL,
                    approved_deduction_amount DECIMAL(12,2) DEFAULT 0.00,
                    description CLOB NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    resolution_notes CLOB,
                    resolved_by_user_id CHAR(36),
                    resolved_at TIMESTAMP(6),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE refunds (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    payment_id CHAR(36) NOT NULL,
                    razorpay_refund_id VARCHAR(64) NOT NULL,
                    amount DECIMAL(12,2) NOT NULL,
                    refund_type VARCHAR(50) NOT NULL,
                    status VARCHAR(30) NOT NULL,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6),
                    UNIQUE (booking_id, refund_type),
                    UNIQUE (razorpay_refund_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE audit_logs (
                    id CHAR(36) PRIMARY KEY,
                    actor_user_id CHAR(36),
                    action VARCHAR(100) NOT NULL,
                    entity_name VARCHAR(100) NOT NULL,
                    entity_id VARCHAR(64) NOT NULL,
                    details_json CLOB,
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE reviews (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL,
                    reviewer_user_id CHAR(36) NOT NULL,
                    reviewee_user_id CHAR(36) NOT NULL,
                    equipment_id CHAR(36) NOT NULL,
                    rating INT NOT NULL CHECK (rating >= 1 AND rating <= 5),
                    comments CLOB,
                    is_published BOOLEAN NOT NULL DEFAULT TRUE,
                    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
                    UNIQUE (booking_id, reviewer_user_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE booking_cancellations (
                    id CHAR(36) PRIMARY KEY,
                    booking_id CHAR(36) NOT NULL UNIQUE,
                    cancelled_by_user_id CHAR(36) NOT NULL,
                    cancelled_by VARCHAR(10) NOT NULL,
                    reason CLOB NOT NULL,
                    policy_tier VARCHAR(30) NOT NULL,
                    hours_before_start DECIMAL(10,2) NOT NULL,
                    amount_paid DECIMAL(12,2) NOT NULL,
                    rental_charges DECIMAL(12,2) NOT NULL,
                    rental_refund DECIMAL(12,2) NOT NULL,
                    gateway_fee DECIMAL(12,2) NOT NULL,
                    deposit_refund DECIMAL(12,2) NOT NULL,
                    total_refund DECIMAL(12,2) NOT NULL,
                    owner_compensation DECIMAL(12,2) NOT NULL,
                    platform_credit DECIMAL(12,2) NOT NULL,
                    razorpay_refund_id VARCHAR(64),
                    created_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6)
                )
                """);

        jdbcTemplate.update("""
                INSERT INTO users (id, keycloak_subject_id, email, phone, first_name, last_name)
                VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)
                """,
                OWNER_ID, "phase4-owner", "phase4-owner@example.com", "9876543210", "Owner", "Test",
                RENTER_ID, "phase4-renter", "phase4-renter@example.com", "9876543211", "Renter", "Test",
                "phase4-admin-user", "phase4-admin", "phase4-admin@example.com", "9876543212", "Admin", "Test");
        jdbcTemplate.update("""
                INSERT INTO owner_profiles (id, user_id, verification_status, razorpay_linked_account_id)
                VALUES (?, ?, 'VERIFIED', NULL)
                """, OWNER_PROFILE_ID, OWNER_ID);
        jdbcTemplate.update("INSERT INTO equipment_categories (id, slug, name) VALUES (?, 'tools', 'Tools')", CATEGORY_ID);
        jdbcTemplate.update("INSERT INTO equipment (id, owner_id, category_id, status) VALUES (?, ?, ?, 'ACTIVE')",
                EQUIPMENT_ID, OWNER_PROFILE_ID, CATEGORY_ID);
        jdbcTemplate.update("""
                INSERT INTO bookings
                    (id, booking_reference, renter_user_id, equipment_id, start_time, end_time,
                     status, hold_expires_at, total_payable_amount, base_rental_amount,
                     owner_commission_amount, security_deposit_amount)
                VALUES (?, 'BKG-PHASE4-001', ?, ?, ?, ?, 'PAYMENT_PENDING', ?, 100.00, 80.00, 9.60, 20.00)
                """,
                BOOKING_ID, RENTER_ID, EQUIPMENT_ID,
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)),
                java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),
                java.sql.Timestamp.from(Instant.now().plusSeconds(1800)));
    }
}
