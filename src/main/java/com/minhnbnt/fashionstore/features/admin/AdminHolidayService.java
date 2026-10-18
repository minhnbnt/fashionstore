package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.dto.holiday.HolidayResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidaySyncResultResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidayYearResponse;
import com.minhnbnt.fashionstore.features.holiday.HolidayService;
import com.minhnbnt.fashionstore.model.HolidayKind;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Quản lý lịch nghỉ lễ dùng cho SLA đơn hàng: xem theo năm, sửa tay, đồng bộ Calendarific. Luật nằm ở holiday.
 */
@Service
@RequiredArgsConstructor
public class AdminHolidayService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final HolidayService holidays;

    private final Clock clock;

    public int currentYear() {
        return clock.instant().atZone(ZONE).getYear();
    }

    public HolidayYearResponse listYear(int year) {
        return holidays.listYear(year);
    }

    public HolidayResponse save(
        @NonNull UUID adminId,
        @NonNull LocalDate day,
        @NonNull String name,
        @NonNull HolidayKind kind
    ) {
        return holidays.save(adminId, day, name, kind);
    }

    public HolidayResponse disable(@NonNull UUID adminId, @NonNull LocalDate day) {
        return holidays.disable(adminId, day);
    }

    public HolidaySyncResultResponse sync(int year) {
        return holidays.sync(year);
    }
}
