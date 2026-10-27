package com.marketplace.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak.admin")
public record KeycloakAdminProperties(
        String baseUrl,
        String realm,
        String clientId,
        String clientSecret
) {
    public boolean configured() {
        return notBlank(baseUrl) && notBlank(realm) && notBlank(clientId) && notBlank(clientSecret);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
