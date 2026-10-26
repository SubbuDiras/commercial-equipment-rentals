package com.marketplace.admin;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/pilot-metrics")
public class PilotMetricsController {

    private final PilotMetricsService pilotMetricsService;

    public PilotMetricsController(PilotMetricsService pilotMetricsService) {
        this.pilotMetricsService = pilotMetricsService;
    }

    @GetMapping
    public PilotMetricsResponse getMetrics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return pilotMetricsService.getMetrics(from, to);
    }
}
