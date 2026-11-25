package com.minhnbnt.fashionstore.features.shipping;

import com.minhnbnt.fashionstore.features.settings.SystemSettings;
import com.minhnbnt.fashionstore.features.settings.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Server tự tính phí ship cho từng đơn (mỗi shop 1 kiện), công thức phẳng theo cấu hình sàn:
 * phí = phí cơ bản + max(0, số món - số món cơ bản) × phí mỗi món thêm;
 * tiền hàng của đơn đạt ngưỡng (ngưỡng > 0) thì miễn phí.
 */
@Service
@RequiredArgsConstructor
public class ShippingFeeService {

    private final SystemSettingsService settings;

    @Transactional(readOnly = true)
    public ShippingQuote quote(long subtotal, int itemCount) {
        return calculate(settings.current(), subtotal, itemCount);
    }

    public static ShippingQuote calculate(SystemSettings rules, long subtotal, int itemCount) {
        var extraItems = Math.max(0, itemCount - rules.baseItems());
        var originalFee = rules.shippingBaseFee() + extraItems * rules.shippingExtraItemFee();

        var threshold = rules.freeShippingThreshold();
        if (threshold <= 0) {
            return new ShippingQuote(originalFee, originalFee, false, 0);
        }

        if (subtotal >= threshold) {
            return new ShippingQuote(0, originalFee, true, 0);
        }

        return new ShippingQuote(originalFee, originalFee, false, threshold - subtotal);
    }
}
