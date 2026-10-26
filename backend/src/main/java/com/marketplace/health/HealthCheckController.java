package com.marketplace.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & Diagnostics", description = "Public health verification endpoints")
public class HealthCheckController {

    private final Instant startupTime = Instant.now();

    @GetMapping
    @Operation(summary = "Check backend service health", description = "Returns active service status, current UTC timestamp, and API version.")
    public ResponseEntity<Map<String, Object>> getHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "marketplace-backend",
                "version", "0.0.1-SNAPSHOT",
                "timestamp", Instant.now(),
                "startedAt", startupTime
        ));
    }
}
