package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record SearchFacetsResponse(
    @Schema(description = "Size có trong tập kết quả, xếp XS → XXXL rồi tới size lạ") List<String> sizes,
    @Schema(description = "Màu có trong tập kết quả, xếp A-Z") List<String> colors,
    @Schema(description = "Giá variant rẻ nhất, null nếu không có kết quả", nullable = true)
    @Nullable Long minPrice,
    @Schema(description = "Giá variant đắt nhất, null nếu không có kết quả", nullable = true)
    @Nullable Long maxPrice
) {
}
