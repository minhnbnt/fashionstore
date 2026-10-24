package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record MyPermissionsResponse(
    @Schema(description = "Người dùng hiện tại là chủ shop") boolean isOwner,
    @Schema(description = "Danh sách tên quyền đã sắp xếp", example = "[\"ORDER_READ\", \"REVIEW_REPLY\"]")
    List<String> permissions
) {
}
