package com.marketplace.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/equipment")
@Validated
public class EquipmentAvailabilityController {

    private final BookingService bookingService;

    public EquipmentAvailabilityController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{equipmentId}/availability")
    public EquipmentAvailabilityResponse availability(
            @PathVariable @NotBlank String equipmentId,
            @RequestParam @NotNull Instant startTime,
            @RequestParam @NotNull Instant endTime
    ) {
        boolean available = bookingService.isAvailable(equipmentId, startTime, endTime);
        return new EquipmentAvailabilityResponse(equipmentId, startTime, endTime, available);
    }
}
