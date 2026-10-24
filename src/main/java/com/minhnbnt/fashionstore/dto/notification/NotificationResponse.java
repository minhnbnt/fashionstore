package com.minhnbnt.fashionstore.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    @Schema(description = "Id thông báo")
    UUID id,

    @Schema(description = "Loại thông báo, ví dụ SHOP_INVITATION")
    String type,

    @Schema(description = "Tiêu đề thông báo")
    String title,

    @Schema(description = "Nội dung chi tiết thông báo")
    String body,

    @Schema(description = "Đường dẫn thao tác trong ứng dụng nếu có", example = "/invitations/token-1")
    @Nullable String link,

    @Schema(description = "Thời điểm đọc thông báo, null nếu chưa đọc")
    @Nullable Instant readAt,

    @Schema(description = "Thời điểm tạo thông báo")
    Instant createdAt
) {
}
