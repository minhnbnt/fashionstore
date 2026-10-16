package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateShopRoleRequest(
    @Schema(description = "Tên vai trò", example = "Kho vận")
    @NotBlank
    String name,

    @Schema(description = "Danh sách quyền", example = "[\"ORDER_READ\", \"INVENTORY_WRITE\"]")
    @NotEmpty
    List<String> permissions
) {
}
