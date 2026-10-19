package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static Order pendingOrder() {
        return Order.create(
            "ORD-TEST01",
            UUID.randomUUID(),
            UUID.randomUUID(),
            100000L,
            0L,
            "An",
            "0900000000",
            "Ha Noi",
            null
        );
    }

    @Test
    void createStartsPendingUnpaid() {
        var order = pendingOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(order.getTotal()).isEqualTo(100000L);
    }

    @Test
    void confirmFlipsPendingToConfirmed() {
        var order = pendingOrder();

        order.confirm();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void shipRequiresConfirmed() {
        var order = pendingOrder();

        assertThatThrownBy(order::ship).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deliverCollectsCod() {
        var order = pendingOrder();
        order.confirm();
        order.ship();

        order.deliver();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COLLECTED);
    }

    @Test
    void cancelOnlyFromPending() {
        var order = pendingOrder();
        order.confirm();

        assertThatThrownBy(order::cancel).isInstanceOf(ResponseStatusException.class);
    }
}
