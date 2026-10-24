package com.minhnbnt.fashionstore.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationCountResponse(
    @Schema(description = "Số lượng thông báo chưa đọc", example = "3")
    long unread
) {
}
