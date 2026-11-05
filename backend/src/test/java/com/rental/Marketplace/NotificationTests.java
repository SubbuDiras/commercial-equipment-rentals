package com.rental.Marketplace;

import com.marketplace.booking.CancellationBreakdown;
import com.marketplace.notification.NotificationEvent;
import com.marketplace.notification.NotificationService;
import com.marketplace.notification.NotificationType;
import com.marketplace.rental.InspectionType;
import com.marketplace.rental.OtpChallengeResponse;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.storage.PrivateObjectStorage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.rental.Marketplace.RentalLifecycleSchema.BOOKING_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class,
        properties = {"payment.razorpay.fake=true", "notifications.enabled=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RentalInspectionService rentalInspectionService;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private ITemplateEngine templateEngine;

    @MockBean
    private JavaMailSender mailSender;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void setUp() {
        RentalLifecycleSchema.create(jdbcTemplate);
        reset(mailSender);
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));
    }

    @Test
    void paymentEmailsBothPartiesAfterCommit() throws Exception {
        pay();

        List<MimeMessage> sent = sentMessages();
        assertThat(sent).extracting(NotificationTests::recipient)
                .containsExactlyInAnyOrder("phase4-renter@example.com", "phase4-owner@example.com");
        MimeMessage renterMail = sent.stream()
                .filter(message -> recipient(message).equals("phase4-renter@example.com")).findFirst().orElseThrow();
        assertThat(renterMail.getSubject()).isEqualTo("Payment received - booking BKG-PHASE4-001 confirmed");
        assertThat(body(renterMail)).contains("Hi Renter,", "100.00", "Phase four breaker");
    }

    @Test
    void inspectionCodeIsEmailedToTheCounterparty() throws Exception {
        jdbcTemplate.update("UPDATE bookings SET status = 'CONFIRMED' WHERE id = ?", BOOKING_ID);

        OtpChallengeResponse otp = rentalInspectionService.issueOtp(BOOKING_ID, InspectionType.HANDOVER, jwt("phase4-owner"));

        assertThat(otp.emailedToCounterparty()).isTrue();
        assertThat(otp.code()).as("the test profile still returns the code").isNotNull();
        MimeMessage mail = sentMessages().get(0);
        assertThat(recipient(mail)).isEqualTo("phase4-renter@example.com");
        assertThat(mail.getSubject()).isEqualTo("Your handover code for booking BKG-PHASE4-001");
        assertThat(body(mail)).contains(otp.code(), ">Owner<", "handover");
    }

    @Test
    void rolledBackTransactionSendsNothing() {
        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            events.publishEvent(NotificationEvent.of(NotificationType.BOOKING_REQUESTED, BOOKING_ID));
            throw new IllegalStateException("simulated failure after publishing");
        })).isInstanceOf(IllegalStateException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void mailFailureDoesNotUndoTheBookingChange() throws Exception {
        doThrow(new MailSendException("SMTP down")).when(mailSender).send(any(MimeMessage.class));

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", BOOKING_ID).with(renter())
                        .contentType(APPLICATION_JSON).content("{\"reason\":\"Plans changed.\"}"))
                .andExpect(status().isOk());

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, BOOKING_ID))
                .isEqualTo("CANCELLED_BY_RENTER");
    }

    @Test
    void everyTemplateRendersForBothRoles() {
        NotificationService.Recipient renter =
                new NotificationService.Recipient("r", "renter@example.com", "Rajesh", "RENTER");
        NotificationService.Recipient owner =
                new NotificationService.Recipient("o", "owner@example.com", "Suresh", "OWNER");
        NotificationService.BookingContext booking = new NotificationService.BookingContext(
                "BKG-1", "Breaker", "01 Nov 2026, 09:00 IST", "03 Nov 2026, 18:00 IST", renter, owner);
        CancellationBreakdown breakdown = new CancellationBreakdown("OWNER", "OWNER_CANCELLED",
                new BigDecimal("30.00"), "INR", new BigDecimal("100.00"), new BigDecimal("80.00"),
                new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("20.00"), new BigDecimal("100.00"),
                new BigDecimal("12.00"), new BigDecimal("500.00"), "Draft policy.");
        Map<String, Object> model = Map.ofEntries(
                Map.entry("appUrl", "http://localhost:4200"), Map.entry("bookingUrl", "http://localhost:4200/b"),
                Map.entry("ownerProfileUrl", "http://localhost:4200/o"), Map.entry("listingUrl", "http://x/l"),
                Map.entry("equipmentTitle", "Breaker"), Map.entry("booking", booking),
                Map.entry("reason", "Because."), Map.entry("amount", new BigDecimal("100.00")),
                Map.entry("breakdown", breakdown), Map.entry("code", "123456"), Map.entry("inspection", "handover"),
                Map.entry("requesterName", "Suresh"), Map.entry("category", "DAMAGE"),
                Map.entry("claimAmount", new BigDecimal("5.00")), Map.entry("description", "Dent."),
                Map.entry("approvedDeduction", new BigDecimal("5.00")),
                Map.entry("refundAmount", new BigDecimal("15.00")), Map.entry("resolutionNotes", "Agreed."),
                Map.entry("reviewWindowDays", 14));

        for (NotificationType type : NotificationType.values()) {
            for (NotificationService.Recipient recipient : List.of(renter, owner)) {
                Context context = new Context();
                context.setVariables(model);
                context.setVariable("recipient", recipient);
                String html = templateEngine.process(type.template(), context);
                assertThat(html).as(type.name()).contains("Hi " + recipient.name() + ",", "automated message");
            }
        }
    }

    private void pay() throws Exception {
        jdbcTemplate.update("UPDATE bookings SET start_time = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(72 * 3600L)), BOOKING_ID);
        String order = mockMvc.perform(post("/api/v1/bookings/{id}/payment-order", BOOKING_ID).with(renter()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String orderId = order.replaceAll(".*\"razorpayOrderId\":\"([^\"]+)\".*", "$1");
        clearInvocations(mailSender);
        mockMvc.perform(post("/api/v1/dev/payments/{orderId}/simulate", orderId).with(renter())
                        .contentType(APPLICATION_JSON).content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk());
    }

    private List<MimeMessage> sentMessages() {
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues();
    }

    private static String recipient(MimeMessage message) {
        try {
            return message.getAllRecipients()[0].toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String body(MimeMessage message) throws Exception {
        return (String) message.getContent();
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
