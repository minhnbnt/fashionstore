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
}
