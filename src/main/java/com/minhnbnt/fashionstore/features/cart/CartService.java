package com.minhnbnt.fashionstore.features.cart;

import com.minhnbnt.fashionstore.dto.cart.CartCheckoutRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemRequest;
import com.minhnbnt.fashionstore.dto.cart.CartItemResponse;
import com.minhnbnt.fashionstore.dto.cart.CartResponse;
import com.minhnbnt.fashionstore.dto.cart.CheckoutBlockedItem;
import com.minhnbnt.fashionstore.dto.cart.CheckoutPreviewRequest;
import com.minhnbnt.fashionstore.dto.cart.CheckoutPreviewResponse;
import com.minhnbnt.fashionstore.dto.cart.ShopCheckoutPreview;
import com.minhnbnt.fashionstore.dto.cart.ReorderItemIssue;
import com.minhnbnt.fashionstore.dto.cart.ReorderResponse;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.OrderItemResponse;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.dto.voucher.VoucherLineItem;
import com.minhnbnt.fashionstore.features.cart.repository.CartItemRepository;
import com.minhnbnt.fashionstore.features.cart.repository.CartRepository;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.payment.PaymentGatewayService;
import com.minhnbnt.fashionstore.features.payment.PaymentTransactionService;
import com.minhnbnt.fashionstore.features.shipping.ShippingFeeService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.features.voucher.service.VoucherService;
import com.minhnbnt.fashionstore.model.Cart;
import com.minhnbnt.fashionstore.model.CartItem;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.PaymentMethod;
import com.minhnbnt.fashionstore.model.ProductImage;
import com.minhnbnt.fashionstore.model.ProductStatus;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository carts;

    private final CartItemRepository items;

    private final CatalogService catalog;

    private final ShopService shops;

    private final OrderService orders;

    private final UploadRepository uploads;

    private final S3UrlService s3Urls;

    private final ShippingFeeService shipping;

    private final PaymentGatewayService paymentGateways;

    private final PaymentTransactionService paymentTransactions;

    private final VoucherService vouchers;

    /** FK bảo đảm upload tồn tại; thiếu là lỗi dữ liệu, fail loud. */
    private String imageUrlOf(UUID uploadId) {
        return s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(@NonNull UUID buyerId) {
        var cart = carts.findByBuyerId(buyerId).orElse(null);
        if (cart == null) {
            return new CartResponse(List.of(), 0, 0L);
        }

        return toResponse(items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse addItem(@NonNull UUID buyerId, @NonNull CartItemRequest request) {
        var variant = catalog.findVariantById(request.variantId()).orElse(null);
        if (variant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
        }

        var cart = getOrCreate(buyerId);
        cart.touch();
        carts.save(cart);

        var existing = items.findByCartIdAndVariantId(cart.getId(), request.variantId()).orElse(null);
        if (existing == null) {
            items.save(CartItem.create(cart.getId(), request.variantId(), request.quantity(), variant.getPrice()));
        } else {
            existing.increaseQuantity(request.quantity());
            items.save(existing);
        }

        return toResponse(items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
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

        return toResponse(items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
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

        return toResponse(items.findByCartIdOrderByCreatedAtAsc(cart.getId()));
    }

    @Transactional
    public CartResponse clear(@NonNull UUID buyerId) {
        var cart = getOrCreate(buyerId);
        cart.touch();
        carts.save(cart);
        items.deleteByCartId(cart.getId());

        return toResponse(List.of());
    }

    /**
     * Xem trước tiền các đơn sẽ tạo (mỗi shop 1 đơn) với giá và phí ship do server tính.
     * Không giữ hàng, không chặn món hết hàng/ngừng bán — checkout mới chặn; variant đã bị xoá thì bỏ qua.
     */
    @Transactional(readOnly = true)
    public CheckoutPreviewResponse previewCheckout(@NonNull UUID buyerId, @NonNull CheckoutPreviewRequest request) {
        List<CartItem> lines = List.of();
        var cart = carts.findByBuyerId(buyerId).orElse(null);
        if (cart != null) {
            lines = items.findByCartIdOrderByCreatedAtAsc(cart.getId());
        }

        var selected = selectLines(lines, request.variantIds());
        var selectedVariantIds = selected.stream().map(CartItem::getVariantId).toList();
        var variantsById = catalog.findVariantsByIds(selectedVariantIds);

        var totalsByShop = new LinkedHashMap<UUID, ShopLineTotals>();
        var voucherLinesByShop = new LinkedHashMap<UUID, List<VoucherLineItem>>();
        for (var item : selected) {
            var variant = variantsById.get(item.getVariantId());
            if (variant == null && variantsById.isEmpty()) {
                variant = catalog.findVariantById(item.getVariantId()).orElse(null);
            }
            if (variant == null) {
                continue;
            }

            var lineTotal = variant.getPrice() * item.getQuantity();
            var line = new ShopLineTotals(lineTotal, item.getQuantity());
            totalsByShop.merge(variant.getShopId(), line, ShopLineTotals::plus);
            voucherLinesByShop
                .computeIfAbsent(variant.getShopId(), ignored -> new ArrayList<>())
                .add(new VoucherLineItem(variant.getProductId(), lineTotal));
        }

        var shopPreviews = new ArrayList<ShopCheckoutPreview>();
        var subtotal = 0L;
        var shippingFee = 0L;
        var discountAmount = 0L;
        for (var entry : totalsByShop.entrySet()) {
            var shopId = entry.getKey();
            var totals = entry.getValue();
            var quote = shipping.quote(totals.subtotal(), totals.itemCount());

            String rawVoucherCode = null;
            if (request.shopVouchers() != null) {
                rawVoucherCode = request.shopVouchers().get(shopId);
            }

            String appliedCode = null;
            var shopDiscount = 0L;
            String voucherError = null;
            if (rawVoucherCode != null && !rawVoucherCode.isBlank()) {
                var shopLines = voucherLinesByShop.getOrDefault(shopId, List.of());
                var voucherQuote = vouchers.quoteForPreview(shopId, buyerId, rawVoucherCode, shopLines);
                appliedCode = voucherQuote.voucherCode();
                shopDiscount = voucherQuote.discountAmount();
                voucherError = voucherQuote.error();
            }

            shopPreviews.add(new ShopCheckoutPreview(
                shopId,
                totals.itemCount(),
                totals.subtotal(),
                quote.fee(),
                quote.originalFee(),
                quote.freeShipApplied(),
                quote.amountToFreeShip(),
                totals.subtotal() + quote.fee() - shopDiscount,
                appliedCode,
                shopDiscount,
                voucherError
            ));
            subtotal += totals.subtotal();
            shippingFee += quote.fee();
            discountAmount += shopDiscount;
        }

        return new CheckoutPreviewResponse(
            shopPreviews,
            subtotal,
            shippingFee,
            subtotal + shippingFee - discountAmount,
            discountAmount
        );
    }

    private record ShopLineTotals(long subtotal, int itemCount) {

        ShopLineTotals plus(ShopLineTotals other) {
            return new ShopLineTotals(subtotal + other.subtotal, itemCount + other.itemCount);
        }
    }

    /** Lọc các dòng được chọn; null/rỗng = cả giỏ. Chọn món không có trong giỏ → 400. */
    private static List<CartItem> selectLines(List<CartItem> lines, @Nullable List<UUID> selectedIds) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return lines;
        }

        var wanted = new HashSet<>(selectedIds);
        var selected = new ArrayList<CartItem>();
        for (var item : lines) {
            if (wanted.remove(item.getVariantId())) {
                selected.add(item);
            }
        }
        if (!wanted.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Some selected items are not in the cart");
        }

        return selected;
    }

    @Transactional
    public List<OrderResponse> checkout(@NonNull UUID buyerId, @NonNull CartCheckoutRequest request) {
        var cart = getOrCreateLocked(buyerId);
        cart.touch();
        carts.save(cart);
        var lines = items.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (lines.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        var checked = selectLines(lines, request.variantIds());

        var linesByShop = new LinkedHashMap<UUID, List<CheckoutItemRequest>>();
        var totalsByShop = new LinkedHashMap<UUID, ShopLineTotals>();
        var problems = new ArrayList<CheckoutBlockedItem>();

        for (var item : checked) {
            var variant = catalog.findVariantById(item.getVariantId()).orElse(null);
            if (variant == null) {
                problems.add(new CheckoutBlockedItem(item.getVariantId(), "Variant not found"));
                continue;
            }

            var product = catalog.findProductById(variant.getProductId()).orElse(null);
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

            var lineTotals = new ShopLineTotals(variant.getPrice() * item.getQuantity(), item.getQuantity());
            totalsByShop.merge(variant.getShopId(), lineTotals, ShopLineTotals::plus);
        }

        if (!problems.isEmpty()) {
            throw new CartCheckoutBlockedException(problems);
        }

        var method = request.resolvedPaymentMethod();
        var placed = new ArrayList<OrderResponse>();

        if (method == PaymentMethod.VIETQR) {
            var expiresAt = orders.nextPaymentExpiresAt();
            var createdOrders = new ArrayList<Order>();
            var draftResponses = new ArrayList<OrderResponse>();

            for (Map.Entry<UUID, List<CheckoutItemRequest>> entry : linesByShop.entrySet()) {
                var draft = orders.checkoutVietQrDraft(
                    buyerId,
                    new CheckoutRequest(
                        entry.getKey(),
                        entry.getValue(),
                        request.shippingName(),
                        request.shippingPhone(),
                        request.shippingAddress(),
                        PaymentMethod.VIETQR,
                        resolveShopVoucherCode(request.shopVouchers(), entry.getKey())
                    ),
                    expiresAt
                );
                createdOrders.add(draft.order());
                draftResponses.add(draft.response());
            }

            var tx = paymentTransactions.createSession(buyerId, createdOrders, expiresAt);
            for (var draftResponse : draftResponses) {
                placed.add(draftResponse.withPaymentSession(tx.getTxnRef(), tx.getQrImageUrl(), tx.getExpiresAt()));
            }
        } else {
            var combinedTotal = 0L;
            for (Map.Entry<UUID, List<CheckoutItemRequest>> entry : linesByShop.entrySet()) {
                var placedOrder = orders.checkout(
                    buyerId,
                    new CheckoutRequest(
                        entry.getKey(),
                        entry.getValue(),
                        request.shippingName(),
                        request.shippingPhone(),
                        request.shippingAddress(),
                        PaymentMethod.COD,
                        resolveShopVoucherCode(request.shopVouchers(), entry.getKey())
                    )
                );
                combinedTotal += placedOrder.total();
                placed.add(placedOrder);
            }
            paymentGateways.requireAvailable(PaymentMethod.COD, combinedTotal);
        }

        items.deleteAll(checked);

        return placed;
    }

    @Transactional
    public ReorderResponse reorder(@NonNull UUID buyerId, @NonNull UUID orderId) {
        var order = orders.getDetail(buyerId, orderId);
        if (!shops.isShopApproved(order.shopId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop is not active for reorder");
        }

        var cart = getOrCreate(buyerId);
        var existingByVariant = new LinkedHashMap<UUID, CartItem>();
        if (!cart.isNew()) {
            for (var existing : items.findByCartIdOrderByCreatedAtAsc(cart.getId())) {
                existingByVariant.put(existing.getVariantId(), existing);
            }
        }

        var dirtyItems = new LinkedHashMap<UUID, CartItem>();
        var adjustedItems = new ArrayList<ReorderItemIssue>();
        var skippedItems = new ArrayList<ReorderItemIssue>();
        for (var orderItem : order.items()) {
            reorderLine(
                orderItem,
                cart.getId(),
                existingByVariant,
                dirtyItems,
                adjustedItems,
                skippedItems
            );
        }

        if (!dirtyItems.isEmpty()) {
            cart.touch();
            carts.save(cart);
            items.saveAll(dirtyItems.values());
        }

        return new ReorderResponse(
            getCart(buyerId),
            order.items().size(),
            order.items().size() - skippedItems.size(),
            adjustedItems.size(),
            skippedItems.size(),
            adjustedItems,
            skippedItems
        );
    }

    private void reorderLine(
        OrderItemResponse orderItem,
        UUID cartId,
        Map<UUID, CartItem> existingByVariant,
        Map<UUID, CartItem> dirtyItems,
        List<ReorderItemIssue> adjustedItems,
        List<ReorderItemIssue> skippedItems
    ) {
        int requested = orderItem.quantity();
        var productId = orderItem.productId();
        var variantId = orderItem.variantId();
        if (productId == null) {
            skippedItems.add(issue(orderItem, "PRODUCT_DELETED", 0));
            return;
        }
        var product = catalog.findProductById(productId).orElse(null);
        if (product == null) {
            skippedItems.add(issue(orderItem, "PRODUCT_DELETED", 0));
            return;
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            skippedItems.add(issue(orderItem, "PRODUCT_UNAVAILABLE", 0));
            return;
        }
        if (variantId == null) {
            skippedItems.add(issue(orderItem, "VARIANT_UNAVAILABLE", 0));
            return;
        }
        var variant = catalog.findVariantById(variantId).orElse(null);
        if (variant == null) {
            skippedItems.add(issue(orderItem, "VARIANT_UNAVAILABLE", 0));
            return;
        }
        int stock = variant.getStockQuantity();
        if (stock <= 0) {
            skippedItems.add(issue(orderItem, "OUT_OF_STOCK", 0));
            return;
        }
        var existing = existingByVariant.get(variantId);
        int existingQty = 0;
        if (existing != null) {
            existingQty = existing.getQuantity();
        }
        int remainingRoom = stock - existingQty;
        if (remainingRoom <= 0) {
            skippedItems.add(issue(orderItem, "CART_MAXED", 0));
            return;
        }

        int toAdd = Math.min(requested, remainingRoom);
        if (toAdd < requested) {
            adjustedItems.add(issue(orderItem, "STOCK_LIMITED", toAdd));
        }
        if (existing != null) {
            existing.increaseQuantity(toAdd);
            dirtyItems.put(variantId, existing);
            return;
        }
        var newItem = CartItem.create(cartId, variantId, toAdd, variant.getPrice());
        existingByVariant.put(variantId, newItem);
        dirtyItems.put(variantId, newItem);
    }

    private static ReorderItemIssue issue(
        OrderItemResponse orderItem,
        String reason,
        int addedQuantity
    ) {
        return new ReorderItemIssue(
            orderItem.productId(),
            orderItem.variantId(),
            orderItem.productName(),
            orderItem.variantLabel(),
            reason,
            orderItem.quantity(),
            addedQuantity
        );
    }

    private static @Nullable String resolveShopVoucherCode(
        @Nullable Map<UUID, String> shopVouchers,
        UUID shopId
    ) {
        if (shopVouchers == null) {
            return null;
        }
        var raw = shopVouchers.get(shopId);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    private Cart getOrCreate(UUID buyerId) {
        var cart = carts.findByBuyerId(buyerId).orElse(null);
        if (cart == null) {
            cart = Cart.create(buyerId);
        }

        return cart;
    }

    private Cart getOrCreateLocked(UUID buyerId) {
        var cart = carts.findLockedByBuyerId(buyerId).orElse(null);
        if (cart == null) {
            cart = carts.findByBuyerId(buyerId).orElse(null);
        }
        if (cart == null) {
            cart = Cart.create(buyerId);
        }

        return cart;
    }

    private CartResponse toResponse(List<CartItem> lines) {
        var responses = new ArrayList<CartItemResponse>();
        var itemCount = 0;
        var subtotal = 0L;
        var shopCache = new HashMap<UUID, String>();
        var imageCache = new HashMap<UUID, List<ProductImage>>();

        for (var item : lines) {
            var variant = catalog.findVariantById(item.getVariantId()).orElse(null);
            if (variant == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
            }

            var product = catalog.findProductById(variant.getProductId()).orElse(null);
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }

            var shopName = shopCache.computeIfAbsent(variant.getShopId(), shops::getShopName);

            var productImages = imageCache.computeIfAbsent(variant.getProductId(), catalog::findImagesByProductId);

            String imageUrl = null;
            for (var img : productImages) {
                if (variant.getId().equals(img.getVariantId())) {
                    imageUrl = imageUrlOf(img.getUploadId());
                    break;
                }
            }
            if (imageUrl == null && !productImages.isEmpty()) {
                imageUrl = imageUrlOf(productImages.getFirst().getUploadId());
            }

            itemCount += item.getQuantity();
            subtotal += variant.getPrice() * item.getQuantity();

            long priceAtAdded = item.getPriceAtAdded();
            if (priceAtAdded <= 0) {
                priceAtAdded = variant.getPrice();
            }
            boolean priceChanged = priceAtAdded != variant.getPrice();

            responses.add(new CartItemResponse(
                item.getVariantId(),
                variant.getProductId(),
                variant.getShopId(),
                shopName,
                product.getName(),
                imageUrl,
                variant.getSize() + " / " + variant.getColor(),
                variant.getSku(),
                variant.getPrice(),
                priceAtAdded,
                priceChanged,
                item.getQuantity(),
                variant.getStockQuantity()
            ));
        }

        return new CartResponse(responses, itemCount, subtotal);
    }
}
