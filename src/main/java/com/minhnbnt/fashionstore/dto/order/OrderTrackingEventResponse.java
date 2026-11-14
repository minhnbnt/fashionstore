package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Mốc hành trình di chuyển gói hàng")
public record OrderTrackingEventResponse(
    @Schema(description = "ID mốc hành trình")
    UUID id,

    @Schema(description = "Mã trạng thái hành trình", example = "IN_TRANSIT")
    String statusCode,

    @Schema(description = "Tiêu đề hiển thị", example = "Đang trung chuyển")
    String title,

    @Nullable
    @Schema(description = "Địa điểm ghi nhận", nullable = true, example = "Kho Đà Nẵng")
    String location,

    @Nullable
    @Schema(description = "Mô tả chi tiết", nullable = true)
    String description,

    @Schema(description = "Thời điểm xảy ra sự kiện")
    Instant eventTime,

    @Schema(description = "Thời điểm tạo bản ghi")
    Instant createdAt
) {
}

