package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record UpdateReviewRequest(
    @Schema(description = "Điểm đánh giá 1-5 sao", example = "4")
    @Min(1)
    @Max(5)
    int stars,

    @Schema(description = "Nhận xét chi tiết (tối thiểu 10 ký tự sau khi bỏ khoảng trắng)")
    @NotBlank
    @Size(max = 2000)
    String comment,

    @Schema(description = "Danh sách S3 key ảnh đính kèm (tối đa 5 ảnh)", nullable = true)
    @Nullable
    @Size(max = 5)
    List<@NotBlank String> imageKeys,

    @Schema(description = "S3 key video thực tế (tối đa 1 video <= 30 giây)", nullable = true)
    @Nullable
    String videoKey,

    @Schema(description = "Danh sách tag đánh giá nhanh", nullable = true)
    @Nullable
    @Size(max = 5)
    List<@NotBlank String> tags,

    @Schema(description = "Ẩn danh tên người mua khi hiển thị công khai", example = "false")
    boolean anonymous
) {
}
