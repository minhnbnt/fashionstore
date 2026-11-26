package com.minhnbnt.fashionstore.features.payment.controller;

import com.minhnbnt.fashionstore.dto.payment.SimulateVietQrRequest;
import com.minhnbnt.fashionstore.dto.payment.VietQrSessionResponse;
import com.minhnbnt.fashionstore.features.payment.PaymentFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments/vietqr")
@RequiredArgsConstructor
@Tag(
    name = "Payments - VietQR",
    description = "Tra cứu phiên thanh toán VietQR và giả lập chuyển khoản Sandbox."
)
public class PaymentController {

    private final PaymentFlowService paymentFlow;

    @GetMapping("/{txnRef}")
    @Operation(
        operationId = "getVietQrSession",
        summary = "Thông tin phiên thanh toán VietQR",
        description = "Trả về thông tin mã QR, tài khoản ngân hàng nhận tiền, "
            + "thời hạn và danh sách đơn hàng thuộc phiên."
    )
    @ApiResponse(responseCode = "200", description = "Thông tin phiên thanh toán VietQR")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Phiên thanh toán không tồn tại hoặc không thuộc quyền xem")
    @SecurityRequirement(name = "bearerAuth")
    public VietQrSessionResponse getVietQrSession(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Mã tham chiếu giao dịch VietQR", example = "DHMQ1234567890")
        @PathVariable String txnRef
    ) {
        return paymentFlow.getSessionForViewer(userIdOf(jwt), isAdmin(jwt), txnRef);
    }

    @PostMapping("/{txnRef}/simulate")
    @Operation(
        operationId = "simulateVietQrTransfer",
        summary = "Giả lập chuyển khoản VietQR (Sandbox)",
        description = "Chỉ hoạt động khi cổng VietQR đang bật chế độ Demo Sandbox. Giả lập ngân hàng báo nhận tiền."
    )
    @ApiResponse(responseCode = "200", description = "Trạng thái phiên thanh toán sau giả lập")
    @ApiResponse(responseCode = "400", description = "Số tiền giả lập không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Phiên thanh toán không tồn tại")
    @ApiResponse(responseCode = "409", description = "Chế độ Demo Sandbox đang tắt")
    @SecurityRequirement(name = "bearerAuth")
    public VietQrSessionResponse simulateVietQrTransfer(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Mã tham chiếu giao dịch VietQR", example = "DHMQ1234567890")
        @PathVariable String txnRef,
        @RequestBody(required = false) @Valid @Nullable SimulateVietQrRequest request
    ) {
        Long amount = null;
        if (request != null) {
            amount = request.amount();
        }
        return paymentFlow.simulateTransfer(userIdOf(jwt), isAdmin(jwt), txnRef, amount);
    }

    private static UUID userIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }

    private static boolean isAdmin(Jwt jwt) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            for (var authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        var metadata = jwt.getClaimAsMap("app_metadata");
        if (metadata != null) {
            return "admin".equals(metadata.get("role"));
        }
        return false;
    }
}
