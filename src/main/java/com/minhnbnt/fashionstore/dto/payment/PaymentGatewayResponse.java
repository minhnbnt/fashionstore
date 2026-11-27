package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record PaymentGatewayResponse(
    @Schema(description = "Mã cổng thanh toán", example = "VIETQR")
    String code,

    @Schema(description = "Tên hiển thị cổng thanh toán", example = "Chuyển khoản ngân hàng (VietQR)")
    String name,

    @Schema(description = "Cổng thanh toán đang bật hay tắt", example = "true")
    boolean enabled,

    @Schema(description = "Mã BIN ngân hàng NAPAS (6 chữ số)", example = "970422")
    @Nullable String bankBin,

    @Schema(description = "Mã viết tắt ngân hàng", example = "MB")
    @Nullable String bankCode,

    @Schema(description = "Tên ngân hàng nhận tiền của sàn", example = "Ngân hàng TMCP Quân Đội")
    @Nullable String bankName,

    @Schema(description = "Số tài khoản ngân hàng nhận tiền của sàn", example = "0900123456789")
    @Nullable String accountNumber,

    @Schema(description = "Tên chủ tài khoản ngân hàng của sàn", example = "SAN TMDT FASHIONSTORE")
    @Nullable String accountName,

    @Schema(description = "Đã thiết lập khóa bí mật xác thực Webhook hay chưa (write-only)", example = "true")
    boolean webhookSecretSet,

    @Schema(description = "Chế độ Demo Sandbox cho phép giả lập biến động số dư", example = "true")
    boolean sandboxMode,

    @Schema(description = "Thời điểm cập nhật cấu hình gần nhất")
    Instant updatedAt
) {
}
