package com.minhnbnt.fashionstore.dto.qna;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateQuestionRequest(
    @Schema(
        description = "Nội dung câu hỏi (1-1000 ký tự)",
        example = "Sản phẩm này có kèm phụ kiện không shop?"
    )
    @NotBlank
    @Size(max = 1000)
    String content
) {
}
