package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.admin.AdminProductDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.AttachImagesRequest;
import com.minhnbnt.fashionstore.dto.catalog.CategoryResponse;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductAuditLogResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSearchRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductSearchResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSort;
import com.minhnbnt.fashionstore.dto.catalog.SearchFacetsResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.AdjustStockRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkDeleteProductsRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkImportResult;
import com.minhnbnt.fashionstore.dto.catalog.BulkOperationResult;
import com.minhnbnt.fashionstore.dto.catalog.BulkUpdatePriceRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkUpdateProductStatusRequest;
import com.minhnbnt.fashionstore.dto.catalog.BulkUpdateStockRequest;
import com.minhnbnt.fashionstore.dto.catalog.InventoryItemResponse;
import com.minhnbnt.fashionstore.dto.catalog.SellerProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.StockMovementResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductAdminQueryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductAuditLogRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductImageRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductModerationHistoryRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductReportRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductSearchRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.StockMovementRepository;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.infrastructure.media.VideoMetadataService;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductAuditLog;
import com.minhnbnt.fashionstore.model.ProductImage;
import com.minhnbnt.fashionstore.model.ProductModerationHistory;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import com.minhnbnt.fashionstore.model.ReportStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.StockMovement;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final ProductRepository products;

    private final ProductVariantRepository variants;

    private final ProductImageRepository images;

    private final CategoryRepository categories;

    private final ProductSearchRepository search;

    private final StockMovementRepository movements;

    private final ProductAdminQueryRepository adminQueries;

    private final ProductModerationHistoryRepository moderationHistories;

    private final ProductReportRepository productReports;

    private final KeywordScanner keywordScanner;

    private final OrderService orderService;

    private final ShopService shops;

    private final ShopRepository shopRepository;

    private final ReviewService reviews;

    private final ShopAccessChecker access;

    private final VideoMetadataService videoMetadataService;

    private final ProductAuditLogRepository auditLogs;

    private final ObjectMapper objectMapper;

    @Transactional
    public ProductResponse createProduct(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull CreateProductRequest request
    ) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);

        requireSingleDefault(request.variants());

        for (var variant : request.variants()) {
            requireValidPrice(variant.price(), variant.compareAtPrice());
        }

        requireCategoryExists(request.categoryId());

        if (Boolean.TRUE.equals(request.tryOnEnabled())) {
            if (request.garmentKey() == null || request.garmentKey().isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Garment image is required when virtual try-on is enabled"
                );
            }
        }

        if (request.weightGrams() != null && request.weightGrams() <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package weight must be greater than 0"
            );
        }
        if ((request.lengthCm() != null && request.lengthCm() <= 0)
            || (request.widthCm() != null && request.widthCm() <= 0)
            || (request.heightCm() != null && request.heightCm() <= 0)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package dimensions must be greater than 0"
            );
        }

        videoMetadataService.validateVideoDuration(request.videoKey(), 60.0);

        var shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));

        ProductStatus initialStatus;
        if (shop.isTrusted()) {
            if ("HIDDEN".equalsIgnoreCase(request.status())) {
                initialStatus = ProductStatus.HIDDEN;
            } else {
                initialStatus = ProductStatus.ACTIVE;
            }
        } else {
            initialStatus = ProductStatus.PENDING;
        }

        var skuList = request.variants().stream()
            .map(CreateVariantRequest::sku)
            .map(String::trim)
            .toList();
        var uniqueSkuSet = new HashSet<>(skuList);
        if (uniqueSkuSet.size() < skuList.size()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Variant SKUs must be unique within the product"
            );
        }

        var candidateSlug = request.slug();
        if (candidateSlug == null || candidateSlug.isBlank()) {
            candidateSlug = request.name().trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "");
        }
        var finalSlug = resolveUniqueSlug(shopId, candidateSlug);

        var product = Product.create(
            shopId,
            request.categoryId(),
            request.name(),
            finalSlug,
            Objects.requireNonNullElse(request.description(), ""),
            initialStatus,
            request.videoKey(),
            request.garmentKey(),
            Boolean.TRUE.equals(request.tryOnEnabled()),
            request.material(),
            request.origin(),
            request.careInstructions(),
            request.sizeGuide(),
            request.weightGrams(),
            request.lengthCm(),
            request.widthCm(),
            request.heightCm(),
            userId
        );
        try {
            products.save(product);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already taken");
        }

        var createdVariants = request.variants().stream()
            .map(variant -> ProductVariant.create(
                shopId,
                product.getId(),
                variant.sku(),
                variant.size(),
                variant.color(),
                variant.price(),
                variant.compareAtPrice(),
                variant.stockQuantity(),
                variant.defaultVariant()
            ))
            .toList();

        try {
            variants.saveAll(createdVariants);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already taken");
        }

        if (request.images() != null && !request.images().isEmpty()) {
            var imageEntities = request.images().stream()
                .map(img -> ProductImage.create(
                    product.getId(),
                    img.variantId(),
                    img.s3Key(),
                    img.sortOrder()
                ))
                .toList();
            images.saveAll(imageEntities);
        }

        var variantImages = new ArrayList<ProductImage>();
        for (int i = 0; i < request.variants().size(); i++) {
            var reqVar = request.variants().get(i);
            var createdVar = createdVariants.get(i);
            if (reqVar.imageKey() != null && !reqVar.imageKey().isBlank()) {
                variantImages.add(ProductImage.create(
                    product.getId(),
                    createdVar.getId(),
                    reqVar.imageKey(),
                    0
                ));
            }
        }
        if (!variantImages.isEmpty()) {
            images.saveAll(variantImages);
        }

        keywordScanner.scan(product.getId(), product.getName(), product.getDescription());

        return toResponse(product);
    }

    @Transactional
    public ProductResponse updateProduct(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId,
        @NonNull UpdateProductRequest request
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);

        if (product.getStatus() == ProductStatus.DELETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted product cannot be modified");
        }

        if (request.categoryId() != null) {
            requireCategoryExists(request.categoryId());
        }

        if (request.videoKey() != null && !request.videoKey().isBlank()
            && !request.videoKey().equals(product.getVideoKey())) {
            videoMetadataService.validateVideoDuration(request.videoKey(), 60.0);
        }

        var changes = new ArrayList<String>();
        var oldValues = new LinkedHashMap<String, Object>();
        var newValues = new LinkedHashMap<String, Object>();

        if (request.name() != null && !request.name().equals(product.getName())) {
            changes.add("Đổi tên: " + product.getName() + " -> " + request.name());
            oldValues.put("name", product.getName());
            newValues.put("name", request.name());
        }
        if (request.categoryId() != null && !request.categoryId().equals(product.getCategoryId())) {
            changes.add("Đổi danh mục ngành hàng");
            oldValues.put("categoryId", product.getCategoryId());
            newValues.put("categoryId", request.categoryId());
        }
        if (request.description() != null && !request.description().equals(product.getDescription())) {
            changes.add("Cập nhật mô tả sản phẩm");
            oldValues.put("description", product.getDescription());
            newValues.put("description", request.description());
        }
        if (request.videoKey() != null && !request.videoKey().equals(product.getVideoKey())) {
            changes.add("Cập nhật video sản phẩm");
            oldValues.put("videoKey", product.getVideoKey());
            newValues.put("videoKey", request.videoKey());
        }
        if (request.garmentKey() != null && !request.garmentKey().equals(product.getGarmentKey())) {
            changes.add("Cập nhật ảnh garment thử đồ ảo");
            oldValues.put("garmentKey", product.getGarmentKey());
            newValues.put("garmentKey", request.garmentKey());
        }
        if (request.tryOnEnabled() != null && request.tryOnEnabled() != product.isTryOnEnabled()) {
            if (request.tryOnEnabled()) {
                changes.add("Bật thử đồ ảo");
            } else {
                changes.add("Tắt thử đồ ảo");
            }
            oldValues.put("tryOnEnabled", product.isTryOnEnabled());
            newValues.put("tryOnEnabled", request.tryOnEnabled());
        }
        if (request.status() != null && !request.status().equalsIgnoreCase(product.getStatus().name())) {
            changes.add("Đổi trạng thái: " + product.getStatus().name() + " -> " + request.status());
            oldValues.put("status", product.getStatus().name());
            newValues.put("status", request.status());
        }

        product.updateAll(
            request.name(),
            request.description(),
            request.categoryId(),
            request.videoKey(),
            request.garmentKey(),
            request.tryOnEnabled(),
            request.material(),
            request.origin(),
            request.careInstructions(),
            request.sizeGuide(),
            request.weightGrams(),
            request.lengthCm(),
            request.widthCm(),
            request.heightCm()
        );

        if (request.status() != null) {
            applyStatus(product, request.status());
        }

        if (request.images() != null) {
            images.deleteByProductId(product.getId());
            var newImages = request.images().stream()
                .map(img -> ProductImage.create(
                    product.getId(),
                    img.variantId(),
                    img.s3Key(),
                    img.sortOrder()
                ))
                .toList();
            images.saveAll(newImages);
            changes.add("Cập nhật lại " + newImages.size() + " ảnh sản phẩm");
        }

        if (request.variants() != null) {
            applyVariantUpdates(product, request.variants(), changes, oldValues, newValues);
        }

        var saved = products.save(product);
        keywordScanner.scan(saved.getId(), saved.getName(), saved.getDescription());

        var summary = String.join("; ", changes);
        if (summary.isBlank()) {
            summary = "Cập nhật sản phẩm";
        }
        auditLogs.save(ProductAuditLog.record(
            saved.getId(),
            shopId,
            userId,
            "UPDATE_PRODUCT",
            summary,
            toJson(oldValues),
            toJson(newValues)
        ));

        return toResponse(saved);
    }

    @Transactional
    public List<ImageResponse> attachImages(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId,
        @NonNull AttachImagesRequest request
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);

        for (var item : request.images()) {
            if (item.variantId() != null) {
                requireVariantInProduct(product.getId(), item.variantId());
            }
        }

        var created = request.images().stream()
            .map(item -> ProductImage.create(
                product.getId(),
                item.variantId(),
                item.s3Key(),
                item.sortOrder()
            ))
            .toList();

        var savedImages = images.saveAll(created).spliterator();

        return StreamSupport.stream(savedImages, false)
            .map(CatalogMapper::toImageResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public long countProductsByShop(@NonNull UUID shopId) {
        return products.countByShopId(shopId);
    }

    @Transactional(readOnly = true)
    public ProductResponse getDetail(@NonNull UUID productId) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (!shops.isShopApproved(product.getShopId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public ProductSearchResponse search(@NonNull ProductSearchRequest request) {
        var criteria = toCriteria(request);

        var items = search.search(criteria).stream()
            .map(CatalogMapper::toSummary)
            .toList();
        var total = search.count(criteria);

        var page = Math.max(request.page(), 0);
        return new ProductSearchResponse(items, total, page, criteria.limit());
    }

    @Transactional(readOnly = true)
    public SearchFacetsResponse facets(@Nullable String query, @Nullable String categorySlug) {
        var criteria = toCriteria(new ProductSearchRequest(
            query,
            categorySlug,
            null,
            null,
            null,
            null,
            null,
            0,
            1
        ));

        var priceRange = search.priceRange(criteria);

        return new SearchFacetsResponse(
            search.sizes(criteria),
            search.colors(criteria),
            priceRange.min(),
            priceRange.max()
        );
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categories.findAllByOrderBySortOrderAsc().stream()
            .map(CatalogMapper::toCategoryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductResponse> searchForAdmin(
        @Nullable ProductStatus status,
        @Nullable UUID shopId,
        @Nullable String query,
        boolean reported,
        int page,
        int size
    ) {
        var items = adminQueries.search(status, shopId, query, reported, page, size);
        var total = adminQueries.count(status, shopId, query, reported);
        return new PageResponse<>(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public AdminProductDetailResponse getForAdmin(@NonNull UUID productId) {
        var summary = requireAdminProduct(productId);
        var product = products.findById(productId).orElseThrow();

        var variantResponses = variants.findByProductIdOrderByCreatedAtAsc(productId).stream()
            .map(CatalogMapper::toVariantResponse)
            .toList();
        var imageResponses = images.findByProductIdOrderBySortOrderAsc(productId).stream()
            .map(CatalogMapper::toImageResponse)
            .toList();

        return new AdminProductDetailResponse(
            summary,
            product.getDescription(),
            variantResponses,
            imageResponses,
            adminQueries.history(productId),
            adminQueries.reports(productId),
            product.getVideoKey(),
            product.getGarmentKey(),
            product.isTryOnEnabled(),
            product.getMaterial(),
            product.getOrigin(),
            product.getCareInstructions(),
            product.getSizeGuide(),
            product.getWeightGrams(),
            product.getLengthCm(),
            product.getWidthCm(),
            product.getHeightCm()
        );
    }

    /**
     * Admin gỡ sản phẩm vi phạm: biến mất khỏi search/PDP/checkout ngay, đơn đang xử lý vẫn tiếp tục.
     * Mọi báo cáo đang mở của sản phẩm được đóng là ACTION_TAKEN với lý do gỡ.
     */
    @Transactional
    public AdminProductResponse banProduct(
        @NonNull UUID adminId,
        @NonNull UUID productId,
        @NonNull ProductViolationType violationType,
        @NonNull String reason
    ) {
        var product = lockedProduct(productId);
        var from = product.getStatus();

        product.ban(violationType, reason);
        products.save(product);

        moderationHistories.save(ProductModerationHistory.record(
            productId, from, product.getStatus(), violationType, adminId, reason
        ));
        productReports.resolveOpen(productId, ReportStatus.ACTION_TAKEN.name(), adminId, reason);
        return requireAdminProduct(productId);
    }

    @Transactional
    public AdminProductResponse restoreProduct(@NonNull UUID adminId, @NonNull UUID productId) {
        var product = lockedProduct(productId);
        var from = product.getStatus();

        product.restore();
        products.save(product);

        moderationHistories.save(ProductModerationHistory.record(
            productId, from, product.getStatus(), null, adminId, ""
        ));
        return requireAdminProduct(productId);
    }

    @Transactional
    public AdminProductResponse approveProduct(@NonNull UUID adminId, @NonNull UUID productId) {
        var product = lockedProduct(productId);
        var from = product.getStatus();

        product.approve();
        products.save(product);

        moderationHistories.save(ProductModerationHistory.record(
            productId, from, product.getStatus(), null, adminId, ""
        ));
        return requireAdminProduct(productId);
    }

    @Transactional
    public AdminProductResponse rejectProduct(@NonNull UUID adminId, @NonNull UUID productId, @NonNull String reason) {
        var product = lockedProduct(productId);
        var from = product.getStatus();

        product.reject(reason);
        products.save(product);

        moderationHistories.save(ProductModerationHistory.record(
            productId, from, product.getStatus(), ProductViolationType.OTHER, adminId, reason
        ));
        return requireAdminProduct(productId);
    }

    private Product lockedProduct(UUID productId) {
        var product = products.findLockedById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
    }

    private AdminProductResponse requireAdminProduct(UUID productId) {
        var product = adminQueries.findById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return product;
    }

    private void applyStatus(Product product, String status) {
        if (status.equalsIgnoreCase(product.getStatus().name())) {
            return;
        }

        if ("HIDDEN".equalsIgnoreCase(status)) {
            product.hide();
            return;
        }

        if ("ACTIVE".equalsIgnoreCase(status)) {
            product.activate();
        }
    }

    private void applyVariantUpdates(
        Product product,
        List<UpdateVariantRequest> updates,
        List<String> changes,
        Map<String, Object> oldValues,
        Map<String, Object> newValues
    ) {
        for (var update : updates) {
            if (update.id() != null) {
                var variant = requireVariantInProduct(product.getId(), update.id());

                var price = update.price();
                if (price == null) {
                    price = variant.getPrice();
                }
                var compareAtPrice = update.compareAtPrice();
                if (compareAtPrice == null) {
                    compareAtPrice = variant.getCompareAtPrice();
                }
                requireValidPrice(price, compareAtPrice);

                if (Boolean.TRUE.equals(update.defaultVariant()) && !variant.isDefaultVariant()) {
                    unsetCurrentDefault(product.getId(), variant.getId());
                }

                if (Boolean.FALSE.equals(update.defaultVariant()) && variant.isDefaultVariant()) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Cannot unset the only default variant"
                    );
                }

                if (update.stockQuantity() != null && update.stockQuantity() != variant.getStockQuantity()) {
                    changes.add("Tồn kho " + variant.getSize() + "/" + variant.getColor() + ": "
                        + variant.getStockQuantity() + " -> " + update.stockQuantity());
                    oldValues.put("stock_" + variant.getId(), variant.getStockQuantity());
                    newValues.put("stock_" + variant.getId(), update.stockQuantity());
                }

                if (update.price() != null && !update.price().equals(variant.getPrice())) {
                    changes.add("Giá bán " + variant.getSize() + "/" + variant.getColor() + ": "
                        + variant.getPrice() + " -> " + update.price());
                    oldValues.put("price_" + variant.getId(), variant.getPrice());
                    newValues.put("price_" + variant.getId(), update.price());
                }

                variant.updateDetails(
                    update.sku(),
                    update.size(),
                    update.color(),
                    update.price(),
                    update.compareAtPrice(),
                    update.stockQuantity(),
                    update.defaultVariant()
                );

                variants.save(variant);
            } else {
                if (update.sku() == null || update.sku().isBlank()
                    || update.size() == null || update.size().isBlank()
                    || update.color() == null || update.color().isBlank()
                    || update.price() == null) {
                    throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "New variant requires sku, size, color and price"
                    );
                }
                var existingSku = variants.findByShopIdAndSku(product.getShopId(), update.sku().trim()).orElse(null);
                if (existingSku != null) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "SKU already taken");
                }
                int stock = 0;
                if (update.stockQuantity() != null) {
                    stock = update.stockQuantity();
                }
                var newVar = ProductVariant.create(
                    product.getShopId(),
                    product.getId(),
                    update.sku().trim(),
                    update.size().trim(),
                    update.color().trim(),
                    update.price(),
                    update.compareAtPrice(),
                    stock,
                    Boolean.TRUE.equals(update.defaultVariant())
                );
                if (Boolean.TRUE.equals(update.defaultVariant())) {
                    unsetCurrentDefault(product.getId(), newVar.getId());
                }
                variants.save(newVar);
                changes.add("Thêm biến thể mới: " + newVar.getSize() + " - " + newVar.getColor() + " (SKU: " + newVar.getSku() + ")");
                newValues.put("new_variant_" + newVar.getSku(), newVar.getSize() + "/" + newVar.getColor());
            }
        }
    }

    private @Nullable String toJson(@Nullable Object value) {
        if (value == null) {
            return null;
        }

        return objectMapper.writeValueAsString(value);
    }

    @Transactional(readOnly = true)
    public ProductResponse getSellerProductDetail(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_READ);

        return toResponse(product);
    }

    @Transactional(readOnly = true)
    public List<ProductAuditLogResponse> getAuditLogs(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_READ);

        return auditLogs.findByProductIdOrderByCreatedAtDesc(productId).stream()
            .map(log -> new ProductAuditLogResponse(
                log.getId(),
                log.getProductId(),
                log.getShopId(),
                log.getChangedBy(),
                log.getAction(),
                log.getChangesSummary(),
                log.getOldValues(),
                log.getNewValues(),
                log.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public ProductResponse toggleProductStatus(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId
    ) {
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);

        var oldStatus = product.getStatus().name();
        String newStatus;
        if (product.getStatus() == ProductStatus.ACTIVE) {
            product.hide();
            newStatus = "HIDDEN";
        } else if (product.getStatus() == ProductStatus.HIDDEN) {
            product.activate();
            newStatus = "ACTIVE";
        } else {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot toggle status of " + product.getStatus() + " product"
            );
        }
        var saved = products.save(product);

        auditLogs.save(ProductAuditLog.record(
            productId,
            shopId,
            userId,
            "TOGGLE_STATUS",
            "Chuyển trạng thái sản phẩm từ " + oldStatus + " sang " + newStatus,
            toJson(Map.of("status", oldStatus)),
            toJson(Map.of("status", newStatus))
        ));

        return toResponse(saved);
    }

    private void unsetCurrentDefault(UUID productId, UUID exceptVariantId) {
        for (var variant : variants.findByProductIdOrderByCreatedAtAsc(productId)) {
            if (variant.isDefaultVariant() && !exceptVariantId.equals(variant.getId())) {
                variant.unsetDefault();
                variants.save(variant);
            }
        }
    }

    private ProductVariant requireVariantInProduct(UUID productId, UUID variantId) {
        var variant = variants.findById(variantId).orElse(null);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }
        if (!variant.getProductId().equals(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }

        return variant;
    }

    private static ProductSearchRepository.Criteria toCriteria(ProductSearchRequest request) {
        var minPrice = request.minPrice();
        var maxPrice = request.maxPrice();
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "minPrice must not be greater than maxPrice"
            );
        }

        var query = blankToNull(request.q());
        var limit = Math.clamp(request.limit(), 1, 100);
        var page = Math.max(request.page(), 0);

        return new ProductSearchRepository.Criteria(
            query,
            blankToNull(request.category()),
            minPrice,
            maxPrice,
            blankToNull(request.size()),
            blankToNull(request.color()),
            resolveSort(request.sort(), query),
            (long) page * limit,
            limit
        );
    }

    private static ProductSort resolveSort(@Nullable ProductSort requested, @Nullable String query) {
        if (requested == null && query != null) {
            return ProductSort.RELEVANCE;
        }
        if (requested == null) {
            return ProductSort.NEWEST;
        }
        // Không có từ khóa thì không có điểm để xếp theo độ khớp.
        if (requested == ProductSort.RELEVANCE && query == null) {
            return ProductSort.NEWEST;
        }

        return requested;
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return null;
        }

        return value.strip();
    }

    private void requireCategoryExists(@Nullable UUID categoryId) {
        if (categoryId == null) {
            return;
        }
        if (!categories.existsById(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category does not exist");
        }
    }

    private static void requireSingleDefault(List<CreateVariantRequest> variants) {

        var count = variants.stream()
            .filter(CreateVariantRequest::defaultVariant)
            .count();

        if (count != 1) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Exactly one default variant is required"
            );
        }
    }

    private static void requireValidPrice(long price, @Nullable Long compareAtPrice) {
        if (price < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Price must not be negative"
            );
        }
        if (compareAtPrice != null && compareAtPrice < 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Compare-at price must not be negative"
            );
        }
    }

    private ProductResponse toResponse(Product product) {

        var variantResponses = variants.findByProductIdOrderByCreatedAtAsc(product.getId()).stream()
            .map(CatalogMapper::toVariantResponse)
            .toList();

        var imageResponses = images.findByProductIdOrderBySortOrderAsc(product.getId()).stream()
            .map(CatalogMapper::toImageResponse)
            .toList();

        var summary = reviews.ratingSummary(product.getId());

        return CatalogMapper.toResponse(product, variantResponses, imageResponses, summary.averageRating(), summary.reviewCount());
    }

    @Transactional
    public void deleteProduct(@NonNull UUID userId, @NonNull UUID shopId, @NonNull UUID productId) {
        access.requireShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (orderService.hasActiveOrdersForProduct(productId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete product with active orders");
        }
        product.delete();
        products.save(product);

        var variantList = variants.findByProductIdOrderByCreatedAtAsc(productId);
        var delSuffix = "-del-" + UUID.randomUUID().toString().substring(0, 8);
        for (var v : variantList) {
            v.archiveSku(delSuffix);
        }
        variants.saveAll(variantList);
    }

    @Transactional
    public void deleteProductImage(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID productId,
        @NonNull UUID imageId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);
        var product = products.findByShopIdAndId(shopId, productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        var image = images.findByProductIdAndId(productId, imageId).orElse(null);
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found");
        }
        images.delete(image);
    }

    @Transactional(readOnly = true)
    public List<SellerProductResponse> listSellerProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @Nullable String statusFilter,
        @Nullable String q,
        int page,
        int limit
    ) {
        var status = "ALL";
        if (statusFilter != null) {
            status = statusFilter;
        }
        var search = "";
        if (q != null) {
            search = q;
        }
        return listSellerProducts(
            userId, shopId, status, null, "ALL", 0L, 0L, search, "createdAt", "desc", page, limit
        );
    }

    @Transactional(readOnly = true)
    public List<SellerProductResponse> listSellerProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull String statusFilter,
        @Nullable UUID categoryId,
        @NonNull String stockLevel,
        long minPrice,
        long maxPrice,
        @NonNull String q,
        @NonNull String sortBy,
        @NonNull String sortDir,
        int page,
        int limit
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.PRODUCT_READ);
        List<Product> all;
        var statusNeedsLookup = !statusFilter.isBlank()
            && !"ALL".equalsIgnoreCase(statusFilter)
            && !"OUT_OF_STOCK".equalsIgnoreCase(statusFilter);
        if (statusNeedsLookup) {
            try {
                var targetStatus = ProductStatus.valueOf(statusFilter.toUpperCase(Locale.ROOT));
                all = products.findByShopIdAndStatusOrderByCreatedAtDesc(shopId, targetStatus);
            } catch (IllegalArgumentException e) {
                all = products.findByShopIdOrderByCreatedAtDesc(shopId);
            }
        } else {
            all = products.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        if (!statusFilter.equalsIgnoreCase("DELETED")) {
            all = all.stream()
                .filter(p -> p.getStatus() != ProductStatus.DELETED)
                .toList();
        }

        if (categoryId != null) {
            all = all.stream()
                .filter(p -> p.getCategoryId() != null && p.getCategoryId().equals(categoryId))
                .toList();
        }

        var responses = all.stream()
            .map(this::toSellerResponse)
            .toList();

        if (!q.isBlank()) {
            var lower = q.toLowerCase(Locale.ROOT).strip();
            responses = responses.stream()
                .filter(p -> {
                    if (p.name().toLowerCase(Locale.ROOT).contains(lower)) {
                        return true;
                    }
                    if (p.slug().toLowerCase(Locale.ROOT).contains(lower)) {
                        return true;
                    }
                    if (p.variants() != null) {
                        for (var v : p.variants()) {
                            if (v.sku() != null && v.sku().toLowerCase(Locale.ROOT).contains(lower)) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .toList();
        }

        if ("OUT_OF_STOCK".equalsIgnoreCase(statusFilter)) {
            responses = responses.stream()
                .filter(p -> p.totalStock() == 0)
                .toList();
        }

        if ("OUT_OF_STOCK".equalsIgnoreCase(stockLevel)) {
            responses = responses.stream()
                .filter(p -> p.totalStock() == 0)
                .toList();
        } else if ("LOW_STOCK".equalsIgnoreCase(stockLevel)) {
            responses = responses.stream()
                .filter(p -> p.totalStock() <= 15)
                .toList();
        }

        if (minPrice > 0) {
            responses = responses.stream()
                .filter(p -> p.maxPrice() >= minPrice)
                .toList();
        }

        if (maxPrice > 0) {
            responses = responses.stream()
                .filter(p -> p.minPrice() <= maxPrice)
                .toList();
        }

        Comparator<SellerProductResponse> comparator;
        if ("stock".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparingInt(SellerProductResponse::totalStock);
        } else if ("sold".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparingInt(SellerProductResponse::soldCount);
        } else if ("price".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparingLong(SellerProductResponse::minPrice);
        } else {
            comparator = Comparator.comparing(SellerProductResponse::createdAt);
        }

        if ("asc".equalsIgnoreCase(sortDir)) {
            responses = responses.stream().sorted(comparator).toList();
        } else {
            responses = responses.stream().sorted(comparator.reversed()).toList();
        }

        var pageSize = Math.clamp(limit, 1, 100);
        var offset = (long) Math.max(page, 0) * pageSize;
        return responses.stream()
            .skip(offset)
            .limit(pageSize)
            .toList();
    }

    @Transactional
    public BulkOperationResult bulkUpdateProductStatus(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull BulkUpdateProductStatusRequest request
    ) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);
        var targetStatus = ProductStatus.valueOf(request.status());
        var successCount = 0;
        var failedCount = 0;
        var messages = new ArrayList<String>();

        for (var productId : request.productIds()) {
            var product = products.findByShopIdAndId(shopId, productId).orElse(null);
            if (product == null) {
                failedCount++;
                messages.add("Sản phẩm ID " + productId + " không tồn tại");
                continue;
            }
            if (product.getStatus() == ProductStatus.DELETED) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' đã bị xóa, không thể cập nhật");
                continue;
            }
            if (product.getStatus() == ProductStatus.BANNED) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName()
                    + "' bị cấm vi phạm chính sách, chỉ Admin mới có quyền mở");
                continue;
            }

            if (product.getStatus() == targetStatus) {
                successCount++;
                continue;
            }

            var oldStatus = product.getStatus().name();
            if (targetStatus == ProductStatus.ACTIVE) {
                if (product.getStatus() == ProductStatus.HIDDEN) {
                    product.activate();
                } else {
                    failedCount++;
                    messages.add("Sản phẩm '" + product.getName()
                        + "' ở trạng thái " + oldStatus + ", không thể bật bán");
                    continue;
                }
            } else if (targetStatus == ProductStatus.HIDDEN) {
                if (product.getStatus() == ProductStatus.ACTIVE) {
                    product.hide();
                } else {
                    failedCount++;
                    messages.add("Sản phẩm '" + product.getName() + "' ở trạng thái " + oldStatus + ", không thể ẩn");
                    continue;
                }
            }

            products.save(product);
            auditLogs.save(ProductAuditLog.record(
                product.getId(),
                shopId,
                userId,
                "BULK_UPDATE_STATUS",
                "Cập nhật hàng loạt trạng thái: " + oldStatus + " -> " + targetStatus.name(),
                "{\"status\":\"" + oldStatus + "\"}",
                "{\"status\":\"" + targetStatus.name() + "\"}"
            ));
            successCount++;
        }

        return new BulkOperationResult(successCount, failedCount, messages);
    }

    @Transactional
    public BulkOperationResult bulkDeleteProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull BulkDeleteProductsRequest request
    ) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);
        var successCount = 0;
        var failedCount = 0;
        var messages = new ArrayList<String>();

        for (var productId : request.productIds()) {
            var product = products.findByShopIdAndId(shopId, productId).orElse(null);
            if (product == null) {
                failedCount++;
                messages.add("Sản phẩm ID " + productId + " không tồn tại");
                continue;
            }
            if (product.getStatus() == ProductStatus.DELETED) {
                successCount++;
                continue;
            }
            if (orderService.hasActiveOrdersForProduct(productId)) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' đang có đơn hàng xử lý, không thể xóa");
                continue;
            }

            product.delete();
            products.save(product);

            var variantList = variants.findByProductIdOrderByCreatedAtAsc(productId);
            var delSuffix = "-del-" + UUID.randomUUID().toString().substring(0, 8);
            for (var v : variantList) {
                v.archiveSku(delSuffix);
            }
            variants.saveAll(variantList);

            auditLogs.save(ProductAuditLog.record(
                productId,
                shopId,
                userId,
                "BULK_DELETE",
                "Xóa mềm sản phẩm hàng loạt",
                null,
                null
            ));
            successCount++;
        }

        return new BulkOperationResult(successCount, failedCount, messages);
    }

    @Transactional
    public BulkOperationResult bulkUpdatePrices(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull BulkUpdatePriceRequest request
    ) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);
        var successCount = 0;
        var failedCount = 0;
        var messages = new ArrayList<String>();

        for (var productId : request.productIds()) {
            var product = products.findByShopIdAndId(shopId, productId).orElse(null);
            if (product == null) {
                failedCount++;
                messages.add("Sản phẩm ID " + productId + " không tồn tại");
                continue;
            }
            if (product.getStatus() == ProductStatus.DELETED) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' đã bị xóa");
                continue;
            }
            if (product.getStatus() == ProductStatus.BANNED) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' bị cấm vi phạm chính sách");
                continue;
            }

            var variantList = variants.findByProductIdOrderByCreatedAtAsc(productId);
            if (variantList.isEmpty()) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' không có biến thể");
                continue;
            }

            for (var v : variantList) {
                long newPrice;
                if ("FIXED".equalsIgnoreCase(request.mode())) {
                    newPrice = Math.max(1000L, Math.round(request.value()));
                } else {
                    var factor = 1.0 + (request.value() / 100.0);
                    newPrice = Math.max(1000L, Math.round(v.getPrice() * factor));
                }
                v.setPrice(newPrice);
            }
            variants.saveAll(variantList);

            var summary = "Cập nhật giá hàng loạt: ";
            if ("FIXED".equalsIgnoreCase(request.mode())) {
                summary = summary + "giá cố định " + Math.round(request.value()) + " VND";
            } else {
                summary = summary + "thay đổi " + request.value() + "%";
            }
            auditLogs.save(ProductAuditLog.record(
                productId,
                shopId,
                userId,
                "BULK_UPDATE_PRICE",
                summary,
                null,
                null
            ));
            successCount++;
        }

        return new BulkOperationResult(successCount, failedCount, messages);
    }

    @Transactional
    public BulkOperationResult bulkUpdateStocks(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull BulkUpdateStockRequest request
    ) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.INVENTORY_WRITE);
        var successCount = 0;
        var failedCount = 0;
        var messages = new ArrayList<String>();

        for (var productId : request.productIds()) {
            var product = products.findByShopIdAndId(shopId, productId).orElse(null);
            if (product == null) {
                failedCount++;
                messages.add("Sản phẩm ID " + productId + " không tồn tại");
                continue;
            }
            if (product.getStatus() == ProductStatus.DELETED) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' đã bị xóa");
                continue;
            }

            var variantList = variants.findByProductIdOrderByCreatedAtAsc(productId);
            if (variantList.isEmpty()) {
                failedCount++;
                messages.add("Sản phẩm '" + product.getName() + "' không có biến thể");
                continue;
            }

            for (var v : variantList) {
                int newStock;
                if ("FIXED".equalsIgnoreCase(request.mode())) {
                    newStock = Math.max(0, request.quantity());
                } else {
                    newStock = Math.max(0, v.getStockQuantity() + request.quantity());
                }
                var delta = newStock - v.getStockQuantity();
                v.setStockQuantity(newStock);
                movements.save(StockMovement.record(v.getId(), shopId, delta, "BULK_ADJUST", null));
            }
            variants.saveAll(variantList);

            var summary = "Cập nhật tồn kho hàng loạt: ";
            if ("FIXED".equalsIgnoreCase(request.mode())) {
                summary = summary + "tồn cố định " + request.quantity();
            } else {
                summary = summary + "thay đổi " + request.quantity();
            }
            auditLogs.save(ProductAuditLog.record(
                productId,
                shopId,
                userId,
                "BULK_UPDATE_STOCK",
                summary,
                null,
                null
            ));
            successCount++;
        }

        return new BulkOperationResult(successCount, failedCount, messages);
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> listInventory(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        boolean onlyLowStock
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.INVENTORY_READ);
        var variantsList = variants.findByShopId(shopId);
        var result = new ArrayList<InventoryItemResponse>();
        for (var v : variantsList) {
            var isLow = v.getStockQuantity() <= v.getLowStockThreshold();
            if (onlyLowStock && !isLow) {
                continue;
            }
            var prod = products.findById(v.getProductId()).orElse(null);
            var prodName = "Unknown";
            if (prod != null) {
                prodName = prod.getName();
            }
            result.add(new InventoryItemResponse(
                v.getId(),
                v.getProductId(),
                prodName,
                v.getSku(),
                v.getSize(),
                v.getColor(),
                v.getPrice(),
                v.getStockQuantity(),
                v.getLowStockThreshold(),
                isLow
            ));
        }
        return result;
    }

    @Transactional
    public InventoryItemResponse adjustStock(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID variantId,
        @NonNull AdjustStockRequest request
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.INVENTORY_WRITE);
        var variant = variants.findByShopIdAndId(shopId, variantId).orElse(null);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }
        if (request.newStock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock cannot be negative");
        }

        var oldStock = variant.getStockQuantity();
        var delta = request.newStock() - oldStock;
        variant.setStockQuantity(request.newStock());
        variants.save(variant);

        var reason = "MANUAL_ADJUST";
        if (request.reason() != null && !request.reason().isBlank()) {
            reason = request.reason();
        }
        movements.save(StockMovement.record(variant.getId(), shopId, delta, reason, null));

        var prod = products.findById(variant.getProductId()).orElse(null);
        var prodName = "Unknown";
        if (prod != null) {
            prodName = prod.getName();
        }
        var isLow = variant.getStockQuantity() <= variant.getLowStockThreshold();

        return new InventoryItemResponse(
            variant.getId(),
            variant.getProductId(),
            prodName,
            variant.getSku(),
            variant.getSize(),
            variant.getColor(),
            variant.getPrice(),
            variant.getStockQuantity(),
            variant.getLowStockThreshold(),
            isLow
        );
    }

    @Transactional(readOnly = true)
    public List<StockMovementResponse> listStockMovements(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull UUID variantId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.INVENTORY_READ);
        var variant = variants.findByShopIdAndId(shopId, variantId).orElse(null);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }
        return movements.findByVariantId(variantId).stream()
            .map(m -> new StockMovementResponse(
                m.getId(),
                m.getVariantId(),
                m.getShopId(),
                m.getDelta(),
                m.getReason(),
                m.getReferenceId(),
                m.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    @SuppressWarnings("StringSplitter")
    public BulkImportResult bulkImport(@NonNull UUID userId, @NonNull UUID shopId, byte[] csvBytes) {
        access.requireApprovedShopPermission(userId, shopId, ShopPermission.PRODUCT_WRITE);
        var content = new String(csvBytes, StandardCharsets.UTF_8);
        var lines = Pattern.compile("\\r?\\n").split(content);
        var success = 0;
        var failed = 0;
        var errors = new ArrayList<String>();

        for (var i = 1; i < lines.length; i++) {
            var line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            var parts = Pattern.compile(",").split(line);
            if (parts.length < 8) {
                failed++;
                errors.add("Row " + (i + 1) + ": Not enough columns (required 8)");
                continue;
            }
            try {
                var name = parts[0].trim();
                var slug = parts[1].trim();
                var desc = parts[2].trim();
                var sku = parts[3].trim();
                var size = parts[4].trim();
                var color = parts[5].trim();
                var price = Long.parseLong(parts[6].trim());
                var stock = Integer.parseInt(parts[7].trim());

                var createReq = new CreateProductRequest(
                    name,
                    slug,
                    desc,
                    null,
                    List.of(new CreateVariantRequest(sku, size, color, price, null, stock, true)),
                    List.of(),
                    null, null, null,
                    null, null, null, null,
                    null, null, null, null,
                    null
                );
                createProduct(userId, shopId, createReq);
                success++;
            } catch (Exception ex) {
                failed++;
                errors.add("Row " + (i + 1) + ": " + ex.getMessage());
            }
        }
        return new BulkImportResult(success, failed, errors);
    }

    @Transactional(readOnly = true)
    public String exportCsv(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.PRODUCT_READ);
        var allProducts = products.findByShopIdOrderByCreatedAtDesc(shopId);
        var sb = new StringBuilder("name,slug,description,sku,size,color,price,stockQuantity,status\n");
        for (var p : allProducts) {
            var varList = variants.findByProductIdOrderByCreatedAtAsc(p.getId());
            for (var v : varList) {
                sb.append('"').append(p.getName().replace("\"", "\"\"")).append("\",");
                sb.append('"').append(p.getSlug().replace("\"", "\"\"")).append("\",");
                sb.append('"').append(p.getDescription().replace("\"", "\"\"")).append("\",");
                sb.append('"').append(v.getSku().replace("\"", "\"\"")).append("\",");
                sb.append('"').append(v.getSize().replace("\"", "\"\"")).append("\",");
                sb.append('"').append(v.getColor().replace("\"", "\"\"")).append("\",");
                sb.append(v.getPrice()).append(",");
                sb.append(v.getStockQuantity()).append(",");
                sb.append(p.getStatus().name()).append("\n");
            }
        }
        return sb.toString();
    }

    private SellerProductResponse toSellerResponse(Product product) {
        var varList = variants.findByProductIdOrderByCreatedAtAsc(product.getId());
        var totalStock = varList.stream().mapToInt(ProductVariant::getStockQuantity).sum();
        var minPrice = varList.stream().mapToLong(ProductVariant::getPrice).min().orElse(0L);
        var maxPrice = varList.stream().mapToLong(ProductVariant::getPrice).max().orElse(0L);
        var imgList = images.findByProductIdOrderBySortOrderAsc(product.getId());
        String defaultImg = null;
        if (!imgList.isEmpty()) {
            defaultImg = imgList.getFirst().getS3Key();
        }
        var variantResponses = varList.stream().map(CatalogMapper::toVariantResponse).toList();
        var soldCount = orderService.getSoldQuantityForProduct(product.getId());

        return new SellerProductResponse(
            product.getId(),
            product.getShopId(),
            product.getCategoryId(),
            product.getName(),
            product.getSlug(),
            product.getDescription(),
            product.getStatus().name(),
            violationNameOf(product),
            product.getStatusReason(),
            totalStock,
            minPrice,
            maxPrice,
            defaultImg,
            variantResponses,
            product.getCreatedAt(),
            product.getUpdatedAt(),
            soldCount
        );
    }

    private String resolveUniqueSlug(UUID shopId, String baseSlug) {
        var cleanBase = baseSlug.trim().toLowerCase(Locale.ROOT);
        if (cleanBase.isBlank()) {
            cleanBase = "product";
        }
        var candidate = cleanBase;
        var counter = 1;
        while (products.findByShopIdAndSlug(shopId, candidate).isPresent()) {
            candidate = cleanBase + "-" + counter;
            counter++;
        }
        return candidate;
    }

    private static @Nullable String violationNameOf(Product product) {
        var violationType = product.getViolationType();
        if (violationType == null) {
            return null;
        }
        return violationType.name();
    }
}
