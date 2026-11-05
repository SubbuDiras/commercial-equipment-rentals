package com.rental.Marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.booking.BookingDetailsService;
import com.marketplace.booking.BookingAction;
import com.marketplace.payment.FakeRazorpayGateway;
import com.marketplace.payment.PaymentService;
import com.marketplace.payment.RazorpayGateway;
import com.marketplace.storage.PrivateObjectStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.rental.Marketplace.RentalLifecycleSchema.BOOKING_ID;
import static com.rental.Marketplace.RentalLifecycleSchema.OWNER_PROFILE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class, properties = "payment.razorpay.fake=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CancellationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RazorpayGateway gateway;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private BookingDetailsService bookingDetailsService;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void createSchema() {
        RentalLifecycleSchema.create(jdbcTemplate);
    }

    @Test
    void renterCancelsBeforePaymentWithoutMovingMoney() throws Exception {
        cancel("/api/v1/bookings/{id}/cancel", renter())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED_BY_RENTER"))
                .andExpect(jsonPath("$.razorpayRefundId").doesNotExist())
                .andExpect(jsonPath("$.breakdown.policyTier").value("NO_PAYMENT"))
                .andExpect(jsonPath("$.breakdown.totalRefund").value(0));

        assertThat(jdbcTemplate.queryForObject("SELECT hold_expires_at FROM bookings WHERE id = ?",
                Timestamp.class, BOOKING_ID)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM refunds", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT reason_or_notes FROM booking_status_history WHERE booking_id = ? AND to_status = 'CANCELLED_BY_RENTER'
                """, String.class, BOOKING_ID)).isEqualTo("Plans changed.");
    }

    @Test
    void earlyRenterCancellationRefundsRentalLessFeeAndClosesSettlement() throws Exception {
        String paymentId = payWithRentalStartingIn(72);

        mockMvc.perform(get("/api/v1/bookings/{id}/cancellation-quote", BOOKING_ID).with(renter()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.bookingStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.breakdown.policyTier").value("FULL_REFUND"))
                .andExpect(jsonPath("$.breakdown.totalRefund").value(98.40));
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");

        JsonNode cancelled = json(cancel("/api/v1/bookings/{id}/cancel", renter()).andExpect(status().isOk()));
        assertThat(cancelled.path("breakdown").path("gatewayFee").decimalValue()).isEqualByComparingTo("1.60");
        assertThat(cancelled.path("razorpayRefundId").asText()).startsWith("rfnd_FAKE");
        assertThat(fake().refundedInPaise(paymentId)).isEqualTo(9_840);
        assertThat(bookingStatus()).isEqualTo("CANCELLED_BY_RENTER");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM owner_settlements WHERE booking_id = ?",
                String.class, BOOKING_ID)).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT amount FROM refunds WHERE booking_id = ? AND refund_type = 'CANCELLATION'
                """, BigDecimal.class, BOOKING_ID)).isEqualByComparingTo("98.40");

        cancel("/api/v1/bookings/{id}/cancel", renter()).andExpect(status().isConflict());
        assertThat(fake().refundedInPaise(paymentId)).isEqualTo(9_840);
        assertThat(bookingDetailsService.get(BOOKING_ID, jwt("phase4-renter"), List.of()).cancellation()
                .breakdown().policyTier()).isEqualTo("FULL_REFUND");
    }

    @Test
    void lateRenterCancellationRefundsDepositAndPaysOwnerCompensationThroughSettlement() throws Exception {
        String paymentId = payWithRentalStartingIn(10);

        JsonNode cancelled = json(cancel("/api/v1/bookings/{id}/cancel", renter()).andExpect(status().isOk()));
        assertThat(cancelled.path("breakdown").path("policyTier").asText()).isEqualTo("NO_RENTAL_REFUND");
        assertThat(cancelled.path("breakdown").path("ownerCompensation").decimalValue()).isEqualByComparingTo("56.32");
        assertThat(fake().refundedInPaise(paymentId)).isEqualTo(2_000);

        jdbcTemplate.update("UPDATE owner_profiles SET razorpay_linked_account_id = 'acc_FAKEowner00001' WHERE id = ?",
                OWNER_PROFILE_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isZero();
        jdbcTemplate.update("UPDATE owner_settlements SET release_after = ? WHERE booking_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), BOOKING_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isEqualTo(1);

        String transferId = jdbcTemplate.queryForObject(
                "SELECT razorpay_transfer_id FROM owner_settlements WHERE booking_id = ?", String.class, BOOKING_ID);
        assertThat(fake().isTransferReleased(transferId)).isTrue();
        assertThat(bookingStatus()).isEqualTo("CANCELLED_BY_RENTER");
        assertThat(fake().refundedInPaise(paymentId)).isEqualTo(2_000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refunds WHERE refund_type = 'SECURITY_DEPOSIT'", Integer.class)).isZero();
    }

    @Test
    void ownerCancellationRefundsEverythingAndRecordsPenalty() throws Exception {
        String paymentId = payWithRentalStartingIn(30);

        mockMvc.perform(get("/api/v1/bookings/{id}/cancellation-quote", BOOKING_ID).with(owner()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.cancelledBy").value("OWNER"))
                .andExpect(jsonPath("$.breakdown.platformCredit").value(500.00));
        cancel("/api/v1/owner/bookings/{id}/cancel", owner())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingStatus").value("CANCELLED_BY_OWNER"))
                .andExpect(jsonPath("$.breakdown.totalRefund").value(100.00));

        assertThat(fake().refundedInPaise(paymentId)).isEqualTo(10_000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT cancellation_penalty_count FROM owner_profiles WHERE id = ?", Integer.class, OWNER_PROFILE_ID))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM audit_logs WHERE action = 'OWNER_CANCELLATION_PENALTY' AND entity_id = ?
                """, Integer.class, OWNER_PROFILE_ID)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM owner_settlements WHERE booking_id = ?",
                String.class, BOOKING_ID)).isEqualTo("CANCELLED");
    }

    @Test
    void cancellationIsLimitedToTheRightPartyAndToBeforeHandover() throws Exception {
        cancel("/api/v1/owner/bookings/{id}/cancel", renter()).andExpect(status().isForbidden());
        cancel("/api/v1/bookings/{id}/cancel", SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("phase4-owner"))
                .authorities(new SimpleGrantedAuthority("ROLE_RENTER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_ID).with(renter())
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        jdbcTemplate.update("UPDATE bookings SET status = 'REQUESTED' WHERE id = ?", BOOKING_ID);
        cancel("/api/v1/owner/bookings/{id}/cancel", owner()).andExpect(status().isConflict());

        jdbcTemplate.update("UPDATE bookings SET status = 'IN_PROGRESS' WHERE id = ?", BOOKING_ID);
        cancel("/api/v1/bookings/{id}/cancel", renter()).andExpect(status().isConflict());
        mockMvc.perform(get("/api/v1/bookings/{id}/cancellation-quote", BOOKING_ID).with(renter()))
                .andExpect(status().isConflict());
        assertThat(bookingDetailsService.get(BOOKING_ID, jwt("phase4-renter"), List.of()).availableActions())
                .doesNotContain(BookingAction.CANCEL);
    }

    private String payWithRentalStartingIn(int hours) throws Exception {
        Instant start = Instant.now().plusSeconds(hours * 3600L);
        jdbcTemplate.update("UPDATE bookings SET start_time = ?, end_time = ? WHERE id = ?",
                Timestamp.from(start), Timestamp.from(start.plusSeconds(2 * 86_400L)), BOOKING_ID);
        JsonNode order = json(mockMvc.perform(post("/api/v1/bookings/{id}/payment-order", BOOKING_ID).with(renter()))
                .andExpect(status().isOk()));
        JsonNode checkout = json(mockMvc.perform(post("/api/v1/dev/payments/{orderId}/simulate",
                        order.path("razorpayOrderId").asText())
                        .with(renter())
                        .contentType(APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk()));
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
        return checkout.path("razorpay_payment_id").asText();
    }

    private ResultActions cancel(String path, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post(path, BOOKING_ID).with(caller)
                .contentType(APPLICATION_JSON)
                .content("{\"reason\":\"Plans changed.\"}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bookingStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, BOOKING_ID);
    }

    private FakeRazorpayGateway fake() {
        return (FakeRazorpayGateway) gateway;
    }

    private static RequestPostProcessor renter() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("phase4-renter"))
                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"));
    }

    private static RequestPostProcessor owner() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("phase4-owner"))
                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"));
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue(subject + "-token").header("alg", "none").subject(subject).build();
    }
}
