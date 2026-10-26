package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Phân tích khách hàng mua sắm tại shop")
public record CustomerStatsResponse(
    @Schema(description = "Tổng số khách hàng từng mua", example = "320")
    long totalCustomers,

    @Schema(description = "Số khách hàng mới (chỉ 1 đơn)", example = "240")
    long newCustomers,

    @Schema(description = "Số khách hàng quay lại (> 1 đơn)", example = "80")
    long returningCustomers,

    @Schema(description = "Tỷ lệ giữ chân khách hàng (retention rate)", example = "0.25")
    double retentionRate,

    @Schema(description = "Giá trị trung bình một đơn hàng - AOV (VND)", example = "450000")
    long avgOrderValue,

    @Schema(description = "Tổng số người theo dõi shop", example = "1250")
    long totalFollowers,

    @Schema(description = "Thay đổi số follower so với tháng trước", example = "150")
    long followerChange
) {}
