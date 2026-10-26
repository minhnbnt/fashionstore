package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Một dòng nhật ký hoạt động của shop")
public record ShopActivityResponse(
    @Schema(description = "Id bản ghi nhật ký")
    UUID id,

    @Schema(description = "Id người thực hiện thao tác")
    UUID actorId,

    @Schema(description = "Email người thực hiện")
    String actorEmail,

    @Schema(description = "Tên hiển thị người thực hiện")
    String actorName,

    @Schema(description = "Mã hành động, ví dụ PRODUCT_CREATE")
    String action,

    @Schema(description = "Loại đối tượng bị tác động", nullable = true)
    @Nullable String targetType,

    @Schema(description = "Id đối tượng bị tác động", nullable = true)
    @Nullable UUID targetId,

    @Schema(description = "Mô tả ngắn gọn, không chứa nội dung chat/đánh giá", nullable = true)
    @Nullable String detail,

    @Schema(description = "Thời điểm thao tác")
    Instant createdAt
) {
}
