package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.model.OrderAlert;
import com.minhnbnt.fashionstore.model.OrderStatus;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bộ lọc màn giám sát đơn admin, null = không lọc.
 * {@code from}/{@code to} là ngày đặt theo giờ Việt Nam, tính trọn ngày.
 */
public record AdminOrderFilter(
    @Nullable OrderStatus status,

    @Nullable OrderAlert alert,

    @Nullable UUID shopId,

    @Nullable UUID buyerId,

    @Nullable String query,

    @Nullable LocalDate from,

    @Nullable LocalDate to
) {
}
