package com.minhnbnt.fashionstore.dto.review;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ReviewHelpfulResponse(
    @Schema(description = "ID đánh giá") UUID reviewId,
    @Schema(description = "Tổng số lượt hữu ích sau thao tác") int helpfulCount,
    @Schema(description = "Trạng thái đã đánh dấu hữu ích của người dùng hiện tại") boolean helpfulByMe
) {
}

