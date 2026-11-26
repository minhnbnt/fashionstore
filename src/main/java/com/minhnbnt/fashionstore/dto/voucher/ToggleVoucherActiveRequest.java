package com.minhnbnt.fashionstore.dto.voucher;

import io.swagger.v3.oas.annotations.media.Schema;

public record ToggleVoucherActiveRequest(
    @Schema(description = "Trạng thái kích hoạt mới của mã giảm giá", example = "true")
    boolean active
) {
}
