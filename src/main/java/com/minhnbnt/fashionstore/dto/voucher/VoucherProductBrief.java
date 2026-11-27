package com.minhnbnt.fashionstore.dto.voucher;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record VoucherProductBrief(
    @Schema(description = "ID sản phẩm")
    UUID productId,

    @Schema(description = "Tên sản phẩm", example = "Áo thun oversize")
    String productName,

    @Schema(description = "Trạng thái sản phẩm", example = "ACTIVE")
    String productStatus
) {
}
