package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.features.holiday.HolidayService;
import com.minhnbnt.fashionstore.features.holiday.WorkingCalendar;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Mốc SLA xử lý đơn tính theo ngày làm việc như Shopee/Lazada: không tính Chủ nhật và ngày lễ (giờ Việt Nam).
 * Một ngày làm việc = 24 giờ trôi qua trong ngày làm việc, nên đơn đặt tối thứ Bảy chưa trễ vào sáng thứ Hai.
 * Lịch lễ lấy từ DB ({@link HolidayService}), không gọi API bên ngoài.
 */
@Component
@RequiredArgsConstructor
public class OrderSlaPolicy {

    /** Shop phải xác nhận đơn trong 1 ngày làm việc. */
    static final int CONFIRM_DAYS = 1;

    /** Shopee: 2 ngày làm việc kể từ lúc đặt phải bàn giao cho vận chuyển. */
    static final int HANDOVER_DAYS = 2;

    /** Hàng phải tới nơi trong 7 ngày làm việc kể từ lúc gửi. */
    static final int DELIVERY_DAYS = 7;

    /** 7 ngày làm việc + Chủ nhật + kỳ nghỉ Tết dài nhất vẫn nằm gọn trong 90 ngày. */
    private static final int LOOKBACK_DAYS = 90;

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final HolidayService holidays;

    /**
     * Đơn có mốc (lúc đặt / lúc gửi) trước hoặc bằng cutoff là đã trễ.
     */
    public record Cutoffs(Instant confirm, Instant handover, Instant delivery) {
    }

    public Cutoffs cutoffsAt(Instant now) {
        var today = now.atZone(ZONE).toLocalDate();
        var calendar = holidays.calendarBetween(today.minusDays(LOOKBACK_DAYS), today);
        return cutoffsAt(calendar, now);
    }

    static Cutoffs cutoffsAt(WorkingCalendar calendar, Instant now) {
        return new Cutoffs(
            workingDaysBefore(calendar, now, CONFIRM_DAYS),
            workingDaysBefore(calendar, now, HANDOVER_DAYS),
            workingDaysBefore(calendar, now, DELIVERY_DAYS)
        );
    }

    /**
     * Lùi từ {@code now} đúng {@code days} × 24 giờ làm việc, bỏ qua trọn các ngày nghỉ.
     */
    static Instant workingDaysBefore(WorkingCalendar calendar, Instant now, int days) {
        var remaining = Duration.ofDays(days);
        var day = now.atZone(ZONE).toLocalDate();
        var end = now.atZone(ZONE);

        while (true) {
            var start = day.atStartOfDay(ZONE);

            if (calendar.isWorkingDay(day)) {
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
