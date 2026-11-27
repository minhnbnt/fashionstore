package com.minhnbnt.fashionstore.dto.admin;

/**
 * Tốc độ xử lý đơn và độ trung thành người mua trong một cửa sổ thời gian.
 * Giờ trung bình = 0 khi chưa có đơn nào đi qua bước đó.
 */
public record FulfillmentTotals(
    double avgConfirmHours,
    double avgDeliveryHours,
    long activeBuyers,
    long repeatBuyers
) {
}
