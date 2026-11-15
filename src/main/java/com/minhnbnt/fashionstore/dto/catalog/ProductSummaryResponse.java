package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record ProductSummaryResponse(
    @Schema UUID id,
    @Schema UUID shopId,
    @Schema String name,
    @Schema String slug,
    @Schema(description = "Giá variant thấp nhất (VND), null nếu chưa có variant", nullable = true)
    @Nullable Long minPrice,
    @Schema(description = "URL ảnh đại diện, null nếu chưa có", nullable = true)
    @Nullable String defaultImageUrl,
    @Schema(description = "Khoảng cách BM25, càng nhỏ càng khớp. Null khi không có từ khóa", nullable = true)
    @Nullable Double rank
) {
}
