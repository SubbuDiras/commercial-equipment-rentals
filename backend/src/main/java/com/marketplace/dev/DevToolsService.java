package com.marketplace.dev;

import com.marketplace.booking.BookingService;
import com.marketplace.exception.BadRequestException;
import com.marketplace.exception.ConflictException;
import com.marketplace.exception.ResourceNotFoundException;
import com.marketplace.keycloak.KeycloakAdminClient;
import com.marketplace.keycloak.KeycloakAdminException;
import com.marketplace.payment.PaymentService;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.review.ReviewService;
import com.marketplace.storage.PrivateObjectStorage;
import com.marketplace.user.UserProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Shortcuts for local manual testing and the Playwright suite. Exists only under the dev and test profiles.
 */
@Service
@Profile({"dev", "test"})
public class DevToolsService {

    static final int MAX_SHIFT_HOURS = 24 * 365;

    /** Title keyword → bundled illustration under {@code dev/listing-photos/}, checked in this order. */
    private static final Map<String, String> SAMPLE_PHOTO_KEYWORDS = new LinkedHashMap<>();

    static {
        SAMPLE_PHOTO_KEYWORDS.put("breaker", "demolition-hammers");
        SAMPLE_PHOTO_KEYWORDS.put("hammer", "demolition-hammers");
        SAMPLE_PHOTO_KEYWORDS.put("compactor", "plate-compactors");
        SAMPLE_PHOTO_KEYWORDS.put("vibrator", "concrete-vibrators");
        SAMPLE_PHOTO_KEYWORDS.put("core", "core-cutters");
        SAMPLE_PHOTO_KEYWORDS.put("cutter", "core-cutters");
        SAMPLE_PHOTO_KEYWORDS.put("drill", "core-cutters");
        SAMPLE_PHOTO_KEYWORDS.put("generator", "portable-generators");
        SAMPLE_PHOTO_KEYWORDS.put("mixer", "small-construction");
    }

    /** What the owner-listing seed creates, in turn. Deposits stay at ₹2,000 or more for the dispute tests. */
    private static final List<SampleListing> SAMPLE_LISTINGS = List.of(
            new SampleListing("demolition-hammers", "demolition-hammers", "Bosch GSH 11 E demolition breaker", "Bosch", "GSH 11 E",
                    "1500 W demolition hammer with pointed and flat chisels.",
                    "{\"Power\":\"1500 W\",\"Weight\":\"11 kg\"}", 1000, 2000,
                    "Plot 1, HITEC City, Madhapur", "Hyderabad", "500081", 17.4474, 78.3762),
            new SampleListing("plate-compactors", "plate-compactors", "Wacker Neuson VP1550 plate compactor", "Wacker Neuson", "VP1550",
                    "Petrol forward plate compactor for soil, gravel and paver laying.",
                    "{\"Plate size\":\"50 x 58 cm\",\"Engine\":\"Honda GX160\"}", 1500, 3000,
                    "Gate 4, Chakan MIDC Phase II", "Pune", "410501", 18.7606, 73.8636),
            new SampleListing("core-cutters", "core-cutters", "Hilti DD 200 diamond core cutter", "Hilti", "DD 200",
                    "Stand-mounted wet core drill with 50-250 mm bits.",
                    "{\"Max bore\":\"250 mm\",\"Power\":\"2200 W\"}", 1800, 5000,
                    "Unit 12, Marol Industrial Estate, Andheri East", "Mumbai", "400093", 19.1136, 72.8697),
            new SampleListing("portable-generators", "portable-generators", "Kirloskar 5 kVA portable generator", "Kirloskar", "KG1-5000",
                    "Open-frame petrol generator with two 16 A sockets.",
                    "{\"Output\":\"5 kVA\",\"Fuel\":\"Petrol\"}", 1200, 4000,
                    "Shed 7, SIDCO Industrial Estate, Guindy", "Chennai", "600032", 13.0067, 80.2206),
            new SampleListing("concrete-vibrators", "concrete-vibrators", "Atlas Copco AX 48 needle vibrator", "Atlas Copco", "AX 48",
                    "Electric drive unit with 48 mm needle and 6 m flexible shaft.",
                    "{\"Needle\":\"48 mm\",\"Shaft length\":\"6 m\"}", 600, 2000,
                    "Plot 7, Peenya Industrial Area", "Bengaluru", "560058", 13.0285, 77.5197),
            new SampleListing("small-construction", "small-construction", "Universal 1-bag concrete mixer", "Universal", "UCM-1B",
                    "Tilting drum mixer with diesel engine, one bag per batch.",
                    "{\"Drum\":\"1 bag (50 kg cement)\",\"Engine\":\"Diesel 5 HP\"}", 900, 3000,
                    "B-41, Okhla Industrial Area Phase II", "Delhi", "110020", 28.5355, 77.2732),
            new SampleListing("demolition-hammers", "demolition-hammers-2", "Makita HM1810 heavy breaker", "Makita", "HM1810",
                    "30 kg class electric breaker for slab and road demolition.",
                    "{\"Power\":\"2000 W\",\"Weight\":\"31 kg\"}", 1400, 3000,
                    "Plot 22, GIDC Vatva", "Ahmedabad", "382445", 22.9667, 72.6333),
            new SampleListing("plate-compactors", "plate-compactors-2", "Dynapac LF 75 plate compactor", "Dynapac", "LF 75",
                    "Light forward plate compactor for trench and paving work.",
                    "{\"Plate size\":\"45 x 50 cm\",\"Engine\":\"Honda GX120\"}", 1300, 3000,
                    "Shed 3, Taratala Industrial Estate", "Kolkata", "700088", 22.5050, 88.3200),
            new SampleListing("core-cutters", "core-cutters-2", "Husqvarna DM 400 core drill", "Husqvarna", "DM 400",
                    "Rig-mounted core drill for openings up to 400 mm.",
                    "{\"Max bore\":\"400 mm\",\"Power\":\"3900 W\"}", 2200, 6000,
                    "E-17, Sitapura Industrial Area", "Jaipur", "302022", 26.7900, 75.8300),
            new SampleListing("portable-generators", "portable-generators-2", "Honda EU70is inverter generator", "Honda", "EU70is",
                    "Quiet inverter generator, safe for sensitive site electronics.",
                    "{\"Output\":\"7 kVA\",\"Fuel\":\"Petrol\"}", 1600, 5000,
                    "Kalamassery Industrial Development Area", "Kochi", "683104", 10.0500, 76.3200),
            new SampleListing("concrete-vibrators", "concrete-vibrators-2", "Wacker Neuson IRFU 45 needle vibrator", "Wacker Neuson", "IRFU 45",
                    "High-frequency internal vibrator with built-in converter.",
                    "{\"Needle\":\"45 mm\",\"Shaft length\":\"5 m\"}", 800, 2000,
                    "Plot 9, Talkatora Industrial Area", "Lucknow", "226011", 26.8300, 80.9000),
            new SampleListing("small-construction", "small-construction-2", "Greaves 10/7 concrete mixer", "Greaves", "10/7 CFT",
                    "Half-bag reversing drum mixer with hopper.",
                    "{\"Drum\":\"10/7 cft\",\"Engine\":\"Diesel 8 HP\"}", 1100, 3000,
                    "Sector 6, IMT Manesar", "Gurugram", "122052", 28.3600, 76.9400));

    private static final Logger log = LoggerFactory.getLogger(DevToolsService.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserProfileService userProfileService;
    private final KeycloakAdminClient keycloakAdminClient;
    private final BookingService bookingService;
    private final RentalInspectionService inspectionService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final PrivateObjectStorage objectStorage;

    public DevToolsService(
            JdbcTemplate jdbcTemplate,
            UserProfileService userProfileService,
            KeycloakAdminClient keycloakAdminClient,
            BookingService bookingService,
            RentalInspectionService inspectionService,
            PaymentService paymentService,
            ReviewService reviewService,
            PrivateObjectStorage objectStorage
    ) {
        this.objectStorage = objectStorage;
        this.jdbcTemplate = jdbcTemplate;
        this.userProfileService = userProfileService;
        this.keycloakAdminClient = keycloakAdminClient;
        this.bookingService = bookingService;
        this.inspectionService = inspectionService;
        this.paymentService = paymentService;
        this.reviewService = reviewService;
    }

    /**
     * Moves a booking through time: its start, end and hold times, plus everything dated relative to them (inspections,
     * status history, settlement release), shift by {@code hours}. A negative value makes the rental "happen" sooner.
     */
    @Transactional
    public ShiftTimeResponse shiftTime(String bookingId, int hours) {
        if (hours == 0 || Math.abs(hours) > MAX_SHIFT_HOURS) {
            throw new BadRequestException("Shift by a non-zero number of hours up to one year either way.");
        }
        int updated = jdbcTemplate.update("""
                UPDATE bookings
                SET start_time = TIMESTAMPADD(HOUR, ?, start_time),
                    end_time = TIMESTAMPADD(HOUR, ?, end_time),
                    hold_expires_at = CASE WHEN hold_expires_at IS NULL THEN NULL
                                           ELSE TIMESTAMPADD(HOUR, ?, hold_expires_at) END
                WHERE id = ?
                """, hours, hours, hours, bookingId);
        if (updated != 1) {
            throw new ResourceNotFoundException("Booking not found.");
        }
        jdbcTemplate.update("UPDATE rental_inspections SET inspected_at = TIMESTAMPADD(HOUR, ?, inspected_at) WHERE booking_id = ?",
                hours, bookingId);
        jdbcTemplate.update("UPDATE booking_status_history SET created_at = TIMESTAMPADD(HOUR, ?, created_at) WHERE booking_id = ?",
                hours, bookingId);
        jdbcTemplate.update("""
                UPDATE owner_settlements SET release_after = TIMESTAMPADD(HOUR, ?, release_after)
                WHERE booking_id = ? AND release_after IS NOT NULL
                """, hours, bookingId);
        return jdbcTemplate.queryForObject("""
                        SELECT id, status, start_time, end_time, hold_expires_at FROM bookings WHERE id = ?
                        """,
                (rs, rowNum) -> new ShiftTimeResponse(
                        rs.getString("id"),
                        rs.getString("status"),
                        rs.getTimestamp("start_time").toInstant(),
                        rs.getTimestamp("end_time").toInstant(),
                        rs.getTimestamp("hold_expires_at") == null ? null : rs.getTimestamp("hold_expires_at").toInstant()),
                bookingId);
    }

    /** Runs every scheduled sweep once, now, instead of waiting for the next minute. */
    public SweepResponse runScheduledJobs() {
        int expiredHolds = bookingService.expireHolds();
        int advancedRentals = inspectionService.advanceTimedStates();
        int releasedSettlements = paymentService.financialOperationsEnabled()
                ? paymentService.releaseMaturedSettlements()
                : 0;
        int closedBookings = reviewService.closeExpiredReviewWindows();
        return new SweepResponse(expiredHolds, advancedRentals, releasedSettlements, closedBookings);
    }

    /**
     * Makes the caller a verified owner (with a fake Razorpay payout account) and creates one approved listing, taking
     * the next of the sample equipment types. Each call adds a new listing, so tests can book without date clashes.
     */
    @Transactional
    public SeedResponse seedOwnerListing(Jwt jwt) {
        String userId = userProfileService.resolveOrCreate(jwt);
        List<String> profiles = jdbcTemplate.queryForList(
                "SELECT id FROM owner_profiles WHERE user_id = ?", String.class, userId);
        String ownerProfileId;
        if (profiles.isEmpty()) {
            ownerProfileId = UUID.randomUUID().toString();
            jdbcTemplate.update("""
                    INSERT INTO owner_profiles
                        (id, user_id, business_type, verification_status, verified_at, razorpay_linked_account_id)
                    VALUES (?, ?, 'INDIVIDUAL', 'VERIFIED', CURRENT_TIMESTAMP(6), ?)
                    """, ownerProfileId, userId, fakeAccountId());
        } else {
            ownerProfileId = profiles.get(0);
            jdbcTemplate.update("""
                    UPDATE owner_profiles
                    SET verification_status = 'VERIFIED',
                        verified_at = COALESCE(verified_at, CURRENT_TIMESTAMP(6)),
                        razorpay_linked_account_id = COALESCE(razorpay_linked_account_id, ?)
                    WHERE id = ?
                    """, fakeAccountId(), ownerProfileId);
        }

        // The least-used sample (earliest in the list on a tie), so the catalogue shows a mix rather than one repeated item.
        SampleListing sample = SAMPLE_LISTINGS.get(0);
        long fewest = Long.MAX_VALUE;
        for (SampleListing candidate : SAMPLE_LISTINGS) {
            Long used = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM equipment WHERE title LIKE ?", Long.class, candidate.title() + " · DEV-%");
            if (used != null && used < fewest) {
                fewest = used;
                sample = candidate;
            }
        }
        // Its own category if active, otherwise any active one.
        List<String> categories = jdbcTemplate.queryForList("""
                SELECT id FROM equipment_categories WHERE is_active = TRUE
                ORDER BY slug = ? DESC, name LIMIT 1
                """, String.class, sample.categorySlug());
        if (categories.isEmpty()) {
            throw new ConflictException("No active equipment category exists to list the seeded equipment in.");
        }
        String equipmentId = UUID.randomUUID().toString();
        // A short unique suffix: tests find their listing by title.
        String title = sample.title() + " · DEV-" + Long.toString(Instant.now().toEpochMilli(), 36).toUpperCase(Locale.ROOT);
        jdbcTemplate.update("""
                INSERT INTO equipment
                    (id, owner_id, category_id, title, description, manufacturer, model_number, condition_grade,
                     specifications_json, base_daily_rate, base_weekly_rate, security_deposit, minimum_rental_days,
                     delivery_supported, pickup_supported, delivery_charge_flat, address_line, city, postal_code,
                     latitude, longitude, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'GOOD', ?, ?, ?, ?, 1, TRUE, TRUE, 300.00, ?, ?, ?, ?, ?, 'ACTIVE')
                """, equipmentId, ownerProfileId, categories.get(0), title,
                "Seeded for local testing: " + sample.description(), sample.manufacturer(), sample.model(),
                sample.specificationsJson(), sample.dailyRate(), sample.dailyRate() * 6, sample.deposit(),
                sample.address(), sample.city(), sample.postalCode(), sample.latitude(), sample.longitude());

        boolean photoAttached = attachSamplePhoto(equipmentId, title, sample.categorySlug());

        boolean ownerRoleGranted;
        try {
            keycloakAdminClient.grantRealmRole(jwt.getSubject(), "ROLE_OWNER");
            ownerRoleGranted = true;
        } catch (KeycloakAdminException exception) {
            ownerRoleGranted = false;
        }
        return new SeedResponse(ownerProfileId, equipmentId, title, ownerRoleGranted, photoAttached);
    }

    /** Gives every listing that has no uploaded photo a sample picture that matches its title or category. */
    public SamplePhotosResponse addSamplePhotos() {
        List<Map<String, Object>> listings = jdbcTemplate.queryForList("""
                SELECT e.id, e.title, c.slug
                FROM equipment e
                JOIN equipment_categories c ON c.id = e.category_id
                WHERE NOT EXISTS (
                    SELECT 1 FROM equipment_images i WHERE i.equipment_id = e.id AND i.uploaded_at IS NOT NULL)
                """);
        int attached = 0;
        for (Map<String, Object> listing : listings) {
            if (attachSamplePhoto((String) listing.get("id"), (String) listing.get("title"), (String) listing.get("slug"))) {
                attached++;
            }
        }
        return new SamplePhotosResponse(listings.size(), attached);
    }

    /**
     * Uploads a bundled illustration as the listing's primary photo. Best-effort: returns false, and adds no image row,
     * when object storage cannot be reached.
     */
    private boolean attachSamplePhoto(String equipmentId, String title, String categorySlug) {
        String imageId = UUID.randomUUID().toString();
        String key = "equipment/" + equipmentId + "/images/" + imageId + ".jpg";
        try {
            objectStorage.put(key, "image/jpeg", samplePhoto(title, categorySlug));
        } catch (RuntimeException exception) {
            log.warn("Could not upload a sample photo for listing {}: {}", equipmentId, exception.getMessage());
            return false;
        }
        jdbcTemplate.update("""
                INSERT INTO equipment_images
                    (id, equipment_id, storage_object_key, is_primary, display_order, content_type, uploaded_at)
                VALUES (?, ?, ?, TRUE, 0, 'image/jpeg', CURRENT_TIMESTAMP(6))
                """, imageId, equipmentId, key);
        return true;
    }

    /**
     * Picks the illustration: a seeded sample's own picture, else by what the title names, then by category, then a
     * generic one.
     */
    static String samplePhotoName(String title, String categorySlug) {
        for (SampleListing sample : SAMPLE_LISTINGS) {
            if (title != null && title.startsWith(sample.title() + " · DEV-")) {
                return sample.photo();
            }
        }
        String lowerTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> keyword : SAMPLE_PHOTO_KEYWORDS.entrySet()) {
            if (lowerTitle.contains(keyword.getKey())) {
                return keyword.getValue();
            }
        }
        return SAMPLE_PHOTO_KEYWORDS.containsValue(categorySlug) ? categorySlug : "small-construction";
    }

    private static byte[] samplePhoto(String title, String categorySlug) {
        String path = "dev/listing-photos/" + samplePhotoName(title, categorySlug) + ".jpg";
        try (InputStream photo = new ClassPathResource(path).getInputStream()) {
            return photo.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Missing bundled sample photo " + path, exception);
        }
    }

    private static String fakeAccountId() {
        return "acc_FAKE" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public record ShiftTimeResponse(String bookingId, String status, Instant startTime, Instant endTime,
                                    Instant holdExpiresAt) {
    }

    public record SweepResponse(int expiredHolds, int advancedRentals, int releasedSettlements, int closedBookings) {
    }

    /**
     * @param ownerRoleGranted false when Keycloak could not be reached; assign ROLE_OWNER by hand then
     * @param photoAttached    false when object storage could not be reached; the listing then has no photo
     */
    public record SeedResponse(String ownerProfileId, String equipmentId, String title, boolean ownerRoleGranted,
                               boolean photoAttached) {
    }

    /** @param photo the bundled illustration under {@code dev/listing-photos/}, without the extension */
    private record SampleListing(String categorySlug, String photo, String title, String manufacturer, String model,
                                 String description, String specificationsJson, int dailyRate, int deposit,
                                 String address, String city, String postalCode, double latitude, double longitude) {
    }

    /** @param listingsWithoutPhotos how many listings had no photo; {@code photosAttached} of them now have one */
    public record SamplePhotosResponse(int listingsWithoutPhotos, int photosAttached) {
    }
}
