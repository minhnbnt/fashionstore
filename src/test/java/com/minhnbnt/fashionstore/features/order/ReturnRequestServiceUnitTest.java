package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnRequestRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceUnitTest {

    @Mock
    private ReturnRequestRepository returns;

    @Mock
    private OrderRepository orders;

    @Mock
    private ShopAccessChecker access;

    @InjectMocks
    private ReturnRequestService returnService;

    @Test
    void createReturnThrowsConflictWhenOrderNotDelivered() {
        var userId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        var order = Order.create(
            "ORD-01",
            shopId,
            userId,
            100000L,
            0L,
            "Buyer",
            "0900",
            "Address"
        );
        // Order status is PENDING, not DELIVERED

        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        var req = new CreateReturnRequest("DAMAGED", "Item broken", List.of("img1.jpg"));

        assertThatThrownBy(() -> returnService.create(userId, orderId, req))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createReturnSucceedsWhenOrderDelivered() {
        var userId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        var order = Order.create(
            "ORD-02",
            shopId,
            userId,
            100000L,
            0L,
            "Buyer",
            "0900",
            "Address"
        );
        order.confirm();
        order.ship();
        order.deliver();

        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        var ret = ReturnRequest.create(
            orderId,
            shopId,
            userId,
            "DAMAGED",
            "Item broken",
            new String[]{"img1.jpg"}
        );
        when(returns.save(any(ReturnRequest.class))).thenReturn(ret);

        var req = new CreateReturnRequest("DAMAGED", "Item broken", List.of("img1.jpg"));
        var resp = returnService.create(userId, orderId, req);

        assertThat(resp.orderId()).isEqualTo(orderId);
        assertThat(resp.status()).isEqualTo("REQUESTED");
        verify(returns).save(any(ReturnRequest.class));
    }

    @Test
    void approveReturnChangesStatusToApproved() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var returnId = UUID.randomUUID();

        var ret = ReturnRequest.create(
            UUID.randomUUID(),
            shopId,
            userId,
            "DAMAGED",
            "Broken",
            new String[]{"img.jpg"}
        );

        when(returns.findById(returnId)).thenReturn(Optional.of(ret));
        when(returns.save(any(ReturnRequest.class))).thenReturn(ret);

        var req = new ApproveReturnRequest("REFUND", 100000L, "Approved full refund");
        var resp = returnService.approve(userId, returnId, req);

        verify(access).requireShopPermission(userId, shopId, ShopPermission.RETURN_PROCESS);
        assertThat(resp.status()).isEqualTo("APPROVED");
        verify(returns).save(ret);
    }

    @Test
    void rejectReturnChangesStatusToRejected() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var returnId = UUID.randomUUID();

        var ret = ReturnRequest.create(
            UUID.randomUUID(),
            shopId,
            userId,
            "DEFECTIVE",
            "Broken",
            new String[]{"img.jpg"}
        );

        when(returns.findById(returnId)).thenReturn(Optional.of(ret));
        when(returns.save(any(ReturnRequest.class))).thenReturn(ret);

        var req = new RejectReturnRequest("Item was used over 30 days");
        var resp = returnService.reject(userId, returnId, req);

        assertThat(resp.status()).isEqualTo("REJECTED");
        assertThat(ret.getSellerNote()).isEqualTo("Item was used over 30 days");
        verify(returns).save(ret);
    }
}
