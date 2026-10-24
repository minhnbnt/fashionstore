package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Doanh thu phân theo phương thức thanh toán")
public record RevenueByPaymentResponse(
    @Schema(description = "Phương thức thanh toán (COD, VNPAY, WALLET...)", example = "COD")
    String paymentMethod,

    @Schema(description = "Tổng doanh thu (VND)", example = "28000000")
    long revenue,

    @Schema(description = "Số đơn hàng", example = "95")
    long orderCount
) {}
