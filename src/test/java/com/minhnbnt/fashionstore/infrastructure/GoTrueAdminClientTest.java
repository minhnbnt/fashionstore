package com.minhnbnt.fashionstore.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoTrueAdminClientTest {

    private static final String URL = "http://gotrue.test";

    private final RestClient.Builder builder = RestClient.builder();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private GoTrueAdminClient client(String key) {
        return new GoTrueAdminClient(builder, URL, key);
    }

    @Test
    void banUserSendsLongBanDurationWithServiceKey() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users/" + id))
            .andExpect(method(HttpMethod.PUT))
            .andExpect(header("Authorization", "Bearer service-key"))
            .andExpect(content().json("{\"ban_duration\":\"876000h\"}"))
            .andRespond(withSuccess());

        client("service-key").banUser(id);

        server.verify();
    }

    @Test
    void unbanUserClearsBan() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users/" + id))
            .andExpect(content().json("{\"ban_duration\":\"none\"}"))
            .andRespond(withSuccess());

        client("service-key").unbanUser(id);

        server.verify();
    }

    @Test
    void setRoleWritesAppMetadata() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users/" + id))
            .andExpect(content().json("{\"app_metadata\":{\"role\":\"shop\"}}"))
            .andRespond(withSuccess());

        client("service-key").setRole(id, "shop");

        server.verify();
    }

    @Test
    void blankKeySkipsTheCall() {
        client("").banUser(UUID.randomUUID());

        server.verify();
    }

    @Test
    void isConfiguredDependsOnKey() {
        assertThat(client("service-key").isConfigured()).isTrue();
        assertThat(client(" ").isConfigured()).isFalse();
    }

    @Test
    void findUserIdByEmailPicksExactMatchIgnoringCase() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users?filter=admin%40t.local&per_page=50"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", "Bearer service-key"))
            .andRespond(withSuccess(
                """
                {"aud":"authenticated","users":[
                  {"id":"%s","email":"super-admin@t.local","role":"authenticated"},
                  {"id":"%s","email":"Admin@T.local","role":"authenticated","app_metadata":{}}
                ]}
                """.formatted(UUID.randomUUID(), id),
                MediaType.APPLICATION_JSON
            ));

        var found = client("service-key").findUserIdByEmail("admin@t.local");

        assertThat(found).contains(id);
        server.verify();
    }

    @Test
    void findUserIdByEmailReturnsEmptyWhenNoExactMatch() {
        server.expect(requestTo(URL + "/admin/users?filter=admin%40t.local&per_page=50"))
            .andRespond(withSuccess(
                "{\"users\":[{\"id\":\"" + UUID.randomUUID() + "\",\"email\":\"super-admin@t.local\"}]}",
                MediaType.APPLICATION_JSON
            ));

        assertThat(client("service-key").findUserIdByEmail("admin@t.local")).isEmpty();
    }

    @Test
    void createUserIsConfirmedAndCarriesRole() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer service-key"))
            .andExpect(content().json("""
                {
                  "email": "admin@t.local",
                  "password": "Secret@123",
                  "email_confirm": true,
                  "app_metadata": {"role": "admin"},
                  "user_metadata": {"full_name": "Admin"}
                }
                """))
            .andRespond(withSuccess(
                "{\"id\":\"" + id + "\",\"email\":\"admin@t.local\",\"aud\":\"authenticated\"}",
                MediaType.APPLICATION_JSON
            ));

        var created = client("service-key").createUser("admin@t.local", "Secret@123", "Admin", "admin");

        assertThat(created).isEqualTo(id);
        server.verify();
    }

    @Test
    void createUserWithoutKeyFails() {
        assertThatThrownBy(() -> client("").createUser("admin@t.local", "Secret@123", "Admin", "admin"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createUserFailureBecomesBadGateway() {
        server.expect(requestTo(URL + "/admin/users"))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

        assertThatThrownBy(() -> client("service-key").createUser("admin@t.local", "123", "Admin", "admin"))
            .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void upstreamFailureBecomesBadGateway() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users/" + id))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client("service-key").banUser(id))
            .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
