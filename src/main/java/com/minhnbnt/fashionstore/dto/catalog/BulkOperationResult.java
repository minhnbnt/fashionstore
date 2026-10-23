package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record BulkOperationResult(
    @Schema(description = "Số lượng sản phẩm xử lý thành công", example = "5")
    int successCount,

    @Schema(description = "Số lượng sản phẩm xử lý thất bại hoặc bị bỏ qua", example = "1")
    int failedCount,

    @Schema(description = "Danh sách thông điệp chi tiết / cảnh báo")
    List<String> messages
) {
}
