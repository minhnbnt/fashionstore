package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record PaymentMethodsResponse(
    @Schema(description = "Danh sách các phương thức thanh toán đang bật")
    List<PaymentMethodOption> methods,

    @Schema(description = "Tổng tiền đơn tối thiểu được phép dùng COD, VND", example = "0")
    long codMinTotal,

    @Schema(description = "Tổng tiền đơn tối đa được phép dùng COD, VND; 0 = không giới hạn", example = "10000000")
    long codMaxTotal,

    @Schema(description = "Số phút giữ đơn chờ thanh toán trực tuyến trước khi tự động hủy", example = "15")
    int paymentHoldMinutes
) {
}
