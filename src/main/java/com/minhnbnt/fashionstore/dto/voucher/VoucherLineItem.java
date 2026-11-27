package com.minhnbnt.fashionstore.dto.voucher;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record VoucherLineItem(
    @Schema(description = "ID sản phẩm của dòng hàng", nullable = true)
    @Nullable
    UUID productId,

    @Schema(description = "Thành tiền của dòng hàng (VND)", example = "120000")
    long lineTotal
) {
}
