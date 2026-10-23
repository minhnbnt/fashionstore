package com.minhnbnt.fashionstore.features.holiday;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.holiday.HolidayResponse;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.infrastructure.CalendarificClient;
import com.minhnbnt.fashionstore.infrastructure.CalendarificClient.FetchedHoliday;
import com.minhnbnt.fashionstore.model.HolidayKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HolidayServiceTest {

    @Autowired
    private HolidayService holidays;

    @Autowired
    private UserService users;

    @MockitoBean
    private CalendarificClient calendarific;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private static FetchedHoliday holiday(String day, String name) {
        return new FetchedHoliday(LocalDate.parse(day), name, HolidayKind.HOLIDAY);
    }

    private HolidayResponse dayOf(int year, String day) {
        return holidays.listYear(year).holidays().stream()
            .filter(holiday -> holiday.day().equals(LocalDate.parse(day)))
            .findFirst()
            .orElseThrow();
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void seededHolidaysCountAsDaysOff() {
        var calendar = holidays.calendarBetween(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertThat(calendar.isWorkingDay(LocalDate.of(2026, 11, 24))).isFalse();
        assertThat(calendar.isWorkingDay(LocalDate.of(2026, 11, 23))).isTrue();
        assertThat(dayOf(2026, "2026-11-24").source()).isEqualTo("SEED");
    }

    @Test
    void syncAddsUpdatesAndRemovesItsOwnRows() {
        when(calendarific.fetchVietnam(2031)).thenReturn(List.of(
            holiday("2031-01-01", "New Year"),
            holiday("2031-03-01", "Old holiday")
        ));
        var first = holidays.sync(2031);

        when(calendarific.fetchVietnam(2031)).thenReturn(List.of(
            holiday("2031-01-01", "International New Year's Day"),
            holiday("2031-04-30", "Reunification Day")
        ));
        var second = holidays.sync(2031);

        assertThat(first.added()).isEqualTo(2);
        assertThat(second.added()).isEqualTo(1);
        assertThat(second.updated()).isEqualTo(1);
        assertThat(second.removed()).isEqualTo(1);

        var year = holidays.listYear(2031);
        assertThat(year.holidays())
            .extracting(holiday -> holiday.day() + ":" + holiday.name() + ":" + holiday.source())
            .containsExactly(
                "2031-01-01:International New Year's Day:CALENDARIFIC",
                "2031-04-30:Reunification Day:CALENDARIFIC"
            );
        var lastSync = Objects.requireNonNull(year.lastSync());
        assertThat(lastSync.status()).isEqualTo("SUCCESS");
        assertThat(lastSync.holidayCount()).isEqualTo(2);
    }

    @Test
    void manualEditWinsOverSync() {
        var adminId = mirroredUser();
        when(calendarific.fetchVietnam(2032)).thenReturn(List.of(holiday("2032-01-01", "New Year")));
        holidays.sync(2032);
        holidays.save(adminId, LocalDate.of(2032, 1, 1), "Tết Dương lịch", HolidayKind.HOLIDAY);

        var result = holidays.sync(2032);

        assertThat(result.keptManual()).isEqualTo(1);
        var day = dayOf(2032, "2032-01-01");
        assertThat(day.name()).isEqualTo("Tết Dương lịch");
        assertThat(day.source()).isEqualTo("MANUAL");
        assertThat(day.updatedByEmail()).isEqualTo("u-" + adminId + "@t.local");
    }

    @Test
    void disabledDayStaysDisabledAfterSync() {
        var adminId = mirroredUser();
        when(calendarific.fetchVietnam(2033)).thenReturn(List.of(holiday("2033-01-03", "Day off")));
        holidays.sync(2033);

        holidays.disable(adminId, LocalDate.of(2033, 1, 3));
        holidays.sync(2033);

        assertThat(dayOf(2033, "2033-01-03").enabled()).isFalse();
        var calendar = holidays.calendarBetween(LocalDate.of(2033, 1, 1), LocalDate.of(2033, 1, 31));
        assertThat(calendar.isWorkingDay(LocalDate.of(2033, 1, 3))).isTrue();
    }

    @Test
    void savingDisabledDayEnablesItAgain() {
        var adminId = mirroredUser();
        holidays.save(adminId, LocalDate.of(2034, 5, 2), "Nghỉ bù", HolidayKind.HOLIDAY);
        holidays.disable(adminId, LocalDate.of(2034, 5, 2));

        var saved = holidays.save(adminId, LocalDate.of(2034, 5, 2), "Nghỉ bù 1/5", HolidayKind.HOLIDAY);

        assertThat(saved.enabled()).isTrue();
        assertThat(saved.name()).isEqualTo("Nghỉ bù 1/5");
    }

    @Test
    void workingDayMakesSundayCount() {
        var adminId = mirroredUser();
        holidays.save(adminId, LocalDate.of(2036, 1, 6), "Đi làm bù", HolidayKind.WORKING_DAY);

        var calendar = holidays.calendarBetween(LocalDate.of(2036, 1, 1), LocalDate.of(2036, 1, 31));

        assertThat(calendar.isWorkingDay(LocalDate.of(2036, 1, 6))).isTrue();
        assertThat(calendar.isWorkingDay(LocalDate.of(2036, 1, 13))).isFalse();
    }

    @Test
    void failedSyncIsRecordedAndRethrown() {
        when(calendarific.fetchVietnam(2035))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Calendarific request failed"));

        assertThatThrownBy(() -> holidays.sync(2035))
            .satisfies(e -> assertStatus(e, HttpStatus.BAD_GATEWAY));

        var lastSync = Objects.requireNonNull(holidays.listYear(2035).lastSync());
        assertThat(lastSync.status()).isEqualTo("FAILED");
        assertThat(lastSync.message()).contains("Calendarific request failed");
    }

    @Test
    void listReportsWhetherSyncIsConfigured() {
        when(calendarific.isConfigured()).thenReturn(false);

        assertThat(holidays.listYear(2026).syncConfigured()).isFalse();
    }

    @Test
    void disablingUnknownDayIsNotFound() {
        var adminId = mirroredUser();

        assertThatThrownBy(() -> holidays.disable(adminId, LocalDate.of(2040, 1, 1)))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }
}
