package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record PaymentMethodOption(
    @Schema(description = "Mã phương thức thanh toán", example = "VIETQR")
    String code,

    @Schema(description = "Tên hiển thị phương thức thanh toán", example = "Chuyển khoản ngân hàng (VietQR)")
    String name,

    @Schema(description = "Mã ngân hàng nhận tiền (chỉ áp dụng cho VIETQR)", example = "MB")
    @Nullable String bankCode,

    @Schema(
        description = "Tên ngân hàng nhận tiền (chỉ áp dụng cho VIETQR)",
        example = "Ngân hàng TMCP Quân đội (MB Bank)"
    )
    @Nullable String bankName,

    @Schema(description = "Cổng đang bật chế độ Demo Sandbox hay không", example = "true")
    boolean sandboxMode
) {
}
