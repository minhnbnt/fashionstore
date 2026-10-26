package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record InvitationDetailResponse(
    @Schema(description = "Id shop gửi lời mời")
    UUID shopId,

    @Schema(description = "Tên shop gửi lời mời", example = "Thời trang An Nhiên")
    String shopName,

    @Schema(description = "Tên vai trò được mời", example = "Quản lý (Manager)")
    String roleName,

    @Schema(description = "Email tài khoản được mời", example = "staff@example.com")
    String email,

    @Schema(description = "Thời điểm lời mời hết hạn")
    Instant expiresAt,

    @Schema(description = "Trạng thái lời mời", example = "PENDING")
    String status
) {
}
