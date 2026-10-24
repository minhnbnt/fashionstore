package com.minhnbnt.fashionstore.model;

import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopTest {

    private static Shop pending() {
        return ShopTestFixtures.shop("Shop", "shop", "");
    }

    private static Shop approved() {
        var shop = pending();
        shop.approve(UUID.randomUUID());
        return shop;
    }

    @Test
    void newShopIsPendingAndSubmittedNow() {
        var shop = pending();

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING);
        assertThat(shop.getSubmittedAt()).isNotNull();
        assertThat(shop.getStatusReason()).isNull();
    }

    @Test
    void rejectStoresReason() {
        var shop = pending();

        shop.reject("Thiếu thông tin liên hệ");

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.REJECTED);
        assertThat(shop.getStatusReason()).isEqualTo("Thiếu thông tin liên hệ");
    }

    @Test
    void resubmitMovesRejectedBackToPendingAndClearsReason() {
        var shop = pending();
        shop.reject("Logo không hợp lệ");
        var firstSubmit = shop.getSubmittedAt();

        shop.resubmit();

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.PENDING);
        assertThat(shop.getStatusReason()).isNull();
        assertThat(shop.getSubmittedAt()).isAfterOrEqualTo(firstSubmit);
    }

    @Test
    void resubmitOnlyFromRejected() {
        var shop = approved();

        assertThatThrownBy(shop::resubmit).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void suspendApprovedShopStoresReason() {
        var shop = approved();

        shop.suspend("Bán hàng giả");

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.SUSPENDED);
        assertThat(shop.getStatusReason()).isEqualTo("Bán hàng giả");
    }

    @Test
    void suspendOnlyFromApproved() {
        var shop = pending();

        assertThatThrownBy(() -> shop.suspend("Vi phạm")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reinstateSuspendedShopClearsReason() {
        var shop = approved();
        shop.suspend("Bán hàng giả");

        shop.reinstate();

        assertThat(shop.getStatus()).isEqualTo(ShopStatus.APPROVED);
        assertThat(shop.getStatusReason()).isNull();
    }

    @Test
    void reinstateOnlyFromSuspended() {
        var shop = approved();

        assertThatThrownBy(shop::reinstate).isInstanceOf(ResponseStatusException.class);
    }
}
