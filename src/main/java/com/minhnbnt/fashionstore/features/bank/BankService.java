package com.minhnbnt.fashionstore.features.bank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import com.minhnbnt.fashionstore.dto.bank.LookupAccountResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BankService {

    private static final Logger log = LoggerFactory.getLogger(BankService.class);

    private final String clientId;
    private final String apiKey;
    private final String baseUrl;
    private final RestClient restClient;

    private volatile @Nullable List<BankResponse> cachedBanks = null;

    public BankService(
        @Value("${vietqr.client-id:}") String clientId,
        @Value("${vietqr.api-key:}") String apiKey,
        @Value("${vietqr.base-url:https://api.vietqr.io}") String baseUrl
    ) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.baseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl.trim() : "https://api.vietqr.io";

        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .baseUrl(this.baseUrl)
            .build();
    }

    public List<BankResponse> listBanks() {
        var local = cachedBanks;
        if (local != null && !local.isEmpty()) {
            return local;
        }

        try {
            var response = restClient.get()
                .uri("/v2/banks")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(VietQrBanksResponse.class);

            if (response != null && response.data() != null && !response.data().isEmpty()) {
                var mapped = response.data().stream()
                    .map(b -> new BankResponse(
                        String.valueOf(b.id()),
                        b.name() != null ? b.name() : "",
                        b.code() != null ? b.code() : "",
                        b.bin() != null ? b.bin() : "",
                        b.shortName() != null ? b.shortName() : (b.code() != null ? b.code() : ""),
                        b.logo(),
                        b.lookupSupported() == 1
                    ))
                    .toList();
                cachedBanks = mapped;
                return mapped;
            }
        } catch (Exception ex) {
            log.warn("Không thể tải danh sách ngân hàng từ VietQR ({}), sử dụng danh mục tĩnh dự phòng: {}", baseUrl, ex.getMessage());
        }

        cachedBanks = VietQrBanksData.DEFAULT_BANKS;
        return VietQrBanksData.DEFAULT_BANKS;
    }

    public LookupAccountResponse lookupAccount(String bin, String accountNumber) {
        if (bin == null || bin.isBlank() || !bin.matches("^[0-9]{6}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã BIN ngân hàng phải gồm 6 chữ số");
        }
        if (accountNumber == null || accountNumber.isBlank() || accountNumber.length() < 6 || accountNumber.length() > 25) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tài khoản từ 6 đến 25 ký tự");
        }

        // Nếu chưa cấu hình clientId và apiKey -> Chế độ dev/test mock an toàn
        if (clientId.isEmpty() || apiKey.isEmpty()) {
            if ("0000000000".equals(accountNumber.trim())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản ngân hàng không tồn tại hoặc không hợp lệ");
            }
            log.info("Chưa cấu hình VIETQR_CLIENT_ID / VIETQR_API_KEY. Trả về thông tin giả lập cho tài khoản: {}", accountNumber);
            var bankCode = resolveBankCode(bin);
            return new LookupAccountResponse(bankCode, accountNumber.trim(), "NGUYEN VAN A");
        }

        try {
            var body = Map.of(
                "bin", bin.trim(),
                "accountNumber", accountNumber.trim()
            );

            var response = restClient.post()
                .uri("/v2/lookup")
                .header("x-client-id", clientId)
                .header("x-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(VietQrLookupResponse.class);

            if (response != null && "00".equals(response.code()) && response.data() != null) {
                var data = response.data();
                var accountName = data.accountName() != null ? data.accountName().trim().toUpperCase(Locale.ROOT) : "";
                var bankCode = data.bankCode() != null && !data.bankCode().isBlank() ? data.bankCode() : resolveBankCode(bin);
                return new LookupAccountResponse(bankCode, accountNumber.trim(), accountName);
            }

            var desc = response != null && response.desc() != null ? response.desc() : "Không tìm thấy tài khoản tại ngân hàng đã chọn";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, desc);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.error("Lỗi khi gọi VietQR lookup: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể kết nối đến hệ thống ngân hàng Napas: " + ex.getMessage());
        }
    }

    private String resolveBankCode(String bin) {
        return listBanks().stream()
            .filter(b -> bin.equals(b.bin()))
            .findFirst()
            .map(BankResponse::code)
            .orElse("BANK");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VietQrBanksResponse(String code, String desc, List<VietQrBankItem> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VietQrBankItem(
        int id,
        String name,
        String code,
        String bin,
        String shortName,
        @Nullable String logo,
        int lookupSupported
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VietQrLookupResponse(String code, String desc, @Nullable VietQrLookupData data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record VietQrLookupData(
        @Nullable String bankCode,
        @Nullable String accountNumber,
        @Nullable String accountName
    ) {}
}
