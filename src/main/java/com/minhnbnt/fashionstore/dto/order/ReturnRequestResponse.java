package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnRequestResponse(
    @Schema(description = "ID yêu cầu đổi/trả")
    UUID id,

    @Schema(description = "ID đơn hàng")
    UUID orderId,

    @Schema(description = "Mã đơn hàng")
    String orderCode,

    @Schema(description = "Trạng thái đơn hàng")
    String orderStatus,

    @Schema(description = "ID shop")
    UUID shopId,

    @Schema(description = "ID người mua")
    UUID buyerId,

    @Schema(description = "Lý do đổi/trả")
    String reason,

    @Schema(description = "Mô tả chi tiết", nullable = true)
    @Nullable String description,

    @Schema(description = "Danh sách S3 key ảnh/video bằng chứng")
    List<String> evidenceKeys,

    @Schema(description = "Trạng thái yêu cầu đổi/trả")
    String status,

    @Schema(description = "Phương án xử lý", nullable = true)
    @Nullable String resolution,

    @Schema(description = "Số tiền hoàn", nullable = true)
    @Nullable Long refundAmount,

    @Schema(description = "Ghi chú của shop", nullable = true)
    @Nullable String sellerNote,

    @Schema(description = "Thời điểm tạo yêu cầu")
    Instant createdAt,

    @Schema(description = "Thời điểm cập nhật gần nhất")
    Instant updatedAt
) {
}
