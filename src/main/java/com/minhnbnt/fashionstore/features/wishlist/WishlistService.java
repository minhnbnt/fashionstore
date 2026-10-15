package com.minhnbnt.fashionstore.features.wishlist;

import com.minhnbnt.fashionstore.dto.wishlist.WishlistItemResponse;
import com.minhnbnt.fashionstore.dto.wishlist.WishlistToggleResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductImageRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.wishlist.repository.WishlistRepository;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.WishlistItem;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository items;

    private final ProductRepository products;

    private final ProductVariantRepository variants;

    private final ProductImageRepository images;

    private final ShopService shops;

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> getWishlist(@NonNull UUID buyerId) {
        var responses = new ArrayList<WishlistItemResponse>();

        for (var item : items.findByBuyerIdOrderByCreatedAtAsc(buyerId)) {
            responses.add(toResponse(item.getProductId()));
        }

        return responses;
    }

    @Transactional
    public WishlistToggleResponse toggle(@NonNull UUID buyerId, @NonNull UUID productId) {
        requireViewable(productId);

        var existing = items.findByBuyerIdAndProductId(buyerId, productId).orElse(null);
        if (existing != null) {
            items.delete(existing);

            return new WishlistToggleResponse(productId, false);
        }

        items.save(WishlistItem.create(buyerId, productId));

        return new WishlistToggleResponse(productId, true);
    }

    @Transactional
    public void remove(@NonNull UUID buyerId, @NonNull UUID productId) {
        var existing = items.findByBuyerIdAndProductId(buyerId, productId).orElse(null);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not in wishlist");
        }

        items.delete(existing);
    }

    private void requireViewable(UUID productId) {
        if (unavailableReason(productId) != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    private WishlistItemResponse toResponse(UUID productId) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            return new WishlistItemResponse(productId, null, null, null, null, false, "Product no longer exists");
        }

        var reason = unavailableReason(productId);
        if (reason != null) {
            return new WishlistItemResponse(
                productId,
                product.getName(),
                product.getSlug(),
                null,
                null,
                false,
                reason
            );
        }

        Long minPrice = null;
        for (var variant : variants.findByProductIdOrderByCreatedAtAsc(productId)) {
            if (minPrice == null || variant.getPrice() < minPrice) {
                minPrice = variant.getPrice();
            }
        }

        String defaultImageKey = null;
        var productImages = images.findByProductIdOrderBySortOrderAsc(productId);
        if (!productImages.isEmpty()) {
            defaultImageKey = productImages.get(0).getS3Key();
        }

        return new WishlistItemResponse(
            productId,
            product.getName(),
            product.getSlug(),
            minPrice,
            defaultImageKey,
            true,
            null
        );
    }

    private @Nullable String unavailableReason(UUID productId) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            return "Product no longer exists";
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            return "Product is hidden";
        }
        if (!shops.isShopApproved(product.getShopId())) {
            return "Shop is not approved for selling";
        }

        return null;
    }
}
