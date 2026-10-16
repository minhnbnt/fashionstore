package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record ReplyReviewRequest(
    @Schema(description = "Nội dung phản hồi của shop", example = "Cảm ơn bạn đã tin tưởng ủng hộ shop!")
    @NotBlank
    String reply
) {
}
