package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.admin.AdminOrderDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderSummaryResponse;
import com.minhnbnt.fashionstore.dto.admin.DailyOrderTotals;
import com.minhnbnt.fashionstore.dto.admin.OrderRevenueTotals;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.admin.OrderTimelineResponse;
import com.minhnbnt.fashionstore.dto.order.BulkConfirmFailureItem;
import com.minhnbnt.fashionstore.dto.order.BulkConfirmOrderResponse;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.order.OrderItemResponse;
import com.minhnbnt.fashionstore.dto.order.OrderResponse;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.order.repository.OrderAdminQueryRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderMonitoringQueryRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderStatusHistoryRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AdminCancelReason;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderItem;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.OrderStatusHistory;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final OrderStatusHistoryRepository histories;

    private final ProductStockService productStock;

    private final ShopService shops;

    private final RevenueService revenue;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final OrderAdminQueryRepository adminQueries;

    private final OrderMonitoringQueryRepository monitoring;

    private final ReturnRequestService returnRequests;

    private final Clock clock;

    private final OrderSlaPolicy sla;

    private final NotificationService notifications;

    // Single source of truth for the pending-order confirmation timeout. Both the
    // seller-facing confirm() deadline check and the background auto-cancel job read this.
    @Value("${fashionstore.order.pending-timeout-hours:24}")
    private long pendingTimeoutHours;

    @Transactional
    public OrderResponse checkout(@NonNull UUID buyerId, @NonNull CheckoutRequest request) {
        if (!shops.isShopApproved(request.shopId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Shop is not approved for selling");
        }

        if (request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order must have at least one item");
        }

        var shippingFee = 0L;
        if (request.shippingFee() != null) {
            shippingFee = request.shippingFee();
        }
        if (shippingFee < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shipping fee must not be negative");
        }

        var snapshots = new ArrayList<OrderItemSnapshot>();
        var subtotal = 0L;

        for (var item : request.items()) {
            var variant = productStock.findVariantById(item.variantId()).orElse(null);
            if (variant == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Variant not found");
            }
            if (!variant.getShopId().equals(request.shopId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All items must belong to one shop");
            }

            var product = productStock.findProductById(variant.getProductId()).orElse(null);
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

        for (var snapshot : snapshots) {
            if (snapshot.variantId() != null) {
                if (!productStock.decrementVariantStock(snapshot.variantId(), snapshot.quantity())) {
                    throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "Insufficient stock for product " + snapshot.productName()
                    );
                }
            }
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

        for (var snapshot : snapshots) {
            if (snapshot.variantId() != null) {
                productStock.recordStockMovement(
                    snapshot.variantId(),
                    request.shopId(),
                    snapshot.quantity(),
                    "ORDER_CREATED",
                    order.getId()
                );
            }
        }

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

        notifications.create(
            shops.findActiveUserIdsByShop(order.getShopId()),
            NotificationCategory.SELLING,
            "ORDER_PLACED",
            "Có đơn hàng mới #" + order.getCode(),
            "Khách hàng vừa đặt đơn #" + order.getCode() + " tại shop của bạn.",
            "/shop?shopId=" + order.getShopId() + "&tab=orders&orderId=" + order.getId(),
            order.getId()
        );

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

        notifications.create(
            shops.findActiveUserIdsByShop(order.getShopId()),
            NotificationCategory.SELLING,
            "ORDER_CANCELLED",
            "Đơn #" + order.getCode() + " đã bị hủy",
            "Khách hàng đã hủy đơn #" + order.getCode() + ".",
            "/shop?shopId=" + order.getShopId() + "&tab=orders&orderId=" + order.getId(),
            order.getId()
        );

        var orderItems = items.findByOrderId(order.getId());
        restockOrderItems(order, orderItems);
        return toResponse(orders.save(order), orderItems);
    }

    @Transactional
    public OrderResponse confirmReceipt(@NonNull UUID buyerId, @NonNull UUID orderId) {
        var order = requireBuyerOrder(buyerId, orderId);
        order.confirmReceipt();

        var orderItems = items.findByOrderId(order.getId());
        return toResponse(orders.save(order), orderItems);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listSellerOrders(@NonNull UUID userId) {
        var result = new ArrayList<OrderResponse>();

        for (var shopId : orderReadableShopIds(userId)) {
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
        access.requireShopPermission(userId, order.getShopId(), ShopPermission.ORDER_CANCEL);

        var from = order.getStatus();
        var orderItems = items.findByOrderId(order.getId());

        order.cancelBySeller(request.reason(), request.note());
        restockOrderItems(order, orderItems);
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CANCELLED, userId));

        var saved = orders.save(order);
        var cancelDetail = "Hủy đơn hàng #" + saved.getCode() + " | Lý do: " + request.reason();
        if (request.note() != null && !request.note().isBlank()) {
            cancelDetail = cancelDetail + " (" + request.note() + ")";
        }
        cancelDetail = cancelDetail + " | Khôi phục tồn kho cho " + orderItems.size() + " mặt hàng";
        audit.record(saved.getShopId(), userId, "ORDER_CANCEL", "ORDER", saved.getId(), cancelDetail);

        notifications.create(
            Set.of(order.getBuyerId()),
            NotificationCategory.ORDER,
            "ORDER_CANCELLED",
            "Đơn #" + order.getCode() + " đã bị hủy",
            "Đơn #" + order.getCode() + " đã bị hủy: " + request.reason(),
            "/account",
            order.getId()
        );

        return toResponse(saved, orderItems);
    }

    @Transactional
    public void systemCancelExpiredOrder(@NonNull UUID orderId) {
        var order = requireOrder(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        var from = order.getStatus();
        var orderItems = items.findByOrderId(order.getId());

        order.cancelBySystem("Hết thời hạn xác nhận đơn hàng (" + pendingTimeoutHours + " giờ)", null);
        restockOrderItems(order, orderItems);
        histories.save(OrderStatusHistory.record(
            order.getId(),
            from,
            OrderStatus.CANCELLED,
            null,
            "Quá hạn " + pendingTimeoutHours + " giờ"
        ));

        var saved = orders.save(order);
        var cancelDetail = "Hệ thống tự động hủy đơn hàng #" + saved.getCode()
            + " do quá hạn " + pendingTimeoutHours + " giờ chưa xác nhận | Khôi phục tồn kho cho "
            + orderItems.size() + " mặt hàng";
        audit.record(saved.getShopId(), null, "ORDER_SYSTEM_CANCEL", "ORDER", saved.getId(), cancelDetail);

        notifications.create(
            Set.of(saved.getBuyerId()),
            NotificationCategory.ORDER,
            "ORDER_CANCELLED",
            "Đơn #" + saved.getCode() + " đã bị hủy tự động",
            "Đơn #" + saved.getCode()
                + " đã tự động hủy do người bán không xác nhận trong vòng " + pendingTimeoutHours + " giờ.",
            "/account",
            saved.getId()
        );

        notifications.create(
            shops.findActiveUserIdsByShop(saved.getShopId()),
            NotificationCategory.SELLING,
            "ORDER_CANCELLED",
            "Đơn #" + saved.getCode() + " đã bị hủy tự động",
            "Đơn #" + saved.getCode()
                + " đã quá hạn xác nhận " + pendingTimeoutHours + " giờ và bị hệ thống tự động hủy.",
            "/shop?shopId=" + saved.getShopId() + "&tab=orders&orderId=" + saved.getId(),
            saved.getId()
        );
    }

    /**
     * Đơn PENDING đã quá thời hạn xác nhận theo cấu hình {@code pending-timeout-hours}.
     * Job hẹn giờ gọi phương thức này rồi tự gọi {@link #systemCancelExpiredOrder} qua proxy
     * để mỗi đơn được hủy trong một transaction riêng, lỗi của một đơn không ảnh hưởng đơn khác.
     */
    @Transactional(readOnly = true)
    public List<Order> findExpiredPendingOrders() {
        var cutoff = clock.instant().minus(pendingTimeoutHours, ChronoUnit.HOURS);
        return orders.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
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
     * Admin hủy can thiệp đơn chưa bàn giao vận chuyển,
     * hoàn kho nếu shop đã xác nhận (đã trừ kho).
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
        restockOrderItems(order, orderItems);
        orders.save(order);
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CANCELLED, adminId, note));

        notifications.create(
            Set.of(order.getBuyerId()),
            NotificationCategory.ORDER,
            "ORDER_CANCELLED",
            "Đơn #" + order.getCode() + " đã bị hủy",
            "Đơn #" + order.getCode() + " đã bị hủy: " + reason.name(),
            "/account",
            order.getId()
        );

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
            access.requireShopPermission(userId, shopId, ShopPermission.ORDER_READ);
            shopIds = List.of(shopId);
        } else {
            shopIds = orderReadableShopIds(userId);
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
                    var matchId = order.getId().toString().equalsIgnoreCase(lower);
                    var matchCode = order.getCode().toLowerCase(Locale.ROOT).contains(lower);
                    var matchBuyer = order.getShippingName().toLowerCase(Locale.ROOT).contains(lower);
                    if (!matchId && !matchCode && !matchBuyer) {
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

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public OrderResponse confirm(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = requireOrder(orderId);
        access.requireShopPermission(userId, order.getShopId(), ShopPermission.ORDER_PROCESS);

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending orders can be confirmed");
        }

        var cutoff = clock.instant().minus(pendingTimeoutHours, ChronoUnit.HOURS);
        if (order.getCreatedAt().isBefore(cutoff)) {
            systemCancelExpiredOrder(order.getId());
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Order confirmation deadline (" + pendingTimeoutHours + "h) has expired; order was cancelled by system"
            );
        }

        var from = order.getStatus();

        var orderItems = items.findByOrderId(order.getId());
        order.confirm();
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CONFIRMED, userId));

        var saved = orders.save(order);
        var confirmDetail = "Xác nhận đơn hàng #" + saved.getCode()
            + " | Tổng tiền: " + saved.getTotal() + "₫ (" + orderItems.size() + " món)";
        audit.record(saved.getShopId(), userId, "ORDER_CONFIRM", "ORDER", saved.getId(), confirmDetail);

        notifications.create(
            Set.of(order.getBuyerId()),
            NotificationCategory.ORDER,
            "ORDER_CONFIRMED",
            "Đơn #" + order.getCode() + " đã được xác nhận",
            "Shop đã xác nhận đơn #" + order.getCode() + ", đang chuẩn bị hàng.",
            "/account",
            order.getId()
        );

        return toResponse(saved, orderItems);
    }

    public BulkConfirmOrderResponse bulkConfirm(@NonNull UUID userId, @NonNull List<UUID> orderIds) {
        var successList = new ArrayList<UUID>();
        var failures = new ArrayList<BulkConfirmFailureItem>();

        for (var orderId : orderIds) {
            var orderOpt = orders.findById(orderId);
            if (orderOpt.isEmpty()) {
                failures.add(new BulkConfirmFailureItem(orderId, "UNKNOWN", "Order not found"));
                continue;
            }
            var order = orderOpt.get();
            try {
                access.requireShopPermission(userId, order.getShopId(), ShopPermission.ORDER_PROCESS);
            } catch (Exception ex) {
                failures.add(new BulkConfirmFailureItem(
                    orderId,
                    order.getCode(),
                    "Insufficient permission to process order"
                ));
                continue;
            }

            if (order.getStatus() != OrderStatus.PENDING) {
                failures.add(new BulkConfirmFailureItem(
                    orderId,
                    order.getCode(),
                    "Order is not in pending status"
                ));
                continue;
            }

            try {
                confirm(userId, orderId);
                successList.add(orderId);
            } catch (Exception ex) {
                var reason = ex.getMessage();
                if (reason == null || reason.isBlank()) {
                    reason = "Confirmation failed";
                }
                failures.add(new BulkConfirmFailureItem(orderId, order.getCode(), reason));
            }
        }

        return new BulkConfirmOrderResponse(
            orderIds.size(),
            successList.size(),
            failures.size(),
            successList,
            failures
        );
    }

    @Transactional(readOnly = true)
    public List<OrderTimelineResponse> getOrderTimeline(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = orders.findById(orderId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found")
        );
        access.requireShopPermission(userId, order.getShopId(), ShopPermission.ORDER_READ);
        return monitoring.timeline(orderId);
    }

    @Transactional
    public OrderResponse ship(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = requireOrder(orderId);
        access.requireShopPermission(userId, order.getShopId(), ShopPermission.ORDER_PROCESS);

        var from = order.getStatus();
        order.ship();
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.SHIPPING, userId));

        var orderItems = items.findByOrderId(order.getId());
        var saved = orders.save(order);
        var shipDetail = "Giao đơn hàng #" + saved.getCode() + " (" + orderItems.size()
                + " món) cho đơn vị vận chuyển";
        audit.record(saved.getShopId(), userId, "ORDER_SHIP", "ORDER", saved.getId(), shipDetail);

        notifications.create(
            Set.of(order.getBuyerId()),
            NotificationCategory.ORDER,
            "ORDER_SHIPPED",
            "Đơn #" + order.getCode() + " đang được giao",
            "Shop đã bàn giao đơn #" + order.getCode() + " cho đơn vị vận chuyển.",
            "/account",
            order.getId()
        );

        return toResponse(saved, orderItems);
    }

    @Transactional
    public OrderResponse deliver(@NonNull UUID userId, @NonNull UUID orderId) {
        var order = requireOrder(orderId);
        access.requireShopPermission(userId, order.getShopId(), ShopPermission.ORDER_PROCESS);

        var from = order.getStatus();
        order.deliver();
        histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.DELIVERED, userId));
        revenue.createSettlementForOrder(order.getShopId(), order.getId(), order.getSubtotal());

        var orderItems = items.findByOrderId(order.getId());
        var saved = orders.save(order);
        var deliverDetail = "Đánh dấu đơn hàng #" + saved.getCode()
            + " giao thành công | Doanh thu thanh toán: " + saved.getSubtotal() + "₫";
        audit.record(saved.getShopId(), userId, "ORDER_DELIVER", "ORDER", saved.getId(), deliverDetail);

        notifications.create(
            Set.of(order.getBuyerId()),
            NotificationCategory.ORDER,
            "ORDER_DELIVERED",
            "Đơn #" + order.getCode() + " đã được giao",
            "Đơn #" + order.getCode() + " đã giao thành công. Đừng quên đánh giá sản phẩm!",
            "/account",
            order.getId()
        );

        return toResponse(saved, orderItems);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOrdersForProduct(@NonNull UUID productId) {
        return items.hasActiveOrdersForProduct(productId);
    }

    @Transactional(readOnly = true)
    public int getSoldQuantityForProduct(@NonNull UUID productId) {
        return items.countSoldQuantityByProductId(productId);
    }

    @Transactional(readOnly = true)
    public boolean hasDeliveredAndReceivedProduct(@NonNull UUID buyerId, @NonNull UUID productId) {
        for (var order : orders.findByBuyerIdOrderByCreatedAtDesc(buyerId)) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                continue;
            }
            if (order.getReceivedAt() == null) {
                continue;
            }
            for (var item : items.findByOrderId(order.getId())) {
                if (productId.equals(item.getProductId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Danh sách shop mà user được xem đơn: chỉ shop đã cấp ORDER_READ.
     */
    private List<UUID> orderReadableShopIds(UUID userId) {
        var result = new ArrayList<UUID>();
        for (var shopId : shops.findShopIdsByUser(userId)) {
            if (access.permissionsOf(userId, shopId).contains(ShopPermission.ORDER_READ)) {
                result.add(shopId);
            }
        }

        return result;
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
     * Khóa dòng đơn: mọi chuyển trạng thái của shop/admin đi qua đây,
     * người đến sau thấy trạng thái mới và nhận 409.
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
     * Hoàn kho nguyên tử và ghi nhận biến động khi đơn bị hủy
     * ở bất kỳ trạng thái nào trước khi giao.
     */
    private void restockOrderItems(Order order, List<OrderItem> orderItems) {
        for (var item : orderItems) {
            if (item.getVariantId() == null) {
                continue;
            }
            productStock.incrementVariantStock(item.getVariantId(), item.getQuantity());
            productStock.recordStockMovement(
                item.getVariantId(),
                order.getShopId(),
                item.getQuantity(),
                "ORDER_CANCEL",
                order.getId()
            );
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

        String cancelledByName = null;
        if (order.getCancelledBy() != null) {
            cancelledByName = order.getCancelledBy().name();
        }

        return new OrderResponse(
            order.getId(),
            order.getCode(),
            order.getShopId(),
            order.getBuyerId(),
            order.getStatus().name(),
            order.getPaymentStatus().name(),
            order.getPaymentMethod(),
            order.getSubtotal(),
            order.getShippingFee(),
            order.getTotal(),
            order.getShippingName(),
            order.getShippingPhone(),
            order.getShippingAddress(),
            order.getNote(),
            order.getCancelReason(),
            order.getCancelNote(),
            cancelledByName,
            order.getConfirmedAt(),
            order.getCancelledAt(),
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
