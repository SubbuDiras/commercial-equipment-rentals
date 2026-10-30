package com.marketplace.user;

import java.time.Instant;
import java.util.List;

/**
 * @param roles               application roles from the caller's token, e.g. ROLE_RENTER
 * @param signInNameUpdated   after an update: whether Keycloak took the new name too (null on a plain read). When false,
 *                            the marketplace has the new name but the header shows the old one until an admin syncs it
 */
public record MyProfileResponse(
        String firstName,
        String lastName,
        String email,
        String phone,
        List<String> roles,
        Instant memberSince,
        Boolean signInNameUpdated,
        /** Short-lived link to the profile photo, or null when there is none. */
        String photoUrl
) {
}
