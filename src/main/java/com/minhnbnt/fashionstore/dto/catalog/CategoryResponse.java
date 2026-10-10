package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CategoryResponse(
    @Schema UUID id,
    @Schema String name,
    @Schema String slug,
    @Schema Integer sortOrder
) {
}
