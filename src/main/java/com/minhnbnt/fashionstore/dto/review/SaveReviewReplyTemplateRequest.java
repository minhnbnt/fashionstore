package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dữ liệu tạo hoặc cập nhật mẫu phản hồi nhanh")
public record SaveReviewReplyTemplateRequest(
    @Schema(description = "Tiêu đề ngắn của mẫu (tối đa 100 ký tự)", example = "Cảm ơn đánh giá 5 sao")
    @NotBlank
    @Size(max = 100)
    String title,

    @Schema(description = "Nội dung phản hồi văn bản thuần (tối đa 2000 ký tự)", example = "Cảm ơn bạn đã tin tưởng và ủng hộ shop!")
    @NotBlank
    @Size(max = 2000)
    String content
) {
}

