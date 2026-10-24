package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Báo cáo doanh thu chi tiết theo chu kỳ thời gian")
public record RevenueDetailResponse(
    @Schema(description = "Chu kỳ thống kê (day, week, month, year)", example = "day")
    String period,

    @Schema(description = "Danh sách các điểm dữ liệu doanh thu")
    List<Point> points
) {
    @Schema(description = "Điểm dữ liệu doanh thu tại một mốc thời gian")
    public record Point(
        @Schema(description = "Nhãn thời gian (VD: 2026-10-24, 2026-W43, 2026-10)", example = "2026-10-24")
        String label,

        @Schema(description = "Doanh thu tại mốc này (VND)", example = "1500000")
        long revenue,

        @Schema(description = "Số đơn hàng tại mốc này", example = "5")
        long orders
    ) {}
}
