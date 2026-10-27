package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderModelTest {

    @Test
    void cancelBySystemSetsSystemCancellerAndReason() {
        var order = Order.create("ORD-TEST", UUID.randomUUID(), UUID.randomUUID(), 100000, 20000, "A", "090", "Hanoi");
        order.cancelBySystem("Quá thời hạn 24 giờ xác nhận", null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo(OrderCanceller.SYSTEM);
        assertThat(order.getCancelReason()).isEqualTo("Quá thời hạn 24 giờ xác nhận");
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelBySystemFailsWhenNotPending() {
        var order = Order.create("ORD-TEST", UUID.randomUUID(), UUID.randomUUID(), 100000, 20000, "A", "090", "Hanoi");
        order.confirm();

        assertThatThrownBy(() -> order.cancelBySystem("Expired", null))
            .isInstanceOf(ResponseStatusException.class);
    }
}
