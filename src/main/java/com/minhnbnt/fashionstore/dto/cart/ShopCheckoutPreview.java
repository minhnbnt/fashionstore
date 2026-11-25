package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ShopCheckoutPreview(
    @Schema(description = "Shop, mỗi shop thành 1 đơn")
    UUID shopId,

    @Schema(description = "Tổng số món (cộng quantity)", example = "2")
    int itemCount,

    @Schema(description = "Tiền hàng theo giá hiện tại, VND", example = "200000")
    long subtotal,

    @Schema(description = "Phí ship thực thu, VND", example = "30000")
    long shippingFee,

    @Schema(description = "Phí ship theo công thức trước freeship, VND", example = "30000")
    long originalShippingFee,

    @Schema(description = "Đơn đạt ngưỡng miễn phí vận chuyển", example = "false")
    boolean freeShipApplied,

    @Schema(description = "Tiền hàng còn thiếu để được freeship; 0 nếu đã đạt hoặc freeship tắt", example = "300000")
    long amountToFreeShip,

    @Schema(description = "Tổng đơn = tiền hàng + phí ship, VND", example = "230000")
    long total
) {
}
