package com.minhnbnt.fashionstore.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkConfirmOrderRequest(
    @NotEmpty
    @Schema(description = "Danh sách ID đơn hàng cần xác nhận")
    List<UUID> orderIds
) {
}
