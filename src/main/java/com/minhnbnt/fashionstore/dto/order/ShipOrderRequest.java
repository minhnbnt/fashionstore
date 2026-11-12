package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Thông tin vận chuyển khi shop giao đơn cho ĐVVC")
public record ShipOrderRequest(
    @Nullable
    @Size(max = 100, message = "Shipping carrier must not exceed 100 characters")
    @Schema(
        description = "Tên đơn vị vận chuyển (mặc định GHN nếu bỏ trống)",
        nullable = true,
        example = "GHN"
    )
    String shippingCarrier,

    @Nullable
    @Size(max = 100, message = "Tracking code must not exceed 100 characters")
    @Schema(
        description = "Mã vận đơn (tự sinh VN-<code đơn> nếu bỏ trống)",
        nullable = true,
        example = "GHN123456"
    )
    String trackingCode
) {
}

