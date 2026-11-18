package com.minhnbnt.fashionstore.dto.qna;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateQuestionRequest(
    @Schema(
        description = "Nội dung câu hỏi chỉnh sửa (1-1000 ký tự)",
        example = "Sản phẩm này có bảo hành bao lâu vậy shop?"
    )
    @NotBlank
    @Size(max = 1000)
    String content
) {
}
