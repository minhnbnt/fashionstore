package com.minhnbnt.fashionstore.features.admin.service;

import com.minhnbnt.fashionstore.dto.admin.AdminOrderDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderFilter;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminOrderSummaryResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.model.AdminCancelReason;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Giám sát đơn hàng toàn sàn: tìm, đếm theo trạng thái/cảnh báo SLA, xem chi tiết, hủy can thiệp.
 * Luật trạng thái nằm ở order.
 */
@Service
@RequiredArgsConstructor
public class AdminOrderService {

    private final OrderService orders;

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderResponse> search(@NonNull AdminOrderFilter filter, int page, int size) {
        return orders.searchForAdmin(filter, page, size);
    }

    @Transactional(readOnly = true)
    public AdminOrderSummaryResponse summary(@NonNull AdminOrderFilter filter) {
        return orders.summaryForAdmin(filter);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getDetail(@NonNull UUID orderId) {
        return orders.getForAdmin(orderId);
    }

    @Transactional
    public AdminOrderResponse cancel(
        @NonNull UUID adminId,
        @NonNull UUID orderId,
        @NonNull AdminCancelReason reason,
        @NonNull String note
    ) {
        return orders.cancelByAdmin(adminId, orderId, reason, note);
    }
}
