package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

@Schema(description = "Yêu cầu thêm mốc hành trình vận chuyển cho đơn đang giao")
public record AddOrderTrackingEventRequest(
    @NotBlank(message = "Mã trạng thái hành trình không được để trống")
    @Size(max = 40, message = "Mã trạng thái hành trình tối đa 40 ký tự")
    @Schema(
        description = "Mã mốc hành trình (IN_TRANSIT, SORTING_FACILITY, OUT_FOR_DELIVERY, CUSTOM)",
        example = "IN_TRANSIT"
    )
    String statusCode,

    @NotBlank(message = "Tiêu đề mốc hành trình không được để trống")
    @Size(max = 200, message = "Tiêu đề mốc hành trình tối đa 200 ký tự")
    @Schema(
        description = "Tiêu đề ngắn hiển thị trên timeline vận chuyển",
        example = "Đang trung chuyển tại kho Hà Nội"
    )
    String title,

    @Nullable
    @Size(max = 500, message = "Địa điểm tối đa 500 ký tự")
    @Schema(description = "Kho hoặc địa điểm cập nhật", nullable = true, example = "Kho Hà Nội")
    String location,

    @Nullable
    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    @Schema(description = "Mô tả chi tiết thêm", nullable = true)
    String description
) {
}

