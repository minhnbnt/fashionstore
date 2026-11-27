package com.minhnbnt.fashionstore.dto.voucher;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record VoucherQuoteResult(
    @Schema(description = "ID mã giảm giá nếu hợp lệ", nullable = true)
    @Nullable
    UUID voucherId,

    @Schema(description = "Mã giảm giá đã chuẩn hóa", example = "SUMMER15")
    String voucherCode,

    @Schema(description = "Số tiền được giảm (VND)", example = "30000")
    long discountAmount,

    @Schema(
        description = "Thông báo lỗi tiếng Việt cho UI nếu mã không áp dụng được",
        nullable = true
    )
    @Nullable
    String error
) {
}
