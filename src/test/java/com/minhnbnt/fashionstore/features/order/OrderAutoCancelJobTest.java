package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.model.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderAutoCancelJobTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderAutoCancelJob job;

    @Test
    void continuesWithRemainingOrdersWhenOneCancelFails() {
        var first = Order.create("ORD-1", UUID.randomUUID(), UUID.randomUUID(), 100000, 0, "A", "090", "Hanoi");
        var second = Order.create("ORD-2", UUID.randomUUID(), UUID.randomUUID(), 100000, 0, "A", "090", "Hanoi");
        when(orderService.findExpiredPendingOrders()).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("cancel failed"))
            .doNothing()
            .when(orderService)
            .systemCancelExpiredOrder(any(UUID.class));

        job.cancelExpiredPendingOrders();

        verify(orderService).systemCancelExpiredOrder(first.getId());
        verify(orderService).systemCancelExpiredOrder(second.getId());
    }
}
