package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.OrderItemResponse;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderStatusHistoryRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderStockRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderItem;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.OrderStatusHistory;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
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
public class OrderService {

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final OrderStatusHistoryRepository histories;

    private final ProductRepository products;

    private final ProductVariantRepository variants;

    private final OrderStockRepository stocks;

    private final ShopService shops;

    private final ShopMemberRepository members;

    private final ShopAccessChecker access;

    @Transactional
    public OrderResponse checkout(@NonNull UUID buyerId, @NonNull CheckoutRequest request) {
        if (!shops.isShopApproved(request.shopId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop is not approved for selling");
        }

        if (request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must have at least one item");
        }

        var shippingFee = request.shippingFee() == null ? 0L : request.shippingFee();
        if (shippingFee < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping fee must not be negative");
        }

        var snapshots = new ArrayList<OrderItemSnapshot>();
        var subtotal = 0L;

        for (var item : request.items()) {
            var variant = variants.findById(item.variantId()).orElse(null);
            if (variant == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
            }
            if (!variant.getShopId().equals(request.shopId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All items must belong to one shop");
            }

            var product = products.findById(variant.getProductId()).orElse(null);
            if (product == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
            }

            var lineTotal = variant.getPrice() * item.quantity();
            subtotal += lineTotal;

            var label = variant.getSize() + " / " + variant.getColor();
            snapshots.add(new OrderItemSnapshot(
                product.getId(),
                variant.getId(),
                product.getName(),
                label,
                variant.getSku(),
                variant.getPrice(),
                item.quantity()
            ));
        }

        var order = Order.create(
            generateCode(),
            request.shopId(),
            buyerId,
            subtotal,
            shippingFee,
            request.shippingName(),
            request.shippingPhone(),
            request.shippingAddress()
        );
        orders.save(order);

        var createdItems = snapshots.stream()
            .map(snapshot -> OrderItem.snapshot(
                order.getId(),
                snapshot.productId(),
                snapshot.variantId(),
                snapshot.productName(),
                snapshot.variantLabel(),
                snapshot.sku(),
                snapshot.unitPrice(),
                snapshot.quantity()
            ))
            .toList();
        items.saveAll(createdItems);

        histories.save(OrderStatusHistory.record(order.getId(), null, OrderStatus.PENDING, buyerId));

        return toResponse(order, createdItems);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getMine(@NonNull UUID buyerId) {
        var result = new ArrayList<OrderResponse>();

        for (var order : orders.findByBuyerIdOrderByCreatedAtDesc(buyerId)) {
            var orderItems = items.findByOrderId(order.getId());
            result.add(toResponse(order, orderItems));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public OrderResponse getDetail(@NonNull UUID buyerId, @NonNull UUID orderId) {
        var order = requireBuyerOrder(buyerId, orderId);
        var orderItems = items.findByOrderId(order.getId());

        return toResponse(order, orderItems);
    }

    @Transactional
    public OrderResponse cancel(@NonNull UUID buyerId, @NonNull UUID orderId) {
        var order = requireBuyerOrder(buyerId, orderId);
        order.cancel();
        histories.save(OrderStatusHistory.record(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED, buyerId));

        var orderItems = items.findByOrderId(order.getId());
        return toResponse(orders.save(order), orderItems);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listSellerOrders(@NonNull UUID userId) {
        var result = new ArrayList<OrderResponse>();

        for (var shopId : members.findShopIdsByUser(userId)) {
            for (var order : orders.findByShopIdOrderByCreatedAtDesc(shopId)) {
                var orderItems = items.findByOrderId(order.getId());
                result.add(toResponse(order, orderItems));
            }
        }

        result.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));

        return result;
    }

    @Transactional
    public OrderResponse confirm(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = requireOrder(orderId);
        access.requireShopRole(userId, order.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        var from = order.getStatus();

        var orderItems = items.findByOrderId(order.getId());
        for (var item : orderItems) {
            if (item.getVariantId() == null) {
                continue;
            }
            var ok = stocks.decrement(item.getVariantId(), item.getQuantity());
            if (!ok) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock");
            }
        }

        order.confirm();
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CONFIRMED, userId));

        return toResponse(orders.save(order), orderItems);
    }

    @Transactional
    public OrderResponse ship(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = requireOrder(orderId);
        access.requireShopRole(userId, order.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        var from = order.getStatus();
        order.ship();
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.SHIPPING, userId));

        var orderItems = items.findByOrderId(order.getId());
        return toResponse(orders.save(order), orderItems);
    }

    @Transactional
    public OrderResponse deliver(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = requireOrder(orderId);
        access.requireShopRole(userId, order.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        var from = order.getStatus();
        order.deliver();
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.DELIVERED, userId));

        var orderItems = items.findByOrderId(order.getId());
        return toResponse(orders.save(order), orderItems);
    }

    private Order requireBuyerOrder(UUID buyerId, UUID orderId) {
        var order = orders.findById(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        return order;
    }

    private Order requireOrder(UUID orderId) {
        var order = orders.findById(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        return order;
    }

    private static String generateCode() {
        var raw = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        return "ORD-" + raw;
    }

    private static OrderResponse toResponse(Order order, List<OrderItem> orderItems) {
        var itemResponses = orderItems.stream()
            .map(item -> new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getVariantId(),
                item.getProductName(),
                item.getVariantLabel(),
                item.getSku(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getLineTotal()
            ))
            .toList();

        return new OrderResponse(
            order.getId(),
            order.getCode(),
            order.getShopId(),
            order.getBuyerId(),
            order.getStatus().name(),
            order.getPaymentStatus().name(),
            order.getSubtotal(),
            order.getShippingFee(),
            order.getTotal(),
            order.getShippingName(),
            order.getShippingPhone(),
            order.getShippingAddress(),
            itemResponses,
            order.getCreatedAt()
        );
    }

    private record OrderItemSnapshot(
        UUID productId,
        UUID variantId,
        String productName,
        String variantLabel,
        String sku,
        long unitPrice,
        int quantity
    ) {
    }
}
