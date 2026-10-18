package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.admin.AdminOrderDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderSummaryResponse;
import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
import com.minhnbnt.fashionstore.dto.admin.OrderRevenueTotals;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.OrderItemResponse;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.StockMovementRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderAdminQueryRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderMonitoringQueryRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderStatusHistoryRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderStockRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AdminCancelReason;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderItem;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.OrderStatusHistory;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.StockMovement;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final StockMovementRepository movements;

    private final ShopService shops;

    private final ShopMemberRepository members;

    private final RevenueService revenue;

    private final ShopAccessChecker access;

    private final OrderAdminQueryRepository adminQueries;

    private final OrderMonitoringQueryRepository monitoring;

    private final ReturnRequestService returnRequests;

    private final Clock clock;

    private final OrderSlaPolicy sla;

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
            // Hàng shop ẩn/xóa hoặc admin gỡ vi phạm không được bán tiếp.
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is not available for sale");
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
    public long countByBuyer(@NonNull UUID buyerId) {
        return orders.countByBuyerId(buyerId);
    }

    @Transactional(readOnly = true)
    public long countByShop(@NonNull UUID shopId) {
        return orders.countByShopId(shopId);
    }

    @Transactional(readOnly = true)
    public Map<OrderStatus, Long> countByStatus() {
        return adminQueries.countByStatus();
    }

    @Transactional(readOnly = true)
    public OrderRevenueTotals revenueTotals() {
        return adminQueries.revenueTotals();
    }

    @Transactional(readOnly = true)
    public Map<LocalDate, DailyOrderTotals> totalsPerDay(@NonNull Instant from, @NonNull ZoneId zone) {
        return adminQueries.totalsPerDay(from, zone);
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
    public OrderResponse sellerCancel(
        @NonNull UUID userId,
        @NonNull UUID orderId,
        @NonNull CancelOrderRequest request
    ) {
        var order = requireOrder(orderId);
        access.requireShopRole(userId, order.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        var from = order.getStatus();
        var orderItems = items.findByOrderId(order.getId());

        order.cancelBySeller(request.reason(), request.note());
        restockIfConfirmed(order, from, orderItems);
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CANCELLED, userId));

        return toResponse(orders.save(order), orderItems);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> searchForAdmin(@NonNull AdminOrderFilter filter, int page, int size) {
        requireValidRange(filter);

        var cutoffs = sla.cutoffsAt(clock.instant());
        var found = monitoring.search(filter, cutoffs, page, size);
        var total = monitoring.count(filter, cutoffs);
        return new PageResponse<>(found, total, page, size);
    }

    @Transactional(readOnly = true)
    public AdminOrderSummaryResponse summaryForAdmin(@NonNull AdminOrderFilter filter) {
        requireValidRange(filter);

        return monitoring.summary(filter, sla.cutoffsAt(clock.instant()));
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getForAdmin(@NonNull UUID orderId) {
        var summary = requireAdminOrder(orderId);
        var order = orders.findById(orderId).orElseThrow();

        var itemResponses = items.findByOrderId(orderId).stream()
            .map(OrderService::toItemResponse)
            .toList();

        return new AdminOrderDetailResponse(
            summary,
            monitoring.shopStatusOf(orderId),
            order.getPaymentMethod(),
            order.getSubtotal(),
            order.getShippingFee(),
            order.getShippingAddress(),
            order.getNote(),
            order.getCancelNote(),
            order.getConfirmedAt(),
            order.getShippedAt(),
            order.getDeliveredAt(),
            order.getCancelledAt(),
            itemResponses,
            monitoring.timeline(orderId),
            returnRequests.listForOrder(orderId)
        );
    }

    /**
     * Admin hủy can thiệp đơn chưa bàn giao vận chuyển, hoàn kho nếu shop đã xác nhận (đã trừ kho).
     */
    @Transactional
    public AdminOrderResponse cancelByAdmin(
        @NonNull UUID adminId,
        @NonNull UUID orderId,
        @NonNull AdminCancelReason reason,
        @NonNull String note
    ) {
        var order = requireOrder(orderId);
        var from = order.getStatus();
        var orderItems = items.findByOrderId(order.getId());

        order.cancelByAdmin(reason, note);
        restockIfConfirmed(order, from, orderItems);
        orders.save(order);
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CANCELLED, adminId, note));

        return requireAdminOrder(orderId);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listSellerOrdersFiltered(
        @NonNull UUID userId,
        @Nullable UUID shopId,
        @Nullable String statusFilter,
        @Nullable String q,
        int page,
        int limit
    ) {
        List<UUID> shopIds;
        if (shopId != null) {
            access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);
            shopIds = List.of(shopId);
        } else {
            shopIds = members.findShopIdsByUser(userId);
        }

        var result = new ArrayList<OrderResponse>();
        for (var sId : shopIds) {
            for (var order : orders.findByShopIdOrderByCreatedAtDesc(sId)) {
                if (statusFilter != null && !statusFilter.isBlank()) {
                    if (!order.getStatus().name().equalsIgnoreCase(statusFilter)) {
                        continue;
                    }
                }
                if (q != null && !q.isBlank()) {
                    var lower = q.toLowerCase(Locale.ROOT);
                    var matchCode = order.getCode().toLowerCase(Locale.ROOT).contains(lower);
                    var matchBuyer = order.getShippingName().toLowerCase(Locale.ROOT).contains(lower);
                    if (!matchCode && !matchBuyer) {
                        continue;
                    }
                }
                var orderItems = items.findByOrderId(order.getId());
                result.add(toResponse(order, orderItems));
            }
        }

        result.sort((left, right) -> right.createdAt().compareTo(left.createdAt()));

        var pageSize = Math.clamp(limit, 1, 100);
        var offset = (long) Math.max(page, 0) * pageSize;
        return result.stream()
            .skip(offset)
            .limit(pageSize)
            .toList();
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
        revenue.createSettlementForOrder(order.getShopId(), order.getId(), order.getSubtotal());

        var orderItems = items.findByOrderId(order.getId());
        return toResponse(orders.save(order), orderItems);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOrdersForProduct(@NonNull UUID productId) {
        return items.hasActiveOrdersForProduct(productId);
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

    /**
     * Khóa dòng đơn: mọi chuyển trạng thái của shop/admin đi qua đây, người đến sau thấy trạng thái mới và nhận 409.
     */
    private Order requireOrder(UUID orderId) {
        var order = orders.findLockedById(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        return order;
    }

    private AdminOrderResponse requireAdminOrder(UUID orderId) {
        var order = monitoring.findById(orderId, sla.cutoffsAt(clock.instant())).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }

        return order;
    }

    private static void requireValidRange(AdminOrderFilter filter) {
        var from = filter.from();
        var to = filter.to();
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
    }

    /**
     * Đơn CONFIRMED đã trừ kho lúc xác nhận, hủy thì trả lại kho + ghi biến động. Gọi sau khi đơn đã đổi sang hủy.
     */
    private void restockIfConfirmed(Order order, OrderStatus from, List<OrderItem> orderItems) {
        if (from != OrderStatus.CONFIRMED) {
            return;
        }

        for (var item : orderItems) {
            if (item.getVariantId() == null) {
                continue;
            }
            stocks.increment(item.getVariantId(), item.getQuantity());
            movements.save(StockMovement.record(
                item.getVariantId(),
                order.getShopId(),
                item.getQuantity(),
                "ORDER_CANCEL",
                order.getId()
            ));
        }
    }

    private static String generateCode() {
        var raw = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        return "ORD-" + raw;
    }

    private static OrderItemResponse toItemResponse(OrderItem item) {
        return new OrderItemResponse(
            item.getId(),
            item.getProductId(),
            item.getVariantId(),
            item.getProductName(),
            item.getVariantLabel(),
            item.getSku(),
            item.getUnitPrice(),
            item.getQuantity(),
            item.getLineTotal()
        );
    }

    private static OrderResponse toResponse(Order order, List<OrderItem> orderItems) {
        var itemResponses = orderItems.stream()
            .map(OrderService::toItemResponse)
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
