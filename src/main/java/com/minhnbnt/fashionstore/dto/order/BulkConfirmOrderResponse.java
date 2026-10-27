package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

public record BulkConfirmOrderResponse(
    @Schema(description = "Tổng số đơn yêu cầu xác nhận", example = "5")
    int totalRequested,

    @Schema(description = "Số đơn xác nhận thành công", example = "4")
    int successCount,

    @Schema(description = "Số đơn xác nhận thất bại", example = "1")
    int failureCount,

    @Schema(description = "Danh sách ID đơn hàng xác nhận thành công")
    List<UUID> successfulOrderIds,

    @Schema(description = "Danh sách chi tiết các đơn thất bại")
    List<BulkConfirmFailureItem> failures
) {
}
