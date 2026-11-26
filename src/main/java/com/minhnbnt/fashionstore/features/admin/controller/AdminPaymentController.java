package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.dto.payment.PaymentGatewayResponse;
import com.minhnbnt.fashionstore.dto.payment.UpdatePaymentGatewayRequest;
import com.minhnbnt.fashionstore.features.payment.PaymentGatewayService;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(
    name = "Admin - Payments",
    description = "Quản trị cấu hình cổng thanh toán (COD, VietQR), đối soát hoàn tiền và duyệt rút tiền của Shop."
)
public class AdminPaymentController {

    private final PaymentGatewayService gateways;

    @GetMapping("/payment-gateways")
    @Operation(
        operationId = "listPaymentGateways",
        summary = "Danh sách cấu hình cổng thanh toán",
        description = "Trả về cấu hình hiện hành của COD và VIETQR. Trường webhookSecret chỉ báo đã đặt hay chưa."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách cấu hình cổng thanh toán")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @SecurityRequirement(name = "bearerAuth")
    public List<PaymentGatewayResponse> listPaymentGateways() {
        return gateways.list();
    }

    @PutMapping("/payment-gateways/{code}")
    @Operation(
        operationId = "updatePaymentGateway",
        summary = "Cập nhật cấu hình cổng thanh toán",
        description = "Bật/tắt cổng COD hoặc VIETQR, cập nhật tài khoản ngân hàng nhận tiền của sàn, "
            + "khóa bí mật Webhook và chế độ Demo Sandbox. Không cho phép tắt cổng cuối cùng đang bật."
    )
    @ApiResponse(responseCode = "200", description = "Cấu hình cổng thanh toán sau cập nhật")
    @ApiResponse(responseCode = "400", description = "Mã BIN ngân hàng không hợp lệ hoặc dữ liệu sai định dạng")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải admin")
    @ApiResponse(responseCode = "409", description = "Tắt cổng cuối cùng hoặc bật VIETQR khi thiếu cấu hình ngân hàng")
    @SecurityRequirement(name = "bearerAuth")
    public PaymentGatewayResponse updatePaymentGateway(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Mã cổng thanh toán (COD hoặc VIETQR)", example = "VIETQR")
        @PathVariable PaymentMethod code,
        @Valid @RequestBody UpdatePaymentGatewayRequest request
    ) {
        return gateways.update(adminIdOf(jwt), code, request);
    }

    private static UUID adminIdOf(Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        return UUID.fromString(subject);
    }
}
