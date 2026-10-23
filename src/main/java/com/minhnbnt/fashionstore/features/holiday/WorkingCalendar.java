package com.minhnbnt.fashionstore.features.holiday;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Lịch làm việc của 1 khoảng ngày: ngày đi làm bù → làm việc; Chủ nhật → nghỉ; ngày lễ → nghỉ; còn lại làm việc.
 * Ngày nằm ngoài khoảng đã nạp thì chỉ áp luật Chủ nhật.
 */
public record WorkingCalendar(Set<LocalDate> holidays, Set<LocalDate> workingDays) {

    public boolean isWorkingDay(LocalDate day) {
        if (workingDays.contains(day)) {
            return true;
        }
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(day);
    }
}
