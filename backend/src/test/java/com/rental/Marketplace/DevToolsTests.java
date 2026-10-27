package com.rental.Marketplace;

import com.marketplace.dev.DevToolsController;
import com.marketplace.dev.DevToolsService;
import com.marketplace.storage.PrivateObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.rental.Marketplace.RentalLifecycleSchema.BOOKING_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class, properties = "payment.razorpay.fake=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DevToolsTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void createSchema() {
        RentalLifecycleSchema.create(jdbcTemplate);
    }

    @Test
    void shiftTimeMovesTheBookingAndEverythingDatedFromIt() throws Exception {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant inspected = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("UPDATE bookings SET start_time = ?, end_time = ? WHERE id = ?",
                Timestamp.from(start), Timestamp.from(start.plus(2, ChronoUnit.DAYS)), BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO rental_inspections
                    (id, booking_id, inspection_type, inspector_user_id, checklist_json, otp_verified, inspected_at)
                VALUES ('dev-inspection', ?, 'RETURN', 'x', '{}', TRUE, ?)
                """, BOOKING_ID, Timestamp.from(inspected));

        mockMvc.perform(post("/api/v1/dev/bookings/{id}/shift-time", BOOKING_ID).param("hours", "-48").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(BOOKING_ID));

        assertThat(timestamp("SELECT start_time FROM bookings WHERE id = ?")).isEqualTo(start.minus(48, ChronoUnit.HOURS));
        assertThat(timestamp("SELECT end_time FROM bookings WHERE id = ?"))
                .isEqualTo(start.plus(2, ChronoUnit.DAYS).minus(48, ChronoUnit.HOURS));
        assertThat(timestamp("SELECT inspected_at FROM rental_inspections WHERE booking_id = ?"))
                .isEqualTo(inspected.minus(48, ChronoUnit.HOURS));
    }

    @Test
    void rejectsUnknownBookingsAndUnreasonableShifts() throws Exception {
        mockMvc.perform(post("/api/v1/dev/bookings/{id}/shift-time", "missing").param("hours", "-1").with(user()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/dev/bookings/{id}/shift-time", BOOKING_ID).param("hours", "0").with(user()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/dev/bookings/{id}/shift-time", BOOKING_ID).param("hours", "-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void schedulerRunExpiresLapsedHoldsImmediately() throws Exception {
        jdbcTemplate.update("UPDATE bookings SET hold_expires_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), BOOKING_ID);

        mockMvc.perform(post("/api/v1/dev/scheduler/run").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiredHolds").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, BOOKING_ID))
                .isEqualTo("HOLD_EXPIRED");
    }

    @Test
    void devToolsDoNotExistUnderTheProdProfile() {
        new ApplicationContextRunner()
                .withUserConfiguration(DevToolsController.class, DevToolsService.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DevToolsController.class);
                    assertThat(context).doesNotHaveBean(DevToolsService.class);
                });
    }

    private Instant timestamp(String sql) {
        return jdbcTemplate.queryForObject(sql, Timestamp.class, BOOKING_ID).toInstant();
    }

    private static RequestPostProcessor user() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("phase4-renter"))
                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"));
    }
}
