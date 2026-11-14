package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.features.catalog.repository.ProductImageRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.StockMovementRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.model.StockMovement;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final StockMovementRepository movements;
    private final ShopService shops;
    private final UploadRepository uploads;
    private final S3UrlService s3Urls;

    @Transactional(readOnly = true)
    public Optional<ProductVariant> findVariantById(@NonNull UUID variantId) {
        return variants.findById(variantId);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findProductById(@NonNull UUID productId) {
        return products.findById(productId);
    }

    @Transactional(readOnly = true)
    public Product requireActiveProduct(@NonNull UUID productId) {
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
        return product;
    }

    @Transactional(readOnly = true)
    public ProductVariant requireVariant(@NonNull UUID variantId) {
        var variant = variants.findById(variantId).orElse(null);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }
        return variant;
    }

    @Transactional
    public boolean decrementVariantStock(@NonNull UUID variantId, int quantity) {
        return variants.decrementStock(variantId, quantity);
    }

    @Transactional
    public void incrementVariantStock(@NonNull UUID variantId, int quantity) {
        variants.incrementStock(variantId, quantity);
    }

    @Transactional
    public void recordStockMovement(
        @NonNull UUID variantId,
        @NonNull UUID shopId,
        int quantity,
        @NonNull String type,
        @NonNull UUID referenceId
    ) {
        movements.save(new StockMovement(
            UUID.randomUUID(),
            variantId,
            shopId,
            quantity,
            type,
            referenceId,
            Instant.now(),
            true
        ));
    }

    public record ProductDisplayInfo(
        UUID productId,
        boolean available,
        @Nullable String defaultImageUrl,
        Map<UUID, String> variantImageUrls
    ) {}

    @Transactional(readOnly = true)
    public Map<UUID, ProductDisplayInfo> findProductDisplayInfos(@NonNull Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        var productsById = new LinkedHashMap<UUID, Product>();
        var shopIds = new LinkedHashSet<UUID>();
        products.findAllById(productIds).forEach(product -> {
            productsById.put(product.getId(), product);
            shopIds.add(product.getShopId());
        });
        if (productsById.isEmpty()) {
            return Map.of();
        }
        var shopSummaries = shops.findShopSummariesByIds(shopIds);
        var genericDefault = new LinkedHashMap<UUID, UUID>();
        var anyDefault = new LinkedHashMap<UUID, UUID>();
        var variantImages = new LinkedHashMap<UUID, Map<UUID, UUID>>();
        for (var image : images.findByProductIdInOrderBySortOrderAsc(productsById.keySet())) {
            anyDefault.putIfAbsent(image.getProductId(), image.getUploadId());
            if (image.getVariantId() == null) {
                genericDefault.putIfAbsent(image.getProductId(), image.getUploadId());
            } else {
                variantImages
                    .computeIfAbsent(image.getProductId(), ignored -> new LinkedHashMap<>())
                    .putIfAbsent(image.getVariantId(), image.getUploadId());
            }
        }
        var uploadIds = new LinkedHashSet<UUID>();
        uploadIds.addAll(genericDefault.values());
        uploadIds.addAll(anyDefault.values());
        for (var byVariant : variantImages.values()) {
            uploadIds.addAll(byVariant.values());
        }
        var urls = new LinkedHashMap<UUID, String>();
        for (var upload : uploads.findAllById(uploadIds)) {
            urls.put(upload.getId(), s3Urls.publicUrl(upload.getS3Key()));
        }
        var result = new LinkedHashMap<UUID, ProductDisplayInfo>();
        for (var entry : productsById.entrySet()) {
            var productId = entry.getKey();
            var product = entry.getValue();
            var shop = shopSummaries.get(product.getShopId());
            boolean available = product.getStatus() == ProductStatus.ACTIVE
                && shop != null
                && shop.approved();
            var defaultUploadId = genericDefault.get(productId);
            if (defaultUploadId == null) {
                defaultUploadId = anyDefault.get(productId);
            }
            var variantUrls = new LinkedHashMap<UUID, String>();
            for (var byVariant : variantImages.getOrDefault(productId, Map.of()).entrySet()) {
                variantUrls.put(byVariant.getKey(), urls.get(byVariant.getValue()));
            }
            result.put(productId, new ProductDisplayInfo(
                productId,
                available,
                urls.get(defaultUploadId),
                variantUrls
            ));
        }
        return result;
    }
}
