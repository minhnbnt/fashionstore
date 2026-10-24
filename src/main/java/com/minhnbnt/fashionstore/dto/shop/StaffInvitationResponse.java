package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record StaffInvitationResponse(
    @Schema(description = "Id lời mời")
    UUID id,

    @Schema(description = "Id shop gửi lời mời")
    UUID shopId,

    @Schema(description = "Email tài khoản được mời", example = "staff@example.com")
    String email,

    @Schema(description = "Id vai trò gán khi chấp nhận")
    UUID roleId,

    @Schema(description = "Id tài khoản được mời, null với lời mời cũ chưa gắn tài khoản")
    @Nullable UUID inviteeId,

    @Schema(description = "Token dùng cho liên kết chấp nhận lời mời")
    String token,

    @Schema(description = "Thời điểm lời mời hết hạn")
    Instant expiresAt,

    @Schema(description = "Thời điểm chấp nhận, null nếu chưa chấp nhận")
    @Nullable Instant acceptedAt,

    @Schema(description = "Thời điểm tạo lời mời")
    Instant createdAt
) {
}
