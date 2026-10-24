package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.dto.order.ReturnRequestResponse;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnRequestRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.ReturnStatus;
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
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReturnRequestService {

    private final ReturnRequestRepository returns;

    private final OrderRepository orders;

    private final ShopMemberRepository members;

    private final ShopAccessChecker access;

    @Transactional
    public ReturnRequestResponse create(
        @NonNull UUID buyerId,
        @NonNull UUID orderId,
        @NonNull CreateReturnRequest request
    ) {
        var order = orders.findById(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Return request can only be created for delivered orders"
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
            request.reason(),
            request.description(),
            evidenceArr
        );

        return toResponse(returns.save(returnReq));
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listSellerReturns(
        @NonNull UUID userId,
        @Nullable UUID shopId,
        @Nullable String statusFilter
    ) {
        List<UUID> shopIds;
        if (shopId != null) {
            access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);
            shopIds = List.of(shopId);
        } else {
            shopIds = members.findShopIdsByUser(userId);
        }

        var result = new ArrayList<ReturnRequestResponse>();
        for (var sId : shopIds) {
            List<ReturnRequest> list;
            if (statusFilter != null && !statusFilter.isBlank()) {
                list = returns.findByShopIdAndStatusOrderByCreatedAtDesc(
                    sId,
                    ReturnStatus.valueOf(statusFilter.toUpperCase(Locale.ROOT))
                );
            } else {
                list = returns.findByShopIdOrderByCreatedAtDesc(sId);
            }
            for (var item : list) {
                result.add(toResponse(item));
            }
        }
        result.sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        return result;
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getDetail(@NonNull UUID userId, @NonNull UUID id) {
        var req = returns.findById(id).orElse(null);
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Return request not found");
        }
        access.requireShopRole(userId, req.getShopId(), MemberRole.OWNER, MemberRole.STAFF);
        return toResponse(req);
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
        access.requireShopRole(userId, req.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        req.approve(request.resolution(), request.refundAmount(), request.sellerNote());
        return toResponse(returns.save(req));
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
        access.requireShopRole(userId, req.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        req.reject(request.sellerNote());
        return toResponse(returns.save(req));
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
        access.requireShopRole(userId, req.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        req.complete(note);
        return toResponse(returns.save(req));
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listBuyerReturns(@NonNull UUID buyerId) {
        return returns.findByBuyerIdOrderByCreatedAtDesc(buyerId).stream()
            .map(ReturnRequestService::toResponse)
            .toList();
    }

    /**
     * Mọi yêu cầu trả hàng của 1 đơn, mới nhất trước. Không kiểm quyền: chỉ màn giám sát admin gọi.
     */
    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> listForOrder(@NonNull UUID orderId) {
        return returns.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
            .map(ReturnRequestService::toResponse)
            .toList();
    }

    private static ReturnRequestResponse toResponse(ReturnRequest req) {
        List<String> evidence = List.of();
        if (req.getEvidenceKeys() != null) {
            evidence = Arrays.asList(req.getEvidenceKeys());
        }

        return new ReturnRequestResponse(
            req.getId(),
            req.getOrderId(),
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
