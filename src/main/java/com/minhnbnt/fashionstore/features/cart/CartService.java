package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemResponse;
import com.minhnbnt.fashionstore.dto.cart.CartResponse;
import com.minhnbnt.fashionstore.dto.cart.CheckoutBlockedItem;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.cart.repository.CartItemRepository;
import com.minhnbnt.fashionstore.features.cart.repository.CartRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Cart;
import com.minhnbnt.fashionstore.model.CartItem;
import com.minhnbnt.fashionstore.model.ProductStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository carts;

    private final CartItemRepository items;

    private final ProductRepository products;

    private final ProductVariantRepository variants;

    private final ShopService shops;

    private final OrderService orders;

    @Transactional(readOnly = true)
    public CartResponse getCart(@NonNull UUID buyerId) {
        var cart = carts.findByBuyerId(buyerId).orElse(null);
        if (cart == null) {
            return new CartResponse(List.of(), 0, 0L);
        }

        return toResponse(cart, items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse addItem(@NonNull UUID buyerId, @NonNull CartItemRequest request) {
        if (!variants.existsById(request.variantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }

        var cart = getOrCreate(buyerId);
        cart.touch();
        carts.save(cart);

        var existing = items.findByCartIdAndVariantId(cart.getId(), request.variantId()).orElse(null);
        if (existing == null) {
            items.save(CartItem.create(cart.getId(), request.variantId(), request.quantity()));
        } else {
            existing.increaseQuantity(request.quantity());
            items.save(existing);
        }

        return toResponse(cart, items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse updateQuantity(@NonNull UUID buyerId, @NonNull UUID variantId, int quantity) {
        var cart = getOrCreate(buyerId);
        var item = items.findByCartIdAndVariantId(cart.getId(), variantId).orElse(null);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart");
        }

        item.setQuantity(quantity);
        items.save(item);
        cart.touch();
        carts.save(cart);

        return toResponse(cart, items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse removeItem(@NonNull UUID buyerId, @NonNull UUID variantId) {
        var cart = getOrCreate(buyerId);
        var item = items.findByCartIdAndVariantId(cart.getId(), variantId).orElse(null);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not in cart");
        }

        items.delete(item);
        cart.touch();
        carts.save(cart);

        return toResponse(cart, items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse clear(@NonNull UUID buyerId) {
        var cart = getOrCreate(buyerId);
        cart.touch();
        carts.save(cart);
        items.deleteByCartId(cart.getId());

        return toResponse(cart, List.of());
    }

    @Transactional
    public List<OrderResponse> checkout(@NonNull UUID buyerId, @NonNull CartCheckoutRequest request) {
        var cart = getOrCreate(buyerId);
        cart.touch();
        carts.save(cart);
        var lines = items.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        var linesByShop = new LinkedHashMap<UUID, List<CheckoutItemRequest>>();
        var problems = new ArrayList<CheckoutBlockedItem>();

        for (var item : lines) {
            var variant = variants.findById(item.getVariantId()).orElse(null);
            if (variant == null) {
                problems.add(new CheckoutBlockedItem(item.getVariantId(), "Variant not found"));
                continue;
            }

            var product = products.findById(variant.getProductId()).orElse(null);
            if (product == null) {
                problems.add(new CheckoutBlockedItem(item.getVariantId(), "Product not found"));
                continue;
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                problems.add(new CheckoutBlockedItem(item.getVariantId(), "Product is not available"));
                continue;
            }
            if (!shops.isShopApproved(variant.getShopId())) {
                problems.add(new CheckoutBlockedItem(item.getVariantId(), "Shop is not approved for selling"));
                continue;
            }
            if (variant.getStockQuantity() < item.getQuantity()) {
                problems.add(new CheckoutBlockedItem(
                    item.getVariantId(),
                    "Insufficient stock, available: " + variant.getStockQuantity()
                ));
                continue;
            }

            var shopLines = linesByShop.get(variant.getShopId());
            if (shopLines == null) {
                shopLines = new ArrayList<>();
                linesByShop.put(variant.getShopId(), shopLines);
            }
            shopLines.add(new CheckoutItemRequest(item.getVariantId(), item.getQuantity()));
        }

        if (!problems.isEmpty()) {
            throw new CartCheckoutBlockedException(problems);
        }

        var placed = new ArrayList<OrderResponse>();

        for (Map.Entry<UUID, List<CheckoutItemRequest>> entry : linesByShop.entrySet()) {
            placed.add(orders.checkout(
                buyerId,
                new CheckoutRequest(
                    entry.getKey(),
                    entry.getValue(),
                    request.shippingName(),
                    request.shippingPhone(),
                    request.shippingAddress(),
                    request.shippingFee()
                )
            ));
        }

        items.deleteByCartId(cart.getId());

        return placed;
    }

    private Cart getOrCreate(UUID buyerId) {
        var cart = carts.findByBuyerId(buyerId).orElse(null);
        if (cart == null) {
            cart = Cart.create(buyerId);
        }

        return cart;
    }

    private CartResponse toResponse(Cart cart, List<CartItem> lines) {
        var responses = new ArrayList<CartItemResponse>();
        var itemCount = 0;
        var subtotal = 0L;

        for (var item : lines) {
            var variant = variants.findById(item.getVariantId()).orElse(null);
            if (variant == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
            }

            var product = products.findById(variant.getProductId()).orElse(null);
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }

            itemCount += item.getQuantity();
            subtotal += variant.getPrice() * item.getQuantity();

            responses.add(new CartItemResponse(
                item.getVariantId(),
                variant.getProductId(),
                product.getName(),
                variant.getSize() + " / " + variant.getColor(),
                variant.getSku(),
                variant.getPrice(),
                item.getQuantity(),
                variant.getStockQuantity()
            ));
        }

        return new CartResponse(responses, itemCount, subtotal);
    }
}
