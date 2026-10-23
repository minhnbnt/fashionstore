package com.minhnbnt.fashionstore.features.holiday;

import com.minhnbnt.fashionstore.infrastructure.CalendarificClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Đồng bộ lịch lễ năm nay + năm sau, mặc định 03:00 ngày 1 hằng tháng (24 lượt/năm, gói free 500 lượt/tháng).
 * Chưa cấu hình key thì bỏ qua: SLA vẫn dùng dữ liệu seed + bản sửa tay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HolidaySyncJob {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final HolidayService holidays;

    private final CalendarificClient calendarific;

    private final Clock clock;

    @Scheduled(cron = "${calendarific.sync-cron:0 0 3 1 * *}", zone = "Asia/Ho_Chi_Minh")
    public void syncCurrentAndNextYear() {
        if (!calendarific.isConfigured()) {
            log.info("calendarific.api-key is not configured, skipped holiday sync");
            return;
        }

        var year = clock.instant().atZone(ZONE).getYear();
        syncQuietly(year);
        syncQuietly(year + 1);
    }

    private void syncQuietly(int year) {
        try {
            var result = holidays.sync(year);
            log.info(
                "Holiday sync {}: added {}, updated {}, removed {}, kept manual {}",
                year, result.added(), result.updated(), result.removed(), result.keptManual()
            );
        } catch (ResponseStatusException e) {
            log.warn("Holiday sync {} failed: {}", year, e.getReason());
        }
    }
}
