package com.minhnbnt.fashionstore.features.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import com.minhnbnt.fashionstore.dto.bank.LookupAccountResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String bankLookupApiKey;
    private final String bankLookupApiSecret;
    private final String mockAccountName;
    private final RestClient bankLookupClient;

    @Autowired
    public BankService(
        @Value("${banklookup.api-key:}") String bankLookupApiKey,
        @Value("${banklookup.api-secret:}") String bankLookupApiSecret,
        @Value("${banklookup.base-url:https://api.banklookup.net}") String bankLookupBaseUrl,
        @Value("${banklookup.mock-account-name:}") String mockAccountName
    ) {
        this(
            bankLookupApiKey,
            bankLookupApiSecret,
            mockAccountName,
            createDefaultClient(resolveUrl(bankLookupBaseUrl, "https://api.banklookup.net"))
        );
    }

    public BankService(
        String bankLookupApiKey,
        String bankLookupApiSecret,
        String mockAccountName,
        RestClient bankLookupClient
    ) {
        this.bankLookupApiKey = cleanString(bankLookupApiKey);
        this.bankLookupApiSecret = cleanString(bankLookupApiSecret);

        if (mockAccountName != null) {
            this.mockAccountName = mockAccountName.trim().toUpperCase(Locale.ROOT);
        } else {
            this.mockAccountName = "";
        }

        this.bankLookupClient = bankLookupClient;
    }

    public BankService(
        String bankLookupApiKey,
        String bankLookupApiSecret,
        String bankLookupBaseUrl
    ) {
        this(
            bankLookupApiKey,
            bankLookupApiSecret,
            "",
            createDefaultClient(resolveUrl(bankLookupBaseUrl, "https://api.banklookup.net"))
        );
    }

    public BankService() {
        this("", "", "https://api.banklookup.net");
    }

    private static String cleanString(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static String resolveUrl(@Nullable String url, String defaultUrl) {
        if (url != null && !url.isBlank()) {
            return url.trim();
        }
        return defaultUrl;
    }

    private static RestClient createDefaultClient(String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder()
            .requestFactory(factory)
            .baseUrl(baseUrl)
            .build();
    }

    public List<BankResponse> listBanks() {
        return VietQrBanksData.DEFAULT_BANKS;
    }

    public LookupAccountResponse lookupAccount(String bin, String accountNumber) {
        if (bin == null || bin.isBlank() || !bin.matches("^[0-9]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank BIN must be 6 digits");
        }
        if (accountNumber == null || accountNumber.isBlank()
            || accountNumber.length() < 6 || accountNumber.length() > 25) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Bank account number must be between 6 and 25 characters"
            );
        }

        var cleanBin = bin.trim();
        var cleanAccount = accountNumber.trim();

        if (!bankLookupApiKey.isEmpty() && !bankLookupApiSecret.isEmpty()) {
            return lookupViaBankLookup(cleanBin, cleanAccount);
        }

        if ("0000000000".equals(cleanAccount)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account not found or invalid");
        }

        var bankCode = resolveBankCode(cleanBin);
        var accountName = "NGUYEN VAN A";
        if (!this.mockAccountName.isBlank()) {
            accountName = this.mockAccountName;
        }

        log.info(
            "BankLookup API key not configured. Returning simulated account ({}) for: {}",
            accountName,
            cleanAccount
        );
        return new LookupAccountResponse(bankCode, cleanAccount, accountName);
    }

    private LookupAccountResponse lookupViaBankLookup(String bin, String accountNumber) {
        try {
            var body = Map.of(
                "bank", bin,
                "account", accountNumber
            );

            var rawJson = bankLookupClient.post()
                .uri("/")
                .header("x-api-key", bankLookupApiKey)
                .header("x-api-secret", bankLookupApiSecret)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(status -> status.value() == 422, (req, resp) -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account not found or invalid");
                })
                .body(String.class);

            if (rawJson == null || rawJson.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account information not found");
            }

            var root = OBJECT_MAPPER.readTree(rawJson);
            if (root.path("success").asBoolean(false)) {
                var data = root.path("data");
                if (data.isObject()) {
                    var ownerName = data.path("ownerName").asText("");
                    var bank = data.path("bank").asText("");
                    if (!ownerName.isBlank()) {
                        var bankCode = bank;
                        if (bankCode.isBlank()) {
                            bankCode = resolveBankCode(bin);
                        }
                        return new LookupAccountResponse(
                            bankCode,
                            accountNumber,
                            ownerName.trim().toUpperCase(Locale.ROOT)
                        );
                    }
                }
            }

            var msg = "Bank account information not found";
            if (root.hasNonNull("msg")) {
                msg = root.path("msg").asText();
            }
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (RestClientResponseException rcre) {
            log.warn("BankLookup returned HTTP status {}: {}", rcre.getStatusCode(), rcre.getResponseBodyAsString());
            if (rcre.getStatusCode().value() == 422) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account not found or invalid");
            }
            if (rcre.getStatusCode().value() == 402) {
                throw new ResponseStatusException(
                    HttpStatus.PAYMENT_REQUIRED,
                    "Bank account lookup service is out of credit (OUT_OF_CREDIT). Please top up credits."
                );
            }
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Bank lookup service error: " + rcre.getStatusCode()
            );
        } catch (Exception ex) {
            log.error("Error while calling BankLookup API: {}", ex.getMessage());
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Failed to connect to Napas banking system: " + ex.getMessage()
            );
        }
    }

    private String resolveBankCode(String bin) {
        return listBanks().stream()
            .filter(b -> bin.equals(b.bin()))
            .findFirst()
            .map(BankResponse::code)
            .orElse("BANK");
    }
}
