package com.minhnbnt.fashionstore.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record CheckoutPreviewResponse(
    @Schema(description = "Từng đơn sẽ tạo, mỗi shop 1 đơn, theo thứ tự món trong giỏ")
    List<ShopCheckoutPreview> shops,

    @Schema(description = "Tổng tiền hàng các đơn, VND", example = "200000")
    long subtotal,

    @Schema(description = "Tổng phí ship các đơn, VND", example = "30000")
    long shippingFee,

    @Schema(description = "Tổng thanh toán các đơn sau giảm giá, VND", example = "230000")
    long total,

    @Schema(description = "Tổng số tiền giảm giá từ voucher các shop, VND", example = "50000")
    long discountAmount
) {

    public CheckoutPreviewResponse(
        List<ShopCheckoutPreview> shops,
        long subtotal,
        long shippingFee,
        long total
    ) {
        this(shops, subtotal, shippingFee, total, 0L);
    }
}
