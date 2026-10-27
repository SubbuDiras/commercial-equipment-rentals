package com.rental.Marketplace;

import com.marketplace.keycloak.KeycloakAdminClient;
import com.marketplace.keycloak.KeycloakAdminException;
import com.marketplace.keycloak.KeycloakAdminProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KeycloakAdminClientTests {

    private static final String BASE = "http://keycloak.test";
    private static final String ROLE_MAPPINGS = BASE + "/admin/realms/marketplace-realm/users/user-1/role-mappings/realm";
    private static final KeycloakAdminProperties PROPERTIES =
            new KeycloakAdminProperties(BASE, "marketplace-realm", "marketplace-backend", "secret");

    @Test
    void assignsRealmRoleUsingServiceAccountToken() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expectToken(server);
        server.expect(requestTo(ROLE_MAPPINGS)).andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("[{\"id\":\"r1\",\"name\":\"ROLE_RENTER\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROLE_MAPPINGS + "/available")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"r2\",\"name\":\"ROLE_OWNER\"}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ROLE_MAPPINGS)).andExpect(method(HttpMethod.POST))
                .andExpect(content().json("[{\"id\":\"r2\",\"name\":\"ROLE_OWNER\"}]"))
                .andRespond(withSuccess());

        new KeycloakAdminClient(builder.build(), PROPERTIES).grantRealmRole("user-1", "ROLE_OWNER");

        server.verify();
    }

    @Test
    void skipsAssignmentWhenUserAlreadyHasRole() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        expectToken(server);
        server.expect(requestTo(ROLE_MAPPINGS)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"r2\",\"name\":\"ROLE_OWNER\"}]", MediaType.APPLICATION_JSON));

        new KeycloakAdminClient(builder.build(), PROPERTIES).grantRealmRole("user-1", "ROLE_OWNER");

        server.verify();
    }

    @Test
    void reportsKeycloakFailuresAndMissingConfiguration() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/realms/marketplace-realm/protocol/openid-connect/token"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> new KeycloakAdminClient(builder.build(), PROPERTIES)
                .grantRealmRole("user-1", "ROLE_OWNER"))
                .isInstanceOf(KeycloakAdminException.class);
        assertThatThrownBy(() -> new KeycloakAdminClient(RestClient.create(),
                new KeycloakAdminProperties(BASE, "marketplace-realm", "marketplace-backend", ""))
                .grantRealmRole("user-1", "ROLE_OWNER"))
                .isInstanceOf(KeycloakAdminException.class)
                .hasMessageContaining("not configured");
    }

    private static void expectToken(MockRestServiceServer server) {
        server.expect(requestTo(BASE + "/realms/marketplace-realm/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "marketplace-backend",
                        "client_secret", "secret")))
                .andRespond(withSuccess("{\"access_token\":\"service-token\"}", MediaType.APPLICATION_JSON));
    }
}
