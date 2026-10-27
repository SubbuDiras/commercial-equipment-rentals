package com.marketplace.owner;

import com.marketplace.keycloak.KeycloakAdminClient;
import com.marketplace.keycloak.KeycloakAdminException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/**
 * Approves an owner and then grants {@code ROLE_OWNER} in Keycloak. Deliberately not transactional:
 * the approval commits first, so a Keycloak outage never rolls it back; the admin gets a warning instead.
 */
@Service
public class OwnerApprovalService {

    static final String OWNER_ROLE = "ROLE_OWNER";
    private static final Logger log = LoggerFactory.getLogger(OwnerApprovalService.class);

    private final OwnerOnboardingService ownerOnboardingService;
    private final KeycloakAdminClient keycloakAdminClient;

    public OwnerApprovalService(OwnerOnboardingService ownerOnboardingService, KeycloakAdminClient keycloakAdminClient) {
        this.ownerOnboardingService = ownerOnboardingService;
        this.keycloakAdminClient = keycloakAdminClient;
    }

    public OwnerReviewResponse approve(String ownerProfileId, Jwt adminJwt) {
        OwnerReviewResponse approved = ownerOnboardingService.approve(ownerProfileId, adminJwt);
        try {
            keycloakAdminClient.grantRealmRole(ownerOnboardingService.findKeycloakSubject(ownerProfileId), OWNER_ROLE);
            return approved.withRoleGrant(true, null);
        } catch (KeycloakAdminException exception) {
            log.warn("Owner profile {} approved but {} was not granted: {}",
                    ownerProfileId, OWNER_ROLE, exception.getMessage());
            return approved.withRoleGrant(false,
                    "Owner approved, but " + OWNER_ROLE + " could not be assigned automatically ("
                            + exception.getMessage() + "). Assign " + OWNER_ROLE + " to " + approved.email()
                            + " in Keycloak by hand; the user must then sign in again.");
        }
    }
}
