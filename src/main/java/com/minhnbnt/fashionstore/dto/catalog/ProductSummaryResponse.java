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
    @Nullable Double rank,
    @Schema(description = "Giá gốc cao nhất khi có khuyến mãi (VND), null nếu không giảm giá", nullable = true)
    @Nullable Long compareAtPrice,
    @Schema(description = "Điểm đánh giá trung bình (1-5), null nếu chưa có đánh giá", nullable = true)
    @Nullable Double averageRating,
    @Schema(description = "Tổng số lượt đánh giá")
    long reviewCount,
    @Schema(description = "Tổng số lượng sản phẩm đã bán (đơn chưa hủy)")
    long soldCount,
    @Schema(description = "Tên gian hàng bán sản phẩm", nullable = true)
    @Nullable String shopName,
    @Schema(description = "Slug gian hàng để dẫn tới trang storefront", nullable = true)
    @Nullable String shopSlug,
    @Schema(description = "Tổng số lượng tồn kho của các biến thể")
    long totalStock
) {
    public ProductSummaryResponse(
        UUID id,
        UUID shopId,
        String name,
        String slug,
        @Nullable Long minPrice,
        @Nullable String defaultImageUrl,
        @Nullable Double rank
    ) {
        this(id, shopId, name, slug, minPrice, defaultImageUrl, rank, null, null, 0L, 0L, null, null, 0L);
    }
}
