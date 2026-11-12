package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Xu hướng đánh giá của shop theo chu kỳ thời gian")
public record SellerReviewTrendResponse(
    @Schema(description = "Chu kỳ thống kê (day, week, month)", example = "day")
    String period,

    @Schema(description = "Danh sách các điểm dữ liệu theo mốc thời gian")
    List<Point> points
) {
    @Schema(name = "SellerReviewTrendPoint", description = "Dữ liệu đánh giá tại một mốc thời gian")
    public record Point(
        @Schema(description = "Nhãn thời gian (YYYY-MM-DD, YYYY-Www, hoặc YYYY-MM)", example = "2026-11-12")
        String label,

        @Schema(description = "Số lượng đánh giá tại mốc này", example = "4")
        long reviewCount,

        @Schema(description = "Điểm sao trung bình tại mốc này", example = "4.75")
        double averageRating
    ) {
    }
}

