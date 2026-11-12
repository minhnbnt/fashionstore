package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.dto.order.ReturnRequestResponse;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnRequestRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.ReturnStatus;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReturnRequestService {

    private static final List<String> ALLOWED_REASONS = List.of(
        "WRONG_ITEM",
        "DAMAGED",
        "NOT_AS_DESCRIBED",
        "CHANGE_MIND",
        "OTHER"
    );

    private final ReturnRequestRepository returns;

    private final OrderRepository orders;

    private final ShopService shops;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final NotificationService notifications;

    @Transactional
    public ReturnRequestResponse create(
        @NonNull UUID buyerId,
        @NonNull UUID orderId,
        @NonNull CreateReturnRequest request
    ) {
        var order = orders.findById(orderId).orElse(null);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Return request can only be created for delivered orders"
            );
        }
        var reason = request.reason().trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_REASONS.contains(reason)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid return reason: " + request.reason());
        }
        if (returns.existsBlockingByOrderId(order.getId())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Order already has an active or completed return request"
            );
        }

        String[] evidenceArr = new String[0];
        if (request.evidenceKeys() != null) {
            evidenceArr = request.evidenceKeys().toArray(new String[0]);
        }

        var returnReq = ReturnRequest.create(
            order.getId(),
            order.getShopId(),
            buyerId,
            reason,
            request.description(),
            evidenceArr
        );

        var saved = returns.save(returnReq);
        notifications.create(
            shops.findActiveUserIdsByShop(saved.getShopId()),
            NotificationCategory.SELLING,
            "RETURN_REQUESTED",
            "Yêu cầu đổi/trả cho đơn #" + order.getCode(),
            "Khách hàng vừa gửi yêu cầu đổi/trả cho đơn #" + order.getCode() + ".",
            "/shop?shopId=" + saved.getShopId() + "&tab=returns&orderId=" + saved.getOrderId(),
            saved.getId()
        );

        return toResponse(saved, order);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listSellerReturns(
        @NonNull UUID userId,
        @Nullable UUID shopId,
        @Nullable String statusFilter
    ) {
        List<UUID> shopIds;
        if (shopId != null) {
            access.requireShopPermission(userId, shopId, ShopPermission.RETURN_PROCESS);
            shopIds = List.of(shopId);
        } else {
            shopIds = returnProcessShopIds(userId);
        }

        var list = new ArrayList<ReturnRequest>();
        for (var sId : shopIds) {
            if (statusFilter != null && !statusFilter.isBlank()) {
                list.addAll(returns.findByShopIdAndStatusOrderByCreatedAtDesc(
                    sId,
                    ReturnStatus.valueOf(statusFilter.toUpperCase(Locale.ROOT))
                ));
            } else {
                list.addAll(returns.findByShopIdOrderByCreatedAtDesc(sId));
            }
        }
        list.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return toResponses(list);
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getDetail(@NonNull UUID userId, @NonNull UUID id) {
        var req = returns.findById(id).orElse(null);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found");
        }
        access.requireShopPermission(userId, req.getShopId(), ShopPermission.RETURN_PROCESS);
        return toResponses(List.of(req)).getFirst();
    }

    @Transactional
    public ReturnRequestResponse approve(
        @NonNull UUID userId,
        @NonNull UUID id,
        @NonNull ApproveReturnRequest request
    ) {
        var req = returns.findById(id).orElse(null);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found");
        }
        access.requireShopPermission(userId, req.getShopId(), ShopPermission.RETURN_PROCESS);

        req.approve(request.resolution(), request.refundAmount(), request.sellerNote());
        var saved = returns.save(req);
        var approveDetail = "Chấp thuận yêu cầu trả hàng | Phương án: " + request.resolution()
            + " | Số tiền hoàn: " + request.refundAmount() + "₫";
        if (request.sellerNote() != null && !request.sellerNote().isBlank()) {
            approveDetail = approveDetail + " | Ghi chú người bán: " + request.sellerNote();
        }
        audit.record(saved.getShopId(), userId, "RETURN_APPROVE", "RETURN", saved.getId(), approveDetail);

        var order = findOrderOrNull(saved.getOrderId());
        var label = orderLabel(order);
        notifications.create(
            Set.of(saved.getBuyerId()),
            NotificationCategory.ORDER,
            "RETURN_APPROVED",
            "Yêu cầu đổi/trả " + label + " đã được chấp thuận",
            "Shop đã chấp thuận yêu cầu đổi/trả cho đơn " + label + ".",
            "/account/orders/" + saved.getOrderId(),
            saved.getId()
        );

        return toResponse(saved, order);
    }

    @Transactional
    public ReturnRequestResponse reject(
        @NonNull UUID userId,
        @NonNull UUID id,
        @NonNull RejectReturnRequest request
    ) {
        var req = returns.findById(id).orElse(null);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found");
        }
        access.requireShopPermission(userId, req.getShopId(), ShopPermission.RETURN_PROCESS);

        req.reject(request.sellerNote());
        var saved = returns.save(req);
        var detail = "Từ chối yêu cầu trả hàng";
        var sellerNote = request.sellerNote();
        if (sellerNote != null && !sellerNote.isBlank()) {
            detail = "Từ chối yêu cầu trả hàng | Lý do: " + sellerNote;
        }
        audit.record(saved.getShopId(), userId, "RETURN_REJECT", "RETURN", saved.getId(), detail);

        var order = findOrderOrNull(saved.getOrderId());
        var label = orderLabel(order);
        notifications.create(
            Set.of(saved.getBuyerId()),
            NotificationCategory.ORDER,
            "RETURN_REJECTED",
            "Yêu cầu đổi/trả " + label + " bị từ chối",
            "Yêu cầu đổi/trả cho đơn " + label + " đã bị từ chối: " + request.sellerNote(),
            "/account/orders/" + saved.getOrderId(),
            saved.getId()
        );

        return toResponse(saved, order);
    }

    @Transactional
    public ReturnRequestResponse complete(
        @NonNull UUID userId,
        @NonNull UUID id,
        @Nullable String note
    ) {
        var req = returns.findById(id).orElse(null);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found");
        }
        access.requireShopPermission(userId, req.getShopId(), ShopPermission.RETURN_PROCESS);

        req.complete(note);
        var saved = returns.save(req);
        var completeDetail = "Hoàn tất xử lý đổi trả";
        if (note != null && !note.isBlank()) {
            completeDetail = completeDetail + " | Ghi chú: " + note;
        }
        audit.record(saved.getShopId(), userId, "RETURN_COMPLETE", "RETURN", saved.getId(), completeDetail);

        var order = findOrderOrNull(saved.getOrderId());
        var label = orderLabel(order);
        notifications.create(
            Set.of(saved.getBuyerId()),
            NotificationCategory.ORDER,
            "RETURN_COMPLETED",
            "Đổi/trả " + label + " đã hoàn tất",
            "Yêu cầu đổi/trả cho đơn " + label + " đã được hoàn tất.",
            "/account/orders/" + saved.getOrderId(),
            saved.getId()
        );

        return toResponse(saved, order);
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listBuyerReturns(@NonNull UUID buyerId) {
        return toResponses(returns.findByBuyerIdOrderByCreatedAtDesc(buyerId));
    }

    /**
     * Mọi yêu cầu trả hàng của 1 đơn, mới nhất trước. Không kiểm quyền: chỉ màn giám sát admin gọi.
     */
    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listForOrder(@NonNull UUID orderId) {
        return toResponses(returns.findByOrderIdOrderByCreatedAtDesc(orderId));
    }

    /**
     * Danh sách shop mà user được xử lý trả hàng: chỉ shop đã cấp RETURN_PROCESS.
     */
    private List<UUID> returnProcessShopIds(UUID userId) {
        var result = new ArrayList<UUID>();
        for (var shopId : shops.findShopIdsByUser(userId)) {
            if (access.permissionsOf(userId, shopId).contains(ShopPermission.RETURN_PROCESS)) {
                result.add(shopId);
            }
        }

        return result;
    }

    private @Nullable Order findOrderOrNull(UUID orderId) {
        for (var order : orders.findAllById(List.of(orderId))) {
            return order;
        }
        return null;
    }

    private static String orderLabel(@Nullable Order order) {
        if (order == null) {
            return "";
        }
        return "#" + order.getCode();
    }

    private List<ReturnRequestResponse> toResponses(List<ReturnRequest> requests) {
        if (requests.isEmpty()) {
            return List.of();
        }
        var orderIds = new LinkedHashSet<UUID>();
        for (var req : requests) {
            orderIds.add(req.getOrderId());
        }
        Map<UUID, Order> ordersById = new LinkedHashMap<>();
        orders.findAllById(orderIds).forEach(order -> ordersById.put(order.getId(), order));

        var result = new ArrayList<ReturnRequestResponse>(requests.size());
        for (var req : requests) {
            result.add(toResponse(req, ordersById.get(req.getOrderId())));
        }
        return result;
    }

    private static ReturnRequestResponse toResponse(ReturnRequest req, @Nullable Order order) {
        List<String> evidence = List.of();
        if (req.getEvidenceKeys() != null) {
            evidence = Arrays.asList(req.getEvidenceKeys());
        }
        String orderCode = "";
        String orderStatus = OrderStatus.DELIVERED.name();
        if (order != null) {
            orderCode = order.getCode();
            orderStatus = order.getStatus().name();
        }

        return new ReturnRequestResponse(
            req.getId(),
            req.getOrderId(),
            orderCode,
            orderStatus,
            req.getShopId(),
            req.getBuyerId(),
            req.getReason(),
            req.getDescription(),
            evidence,
            req.getStatus().name(),
            req.getResolution(),
            req.getRefundAmount(),
            req.getSellerNote(),
            req.getCreatedAt(),
            req.getUpdatedAt()
        );
    }
}
