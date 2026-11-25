package com.minhnbnt.fashionstore.features.shipping;

import com.minhnbnt.fashionstore.features.settings.SystemSettings;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ShippingFeeServiceTest {

    private static SystemSettings rules(long freeThreshold, int baseItems, long baseFee, long extraItemFee) {
        return new SystemSettings(
            "nét",
            "1900 0000",
            "support@fashionstore.local",
            freeThreshold,
            baseItems,
            baseFee,
            extraItemFee,
            15,
            7,
            0,
            10000000
        );
    }

    @Test
    void belowThresholdChargesBaseFeeAndReportsAmountToFreeShip() {
        var quote = ShippingFeeService.calculate(rules(500000, 2, 30000, 5000), 200000, 1);

        assertThat(quote.fee()).isEqualTo(30000);
        assertThat(quote.originalFee()).isEqualTo(30000);
        assertThat(quote.freeShipApplied()).isFalse();
        assertThat(quote.amountToFreeShip()).isEqualTo(300000);
    }

    @Test
    void itemsBeyondBaseItemsAddExtraFeeEach() {
        var quote = ShippingFeeService.calculate(rules(500000, 2, 30000, 5000), 200000, 5);

        assertThat(quote.fee()).isEqualTo(45000);
    }

    @Test
    void reachingThresholdWaivesFeeButKeepsOriginal() {
        var quote = ShippingFeeService.calculate(rules(500000, 2, 30000, 5000), 500000, 3);

        assertThat(quote.fee()).isZero();
        assertThat(quote.originalFee()).isEqualTo(35000);
        assertThat(quote.freeShipApplied()).isTrue();
        assertThat(quote.amountToFreeShip()).isZero();
    }

    @Test
    void zeroThresholdDisablesFreeShip() {
        var quote = ShippingFeeService.calculate(rules(0, 2, 30000, 0), 9000000, 1);

        assertThat(quote.fee()).isEqualTo(30000);
        assertThat(quote.freeShipApplied()).isFalse();
        assertThat(quote.amountToFreeShip()).isZero();
    }
}
