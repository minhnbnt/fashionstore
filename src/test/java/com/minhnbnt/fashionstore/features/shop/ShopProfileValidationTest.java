package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.dto.shop.UpdateShopProfileRequest;
import com.minhnbnt.fashionstore.model.Shop;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopProfileValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 4, 10})
    void requestRejectsProcessingDaysOutsideOneToThree(int invalidDays) {
        var request = new UpdateShopProfileRequest(
            null, null, null, invalidDays, null, null, null, null, null
        );

        var violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("processingDays"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void requestAcceptsProcessingDaysBetweenOneAndThree(int validDays) {
        var request = new UpdateShopProfileRequest(
            null, null, null, validDays, null, null, null, null, null
        );

        var violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 4, 10})
    void entityRejectsProcessingDaysOutsideOneToThree(int invalidDays) {
        var shop = Shop.create(
            "Test Shop", "test-shop", "Description", null, null,
            "0901234567", "Hanoi", "INDIVIDUAL", "Nguyen Van Test", "123456789",
            "2020-01-01", "Hanoi", null, null, null, null, null,
            "Techcombank", "1903000", "TEST OWNER"
        );

        assertThatThrownBy(() -> shop.updateProfile(
            null, null, null, invalidDays, null, null, null, null, null
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                var rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(rse.getReason()).isEqualTo("Processing days must be between 1 and 3 days");
            });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void entityAcceptsProcessingDaysBetweenOneAndThree(int validDays) {
        var shop = Shop.create(
            "Test Shop", "test-shop", "Description", null, null,
            "0901234567", "Hanoi", "INDIVIDUAL", "Nguyen Van Test", "123456789",
            "2020-01-01", "Hanoi", null, null, null, null, null,
            "Techcombank", "1903000", "TEST OWNER"
        );

        shop.updateProfile(null, null, null, validDays, null, null, null, null, null);
        assertThat(shop.getProcessingDays()).isEqualTo(validDays);
    }
}
