package com.minhnbnt.fashionstore.features.payment.controller;

import com.minhnbnt.fashionstore.dto.payment.PaymentMethodsResponse;
import com.minhnbnt.fashionstore.dto.payment.VietQrWebhookRequest;
import com.minhnbnt.fashionstore.dto.payment.VietQrWebhookResponse;
import com.minhnbnt.fashionstore.features.payment.PaymentFlowService;
import com.minhnbnt.fashionstore.features.payment.PaymentGatewayService;
import com.minhnbnt.fashionstore.features.payment.TransferOutcome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(
    name = "Public - Payments",
    description = "Danh sách phương thức thanh toán khả dụng và webhook nhận biến động số dư VietQR."
)
public class PublicPaymentController {

    private final PaymentGatewayService gateways;

    private final ObjectProvider<PaymentFlowService> paymentFlow;

    @GetMapping("/payment-methods")
    @Operation(
        operationId = "listPaymentMethods",
        summary = "Danh sách phương thức thanh toán khả dụng",
        description = "Trả về các cổng thanh toán đang bật kèm hạn mức COD và số phút giữ đơn chờ thanh toán."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách phương thức thanh toán")
    public PaymentMethodsResponse listPaymentMethods() {
        return gateways.publicMethods();
    }

    @PostMapping("/payments/vietqr/webhook")
    @Operation(
        operationId = "vietQrWebhook",
        summary = "Webhook nhận biến động số dư VietQR",
        description = "Xác thực khóa bí mật ở header Authorization (Apikey hoặc Bearer) và đối soát giao dịch chuyển khoản."
    )
    @ApiResponse(responseCode = "200", description = "Kết quả xử lý webhook")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai khóa bí mật Webhook")
    public VietQrWebhookResponse vietQrWebhook(
        @Parameter(description = "Header xác thực Webhook (Apikey <secret> hoặc Bearer <secret>)")
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) @Nullable String authorization,
        @Valid @RequestBody VietQrWebhookRequest request
    ) {
        verifyWebhookSecret(authorization);

        var transferType = request.transferType();
        if (transferType != null && !transferType.isBlank() && !transferType.equalsIgnoreCase("in")) {
            return new VietQrWebhookResponse(true, TransferOutcome.IGNORED.name(), null);
        }

        var flow = Objects.requireNonNull(paymentFlow.getIfAvailable());
        var result = flow.processIncomingTransfer(
            request.resolvedContent(),
            request.resolvedAmount(),
            request.resolvedBankTransactionId(),
            request.toRawPayloadJson()
        );
        return new VietQrWebhookResponse(true, result.outcome().name(), result.txnRef());
    }

    private void verifyWebhookSecret(@Nullable String authorization) {
        var providedSecret = extractSecret(authorization);
        if (providedSecret == null || providedSecret.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid webhook authorization");
        }

        var gateway = gateways.requireActiveVietQrGateway();
        var expectedSecret = gateway.getWebhookSecret();
        if (expectedSecret == null || expectedSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret is not configured");
        }

        var providedBytes = providedSecret.getBytes(StandardCharsets.UTF_8);
        var expectedBytes = expectedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(providedBytes, expectedBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook authorization secret");
        }
    }

    private static @Nullable String extractSecret(@Nullable String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        var trimmed = authorization.trim();
        if (trimmed.regionMatches(true, 0, "Apikey ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return null;
    }
}
