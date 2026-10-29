package com.marketplace.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local testing shortcuts. Registered only under the dev and test profiles, so these URLs do not exist in prod.
 * Any signed-in user may call them.
 */
@RestController
@RequestMapping("/api/v1/dev")
@Profile({"dev", "test"})
public class DevToolsController {

    private final DevToolsService devToolsService;

    public DevToolsController(DevToolsService devToolsService) {
        this.devToolsService = devToolsService;
    }

    /** e.g. {@code ?hours=-48} makes the rental start two days earlier, so handover is available now. */
    @PostMapping("/bookings/{bookingId}/shift-time")
    public DevToolsService.ShiftTimeResponse shiftTime(@PathVariable String bookingId, @RequestParam int hours) {
        return devToolsService.shiftTime(bookingId, hours);
    }

    @PostMapping("/scheduler/run")
    public DevToolsService.SweepResponse runScheduledJobs() {
        return devToolsService.runScheduledJobs();
    }

    @PostMapping("/seed/owner-listing")
    public DevToolsService.SeedResponse seedOwnerListing(@AuthenticationPrincipal Jwt jwt) {
        return devToolsService.seedOwnerListing(jwt);
    }

    /** Attaches a matching sample picture to every listing that has no photo yet. */
    @PostMapping("/seed/listing-photos")
    public DevToolsService.SamplePhotosResponse addSamplePhotos() {
        return devToolsService.addSamplePhotos();
    }
}
