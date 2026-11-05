package com.rental.Marketplace;

import com.marketplace.booking.BookingStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against SQL writing a booking status that {@link BookingStatus#valueOf} cannot read back,
 * which turns every later read of that booking into a 500.
 */
class BookingStatusSqlConsistencyTests {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");

    private static final Pattern BOOKING_STATUS_UPDATE = Pattern.compile(
            "UPDATE\\s+bookings\\s+SET\\s+status\\s*=\\s*'([A-Z_]+)'", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS_HISTORY_INSERT = Pattern.compile(
            "INTO\\s+booking_status_history[^;]*?VALUES\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTED_CONSTANT = Pattern.compile("'([A-Z][A-Z_]+)'");

    @Test
    void everyBookingStatusWrittenBySqlExistsInEnum() throws IOException {
        Set<String> written = new TreeSet<>();
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                Matcher update = BOOKING_STATUS_UPDATE.matcher(source);
                while (update.find()) {
                    written.add(update.group(1));
                }
                Matcher history = STATUS_HISTORY_INSERT.matcher(source);
                while (history.find()) {
                    Matcher constant = QUOTED_CONSTANT.matcher(history.group(1));
                    while (constant.find()) {
                        written.add(constant.group(1));
                    }
                }
            }
        }

        Set<String> known = Arrays.stream(BookingStatus.values()).map(Enum::name).collect(Collectors.toSet());
        // Sanity check that the scan is still finding the settlement write that caused the original bug.
        assertThat(written).contains("COMPLETED");
        assertThat(known).containsAll(written);
    }
}
