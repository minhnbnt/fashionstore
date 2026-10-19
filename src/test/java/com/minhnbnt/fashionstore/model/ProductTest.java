package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductTest {

    private static Product product() {
        return Product.create(
            UUID.randomUUID(),
            null,
            "Ao thun",
            "ao-thun",
            "Mo ta",
            UUID.randomUUID()
        );
    }

    @Test
    void createStartsActive() {
        assertThat(product().getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void hideFlipsActiveToHidden() {
        var product = product();

        product.hide();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void activateFlipsHiddenToActive() {
        var product = product();
        product.hide();

        product.activate();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    void hideTwiceFails() {
        var product = product();
        product.hide();

        assertThatThrownBy(product::hide)
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void activateWhenActiveFails() {
        assertThatThrownBy(product()::activate)
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void banKeepsViolationTypeAndReason() {
        var product = product();

        product.ban(ProductViolationType.COUNTERFEIT, "Hàng nhái thương hiệu");

        assertThat(product.getStatus()).isEqualTo(ProductStatus.BANNED);
        assertThat(product.getViolationType()).isEqualTo(ProductViolationType.COUNTERFEIT);
        assertThat(product.getStatusReason()).isEqualTo("Hàng nhái thương hiệu");
    }

    @Test
    void banWorksOnHiddenProduct() {
        var product = product();
        product.hide();

        product.ban(ProductViolationType.PROHIBITED, "Hàng cấm kinh doanh");

        assertThat(product.getStatus()).isEqualTo(ProductStatus.BANNED);
    }

    @Test
    void banTwiceFails() {
        var product = product();
        product.ban(ProductViolationType.SPAM, "Đăng trùng lặp");

        assertThatThrownBy(() -> product.ban(ProductViolationType.SPAM, "Đăng trùng lặp"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void banDeletedProductFails() {
        var product = product();
        product.delete();

        assertThatThrownBy(() -> product.ban(ProductViolationType.SPAM, "Đăng trùng lặp"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sellerCannotActivateBannedProduct() {
        var product = product();
        product.ban(ProductViolationType.MISLEADING, "Ảnh không đúng sản phẩm");

        assertThatThrownBy(product::activate)
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("banned");
    }

    @Test
    void sellerCannotHideBannedProduct() {
        var product = product();
        product.ban(ProductViolationType.MISLEADING, "Ảnh không đúng sản phẩm");

        assertThatThrownBy(product::hide)
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sellerCanDeleteBannedProduct() {
        var product = product();
        product.ban(ProductViolationType.MISLEADING, "Ảnh không đúng sản phẩm");

        product.delete();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.DELETED);
    }

    @Test
    void restoreMovesBannedToHiddenAndClearsReason() {
        var product = product();
        product.ban(ProductViolationType.WRONG_CATEGORY, "Sai danh mục");

        product.restore();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
        assertThat(product.getViolationType()).isNull();
        assertThat(product.getStatusReason()).isNull();
    }

    @Test
    void restoreNotBannedFails() {
        assertThatThrownBy(product()::restore)
            .isInstanceOf(ResponseStatusException.class);
    }
}
