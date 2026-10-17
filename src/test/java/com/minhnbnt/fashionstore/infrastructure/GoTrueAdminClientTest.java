package com.minhnbnt.fashionstore.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
    void upstreamFailureBecomesBadGateway() {
        var id = UUID.randomUUID();
        server.expect(requestTo(URL + "/admin/users/" + id))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client("service-key").banUser(id))
            .isInstanceOfSatisfying(ResponseStatusException.class, e ->
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
