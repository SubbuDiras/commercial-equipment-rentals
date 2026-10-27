package com.rental.Marketplace;

import com.marketplace.rental.DamageClaimRequest;
import com.marketplace.rental.RentalInspectionService;
import com.marketplace.rental.RentalOperationsTask;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.marketplace.MarketplaceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RentalInspectionControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RentalInspectionService rentalInspectionService;

    // Keeps the scheduled rental sweep from calling the mocked service while it is being stubbed.
    @MockBean
    private RentalOperationsTask rentalOperationsTask;

    @Test
    void raisingDamageClaimReturnsJsonObject() throws Exception {
        when(rentalInspectionService.raiseDamageClaim(eq("booking-1"), any(DamageClaimRequest.class), any(Jwt.class)))
                .thenReturn("dispute-1");

        mockMvc.perform(post("/api/v1/rentals/booking-1/disputes")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .authorities(new SimpleGrantedAuthority("ROLE_OWNER")))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"category": "HYDRAULICS", "claimAmount": 5000, "description": "Leaking cylinder"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(jsonPath("$.disputeId").value("dispute-1"))
                .andExpect(jsonPath("$.bookingId").value("booking-1"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }
}
