package com.minhnbnt.fashionstore.dto.voucher;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record AppliedVoucher(
    @Schema(description = "ID mã giảm giá đã áp dụng")
    UUID voucherId,

    @Schema(description = "Mã giảm giá đã áp dụng", example = "SUMMER15")
    String voucherCode,

    @Schema(description = "Số tiền được giảm (VND)", example = "30000")
    long discountAmount
) {
}
