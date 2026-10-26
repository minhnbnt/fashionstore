package com.minhnbnt.fashionstore.features.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.minhnbnt.fashionstore.dto.bank.LookupAccountResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BankServiceTest {

    @Test
    @DisplayName("listBanks returns banks list with major Vietnamese banks from fallback")
    void listBanksReturnsFallbackBanks() {
        var service = new BankService("", "", "https://api.vietqr.io");
        var banks = service.listBanks();

        assertThat(banks).isNotEmpty();
        assertThat(banks).anyMatch(b -> "VCB".equals(b.code()) && "970436".equals(b.bin()));
        assertThat(banks).anyMatch(b -> "MB".equals(b.code()) && "970422".equals(b.bin()));
    }

    @Test
    @DisplayName("lookupAccount returns mock valid account when API key is not configured")
    void lookupAccountReturnsMockWhenKeyNotConfigured() {
        var service = new BankService("", "", "https://api.vietqr.io");
        LookupAccountResponse response = service.lookupAccount("970436", "0011001234567");

        assertThat(response).isNotNull();
        assertThat(response.accountNumber()).isEqualTo("0011001234567");
        assertThat(response.accountName()).isNotEmpty();
    }

    @Test
    @DisplayName("lookupAccount throws 400 Bad Request when account is invalid or not found")
    void lookupAccountThrowsWhenInvalid() {
        var service = new BankService("", "", "https://api.vietqr.io");
        // Test invalid simulated account
        assertThatThrownBy(() -> service.lookupAccount("970436", "0000000000"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> {
                var rse = (ResponseStatusException) e;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }
}
