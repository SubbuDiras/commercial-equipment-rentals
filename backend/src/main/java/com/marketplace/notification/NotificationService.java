package com.marketplace.notification;

import jakarta.mail.internet.MimeMessage;
import com.marketplace.review.ReviewProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional emails. Runs after the publishing transaction commits, so a mail failure can never roll back
 * a booking; failures are logged and swallowed.
 */
@Service
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DISPLAY_TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'IST'").withZone(ZoneId.of("Asia/Kolkata"));

    private final JdbcTemplate jdbcTemplate;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final ITemplateEngine templateEngine;
    private final NotificationProperties properties;
    private final ReviewProperties reviewProperties;

    public NotificationService(
            JdbcTemplate jdbcTemplate,
            ObjectProvider<JavaMailSender> mailSender,
            ITemplateEngine templateEngine,
            NotificationProperties properties,
            ReviewProperties reviewProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.reviewProperties = reviewProperties;
    }

    public boolean enabled() {
        return properties.enabled() && mailSender.getIfAvailable() != null;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEvent(NotificationEvent event) {
        if (!enabled()) {
            return;
        }
        try {
            for (Mail mail : compose(event)) {
                send(mail);
            }
        } catch (RuntimeException exception) {
            log.warn("Notification {} for {} was not sent: {}", event.type(), event.subjectId(),
                    exception.getMessage(), exception);
        }
    }

    private List<Mail> compose(NotificationEvent event) {
        Map<String, Object> model = new HashMap<>(event.details());
        model.put("appUrl", properties.appBaseUrl());
        model.put("reviewWindowDays", reviewProperties.windowDays());
        return switch (event.type()) {
            case KYC_SUBMITTED, KYC_APPROVED, KYC_REJECTED -> {
                Recipient owner = ownerOfProfile(event.subjectId());
                model.put("ownerProfileUrl", properties.appBaseUrl() + "/owner/onboarding");
                yield List.of(mail(event, owner, model, Map.of()));
            }
            case LISTING_APPROVED, LISTING_REJECTED -> {
                ListingContext listing = listing(event.subjectId());
                model.put("equipmentTitle", listing.title());
                model.put("listingUrl", properties.appBaseUrl() + "/equipment/" + event.subjectId());
                yield List.of(mail(event, listing.owner(), model, Map.of("{title}", listing.title())));
            }
            default -> {
                BookingContext booking = booking(event.subjectId());
                model.put("booking", booking);
                model.put("bookingUrl", properties.appBaseUrl() + "/bookings/" + event.subjectId());
                Map<String, String> subjectValues = new HashMap<>(Map.of(
                        "{ref}", booking.reference(), "{title}", booking.equipmentTitle()));
                yield switch (event.type()) {
                    case BOOKING_REQUESTED -> List.of(mail(event, booking.owner(), model, subjectValues));
                    case BOOKING_ACCEPTED, BOOKING_REJECTED -> List.of(mail(event, booking.renter(), model, subjectValues));
                    case DELIVERY_DISPATCHED -> {
                        model.put("expectedArrival", DISPLAY_TIME.format((Instant) event.details().get("expectedArrivalAt")));
                        yield List.of(mail(event, booking.renter(), model, subjectValues));
                    }
                    case INSPECTION_OTP -> {
                        // The code goes to the counterpart, who reads it back in person to prove both are present.
                        String requestedBy = String.valueOf(event.details().get("requestedByUserId"));
                        boolean ownerRequested = requestedBy.equals(booking.owner().userId());
                        Recipient requester = ownerRequested ? booking.owner() : booking.renter();
                        Recipient counterpart = ownerRequested ? booking.renter() : booking.owner();
                        String inspection = String.valueOf(event.details().get("inspectionType")).toLowerCase();
                        model.put("inspection", inspection);
                        model.put("requesterName", requester.name());
                        subjectValues.put("{inspection}", inspection);
                        yield List.of(mail(event, counterpart, model, subjectValues));
                    }
                    default -> List.of(
                            mail(event, booking.renter(), model, subjectValues),
                            mail(event, booking.owner(), model, subjectValues));
                };
            }
        };
    }

    private Mail mail(NotificationEvent event, Recipient recipient, Map<String, Object> model,
                      Map<String, String> subjectValues) {
        Context context = new Context();
        context.setVariables(model);
        context.setVariable("recipient", recipient);
        String subject = event.type().subject();
        for (Map.Entry<String, String> value : subjectValues.entrySet()) {
            subject = subject.replace(value.getKey(), value.getValue());
        }
        return new Mail(recipient.email(), subject, templateEngine.process(event.type().template(), context));
    }

    private void send(Mail mail) {
        try {
            JavaMailSender sender = mailSender.getObject();
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.html(), true);
            sender.send(message);
        } catch (Exception exception) {
            log.warn("Email \"{}\" to {} failed: {}", mail.subject(), mail.to(), exception.getMessage());
        }
    }

    private Recipient ownerOfProfile(String ownerProfileId) {
        return jdbcTemplate.queryForObject("""
                        SELECT u.id, u.email, u.first_name FROM owner_profiles op JOIN users u ON u.id = op.user_id
                        WHERE op.id = ?
                        """,
                (rs, rowNum) -> recipient(rs, "", "OWNER"), ownerProfileId);
    }

    private ListingContext listing(String equipmentId) {
        return jdbcTemplate.queryForObject("""
                        SELECT e.title, u.id, u.email, u.first_name
                        FROM equipment e
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN users u ON u.id = op.user_id
                        WHERE e.id = ?
                        """,
                (rs, rowNum) -> new ListingContext(rs.getString("title"), recipient(rs, "", "OWNER")), equipmentId);
    }

    private BookingContext booking(String bookingId) {
        return jdbcTemplate.queryForObject("""
                        SELECT b.booking_reference, b.start_time, b.end_time, e.title,
                               ru.id AS renter_id, ru.email AS renter_email, ru.first_name AS renter_first_name,
                               ou.id AS owner_id, ou.email AS owner_email, ou.first_name AS owner_first_name
                        FROM bookings b
                        JOIN equipment e ON e.id = b.equipment_id
                        JOIN owner_profiles op ON op.id = e.owner_id
                        JOIN users ou ON ou.id = op.user_id
                        JOIN users ru ON ru.id = b.renter_user_id
                        WHERE b.id = ?
                        """,
                (rs, rowNum) -> new BookingContext(
                        rs.getString("booking_reference"),
                        rs.getString("title"),
                        display(rs.getTimestamp("start_time").toInstant()),
                        display(rs.getTimestamp("end_time").toInstant()),
                        recipient(rs, "renter_", "RENTER"),
                        recipient(rs, "owner_", "OWNER")),
                bookingId);
    }

    private static Recipient recipient(ResultSet rs, String prefix, String role) throws SQLException {
        String idColumn = prefix.isEmpty() ? "id" : prefix + "id";
        return new Recipient(rs.getString(idColumn), rs.getString(prefix + "email"),
                rs.getString(prefix + "first_name"), role);
    }

    private static String display(Instant instant) {
        return DISPLAY_TIME.format(instant);
    }

    /** @param role {@code RENTER} or {@code OWNER}, so one template can address each side differently */
    public record Recipient(String userId, String email, String name, String role) {
    }

    public record BookingContext(
            String reference,
            String equipmentTitle,
            String startTime,
            String endTime,
            Recipient renter,
            Recipient owner
    ) {
    }

    private record ListingContext(String title, Recipient owner) {
    }

    private record Mail(String to, String subject, String html) {
    }
}
