package com.minhnbnt.fashionstore.features.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.minhnbnt.fashionstore.dto.bank.LookupAccountResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class BankServiceTest {

    private static final String TEST_KEY = "test-api-key";
    private static final String TEST_SECRET = "test-api-secret";
    private static final String BASE_URL = "https://api.banklookup.net";

    @Test
    @DisplayName("listBanks returns banks list with major Vietnamese banks from fallback")
    void listBanksReturnsFallbackBanks() {
        var service = new BankService();
        var banks = service.listBanks();

        assertThat(banks).isNotEmpty();
        assertThat(banks).anyMatch(b -> "VCB".equals(b.code()) && "970436".equals(b.bin()));
        assertThat(banks).anyMatch(b -> "MB".equals(b.code()) && "970422".equals(b.bin()));
    }

    @Test
    @DisplayName("lookupAccount returns mock valid account when API key is not configured")
    void lookupAccountReturnsMockWhenKeyNotConfigured() {
        var service = new BankService();
        LookupAccountResponse response = service.lookupAccount("970436", "0011001234567");

        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).isEqualTo("0011001234567");
        assertThat(response.accountName()).isNotEmpty();
    }

    @Test
    @DisplayName("lookupAccount throws 400 Bad Request when bin is invalid")
    void lookupAccountThrowsWhenBinInvalid() {
        var service = new BankService();
        assertThatThrownBy(() -> service.lookupAccount("123", "0011001234567"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).contains("Bank BIN");
            });
    }

    @Test
    @DisplayName("lookupAccount throws 400 Bad Request when account number is invalid")
    void lookupAccountThrowsWhenAccountInvalid() {
        var service = new BankService();
        assertThatThrownBy(() -> service.lookupAccount("970436", "123"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).contains("Bank account number");
            });
    }

    @Test
    @DisplayName("lookupAccount parses BankLookup response and returns real owner name for MB bank")
    void lookupAccountWithBankLookupApiSuccess() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        var mockServer = MockRestServiceServer.bindTo(builder).build();

        mockServer.expect(requestTo(BASE_URL + "/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("x-api-key", TEST_KEY))
            .andExpect(header("x-api-secret", TEST_SECRET))
            .andRespond(withSuccess(
                """
                {
                  "code": 200,
                  "success": true,
                  "data": {
                    "ownerName": "NGUYEN HOANG HIEP",
                    "bank": "MB",
                    "account": "0977930202"
                  },
                  "msg": "Success"
                }
                """,
                MediaType.APPLICATION_JSON
            ));

        var service = new BankService(
            TEST_KEY,
            TEST_SECRET,
            "",
            builder.build()
        );

        LookupAccountResponse response = service.lookupAccount("970422", "0977930202");

        mockServer.verify();
        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).isEqualTo("0977930202");
        assertThat(response.accountName()).isEqualTo("NGUYEN HOANG HIEP");
        assertThat(response.bankCode()).isEqualTo("MB");
    }

    @Test
    @DisplayName("lookupAccount throws 400 when account not found (422) on BankLookup API")
    void lookupAccountWithBankLookupApiNotFound() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        var mockServer = MockRestServiceServer.bindTo(builder).build();

        mockServer.expect(requestTo(BASE_URL + "/"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {"code":422,"success":false,"data":[],"msg":"NOT_FOUND"}
                """));

        var service = new BankService(
            TEST_KEY,
            TEST_SECRET,
            "",
            builder.build()
        );

        assertThatThrownBy(() -> service.lookupAccount("970422", "0000000000"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).contains("Bank account not found or invalid");
            });

        mockServer.verify();
    }

    @Test
    @DisplayName("lookupAccount throws 402 PAYMENT_REQUIRED when BankLookup is out of credit")
    void lookupAccountWithBankLookupOutOfCredit() {
        var builder = RestClient.builder().baseUrl(BASE_URL);
        var mockServer = MockRestServiceServer.bindTo(builder).build();

        mockServer.expect(requestTo(BASE_URL + "/"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                {"code":402,"success":false,"data":[],"msg":"OUT_OF_CREDIT"}
                """));

        var service = new BankService(
            TEST_KEY,
            TEST_SECRET,
            "",
            builder.build()
        );

        assertThatThrownBy(() -> service.lookupAccount("970422", "0977930202"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.PAYMENT_REQUIRED);
                assertThat(rse.getReason()).contains("OUT_OF_CREDIT");
            });

        mockServer.verify();
    }
}
