package com.minhnbnt.fashionstore.features.wishlist;

import com.minhnbnt.fashionstore.dto.wishlist.WishlistItemResponse;
import com.minhnbnt.fashionstore.dto.wishlist.WishlistToggleResponse;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.wishlist.repository.WishlistRepository;
import com.minhnbnt.fashionstore.model.WishlistItem;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

    private final CatalogService catalog;

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
        catalog.requireActiveProduct(productId);

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

    private WishlistItemResponse toResponse(UUID productId) {
        var card = catalog.getWishlistProductCard(productId);
        return new WishlistItemResponse(
            card.productId(),
            card.name(),
            card.slug(),
            card.minPrice(),
            card.defaultImageKey(),
            card.available(),
            card.unavailableReason()
        );
    }
}
