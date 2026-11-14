package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.dto.order.ApproveReturnRequest;
import com.minhnbnt.fashionstore.dto.order.CreateReturnRequest;
import com.minhnbnt.fashionstore.dto.order.RejectReturnRequest;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnEvidenceRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnRequestRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.Upload;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReturnRequestServiceUnitTest {

    @Mock
    private ReturnRequestRepository returns;

    @Mock
    private OrderRepository orders;

    @Mock
    private ShopService shops;

    @Mock
    private ShopAccessChecker access;

    @Mock
    private ShopAuditService audit;

    @Mock
    private NotificationService notifications;

    @Mock
    private ReturnEvidenceRepository evidence;

    @Mock
    private UploadRepository uploads;

    @Mock
    private S3UrlService s3Urls;

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

        var req = new CreateReturnRequest("DAMAGED", "Item broken", null);

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
            "Item broken"
        );
        when(returns.save(any(ReturnRequest.class))).thenReturn(ret);

        var evidenceId = UUID.randomUUID();
        when(uploads.findById(evidenceId)).thenReturn(
            Optional.of(Upload.create("returns/" + evidenceId + ".webp", "image/webp")));

        var req = new CreateReturnRequest("DAMAGED", "Item broken", List.of(evidenceId));
        var resp = returnService.create(userId, orderId, req);

        assertThat(resp.orderId()).isEqualTo(orderId);
        assertThat(resp.status()).isEqualTo("REQUESTED");
        assertThat(resp.evidence()).isEmpty();
        verify(returns).save(any(ReturnRequest.class));
        verify(evidence).saveAll(any());
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
            "Broken"
        );

        when(returns.findById(returnId)).thenReturn(Optional.of(ret));
        when(returns.save(any(ReturnRequest.class))).thenReturn(ret);

        var req = new ApproveReturnRequest("REFUND", 100000L, "Approved full refund");
        var resp = returnService.approve(userId, returnId, req);

        verify(access).requireShopPermission(userId, shopId, ShopPermission.RETURN_PROCESS);
        assertThat(resp.status()).isEqualTo("APPROVED");
        verify(returns).save(ret);
        verify(audit).record(eq(shopId), eq(userId), eq("RETURN_APPROVE"), eq("RETURN"), any(), anyString());
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
            "DAMAGED",
            "Broken"
        );

        when(returns.findById(returnId)).thenReturn(Optional.of(ret));
        when(returns.save(any(ReturnRequest.class))).thenReturn(ret);

        var req = new RejectReturnRequest("Item was used over 30 days");
        var resp = returnService.reject(userId, returnId, req);

        assertThat(resp.status()).isEqualTo("REJECTED");
        assertThat(ret.getSellerNote()).isEqualTo("Item was used over 30 days");
        verify(returns).save(ret);
        verify(audit).record(eq(shopId), eq(userId), eq("RETURN_REJECT"), eq("RETURN"), any(), anyString());
    }

    @Test
    void createReturnValidatesReasonBlocksActiveDuplicateAllowsAfterRejectAndNotifiesBothSides() {
        var buyerId = UUID.randomUUID();
        var sellerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var order = Order.create("ORD-RET1", shopId, buyerId, 120000L, 0L, "An", "090", "HN");
        order.confirm();
        order.ship();
        order.deliver();

        when(orders.findById(order.getId())).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> returnService.create(
            buyerId,
            order.getId(),
            new CreateReturnRequest("DEFECTIVE", "Loi", List.of())
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST));

        when(returns.existsBlockingByOrderId(order.getId())).thenReturn(true);
        assertThatThrownBy(() -> returnService.create(
            buyerId,
            order.getId(),
            new CreateReturnRequest("DAMAGED", "Rach", List.of())
        )).isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        when(returns.existsBlockingByOrderId(order.getId())).thenReturn(false);
        when(returns.save(any(ReturnRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orders.findAllById(any())).thenReturn(List.of(order));
        when(shops.findActiveUserIdsByShop(shopId)).thenReturn(List.of(sellerId));

        var imageId = UUID.randomUUID();
        var videoId = UUID.randomUUID();
        when(uploads.findById(imageId)).thenReturn(
            Optional.of(Upload.create("returns/" + imageId + ".webp", "image/webp")));
        when(uploads.findById(videoId)).thenReturn(
            Optional.of(Upload.create("returns/" + videoId + ".mp4", "video/mp4")));

        var created = returnService.create(
            buyerId,
            order.getId(),
            new CreateReturnRequest("DAMAGED", "Rach tay ao", List.of(imageId, videoId))
        );
        assertThat(created.orderCode()).isEqualTo("ORD-RET1");
        assertThat(created.orderStatus()).isEqualTo("DELIVERED");
        verify(notifications).create(
            eq(List.of(sellerId)),
            eq(NotificationCategory.SELLING),
            eq("RETURN_REQUESTED"),
            anyString(),
            anyString(),
            eq("/shop?shopId=" + shopId + "&tab=returns&orderId=" + order.getId()),
            eq(created.id())
        );

        var entity = ReturnRequest.create(
            order.getId(),
            shopId,
            buyerId,
            "DAMAGED",
            "Rach tay ao"
        );
        when(returns.findById(entity.getId())).thenReturn(Optional.of(entity));
        var approved = returnService.approve(
            sellerId,
            entity.getId(),
            new ApproveReturnRequest("REFUND", 50000L, "OK")
        );
        assertThat(approved.orderCode()).isEqualTo("ORD-RET1");
        verify(notifications).create(
            eq(Set.of(buyerId)),
            eq(NotificationCategory.ORDER),
            eq("RETURN_APPROVED"),
            anyString(),
            anyString(),
            eq("/account/orders/" + order.getId()),
            eq(entity.getId())
        );

        var completed = returnService.complete(sellerId, entity.getId(), "Da hoan tien");
        assertThat(completed.status()).isEqualTo("COMPLETED");
        verify(notifications).create(
            eq(Set.of(buyerId)),
            eq(NotificationCategory.ORDER),
            eq("RETURN_COMPLETED"),
            anyString(),
            anyString(),
            eq("/account/orders/" + order.getId()),
            eq(entity.getId())
        );

        var rejectedEntity = ReturnRequest.create(
            order.getId(),
            shopId,
            buyerId,
            "WRONG_ITEM",
            "Sai size"
        );
        when(returns.findById(rejectedEntity.getId())).thenReturn(Optional.of(rejectedEntity));
        var rejected = returnService.reject(
            sellerId,
            rejectedEntity.getId(),
            new RejectReturnRequest("Khong du chung cu")
        );
        assertThat(rejected.status()).isEqualTo("REJECTED");
        verify(notifications).create(
            eq(Set.of(buyerId)),
            eq(NotificationCategory.ORDER),
            eq("RETURN_REJECTED"),
            anyString(),
            anyString(),
            eq("/account/orders/" + order.getId()),
            eq(rejectedEntity.getId())
        );
    }
}
