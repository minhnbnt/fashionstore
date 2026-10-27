package com.minhnbnt.fashionstore.dto.shop;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Schema(description = "Yêu cầu truy vấn sản phẩm của shop")
public record ShopProductQueryRequest(
    @Schema(description = "Từ khóa tìm kiếm trong shop", nullable = true)
    @Nullable String q,

    @Schema(description = "Loại bộ sưu tập nổi bật", nullable = true)
    @Nullable ShopProductCollectionType collection,

    @Schema(description = "Id danh mục cần lọc", nullable = true)
    @Nullable UUID categoryId,

    @Schema(description = "Kiểu sắp xếp: NEWEST, BEST_SELLER, PRICE_ASC, PRICE_DESC", nullable = true)
    @Nullable ShopProductSort sort,

    @Schema(description = "Số trang, đếm từ 0")
    int page,

    @Schema(description = "Số lượng sản phẩm mỗi trang (tối đa 50)")
    int limit
) {}
