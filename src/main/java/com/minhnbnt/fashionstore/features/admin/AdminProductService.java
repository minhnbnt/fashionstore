package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.admin.AdminProductDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Kiểm duyệt sản phẩm toàn sàn: tìm, xem chi tiết, gỡ vi phạm, khôi phục. Luật trạng thái nằm ở catalog.
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final CatalogService catalog;

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> search(
        @Nullable ProductStatus status,
        @Nullable UUID shopId,
        @Nullable String query,
        int page,
        int size
    ) {
        return catalog.searchForAdmin(status, shopId, query, page, size);
    }

    @Transactional(readOnly = true)
    public AdminProductDetailResponse getDetail(@NonNull UUID productId) {
        return catalog.getForAdmin(productId);
    }

    @Transactional
    public AdminProductResponse ban(
        @NonNull UUID adminId,
        @NonNull UUID productId,
        @NonNull ProductViolationType violationType,
        @NonNull String reason
    ) {
        return catalog.banProduct(adminId, productId, violationType, reason);
    }

    @Transactional
    public AdminProductResponse restore(@NonNull UUID adminId, @NonNull UUID productId) {
        return catalog.restoreProduct(adminId, productId);
    }
}
