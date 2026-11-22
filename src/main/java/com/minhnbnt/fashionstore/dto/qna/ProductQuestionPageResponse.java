package com.minhnbnt.fashionstore.dto.qna;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record ProductQuestionPageResponse(
    @Schema(description = "Danh sách câu hỏi")
    List<ProductQuestionResponse> items,

    @Schema(description = "Tổng số câu hỏi")
    long total,

    @Schema(description = "Trang hiện tại (0-indexed)")
    int page,

    @Schema(description = "Kích thước trang")
    int size,

    @Schema(
        description = "Người dùng hiện tại có đủ điều kiện trả lời với vai trò "
            + "người mua xác thực hay không"
    )
    boolean canAnswerAsBuyer,

    @Schema(description = "Độ dài ký tự tối đa của câu hỏi / câu trả lời")
    int maxContentLength
) {
}
