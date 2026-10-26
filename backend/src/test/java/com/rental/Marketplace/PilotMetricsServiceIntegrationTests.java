package com.rental.Marketplace;

import com.marketplace.admin.PilotMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PilotMetricsServiceIntegrationTests {

    private JdbcTemplate jdbcTemplate;
    private PilotMetricsService service;

    @BeforeEach
    void createSchemaAndService() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:pilot_metrics_" + UUID.randomUUID()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE bookings (id VARCHAR(36), created_at TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE payments (booking_id VARCHAR(36), amount DECIMAL(12,2), status VARCHAR(30))");
        jdbcTemplate.execute("""
                CREATE TABLE booking_status_history (
                    booking_id VARCHAR(36), to_status VARCHAR(30), created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE disputes (created_at TIMESTAMP, status VARCHAR(30))
                """);
        jdbcTemplate.execute("""
                CREATE TABLE owner_settlements (
                    gross_rental_amount DECIMAL(12,2),
                    platform_commission_amount DECIMAL(12,2),
                    created_at TIMESTAMP,
                    released_at TIMESTAMP,
                    status VARCHAR(30)
                )
                """);
        jdbcTemplate.execute("CREATE TABLE owner_profiles (verification_status VARCHAR(30))");
        jdbcTemplate.execute("CREATE TABLE equipment (status VARCHAR(30))");
        service = new PilotMetricsService(jdbcTemplate);
    }

    @Test
    void aggregatesPilotMetricsForTheSelectedUtcDateWindow() {
        jdbcTemplate.update("INSERT INTO bookings VALUES ('b1', '2026-10-16 10:00:00')");
        jdbcTemplate.update("INSERT INTO bookings VALUES ('b2', '2026-10-16 11:00:00')");
        jdbcTemplate.update("INSERT INTO payments VALUES ('b1', 1350.00, 'CAPTURED')");
        jdbcTemplate.update("""
                INSERT INTO booking_status_history VALUES ('b1', 'PAYMENT_PENDING', '2026-10-16 10:10:00')
                """);
        jdbcTemplate.update("INSERT INTO disputes VALUES ('2026-10-16 12:00:00', 'OPEN')");
        jdbcTemplate.update("INSERT INTO disputes VALUES ('2026-10-16 13:00:00', 'RESOLVED')");
        jdbcTemplate.update("""
                INSERT INTO owner_settlements
                    VALUES (1000.00, 120.00, '2026-10-16 14:00:00', '2026-10-16 15:00:00', 'RELEASED')
                """);
        jdbcTemplate.update("""
                INSERT INTO owner_settlements
                    VALUES (500.00, 60.00, '2026-10-15 14:00:00', NULL, 'PENDING')
                """);
        jdbcTemplate.update("INSERT INTO owner_profiles VALUES ('VERIFIED'), ('VERIFIED'), ('VERIFIED'), ('PENDING')");
        jdbcTemplate.update("INSERT INTO equipment VALUES ('ACTIVE'), ('DRAFT')");

        var metrics = service.getMetrics(
                LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-16"));

        assertThat(metrics.bookingRequests()).isEqualTo(2);
        assertThat(metrics.capturedBookings()).isEqualTo(1);
        assertThat(metrics.bookingConversionPercent()).isEqualByComparingTo("50.00");
        assertThat(metrics.capturedGrossAmount()).isEqualByComparingTo("1350.00");
        assertThat(metrics.averageOwnerResponseMinutes()).isEqualByComparingTo("10.00");
        assertThat(metrics.disputes()).isEqualTo(2);
        assertThat(metrics.openDisputes()).isEqualTo(1);
        assertThat(metrics.rentalGrossAmount()).isEqualByComparingTo("1000.00");
        assertThat(metrics.platformCommissionAmount()).isEqualByComparingTo("120.00");
        assertThat(metrics.takeRatePercent()).isEqualByComparingTo("12.00");
        assertThat(metrics.verifiedOwners()).isEqualTo(3);
        assertThat(metrics.activeListings()).isEqualTo(1);
        assertThat(metrics.pendingSettlements()).isEqualTo(1);
        assertThat(metrics.releasedSettlements()).isEqualTo(1);
    }

    @Test
    void returnsZeroPercentagesAndNoAverageWhenThereIsNoActivity() {
        var metrics = service.getMetrics(
                LocalDate.parse("2026-10-16"), LocalDate.parse("2026-10-16"));

        assertThat(metrics.bookingConversionPercent()).isEqualByComparingTo("0.00");
        assertThat(metrics.takeRatePercent()).isEqualByComparingTo("0.00");
        assertThat(metrics.averageOwnerResponseMinutes()).isNull();
        assertThat(metrics.openDisputes()).isZero();
    }
}
