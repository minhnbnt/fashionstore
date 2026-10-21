package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.admin.DismissReportsResponse;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductReportRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductReportResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductReportRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ProductReport;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ReportStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.UUID;

/**
 * Người mua báo cáo sản phẩm nghi vi phạm; admin bác bỏ khi xác minh không vi phạm.
 * Gỡ sản phẩm đóng báo cáo ở {@link CatalogService#banProduct}.
 */
@Service
@RequiredArgsConstructor
public class ProductReportService {

    private static final String DUPLICATE_REPORT = "You already have an open report for this product";

    private final ProductRepository products;

    private final ProductReportRepository reports;

    private final ShopService shops;

    @Transactional
    public ProductReportResponse report(
        @NonNull UUID reporterId,
        @NonNull UUID productId,
        @NonNull CreateProductReportRequest request
    ) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        // Chỉ báo cáo được thứ người mua đang nhìn thấy trên sàn, giống điều kiện của trang chi tiết.
        if (product.getStatus() != ProductStatus.ACTIVE || !shops.isShopApproved(product.getShopId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (shops.isMember(product.getShopId(), reporterId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Shop members cannot report their own products"
            );
        }
        if (reports.hasOpenUserReport(productId, reporterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_REPORT);
        }

        var description = Objects.requireNonNullElse(request.description(), "").strip();
        var report = ProductReport.byUser(productId, reporterId, request.reason(), description);
        try {
            reports.save(report);
        } catch (DataIntegrityViolationException e) {
            // Hai request cùng lúc vượt qua bước kiểm tra trên, partial unique index chặn lại.
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_REPORT);
        }

        return new ProductReportResponse(
            report.getId(),
            report.getReason().name(),
            report.getStatus().name(),
            report.getCreatedAt()
        );
    }

    @Transactional
    public DismissReportsResponse dismiss(@NonNull UUID adminId, @NonNull UUID productId, @Nullable String note) {
        if (!products.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        var dismissed = reports.resolveOpen(productId, ReportStatus.DISMISSED.name(), adminId, blankToNull(note));
        if (dismissed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product has no open reports");
        }

        return new DismissReportsResponse(dismissed);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return reports.countOpen();
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
