package com.minhnbnt.fashionstore.features.payment;

import com.minhnbnt.fashionstore.dto.bank.BankResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentGatewayResponse;
import com.minhnbnt.fashionstore.dto.payment.PaymentMethodOption;
import com.minhnbnt.fashionstore.dto.payment.PaymentMethodsResponse;
import com.minhnbnt.fashionstore.dto.payment.UpdatePaymentGatewayRequest;
import com.minhnbnt.fashionstore.features.bank.BankService;
import com.minhnbnt.fashionstore.features.payment.repository.PaymentGatewayRepository;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.settings.SystemSettingsService;
import com.minhnbnt.fashionstore.model.ConfigAuditEntity;
import com.minhnbnt.fashionstore.model.PaymentGateway;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentGatewayService {

    private final PaymentGatewayRepository gateways;

    private final BankService banks;

    private final SystemSettingsService settings;

    private final ConfigAuditService audits;

    @Transactional(readOnly = true)
    public List<PaymentGatewayResponse> list() {
        return gateways.findAllOrdered().stream()
            .map(PaymentGatewayService::toResponse)
            .toList();
    }

    @Transactional
    public PaymentGatewayResponse update(
        UUID adminId,
        PaymentMethod code,
        UpdatePaymentGatewayRequest request
    ) {
        var locked = gateways.findAllForUpdate();
        PaymentGateway target = null;
        for (var g : locked) {
            if (code.name().equals(g.getCode())) {
                target = g;
                break;
            }
        }
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment gateway not found: " + code);
        }

        var oldSnapshot = toAuditSnapshot(target);

        var newName = target.getName();
        var reqName = request.name();
        if (reqName != null && !reqName.isBlank()) {
            newName = reqName.trim();
        }

        var newEnabled = target.isEnabled();
        var reqEnabled = request.enabled();
        if (reqEnabled != null) {
            newEnabled = reqEnabled;
        }

        var newSandboxMode = target.isSandboxMode();
        var reqSandbox = request.sandboxMode();
        if (reqSandbox != null) {
            newSandboxMode = reqSandbox;
        }

        var newBankBin = target.getBankBin();
        var newBankCode = target.getBankCode();
        var newBankName = target.getBankName();
        var newAccountNumber = target.getAccountNumber();
        var newAccountName = target.getAccountName();
        var newWebhookSecret = target.getWebhookSecret();

        if (code == PaymentMethod.VIETQR) {
            var reqBin = request.bankBin();
            if (reqBin != null && !reqBin.isBlank()) {
                var matched = findBankByBin(reqBin.trim());
                newBankBin = matched.bin();
                newBankCode = matched.code();
                newBankName = matched.name();
            }

            var reqAccountNumber = request.accountNumber();
            if (reqAccountNumber != null) {
                newAccountNumber = reqAccountNumber.trim();
            }

            var reqAccountName = request.accountName();
            if (reqAccountName != null) {
                newAccountName = reqAccountName.trim().toUpperCase(Locale.ROOT);
            }

            var reqSecret = request.webhookSecret();
            if (reqSecret != null && !reqSecret.isBlank()) {
                newWebhookSecret = reqSecret.trim();
            }

            if (newEnabled) {
                if (isBlank(newBankBin)
                    || isBlank(newAccountNumber)
                    || isBlank(newAccountName)
                    || isBlank(newWebhookSecret)) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "VietQR gateway requires bank BIN, account number, account name, and webhook secret"
                    );
                }
            }
        }

        var anyEnabled = false;
        for (var g : locked) {
            if (code.name().equals(g.getCode())) {
                if (newEnabled) {
                    anyEnabled = true;
                }
            } else if (g.isEnabled()) {
                anyEnabled = true;
            }
        }
        if (!anyEnabled) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "At least one payment gateway must remain enabled"
            );
        }

        target.applyUpdate(
            newName,
            newEnabled,
            newBankBin,
            newBankCode,
            newBankName,
            newAccountNumber,
            newAccountName,
            newWebhookSecret,
            newSandboxMode,
            adminId,
            Instant.now()
        );
        var saved = gateways.save(target);

        audits.record(
            ConfigAuditEntity.PAYMENT_GATEWAY,
            code.name(),
            "UPDATE",
            oldSnapshot,
            toAuditSnapshot(saved),
            adminId
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentMethodsResponse publicMethods() {
        var options = gateways.findAllOrdered().stream()
            .filter(PaymentGateway::isEnabled)
            .map(g -> new PaymentMethodOption(
                g.getCode(),
                g.getName(),
                g.getBankCode(),
                g.getBankName(),
                g.isSandboxMode()
            ))
            .toList();

        var current = settings.current();
        return new PaymentMethodsResponse(
            options,
            current.codMinTotal(),
            current.codMaxTotal(),
            current.paymentHoldMinutes()
        );
    }

    @Transactional(readOnly = true)
    public void requireAvailable(PaymentMethod method, long totalAmount) {
        var gateway = gateways.findByCode(method.name()).orElse(null);
        if (gateway == null || !gateway.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment method is disabled");
        }

        if (method == PaymentMethod.COD) {
            var current = settings.current();
            if (totalAmount < current.codMinTotal()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total is outside COD limits");
            }
            if (current.codMaxTotal() > 0 && totalAmount > current.codMaxTotal()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Order total is outside COD limits");
            }
        }
    }

    @Transactional(readOnly = true)
    public PaymentGateway requireActiveVietQrGateway() {
        var gateway = gateways.findByCode(PaymentMethod.VIETQR.name()).orElse(null);
        if (gateway == null || !gateway.isEnabled() || !gateway.hasCompleteVietQrBankConfig()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "VietQR payment gateway is disabled or missing bank configuration"
            );
        }
        return gateway;
    }

    private BankResponse findBankByBin(String bin) {
        return banks.listBanks().stream()
            .filter(b -> bin.equals(b.bin()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported VietQR bank BIN: " + bin
            ));
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    private static PaymentGatewayResponse toResponse(PaymentGateway g) {
        return new PaymentGatewayResponse(
            g.getCode(),
            g.getName(),
            g.isEnabled(),
            g.getBankBin(),
            g.getBankCode(),
            g.getBankName(),
            g.getAccountNumber(),
            g.getAccountName(),
            g.hasWebhookSecret(),
            g.isSandboxMode(),
            g.getUpdatedAt()
        );
    }

    private static Map<String, @Nullable Object> toAuditSnapshot(PaymentGateway g) {
        var map = new LinkedHashMap<String, @Nullable Object>();
        map.put("code", g.getCode());
        map.put("name", g.getName());
        map.put("enabled", g.isEnabled());
        map.put("bankBin", g.getBankBin());
        map.put("bankCode", g.getBankCode());
        map.put("bankName", g.getBankName());
        map.put("accountNumber", g.getAccountNumber());
        map.put("accountName", g.getAccountName());
        if (g.hasWebhookSecret()) {
            map.put("webhookSecret", "****");
        } else {
            map.put("webhookSecret", null);
        }
        map.put("sandboxMode", g.isSandboxMode());
        return map;
    }
}
