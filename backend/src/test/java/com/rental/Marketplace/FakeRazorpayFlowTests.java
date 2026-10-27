package com.rental.Marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketplace.payment.FakeRazorpayGateway;
import com.marketplace.payment.PaymentService;
import com.marketplace.payment.RazorpayGateway;
import com.marketplace.rental.InspectionPhotoRequest;
import com.marketplace.rental.InspectionSubmissionRequest;
import com.marketplace.rental.InspectionType;
import com.marketplace.rental.OtpChallengeResponse;
import com.marketplace.rental.RentalInspectionService;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.rental.Marketplace.RentalLifecycleSchema.BOOKING_ID;
import static com.rental.Marketplace.RentalLifecycleSchema.OWNER_PROFILE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class, properties = "payment.razorpay.fake=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FakeRazorpayFlowTests {

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
    private RentalInspectionService rentalInspectionService;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void createSchema() {
        RentalLifecycleSchema.create(jdbcTemplate);
        when(privateObjectStorage.presignPut(anyString(), anyString(), anyLong()))
                .thenAnswer(invocation -> new PrivateObjectStorage.PresignedUpload(
                        "https://private.test/upload", Instant.now().plusSeconds(600)));
        when(privateObjectStorage.existsWithinLimit(anyString())).thenReturn(true);
    }

    @Test
    void fakeGatewayReplacesRealOne() {
        assertThat(gateway).isInstanceOf(FakeRazorpayGateway.class);
    }

    @Test
    void paymentConfirmsBookingThenRefundAndSettlementCompleteIt() throws Exception {
        JsonNode order = createOrder();
        assertThat(order.path("provider").asText()).isEqualTo("FAKE");
        String orderId = order.path("razorpayOrderId").asText();
        assertThat(orderId).startsWith("order_FAKE");

        JsonNode checkout = simulate(orderId, "SUCCESS", renter());
        String razorpayPaymentId = checkout.path("razorpay_payment_id").asText();
        assertThat(razorpayPaymentId).startsWith("pay_FAKE");
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_webhook_events WHERE status = 'PROCESSED' AND event_type = 'payment.captured'",
                Integer.class)).isEqualTo(1);

        mockMvc.perform(post("/api/v1/payments/{paymentId}/verify", order.path("paymentId").asText())
                        .with(renter())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "razorpayOrderId", checkout.path("razorpay_order_id").asText(),
                                "razorpayPaymentId", razorpayPaymentId,
                                "razorpaySignature", checkout.path("razorpay_signature").asText()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM owner_settlements WHERE booking_id = ?",
                Integer.class, BOOKING_ID)).isEqualTo(1);

        completeInspection(InspectionType.HANDOVER);
        jdbcTemplate.update("UPDATE bookings SET end_time = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), BOOKING_ID);
        completeInspection(InspectionType.RETURN);
        rentalInspectionService.approveCondition(BOOKING_ID, jwt("phase4-owner"));
        jdbcTemplate.update("UPDATE owner_profiles SET razorpay_linked_account_id = 'acc_FAKEowner00001' WHERE id = ?",
                OWNER_PROFILE_ID);

        assertThat(paymentService.releaseMaturedSettlements()).isZero();
        String transferId = jdbcTemplate.queryForObject(
                "SELECT razorpay_transfer_id FROM owner_settlements WHERE booking_id = ?", String.class, BOOKING_ID);
        assertThat(transferId).startsWith("trf_FAKE");
        jdbcTemplate.update("UPDATE owner_settlements SET release_after = ? WHERE booking_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), BOOKING_ID);
        assertThat(paymentService.releaseMaturedSettlements()).isEqualTo(1);

        FakeRazorpayGateway fake = (FakeRazorpayGateway) gateway;
        assertThat(bookingStatus()).isEqualTo("COMPLETED");
        assertThat(fake.refundedInPaise(razorpayPaymentId)).isEqualTo(2_000);
        assertThat(fake.isTransferReleased(transferId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT razorpay_refund_id FROM refunds WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                """, String.class, BOOKING_ID)).startsWith("rfnd_FAKE");
    }

    @Test
    void failedPaymentCanBeRetriedAndOnlyTheBookingsRenterMaySimulate() throws Exception {
        String orderId = createOrder().path("razorpayOrderId").asText();

        mockMvc.perform(post("/api/v1/dev/payments/{orderId}/simulate", orderId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token -> token.subject("phase4-owner"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isForbidden());

        JsonNode failed = simulate(orderId, "FAILURE", renter());
        assertThat(failed.path("razorpay_signature").isNull()).isTrue();
        assertThat(failed.path("error_description").asText()).isNotBlank();
        assertThat(bookingStatus()).isEqualTo("PAYMENT_FAILED");

        simulate(orderId, "SUCCESS", renter());
        assertThat(bookingStatus()).isEqualTo("CONFIRMED");
    }

    private JsonNode createOrder() throws Exception {
        String body = mockMvc.perform(post("/api/v1/bookings/{bookingId}/payment-order", BOOKING_ID).with(renter()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private JsonNode simulate(String orderId, String outcome, RequestPostProcessor caller) throws Exception {
        String body = mockMvc.perform(post("/api/v1/dev/payments/{orderId}/simulate", orderId)
                        .with(caller)
                        .contentType(APPLICATION_JSON)
                        .content("{\"outcome\":\"" + outcome + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private void completeInspection(InspectionType type) {
        OtpChallengeResponse ownerOtp = rentalInspectionService.issueOtp(BOOKING_ID, type, jwt("phase4-owner"));
        OtpChallengeResponse renterOtp = rentalInspectionService.issueOtp(BOOKING_ID, type, jwt("phase4-renter"));
        List<InspectionPhotoRequest> photos = IntStream.range(0, 4)
                .mapToObj(index -> new InspectionPhotoRequest(rentalInspectionService.presignPhoto(
                        BOOKING_ID, type, "image/jpeg", 256_000, jwt("phase4-renter")).storageObjectKey(),
                        "CONDITION", null, null))
                .toList();
        rentalInspectionService.submit(BOOKING_ID, type, new InspectionSubmissionRequest(
                new BigDecimal("10"), "FULL", Map.of("condition_acceptable", true), "OK",
                ownerOtp.code(), renterOtp.code(), photos), jwt("phase4-owner"));
    }

    private String bookingStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, BOOKING_ID);
    }

    private static RequestPostProcessor renter() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("phase4-renter"))
                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"));
    }

    private static Jwt jwt(String subject) {
        return Jwt.withTokenValue(subject + "-token").header("alg", "none").subject(subject).build();
    }
}
