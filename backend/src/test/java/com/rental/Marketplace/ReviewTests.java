package com.rental.Marketplace;

import com.marketplace.booking.BookingAction;
import com.marketplace.booking.BookingDetailsResponse;
import com.marketplace.booking.BookingDetailsService;
import com.marketplace.review.ReviewService;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static com.rental.Marketplace.RentalLifecycleSchema.BOOKING_ID;
import static com.rental.Marketplace.RentalLifecycleSchema.EQUIPMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class, properties = "payment.razorpay.fake=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private BookingDetailsService bookingDetailsService;

    @MockBean
    private PrivateObjectStorage privateObjectStorage;

    @BeforeEach
    void createSchema() {
        RentalLifecycleSchema.create(jdbcTemplate);
    }

    @Test
    void bothSidesReviewingClosesTheBooking() throws Exception {
        completeBooking(Instant.now().minusSeconds(3600));
        assertThat(details("phase4-renter").availableActions()).containsExactly(BookingAction.REVIEW);

        review(renter(), 5, "Machine in great shape.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerRole").value("RENTER"))
                .andExpect(jsonPath("$.reviewerName").value("Renter T."))
                .andExpect(jsonPath("$.bookingStatus").value("COMPLETED"));
        assertThat(details("phase4-renter").availableActions()).isEmpty();
        assertThat(details("phase4-owner").availableActions()).containsExactly(BookingAction.REVIEW);

        review(owner(), 4, "Returned on time.")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewerRole").value("OWNER"))
                .andExpect(jsonPath("$.bookingStatus").value("CLOSED"));

        assertThat(bookingStatus()).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT reviewee_user_id FROM reviews WHERE reviewer_user_id = ?
                """, String.class, RentalLifecycleSchema.OWNER_ID)).isEqualTo(RentalLifecycleSchema.RENTER_ID);
        BookingDetailsResponse closed = details("phase4-owner");
        assertThat(closed.reviews()).extracting(review -> review.reviewerRole()).containsExactly("RENTER", "OWNER");
        assertThat(closed.availableActions()).isEmpty();
    }

    @Test
    void reviewsAreLimitedToParticipantsOncePerSideOnCompletedBookings() throws Exception {
        review(renter(), 5, null).andExpect(status().isConflict());

        completeBooking(Instant.now().minusSeconds(3600));
        review(renter(), 6, null).andExpect(status().isBadRequest());
        review(SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token.subject("phase4-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_RENTER")), 3, null)
                .andExpect(status().isForbidden());
        review(renter(), 5, null).andExpect(status().isCreated());
        review(renter(), 1, "Changed my mind.").andExpect(status().isConflict());

        jdbcTemplate.update("UPDATE bookings SET status = 'CLOSED' WHERE id = ?", BOOKING_ID);
        review(owner(), 4, null).andExpect(status().isConflict());
    }

    @Test
    void reviewWindowExpiryClosesUnreviewedBookingsAndBlocksLateReviews() throws Exception {
        completeBooking(Instant.now().minusSeconds(15 * 86_400L));
        review(renter(), 5, null).andExpect(status().isConflict());
        assertThat(details("phase4-renter").availableActions()).isEmpty();

        assertThat(reviewService.closeExpiredReviewWindows()).isEqualTo(1);
        assertThat(bookingStatus()).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT reason_or_notes FROM booking_status_history WHERE booking_id = ? AND to_status = 'CLOSED'
                """, String.class, BOOKING_ID)).contains("14 days");

        jdbcTemplate.update("DELETE FROM booking_status_history");
        completeBooking(Instant.now().minusSeconds(13 * 86_400L));
        assertThat(reviewService.closeExpiredReviewWindows()).isZero();
        assertThat(bookingStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void equipmentReviewsArePublicAndShowOnlyRenterReviews() throws Exception {
        completeBooking(Instant.now().minusSeconds(3600));
        review(renter(), 4, "Solid breaker.").andExpect(status().isCreated());
        review(owner(), 2, "Late return.").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/equipment/{id}/reviews", EQUIPMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(4))
                .andExpect(jsonPath("$.content[0].comments").value("Solid breaker."))
                .andExpect(jsonPath("$.content[0].reviewerName").value("Renter T."));
        mockMvc.perform(get("/api/v1/equipment/{id}/reviews", "unknown-equipment"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/equipment/{id}/reviews", EQUIPMENT_ID).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    private void completeBooking(Instant completedAt) {
        jdbcTemplate.update("UPDATE bookings SET status = 'COMPLETED' WHERE id = ?", BOOKING_ID);
        jdbcTemplate.update("""
                INSERT INTO booking_status_history (id, booking_id, from_status, to_status, created_at)
                VALUES (?, ?, 'INSPECTION_PASSED', 'COMPLETED', ?)
                """, java.util.UUID.randomUUID().toString(), BOOKING_ID, Timestamp.from(completedAt));
    }

    private ResultActions review(RequestPostProcessor caller, int rating, String comments) throws Exception {
        String body = comments == null
                ? "{\"rating\":" + rating + "}"
                : "{\"rating\":" + rating + ",\"comments\":\"" + comments + "\"}";
        return mockMvc.perform(post("/api/v1/bookings/{id}/reviews", BOOKING_ID).with(caller)
                .contentType(APPLICATION_JSON).content(body));
    }

    private BookingDetailsResponse details(String subject) {
        return bookingDetailsService.get(BOOKING_ID,
                Jwt.withTokenValue("token").header("alg", "none").subject(subject).build(), List.of());
    }

    private String bookingStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, BOOKING_ID);
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
}
