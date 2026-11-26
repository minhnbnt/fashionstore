package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record UpdatePaymentGatewayRequest(
    @Schema(description = "Bật hoặc tắt cổng thanh toán; bỏ trống = giữ nguyên", example = "true")
    @Nullable Boolean enabled,

    @Schema(
        description = "Tên hiển thị của cổng thanh toán; bỏ trống = giữ nguyên",
        example = "Chuyển khoản ngân hàng (VietQR)"
    )
    @Size(max = 120)
    @Nullable String name,

    @Schema(description = "Mã BIN ngân hàng nhận tiền (6 chữ số NAPAS); bỏ trống = giữ nguyên", example = "970422")
    @Size(max = 20)
    @Nullable String bankBin,

    @Schema(description = "Số tài khoản ngân hàng nhận tiền; bỏ trống = giữ nguyên", example = "0900123456789")
    @Size(max = 50)
    @Nullable String accountNumber,

    @Schema(description = "Tên chủ tài khoản nhận tiền; bỏ trống = giữ nguyên", example = "SAN TMDT FASHIONSTORE")
    @Size(max = 120)
    @Nullable String accountName,

    @Schema(
        description = "Khóa bí mật xác thực Webhook (write-only); null hoặc rỗng = giữ nguyên khóa cũ",
        example = "demo-vietqr-secret-key"
    )
    @Size(max = 200)
    @Nullable String webhookSecret,

    @Schema(description = "Bật/tắt chế độ Demo Sandbox; bỏ trống = giữ nguyên", example = "true")
    @Nullable Boolean sandboxMode
) {
}
