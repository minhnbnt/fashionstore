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
            "Ha Noi"
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

    @Test
    void shipSetsCarrierAndTrackingCodeOrDefaults() {
        var customOrder = pendingOrder();
        customOrder.confirm();
        customOrder.ship("Viettel Post", "VTP999");

        assertThat(customOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(customOrder.getShippingCarrier()).isEqualTo("Viettel Post");
        assertThat(customOrder.getTrackingCode()).isEqualTo("VTP999");
        assertThat(customOrder.getShippedAt()).isNotNull();

        var defaultOrder = pendingOrder();
        defaultOrder.confirm();
        defaultOrder.ship();

        assertThat(defaultOrder.getShippingCarrier()).isEqualTo("GHN");
        assertThat(defaultOrder.getTrackingCode()).isEqualTo("VN-" + defaultOrder.getCode());
    }

    @Test
    void buyerCancelWithReasonStoresReasonAndNote() {
        var order = pendingOrder();
        order.cancel(BuyerCancelReason.OTHER.name(), "Khong con nhu cau nua");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo(OrderCanceller.BUYER);
        assertThat(order.getCancelReason()).isEqualTo("OTHER");
        assertThat(order.getCancelNote()).isEqualTo("Khong con nhu cau nua");
    }

    @Test
    void buyerCancelReasonAndTrackingStatusValidateInput() {
        assertThat(BuyerCancelReason.parse("change_mind")).isEqualTo(BuyerCancelReason.CHANGE_MIND);
        assertThat(BuyerCancelReason.parse("FOUND_CHEAPER").label()).isEqualTo("Tìm được giá rẻ hơn");
        assertThatThrownBy(() -> BuyerCancelReason.parse("INVALID"))
            .isInstanceOf(ResponseStatusException.class);

        assertThat(OrderTrackingStatus.parse("in_transit")).isEqualTo(OrderTrackingStatus.IN_TRANSIT);
        assertThatThrownBy(() -> OrderTrackingStatus.parse("UNKNOWN"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reviewCanBeEditedOnlyOnce() {
        var review = Review.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "An",
            "M",
            5,
            "San pham dep va dung size",
            new String[0],
            false
        );
        assertThat(review.getEditedAt()).isNull();

        review.update(4, "Cap nhat lan 1 day du ky tu", new String[0], false);
        assertThat(review.getStars()).isEqualTo(4);
        assertThat(review.getComment()).isEqualTo("Cap nhat lan 1 day du ky tu");
        assertThat(review.getEditedAt()).isNotNull();

        assertThatThrownBy(() -> review.update(3, "Sua lan 2 day du ky tu", new String[0], false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var statusEx = (ResponseStatusException) ex;
                assertThat(statusEx.getStatusCode().value()).isEqualTo(409);
            });
    }
}
