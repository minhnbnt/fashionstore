package com.minhnbnt.fashionstore.features.holiday;

import com.minhnbnt.fashionstore.dto.holiday.HolidayResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidaySyncResultResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidayYearResponse;
import com.minhnbnt.fashionstore.infrastructure.CalendarificClient;
import com.minhnbnt.fashionstore.infrastructure.CalendarificClient.FetchedHoliday;
import com.minhnbnt.fashionstore.model.HolidayKind;
import com.minhnbnt.fashionstore.model.HolidaySource;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Lịch nghỉ lễ cho SLA đơn hàng: đồng bộ từ Calendarific, admin sửa tay luôn thắng dữ liệu đồng bộ.
 */
@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidays;

    private final CalendarificClient calendarific;

    private final TransactionTemplate transactions;

    @Transactional(readOnly = true)
    public WorkingCalendar calendarBetween(@NonNull LocalDate from, @NonNull LocalDate to) {
        var days = new HashSet<LocalDate>();
        var workingDays = new HashSet<LocalDate>();

        for (var row : holidays.enabledBetween(from, to)) {
            if (row.kind() == HolidayKind.WORKING_DAY) {
                workingDays.add(row.day());
                continue;
            }
            days.add(row.day());
        }

        return new WorkingCalendar(days, workingDays);
    }

    @Transactional(readOnly = true)
    public HolidayYearResponse listYear(int year) {
        return new HolidayYearResponse(
            year,
            calendarific.isConfigured(),
            holidays.lastSync(year).orElse(null),
            holidays.findByYear(year)
        );
    }

    /**
     * Thêm/sửa tay: nguồn MANUAL, bật lại nếu trước đó đã bỏ.
     */
    @Transactional
    public HolidayResponse save(
        @NonNull UUID adminId,
        @NonNull LocalDate day,
        @NonNull String name,
        @NonNull HolidayKind kind
    ) {
        // Cùng khóa với đồng bộ: đồng bộ đang chạy không ghi đè được bản sửa tay này.
        holidays.lockYear(day.getYear());
        holidays.upsertManual(day, name.trim(), kind, adminId);
        return holidays.findByDay(day).orElseThrow();
    }

    @Transactional
    public HolidayResponse disable(@NonNull UUID adminId, @NonNull LocalDate day) {
        holidays.lockYear(day.getYear());
        var changed = holidays.disable(day, adminId);
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Holiday not found");
        }
        return holidays.findByDay(day).orElseThrow();
    }

    /**
     * Gọi Calendarific ngoài transaction (không giữ khóa DB khi chờ mạng), lỗi thì ghi lại lần đồng bộ thất bại
     * rồi ném tiếp; thành công thì áp kết quả trong 1 transaction.
     */
    public HolidaySyncResultResponse sync(int year) {
        List<FetchedHoliday> fetched;
        try {
            fetched = calendarific.fetchVietnam(year);
        } catch (ResponseStatusException e) {
            holidays.recordSync(year, "FAILED", reasonOf(e), 0);
            throw e;
        }

        var result = transactions.execute(status -> apply(year, fetched));
        return Objects.requireNonNull(result);
    }

    private HolidaySyncResultResponse apply(int year, List<FetchedHoliday> fetched) {
        holidays.lockYear(year);
        var existing = holidays.sourcesOfYear(year);
        var fetchedDays = new HashSet<LocalDate>();
        var added = 0;
        var updated = 0;
        var removed = 0;
        var keptManual = 0;

        for (var holiday : fetched) {
            fetchedDays.add(holiday.day());
            var source = existing.get(holiday.day());

            if (source == null) {
                holidays.insertSynced(holiday.day(), holiday.name(), holiday.kind());
                added++;
                continue;
            }
            if (source == HolidaySource.MANUAL) {
                keptManual++;
                continue;
            }
            holidays.updateSynced(holiday.day(), holiday.name(), holiday.kind());
            updated++;
        }

        // Chỉ xóa dòng do chính đồng bộ tạo ra; seed thiếu trong kết quả thì giữ (nguồn chính thức có thể đủ hơn).
        for (var entry : existing.entrySet()) {
            if (entry.getValue() == HolidaySource.CALENDARIFIC && !fetchedDays.contains(entry.getKey())) {
                holidays.delete(entry.getKey());
                removed++;
            }
        }

        holidays.recordSync(year, "SUCCESS", "", fetched.size());
        return new HolidaySyncResultResponse(year, added, updated, removed, keptManual);
    }

    private static String reasonOf(ResponseStatusException e) {
        var reason = e.getReason();
        if (reason == null) {
            return e.getStatusCode().toString();
        }
        return reason;
    }
}
