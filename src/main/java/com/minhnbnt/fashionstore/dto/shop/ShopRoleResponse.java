package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ShopRoleResponse(
    @Schema(description = "Id vai trò")
    UUID id,

    @Schema(description = "Id shop sở hữu vai trò")
    UUID shopId,

    @Schema(description = "Tên vai trò", example = "Quản lý (Manager)")
    String name,

    @Schema(description = "Danh sách mã quyền thuộc vai trò", example = "[\"ORDER_READ\", \"INVENTORY_WRITE\"]")
    List<String> permissions,

    @Schema(description = "Cờ vai trò mặc định của hệ thống")
    boolean isSystem,

    @Schema(description = "Thời điểm tạo vai trò")
    Instant createdAt
) {
}

