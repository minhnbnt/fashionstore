package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public record CreateReviewRequest(
    @Schema(description = "ID đơn hàng đã hoàn thành chứa sản phẩm cần đánh giá")
    @NotNull
    UUID orderId,

    @Schema(description = "Điểm đánh giá 1-5 sao", example = "5")
    @Min(1)
    @Max(5)
    int stars,

    @Schema(description = "Nhận xét chi tiết (tối thiểu 10 ký tự sau khi bỏ khoảng trắng)", example = "Vải dày dặn, đường may chắc chắn và đúng kích thước mô tả.")
    @NotBlank
    @Size(max = 2000)
    String comment,

    @Schema(description = "Danh sách uploadId ảnh đính kèm (tối đa 5 ảnh, image/* trừ svg)", nullable = true)
    @Nullable
    @Size(max = 5)
    List<UUID> imageUploadIds,

    @Schema(description = "UploadId video (tối đa 1 video <= 30 giây, video/*)", nullable = true, format = "uuid")
    @Nullable
    UUID videoUploadId,

    @Schema(description = "Danh sách tag đánh giá nhanh", nullable = true)
    @Nullable
    @Size(max = 5)
    List<@NotBlank String> tags,

    @Schema(description = "Ẩn danh tên người mua khi hiển thị công khai", example = "false")
    boolean anonymous
) {
}
