package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Yêu cầu xác nhận đã hoàn tiền cho giao dịch")
public record MarkRefundedRequest(
    @Schema(
        description = "Ghi chú đối soát hoàn tiền (từ 5 đến 500 ký tự)",
        example = "Đã chuyển khoản hoàn tiền qua MB Bank mã FT998877"
    )
    @NotBlank
    @Size(min = 5, max = 500)
    String note
) {
}
