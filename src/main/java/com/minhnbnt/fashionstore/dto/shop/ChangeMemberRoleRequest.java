package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ChangeMemberRoleRequest(
    @Schema(description = "Id vai trò mới")
    @NotNull
    UUID roleId
) {
}
