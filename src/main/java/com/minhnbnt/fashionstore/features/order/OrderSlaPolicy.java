package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.config.OrderSlaProperties;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

/**
 * Mốc SLA xử lý đơn tính theo ngày làm việc như Shopee/Lazada: không tính Chủ nhật và ngày lễ (giờ Việt Nam).
 * Một ngày làm việc = 24 giờ trôi qua trong ngày làm việc, nên đơn đặt tối thứ Bảy chưa trễ vào sáng thứ Hai.
 */
@Component
public class OrderSlaPolicy {

    /** Shop phải xác nhận đơn trong 1 ngày làm việc. */
    static final int CONFIRM_DAYS = 1;

    /** Shopee: 2 ngày làm việc kể từ lúc đặt phải bàn giao cho vận chuyển. */
    static final int HANDOVER_DAYS = 2;

    /** Hàng phải tới nơi trong 7 ngày làm việc kể từ lúc gửi. */
    static final int DELIVERY_DAYS = 7;

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final Set<LocalDate> holidays;

    public OrderSlaPolicy(OrderSlaProperties properties) {
        this.holidays = Set.copyOf(properties.holidays());
    }

    /**
     * Đơn có mốc (lúc đặt / lúc gửi) trước hoặc bằng cutoff là đã trễ.
     */
    public record Cutoffs(Instant confirm, Instant handover, Instant delivery) {
    }

    public Cutoffs cutoffsAt(Instant now) {
        return new Cutoffs(
            workingDaysBefore(now, CONFIRM_DAYS),
            workingDaysBefore(now, HANDOVER_DAYS),
            workingDaysBefore(now, DELIVERY_DAYS)
        );
    }

    public boolean isWorkingDay(LocalDate day) {
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(day);
    }

    /**
     * Lùi từ {@code now} đúng {@code days} × 24 giờ làm việc, bỏ qua trọn các ngày nghỉ.
     */
    Instant workingDaysBefore(Instant now, int days) {
        var remaining = Duration.ofDays(days);
        var day = now.atZone(ZONE).toLocalDate();
        var end = now.atZone(ZONE);

        while (true) {
            var start = day.atStartOfDay(ZONE);

            if (isWorkingDay(day)) {
                var available = Duration.between(start, end);
                if (available.compareTo(remaining) >= 0) {
                    return end.minus(remaining).toInstant();
                }
                remaining = remaining.minus(available);
            }

            day = day.minusDays(1);
            end = start;
        }
    }
}
