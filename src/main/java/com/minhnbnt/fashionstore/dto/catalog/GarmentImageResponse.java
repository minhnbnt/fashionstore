package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record GarmentImageResponse(
    @Schema UUID id,
    @Schema(description = "URL public của ảnh garment") String url,
    @Schema(description = "Id bản ghi upload gốc — FE echo lại khi cập nhật danh sách ảnh garment") UUID uploadId,
    @Schema Integer sortOrder
) {
}
