package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.StockMovementRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.model.StockMovement;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final StockMovementRepository movements;
    private final ShopService shops;

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
}
