package com.minhnbnt.fashionstore.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record BannedKeywordCreatedResponse(
    BannedKeywordResponse keyword,

    @Schema(description = "Số sản phẩm đang bán/đang ẩn bị gắn cờ ngay khi thêm từ khóa")
    int flaggedProducts
) {
}
