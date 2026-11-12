package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Yêu cầu thêm mốc hành trình vận chuyển cho đơn đang giao")
public record AddOrderTrackingEventRequest(
    @NotBlank(message = "Tracking status code is required")
    @Size(max = 40, message = "Tracking status code must not exceed 40 characters")
    @Schema(
        description = "Mã mốc hành trình (IN_TRANSIT, SORTING_FACILITY, OUT_FOR_DELIVERY, CUSTOM)",
        example = "IN_TRANSIT"
    )
    String statusCode,

    @NotBlank(message = "Tracking event title is required")
    @Size(max = 200, message = "Tracking event title must not exceed 200 characters")
    @Schema(
        description = "Tiêu đề ngắn hiển thị trên timeline vận chuyển",
        example = "Đang trung chuyển tại kho Hà Nội"
    )
    String title,

    @Nullable
    @Size(max = 500, message = "Location must not exceed 500 characters")
    @Schema(description = "Kho hoặc địa điểm cập nhật", nullable = true, example = "Kho Hà Nội")
    String location,

    @Nullable
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Mô tả chi tiết thêm", nullable = true)
    String description
) {
}

