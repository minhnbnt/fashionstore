package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShopVoucherTest {

    private final UUID shopId = UUID.randomUUID();

    private final UUID sellerId = UUID.randomUUID();

    @Test
    void createNormalizesCodeAndValidatesPercentageAndDates() {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var voucher = ShopVoucher.create(
            shopId,
            "  summer15 ",
            "Chào hè",
            "",
            VoucherDiscountType.PERCENTAGE,
            15L,
            50_000L,
            200_000L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            100,
            1,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            true,
            sellerId,
            now
        );

        assertThat(voucher.getCode()).isEqualTo("SUMMER15");
        assertThat(voucher.statusAt(now)).isEqualTo(VoucherStatus.ONGOING);
        assertThat(voucher.calculateDiscount(300_000L)).isEqualTo(45_000L);
        assertThat(voucher.calculateDiscount(500_000L)).isEqualTo(50_000L);
    }

    @Test
    void createValidatesCodeFormatDatesDiscountAndLimits() {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var startsAt = now.minusSeconds(60);
        var endsAt = now.plusSeconds(3600);

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "AB", "Short code", "", VoucherDiscountType.FIXED_AMOUNT,
            10_000L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "BAD-CODE!", "Special char", "", VoucherDiscountType.FIXED_AMOUNT,
            10_000L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "ABCDEFGHIJKLMNOPQRSTU", "21 chars", "", VoucherDiscountType.FIXED_AMOUNT,
            10_000L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "VALID10", "Ends before starts", "", VoucherDiscountType.FIXED_AMOUNT,
            10_000L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, endsAt, startsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "VALID10", "Zero discount", "", VoucherDiscountType.FIXED_AMOUNT,
            0L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "VALID10", "Percentage 0", "", VoucherDiscountType.PERCENTAGE,
            0L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "VALID10", "Percentage 100", "", VoucherDiscountType.PERCENTAGE,
            100L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "VALID10", "Zero usage limit", "", VoucherDiscountType.FIXED_AMOUNT,
            10_000L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            0, 1, startsAt, endsAt, true, sellerId, now
        ));

        assertBadRequest(() -> ShopVoucher.create(
            shopId, "VALID10", "Zero per buyer limit", "", VoucherDiscountType.FIXED_AMOUNT,
            10_000L, null, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
            10, 0, startsAt, endsAt, true, sellerId, now
        ));
    }

    @Test
    void fixedAmountDiscountNeverExceedsEligibleSubtotalAndNormalizesMaxDiscount() {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var voucher = ShopVoucher.create(
            shopId,
            "GIAM50K",
            "Giảm 50k",
            "",
            VoucherDiscountType.FIXED_AMOUNT,
            50_000L,
            999_000L,
            0L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            10,
            1,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            true,
            sellerId,
            now
        );

        assertThat(voucher.getMaxDiscountAmount()).isNull();
        assertThat(voucher.calculateDiscount(30_000L)).isEqualTo(30_000L);
        assertThat(voucher.calculateDiscount(80_000L)).isEqualTo(50_000L);
    }

    @Test
    void percentageDiscountWithoutCapNeverExceedsEligibleSubtotal() {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var voucher = ShopVoucher.create(
            shopId,
            "PCT25",
            "Giảm 25%",
            "",
            VoucherDiscountType.PERCENTAGE,
            25L,
            null,
            0L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            10,
            1,
            now.minusSeconds(60),
            now.plusSeconds(3600),
            true,
            sellerId,
            now
        );

        assertThat(voucher.getMaxDiscountAmount()).isNull();
        assertThat(voucher.calculateDiscount(400_000L)).isEqualTo(100_000L);
        assertThat(voucher.calculateDiscount(0L)).isEqualTo(0L);
    }

    @Test
    void statusAtEvaluatesPausedExpiredDepletedUpcomingAndOngoingInOrder() {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var startsAt = now.plusSeconds(300);
        var endsAt = now.plusSeconds(3600);
        var voucher = ShopVoucher.create(
            shopId,
            "STATUS1",
            "Status test",
            "",
            VoucherDiscountType.FIXED_AMOUNT,
            15_000L,
            null,
            50_000L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            1,
            1,
            startsAt,
            endsAt,
            true,
            sellerId,
            now
        );

        assertThat(voucher.statusAt(now)).isEqualTo(VoucherStatus.UPCOMING);
        assertThat(voucher.statusAt(startsAt.plusSeconds(10))).isEqualTo(VoucherStatus.ONGOING);
        assertThat(voucher.statusAt(endsAt)).isEqualTo(VoucherStatus.EXPIRED);

        voucher.incrementUsedCount(startsAt.plusSeconds(20));
        assertThat(voucher.statusAt(startsAt.plusSeconds(20))).isEqualTo(VoucherStatus.DEPLETED);
        assertThat(voucher.statusAt(endsAt)).isEqualTo(VoucherStatus.EXPIRED);

        voucher.setActive(false, startsAt.plusSeconds(30));
        assertThat(voucher.statusAt(startsAt.plusSeconds(30))).isEqualTo(VoucherStatus.PAUSED);
        assertThat(voucher.statusAt(endsAt.plusSeconds(30))).isEqualTo(VoucherStatus.PAUSED);
    }

    @Test
    void updateAllowsAllFieldsWhenUnusedAndLocksCoreFieldsWhenUsed() {
        var now = Instant.parse("2026-11-26T10:00:00Z");
        var startsAt = now.minusSeconds(60);
        var endsAt = now.plusSeconds(3600);
        var voucher = ShopVoucher.create(
            shopId,
            "SALE20K",
            "Sale",
            "",
            VoucherDiscountType.FIXED_AMOUNT,
            20_000L,
            null,
            100_000L,
            VoucherProductScope.ALL_PRODUCTS,
            VoucherVisibility.PUBLIC,
            10,
            1,
            startsAt,
            endsAt,
            true,
            sellerId,
            now
        );

        voucher.update(
            "  sale25pct ",
            "Sale 25%",
            "Mo ta moi",
            VoucherDiscountType.PERCENTAGE,
            25L,
            60_000L,
            150_000L,
            VoucherProductScope.SPECIFIC_PRODUCTS,
            VoucherVisibility.PRIVATE,
            20,
            2,
            startsAt,
            endsAt.plusSeconds(1800),
            true,
            now.plusSeconds(10)
        );

        assertThat(voucher.getCode()).isEqualTo("SALE25PCT");
        assertThat(voucher.getDiscountType()).isEqualTo(VoucherDiscountType.PERCENTAGE);
        assertThat(voucher.getDiscountValue()).isEqualTo(25L);
        assertThat(voucher.getMaxDiscountAmount()).isEqualTo(60_000L);
        assertThat(voucher.getMinOrderSubtotal()).isEqualTo(150_000L);
        assertThat(voucher.getProductScope()).isEqualTo(VoucherProductScope.SPECIFIC_PRODUCTS);

        voucher.incrementUsedCount(now.plusSeconds(20));

        // Changing code -> 409
        assertConflict(() -> voucher.update(
            "NEWCODE", "Sale 25%", "Mo ta", VoucherDiscountType.PERCENTAGE,
            25L, 60_000L, 150_000L, VoucherProductScope.SPECIFIC_PRODUCTS, VoucherVisibility.PRIVATE,
            20, 2, startsAt, endsAt, true, now
        ));

        // Changing discountType -> 409
        assertConflict(() -> voucher.update(
            "SALE25PCT", "Sale 25%", "Mo ta", VoucherDiscountType.FIXED_AMOUNT,
            25L, null, 150_000L, VoucherProductScope.SPECIFIC_PRODUCTS, VoucherVisibility.PRIVATE,
            20, 2, startsAt, endsAt, true, now
        ));

        // Changing discountValue -> 409
        assertConflict(() -> voucher.update(
            "SALE25PCT", "Sale 25%", "Mo ta", VoucherDiscountType.PERCENTAGE,
            30L, 60_000L, 150_000L, VoucherProductScope.SPECIFIC_PRODUCTS, VoucherVisibility.PRIVATE,
            20, 2, startsAt, endsAt, true, now
        ));

        // Changing maxDiscountAmount -> 409
        assertConflict(() -> voucher.update(
            "SALE25PCT", "Sale 25%", "Mo ta", VoucherDiscountType.PERCENTAGE,
            25L, 80_000L, 150_000L, VoucherProductScope.SPECIFIC_PRODUCTS, VoucherVisibility.PRIVATE,
            20, 2, startsAt, endsAt, true, now
        ));

        // Changing minOrderSubtotal -> 409
        assertConflict(() -> voucher.update(
            "SALE25PCT", "Sale 25%", "Mo ta", VoucherDiscountType.PERCENTAGE,
            25L, 60_000L, 200_000L, VoucherProductScope.SPECIFIC_PRODUCTS, VoucherVisibility.PRIVATE,
            20, 2, startsAt, endsAt, true, now
        ));

        // Changing productScope -> 409
        assertConflict(() -> voucher.update(
            "SALE25PCT", "Sale 25%", "Mo ta", VoucherDiscountType.PERCENTAGE,
            25L, 60_000L, 150_000L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PRIVATE,
            20, 2, startsAt, endsAt, true, now
        ));

        voucher.incrementUsedCount(now.plusSeconds(25));
        assertThat(voucher.getUsedCount()).isEqualTo(2);

        // Setting usageLimit < usedCount -> 409
        assertConflict(() -> voucher.update(
            "SALE25PCT", "Sale 25%", "Mo ta", VoucherDiscountType.PERCENTAGE,
            25L, 60_000L, 150_000L, VoucherProductScope.SPECIFIC_PRODUCTS, VoucherVisibility.PRIVATE,
            1, 2, startsAt, endsAt, true, now
        ));

        // Allowed fields when usedCount > 0
        var newEndsAt = endsAt.plusSeconds(7200);
        voucher.update(
            "SALE25PCT",
            "Sale 25% Updated",
            "Mo ta cap nhat",
            VoucherDiscountType.PERCENTAGE,
            25L,
            60_000L,
            150_000L,
            VoucherProductScope.SPECIFIC_PRODUCTS,
            VoucherVisibility.PUBLIC,
            2,
            5,
            startsAt,
            newEndsAt,
            false,
            now.plusSeconds(30)
        );

        assertThat(voucher.getName()).isEqualTo("Sale 25% Updated");
        assertThat(voucher.getDescription()).isEqualTo("Mo ta cap nhat");
        assertThat(voucher.getVisibility()).isEqualTo(VoucherVisibility.PUBLIC);
        assertThat(voucher.getUsageLimit()).isEqualTo(2);
        assertThat(voucher.getPerBuyerLimit()).isEqualTo(5);
        assertThat(voucher.getEndsAt()).isEqualTo(newEndsAt);
        assertThat(voucher.isActive()).isFalse();
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private static void assertConflict(Runnable action) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }
}
