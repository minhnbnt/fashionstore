package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

@Schema(description = "Mốc thay đổi trạng thái đơn hàng dành cho người mua")
public record BuyerOrderTimelineResponse(
    @Nullable
    @Schema(description = "Trạng thái trước đó", nullable = true, example = "PENDING")
    String fromStatus,

    @Schema(description = "Trạng thái mới", example = "CONFIRMED")
    String toStatus,

    @Schema(description = "Chủ thể thao tác (BUYER, SELLER, ADMIN, SYSTEM)", example = "SELLER")
    String actor,

    @Schema(description = "Ghi chú hiển thị trên timeline", example = "Shop đã xác nhận đơn hàng")
    String note,

    @Schema(description = "Thời điểm ghi nhận")
    Instant createdAt
) {
}

