package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Yêu cầu hủy đơn hàng kèm lý do từ người mua")
public record BuyerCancelOrderRequest(
    @NotBlank(message = "Cancel reason is required")
    @Schema(
        description = "Mã lý do hủy (CHANGE_MIND, FOUND_CHEAPER, ORDERED_BY_MISTAKE, OTHER)",
        example = "CHANGE_MIND"
    )
    String reason,

    @Nullable
    @Size(max = 500, message = "Cancel note must not exceed 500 characters")
    @Schema(description = "Ghi chú chi tiết (bắt buộc khi chọn OTHER)", nullable = true)
    String note
) {
}

