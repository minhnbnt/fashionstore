package com.minhnbnt.fashionstore.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

public record NotificationCountResponse(
    @Schema(description = "Số lượng thông báo chưa đọc", example = "3")
    long unread,

    @Schema(description = "Số lượng chưa đọc theo từng nhóm (ORDER, ACTIVITY, SELLING)")
    Map<String, Long> byCategory
) {
    public NotificationCountResponse(long unread) {
        this(unread, Map.of());
    }
}
