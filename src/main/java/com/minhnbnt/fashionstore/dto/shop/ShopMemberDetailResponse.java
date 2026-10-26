package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record ShopMemberDetailResponse(
    @Schema(description = "Id shop")
    UUID shopId,

    @Schema(description = "Id tài khoản thành viên")
    UUID userId,

    @Schema(description = "Email tài khoản thành viên", example = "staff@example.com")
    String email,

    @Schema(description = "Tên hiển thị của thành viên", example = "Nguyen Van A")
    String displayName,

    @Schema(description = "Vai trò trong shop", allowableValues = {"OWNER", "STAFF"})
    String role,

    @Schema(description = "Id vai trò tùy chỉnh, null với OWNER hoặc STAFF chưa gán vai trò")
    @Nullable UUID roleId,

    @Schema(description = "Tên vai trò hiển thị")
    @Nullable String roleName,

    @Schema(description = "Id người mời vào shop")
    @Nullable UUID invitedBy,

    @Schema(description = "Thời điểm được mời")
    @Nullable Instant invitedAt,

    @Schema(description = "Trạng thái thành viên", allowableValues = {"ACTIVE", "DEACTIVATED"})
    String status,

    @Schema(description = "Thời điểm tham gia shop")
    Instant joinedAt
) {
}
