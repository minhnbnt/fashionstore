package com.minhnbnt.fashionstore.dto.qna;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAnswerRequest(
    @Schema(
        description = "Nội dung câu trả lời chỉnh sửa (1-1000 ký tự)",
        example = "Chào bạn, sản phẩm có bảo hành chính hãng 12 tháng tại các trung tâm bảo hành."
    )
    @NotBlank
    @Size(max = 1000)
    String content
) {
}
