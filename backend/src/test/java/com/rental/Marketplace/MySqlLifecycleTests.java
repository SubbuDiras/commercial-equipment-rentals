package com.rental.Marketplace;

import com.marketplace.booking.BookingCreateRequest;
import com.marketplace.booking.BookingDetailsResponse;
import com.marketplace.booking.BookingDetailsService;
import com.marketplace.booking.BookingService;
import com.marketplace.booking.BookingStatus;
import com.marketplace.booking.CancellationResponse;
import com.marketplace.booking.CancellationService;
import com.marketplace.booking.DeliveryOption;
import com.marketplace.booking.OwnerBookingSummary;
import com.marketplace.common.PageResponse;
import com.marketplace.dev.DevToolsService;
import com.marketplace.equipment.EquipmentCatalogueService;
import com.marketplace.owner.OwnerOnboardingService;
import com.marketplace.payment.FakeCheckoutService;
import com.marketplace.payment.PaymentService;
import com.marketplace.payment.SimulatePaymentRequest;
import com.marketplace.rental.AdminDisputeService;
import com.marketplace.rental.DamageClaimRequest;
import com.marketplace.rental.DisputeResolutionRequest;
import com.marketplace.rental.DisputeStatus;
import com.marketplace.rental.InspectionPhotoRequest;
import com.marketplace.rental.InspectionSubmissionRequest;
import com.marketplace.rental.InspectionType;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.review.ReviewRequest;
import com.marketplace.review.ReviewService;
import com.marketplace.storage.PrivateObjectStorage;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The booking lifecycle against real MySQL (Flyway schema, strict SQL mode) rather than the hand-made H2 tables the
 * other lifecycle tests use, so MySQL-specific SQL differences are caught. Real services end to end, with the fake
 * Razorpay gateway; only object storage and the mail transport are stubbed.
 */
@SpringBootTest(
        classes = com.marketplace.MarketplaceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class MySqlLifecycleTests {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4.0")
                    .withDatabaseName("marketplace_lifecycle")
                    .withUsername("marketplace_test")
                    .withPassword("marketplace_test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("payment.razorpay.fake", () -> "true");
        registry.add("notifications.enabled", () -> "true");
        // Keep the background sweeps out of the way; the tests run them explicitly.
        registry.add("rental.operations.sweep-interval-ms", () -> "3600000");
        registry.add("booking.expiry.sweep-interval-ms", () -> "3600000");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DevToolsService devTools;
    @Autowired private BookingService bookingService;
    @Autowired private BookingDetailsService bookingDetailsService;
    @Autowired private PaymentService paymentService;
    @Autowired private FakeCheckoutService fakeCheckout;
    @Autowired private RentalInspectionService inspections;
    @Autowired private CancellationService cancellations;
    @Autowired private AdminDisputeService adminDisputes;
    @Autowired private ReviewService reviews;
    @Autowired private EquipmentCatalogueService catalogue;
    @Autowired private OwnerOnboardingService onboarding;

    @MockBean private PrivateObjectStorage storage;
    @MockBean private JavaMailSender mailSender;
    @MockBean private JwtDecoder jwtDecoder;

    private Jwt owner;
    private Jwt renter;
    private String equipmentId;
    private String equipmentTitle;

    @BeforeEach
    void setUp() {
        when(storage.presignPut(anyString(), anyString(), anyLong())).thenAnswer(invocation ->
                new PrivateObjectStorage.PresignedUpload("http://storage.test/upload", Instant.now().plusSeconds(600)));
        when(storage.existsWithinLimit(anyString())).thenReturn(true);
        when(storage.presignGet(anyString(), anyBoolean())).thenReturn("http://storage.test/view");
        when(mailSender.createMimeMessage()).thenAnswer(invocation -> new MimeMessage((Session) null));

        owner = user("Omar", "Owner");
        renter = user("Eva", "Renter");
        DevToolsService.SeedResponse seeded = devTools.seedOwnerListing(owner);
        equipmentId = seeded.equipmentId();
        equipmentTitle = seeded.title();
    }

    @Test
    void fullRentalFromBookingToClosedReviews() {
        String bookingId = paidBooking(72);
        assertThat(onboarding.findMine(owner).status()).isEqualTo("VERIFIED");
        PageResponse<OwnerBookingSummary> upcoming = bookingService.listForOwner(
                owner, List.of(BookingStatus.CONFIRMED, BookingStatus.HANDOVER_PENDING), 0, 20);
        assertThat(upcoming.content()).extracting(OwnerBookingSummary::bookingId).containsExactly(bookingId);
        assertThat(upcoming.content().get(0).renterName()).isEqualTo("Eva Renter");
        assertThat(bookingService.listForRenter(renter, 0, 20).content().get(0).equipmentTitle()).isEqualTo(equipmentTitle);

        runToReturned(bookingId);
        BookingDetailsResponse returned = bookingDetailsService.get(bookingId, owner, List.of());
        assertThat(returned.inspections()).hasSize(2).allSatisfy(report -> assertThat(report.photos()).hasSize(4));
        assertThat(returned.disputeDeadline()).isAfter(Instant.now());
        // The booking page shows the equipment's photo and each party's profile photo, when they have one.
        assertThat(returned.photos().equipment()).isEqualTo("http://storage.test/view");
        assertThat(returned.photos().renter()).isNull();
        jdbcTemplate.update("UPDATE users SET profile_photo_key = 'users/r/profile.jpeg' WHERE keycloak_subject_id = ?",
                renter.getSubject());
        assertThat(bookingDetailsService.get(bookingId, owner, List.of()).photos())
                .extracting("equipment", "owner", "renter")
                .containsExactly("http://storage.test/view", null, "http://storage.test/view");

        inspections.approveCondition(bookingId, owner);
        devTools.runScheduledJobs();                   // deposit refund + held owner transfer
        devTools.shiftTime(bookingId, -1);             // past the transfer hold
        assertThat(devTools.runScheduledJobs().releasedSettlements()).isEqualTo(1);
        assertThat(status(bookingId)).isEqualTo("COMPLETED");

        reviews.submit(bookingId, renter, new ReviewRequest(4, "Solid machine."));
        assertThat(reviews.submit(bookingId, owner, new ReviewRequest(5, "On time.")).bookingStatus()).isEqualTo("CLOSED");
        var listing = catalogue.getPublicListing(equipmentId);
        assertThat(listing.averageRating()).isEqualByComparingTo("4.00");
        assertThat(listing.reviewCount()).isEqualTo(1);
        assertThat(reviews.equipmentReviews(equipmentId, 0, 10).content()).singleElement()
                .satisfies(review -> assertThat(review.reviewerName()).isEqualTo("Eva R."));
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
    }

    @Test
    void reviewWindowExpiryClosesCompletedBookings() {
        String bookingId = paidBooking(72);
        runToReturned(bookingId);
        inspections.approveCondition(bookingId, owner);
        devTools.runScheduledJobs();
        devTools.shiftTime(bookingId, -1);
        devTools.runScheduledJobs();
        assertThat(status(bookingId)).isEqualTo("COMPLETED");

        devTools.shiftTime(bookingId, -15 * 24);
        assertThat(devTools.runScheduledJobs().closedBookings()).isGreaterThanOrEqualTo(1);
        assertThat(status(bookingId)).isEqualTo("CLOSED");
    }

    @Test
    void cancellationsRefundByTierAndSettleOwnerCompensation() {
        String early = paidBooking(72);
        CancellationResponse full = cancellations.cancelByRenter(early, renter, "Plans changed.");
        assertThat(full.breakdown().policyTier()).isEqualTo("FULL_REFUND");
        assertThat(settlementStatus(early)).isEqualTo("CANCELLED");

        String late = paidBooking(10);
        CancellationResponse lateCancel = cancellations.cancelByRenter(late, renter, "Site closed.");
        assertThat(lateCancel.breakdown().policyTier()).isEqualTo("NO_RENTAL_REFUND");
        assertThat(lateCancel.breakdown().ownerCompensation()).isPositive();
        devTools.runScheduledJobs();
        devTools.shiftTime(late, -1);
        devTools.runScheduledJobs();
        assertThat(settlementStatus(late)).isEqualTo("RELEASED");
        assertThat(status(late)).isEqualTo("CANCELLED_BY_RENTER");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM refunds WHERE booking_id = ? AND refund_type = 'SECURITY_DEPOSIT'
                """, Integer.class, late)).isZero();

        String byOwner = paidBooking(30);
        assertThat(cancellations.quote(byOwner, owner).breakdown().platformCredit()).isEqualByComparingTo("500.00");
        cancellations.cancelByOwner(byOwner, owner, "Machine failed its check.");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT op.cancellation_penalty_count FROM owner_profiles op JOIN users u ON u.id = op.user_id
                WHERE u.keycloak_subject_id = ?
                """, Integer.class, owner.getSubject())).isEqualTo(1);
        assertThat(bookingDetailsService.get(byOwner, renter, List.of()).cancellation().breakdown().totalRefund())
                .isEqualByComparingTo(jdbcTemplate.queryForObject(
                        "SELECT total_payable_amount FROM bookings WHERE id = ?", BigDecimal.class, byOwner));
    }

    @Test
    void disputeIsQueuedWithEvidenceAndResolvedByAnAdmin() {
        String bookingId = paidBooking(72);
        runToReturned(bookingId);
        String disputeId = inspections.raiseDamageClaim(bookingId,
                new DamageClaimRequest("DAMAGE", new BigDecimal("500.00"), "Cracked housing."), owner);

        assertThat(adminDisputes.list(DisputeStatus.OPEN)).extracting(summary -> summary.disputeId()).contains(disputeId);
        assertThat(adminDisputes.get(disputeId).inspections()).hasSize(2);

        Jwt admin = user("Ada", "Admin");
        paymentService.resolveDispute(disputeId,
                new DisputeResolutionRequest(new BigDecimal("400.00"), "Partly supported."), admin);
        assertThat(status(bookingId)).isEqualTo("INSPECTION_PASSED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT amount FROM refunds WHERE booking_id = ? AND refund_type = 'DISPUTE_BALANCE'
                """, BigDecimal.class, bookingId)).isEqualByComparingTo(jdbcTemplate.queryForObject(
                "SELECT security_deposit_amount - 400 FROM bookings WHERE id = ?", BigDecimal.class, bookingId));
        assertThat(bookingDetailsService.get(bookingId, admin, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .viewerRole()).isEqualTo("ADMIN");
    }

    /** Books the seeded listing for two days, starting {@code startInHours} from now; the owner accepts; the renter pays. */
    private String paidBooking(int startInHours) {
        Instant start = Instant.now().plus(startInHours, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        String bookingId = bookingService.create(renter, new BookingCreateRequest(
                equipmentId, start, start.plus(2, ChronoUnit.DAYS), DeliveryOption.PICKUP, null)).bookingId();
        bookingService.accept(bookingId, owner);
        String orderId = paymentService.createOrder(bookingId, renter).razorpayOrderId();
        fakeCheckout.simulate(orderId, SimulatePaymentRequest.Outcome.SUCCESS, renter);
        assertThat(status(bookingId)).isEqualTo("CONFIRMED");
        return bookingId;
    }

    /** Time-travels a 72-hours-ahead booking through handover and return. */
    private void runToReturned(String bookingId) {
        devTools.shiftTime(bookingId, -73);
        inspect(bookingId, InspectionType.HANDOVER);
        devTools.shiftTime(bookingId, -49);
        inspect(bookingId, InspectionType.RETURN);
        assertThat(status(bookingId)).isEqualTo("RETURNED");
    }

    private void inspect(String bookingId, InspectionType type) {
        String ownerCode = inspections.issueOtp(bookingId, type, owner).code();
        String renterCode = inspections.issueOtp(bookingId, type, renter).code();
        List<InspectionPhotoRequest> photos = IntStream.range(0, 4)
                .mapToObj(index -> new InspectionPhotoRequest(
                        inspections.presignPhoto(bookingId, type, "image/jpeg", 1_000, renter).storageObjectKey(),
                        "CONDITION", null, null))
                .toList();
        inspections.submit(bookingId, type, new InspectionSubmissionRequest(new BigDecimal("100"), "FULL",
                Map.of("condition_acceptable", true), "Recorded.", ownerCode, renterCode, photos), owner);
    }

    private String status(String bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, bookingId);
    }

    private String settlementStatus(String bookingId) {
        return jdbcTemplate.queryForObject("SELECT status FROM owner_settlements WHERE booking_id = ?",
                String.class, bookingId);
    }

    private static Jwt user(String firstName, String lastName) {
        String subject = UUID.randomUUID().toString();
        return Jwt.withTokenValue("token-" + subject).header("alg", "none")
                .subject(subject)
                .claim("email", firstName.toLowerCase() + "-" + subject + "@example.com")
                .claim("given_name", firstName)
                .claim("family_name", lastName)
                .build();
    }
}
