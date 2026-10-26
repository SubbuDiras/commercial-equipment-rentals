package com.rental.Marketplace;

import com.marketplace.admin.PilotMetricsService;
import com.marketplace.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PilotMetricsServiceTests {

    @Mock
    private JdbcTemplate jdbcTemplate;
    private PilotMetricsService service;

    @BeforeEach
    void createService() {
        service = new PilotMetricsService(jdbcTemplate);
    }

    @Test
    void rejectsInvertedDateRangeBeforeQueryingDatabase() {
        assertThatThrownBy(() -> service.getMetrics(
                LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-01")))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsDateRangesLongerThanOneYearBeforeQueryingDatabase() {
        assertThatThrownBy(() -> service.getMetrics(
                LocalDate.parse("2025-10-01"), LocalDate.parse("2026-10-02")))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(jdbcTemplate);
    }

}
