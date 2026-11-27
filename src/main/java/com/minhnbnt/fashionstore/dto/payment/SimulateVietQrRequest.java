package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record SimulateVietQrRequest(
    @Schema(
        description = "Số tiền giả lập chuyển khoản (VND). Để trống sẽ mặc định chuyển đủ 100% số tiền của phiên.",
        example = "360000"
    )
    @Nullable Long amount
) {
}
