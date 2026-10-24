package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.AdminProductDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordCreatedResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordResponse;
import com.minhnbnt.fashionstore.dto.admin.CreateBannedKeywordRequest;
import com.minhnbnt.fashionstore.dto.admin.DismissReportsResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.features.catalog.BannedKeywordService;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.ProductReportService;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Kiểm duyệt sản phẩm toàn sàn: hàng đợi báo cáo, xem chi tiết, gỡ vi phạm, khôi phục, bác bỏ báo cáo,
 * quản lý từ khóa cấm. Luật nghiệp vụ nằm ở catalog.
 */
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final CatalogService catalog;

    private final ProductReportService reports;

    private final BannedKeywordService keywords;

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> search(
        @Nullable ProductStatus status,
        @Nullable UUID shopId,
        @Nullable String query,
        boolean reported,
        int page,
        int size
    ) {
        return catalog.searchForAdmin(status, shopId, query, reported, page, size);
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

    @Transactional
    public AdminProductResponse approve(@NonNull UUID adminId, @NonNull UUID productId) {
        return catalog.approveProduct(adminId, productId);
    }

    @Transactional
    public AdminProductResponse reject(@NonNull UUID adminId, @NonNull UUID productId, @NonNull String reason) {
        return catalog.rejectProduct(adminId, productId, reason);
    }

    @Transactional
    public DismissReportsResponse dismissReports(
        @NonNull UUID adminId,
        @NonNull UUID productId,
        @Nullable String note
    ) {
        return reports.dismiss(adminId, productId, note);
    }

    @Transactional(readOnly = true)
    public List<BannedKeywordResponse> listKeywords() {
        return keywords.list();
    }

    @Transactional
    public BannedKeywordCreatedResponse addKeyword(
        @NonNull UUID adminId,
        @NonNull CreateBannedKeywordRequest request
    ) {
        return keywords.add(adminId, request);
    }

    @Transactional
    public void deleteKeyword(@NonNull UUID keywordId) {
        keywords.delete(keywordId);
    }
}
