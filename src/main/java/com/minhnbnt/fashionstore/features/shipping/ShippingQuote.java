package com.minhnbnt.fashionstore.features.shipping;

/**
 * Phí ship 1 đơn (1 shop), VND.
 *
 * @param fee              phí thực thu sau freeship
 * @param originalFee      phí theo công thức trước freeship
 * @param freeShipApplied  đơn đạt ngưỡng miễn phí
 * @param amountToFreeShip tiền hàng còn thiếu để được freeship, 0 nếu đã đạt hoặc freeship tắt
 */
public record ShippingQuote(
    long fee,
    long originalFee,
    boolean freeShipApplied,
    long amountToFreeShip
) {
}
