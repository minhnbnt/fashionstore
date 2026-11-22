package com.minhnbnt.fashionstore.dto.qna;

import io.swagger.v3.oas.annotations.media.Schema;

public record SellerQnaStatsResponse(
    @Schema(description = "Tổng số câu hỏi")
    long totalQuestions,

    @Schema(description = "Số câu hỏi chưa trả lời")
    long unansweredCount,

    @Schema(description = "Số câu hỏi đã trả lời")
    long answeredCount,

    @Schema(description = "Số câu hỏi đã đóng")
    long closedCount,

    @Schema(description = "Tỷ lệ phản hồi của shop, tính theo phần trăm (0.0 đến 100.0)")
    double responseRate
) {
}
