package com.rental.Marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceSecurityTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actuatorHealthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void prometheusEndpointIsRestrictedToAdministrators() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/prometheus")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void pilotMetricsEndpointRequiresAdministratorRole() throws Exception {
        mockMvc.perform(get("/api/v1/admin/pilot-metrics"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/admin/pilot-metrics")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void securityHeadersArePresentOnResponses() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"));
    }

    @Test
    void corsAllowsConfiguredFrontendAndRejectsUnlistedOrigins() throws Exception {
        mockMvc.perform(options("/api/v1/bookings")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        mockMvc.perform(options("/api/v1/bookings")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void testPublicEndpointIsAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/test/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Public endpoint accessible to anyone"));
    }

    @Test
    void renterEndpointRejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/test/renter-only"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void renterEndpointAcceptsAuthenticatedRenter() throws Exception {
        mockMvc.perform(get("/api/v1/test/renter-only")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("test-renter"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Renter access granted"))
                .andExpect(jsonPath("$.subject").value("test-renter"));
    }

    @Test
    void renterEndpointRejectsUsersWithoutRenterRole() throws Exception {
        mockMvc.perform(get("/api/v1/test/renter-only")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminOwnerReviewQueueRejectsNonAdminUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/owners")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void equipmentListingManagementEnforcesOwnerAndAdminRoles() throws Exception {
        mockMvc.perform(get("/api/v1/owner/equipment"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/owner/equipment")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/listings/pending")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingCreationRequiresRenterRole() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/bookings")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId": "a1675d19-e470-4a57-8a32-aa4e397ae3c1",
                                  "startTime": "2026-12-01T09:00:00Z",
                                  "endTime": "2026-12-02T09:00:00Z",
                                  "deliveryOption": "PICKUP"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookingConflictsReturnHttp409() throws Exception {
        mockMvc.perform(post("/api/v1/bookings")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(jwt -> jwt.subject("conflicting-renter"))
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "equipmentId": "a1675d19-e470-4a57-8a32-aa4e397ae3c1",
                                  "startTime": "2020-12-01T09:00:00Z",
                                  "endTime": "2020-12-02T09:00:00Z",
                                  "deliveryOption": "PICKUP"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));
    }

    @Test
    void ownerBookingActionsRejectRenterRole() throws Exception {
        mockMvc.perform(patch("/api/v1/owner/bookings/not-a-booking/accept")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_RENTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void paymentOrderRequiresRenterRole() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/not-a-booking/payment-order"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/bookings/not-a-booking/payment-order")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_OWNER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void razorpayWebhookIsPublicButRejectsInvalidSignatures() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/razorpay")
                        .header("X-Razorpay-Signature", "invalid")
                        .header("X-Razorpay-Event-Id", "test-invalid-signature")
                        .contentType(APPLICATION_JSON)
                        .content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isForbidden());
    }
}
