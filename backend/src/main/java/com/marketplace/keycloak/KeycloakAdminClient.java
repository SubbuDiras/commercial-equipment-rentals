package com.marketplace.keycloak;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Calls the Keycloak Admin REST API as the {@code marketplace-backend} service account.
 * The service account needs the {@code realm-management} roles {@code manage-users} and {@code view-users}.
 */
@Component
public class KeycloakAdminClient {

    private final RestClient restClient;
    private final KeycloakAdminProperties properties;

    public KeycloakAdminClient(
            @Qualifier("keycloakAdminRestClient") RestClient restClient,
            KeycloakAdminProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public boolean isConfigured() {
        return properties.configured();
    }

    /**
     * Assigns a realm role to a Keycloak user. Succeeds without change if the user already has the role.
     *
     * @throws KeycloakAdminException if the client is not configured or Keycloak rejects any call
     */
    public void grantRealmRole(String userId, String roleName) {
        if (!isConfigured()) {
            throw new KeycloakAdminException("Keycloak admin client is not configured.");
        }
        try {
            String token = serviceAccountToken();
            String usersPath = "/admin/realms/{realm}/users/{userId}/role-mappings/realm";
            if (hasRole(restClient.get().uri(usersPath, properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(JsonNode.class), roleName)) {
                return;
            }
            JsonNode role = findRole(restClient.get().uri(usersPath + "/available", properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(JsonNode.class), roleName);
            if (role == null) {
                throw new KeycloakAdminException("Realm role " + roleName + " does not exist in Keycloak.");
            }
            restClient.post().uri(usersPath, properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of(Map.of("id", role.path("id").asText(), "name", roleName)))
                    .retrieve()
                    .toBodilessEntity();
        } catch (KeycloakAdminException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KeycloakAdminException("Keycloak role assignment failed: " + exception.getMessage(), exception);
        }
    }

    /**
     * Sets a Keycloak user's first and last name, so the name in new tokens matches the marketplace profile.
     * Reads the full user first and writes it back, because a partial update can drop other user-profile attributes.
     *
     * @throws KeycloakAdminException if the client is not configured or Keycloak rejects any call
     */
    public void updateUserName(String userId, String firstName, String lastName) {
        if (!isConfigured()) {
            throw new KeycloakAdminException("Keycloak admin client is not configured.");
        }
        try {
            String token = serviceAccountToken();
            String userPath = "/admin/realms/{realm}/users/{userId}";
            JsonNode user = restClient.get().uri(userPath, properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .retrieve().body(JsonNode.class);
            if (user == null || !user.isObject()) {
                throw new KeycloakAdminException("Keycloak user " + userId + " was not found.");
            }
            ((com.fasterxml.jackson.databind.node.ObjectNode) user).put("firstName", firstName).put("lastName", lastName);
            restClient.put().uri(userPath, properties.realm(), userId)
                    .headers(headers -> headers.setBearerAuth(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(user)
                    .retrieve()
                    .toBodilessEntity();
        } catch (KeycloakAdminException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new KeycloakAdminException("Keycloak name update failed: " + exception.getMessage(), exception);
        }
    }

    private String serviceAccountToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        JsonNode response = restClient.post()
                .uri("/realms/{realm}/protocol/openid-connect/token", properties.realm())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(JsonNode.class);
        String token = response == null ? "" : response.path("access_token").asText();
        if (token.isBlank()) {
            throw new KeycloakAdminException("Keycloak did not return a service account token.");
        }
        return token;
    }

    private static boolean hasRole(JsonNode roles, String roleName) {
        return findRole(roles, roleName) != null;
    }

    private static JsonNode findRole(JsonNode roles, String roleName) {
        if (roles == null || !roles.isArray()) {
            return null;
        }
        for (JsonNode role : roles) {
            if (roleName.equals(role.path("name").asText())) {
                return role;
            }
        }
        return null;
    }
}
