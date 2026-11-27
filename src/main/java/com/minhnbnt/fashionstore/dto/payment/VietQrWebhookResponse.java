package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record VietQrWebhookResponse(
    @Schema(description = "Trạng thái tiếp nhận webhook", example = "true")
    boolean success,

    @Schema(description = "Kết quả xử lý giao dịch", example = "SUCCESS")
    String outcome,

    @Schema(description = "Mã tham chiếu giao dịch khớp được", example = "DHMQ1234567890")
    @Nullable String txnRef
) {
}
