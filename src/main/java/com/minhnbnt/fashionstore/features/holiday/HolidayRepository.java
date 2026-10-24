package com.minhnbnt.fashionstore.features.holiday;

import com.minhnbnt.fashionstore.dto.holiday.HolidayResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidaySyncResponse;
import com.minhnbnt.fashionstore.model.HolidayKind;
import com.minhnbnt.fashionstore.model.HolidaySource;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class HolidayRepository {

    /** Không gian khóa riêng cho đồng bộ lịch lễ, tránh trùng advisory lock khác. */
    private static final int SYNC_LOCK_NAMESPACE = 4207;

    private static final String SELECT = """
        SELECT h.day, h.name, h.kind, h.source, h.enabled, u.email AS updated_by_email, h.updated_at
        FROM public_holiday h
        LEFT JOIN user_profile u ON u.id = h.updated_by
        """;

    private final JdbcClient jdbc;

    public record CalendarRow(LocalDate day, HolidayKind kind) {
    }

    public List<HolidayResponse> findByYear(int year) {
        return jdbc.sql(SELECT + " WHERE h.day >= :from AND h.day < :to ORDER BY h.day")
            .param("from", LocalDate.of(year, 1, 1))
            .param("to", LocalDate.of(year + 1, 1, 1))
            .query(HolidayRepository::toResponse)
            .list();
    }

    public Optional<HolidayResponse> findByDay(LocalDate day) {
        return jdbc.sql(SELECT + " WHERE h.day = :day")
            .param("day", day)
            .query(HolidayRepository::toResponse)
            .optional();
    }

    public List<CalendarRow> enabledBetween(LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT day, kind FROM public_holiday WHERE enabled AND day BETWEEN :from AND :to")
            .param("from", from)
            .param("to", to)
            .query((rs, rowNum) -> new CalendarRow(
                rs.getObject("day", LocalDate.class),
                HolidayKind.valueOf(rs.getString("kind"))
            ))
            .list();
    }

    /**
     * Advisory lock theo năm tới hết transaction: 2 lần đồng bộ cùng năm chạy lần lượt, không chèn trùng ngày mới.
     */
    public void lockYear(int year) {
        jdbc.sql("SELECT pg_advisory_xact_lock(:namespace, :year)")
            .param("namespace", SYNC_LOCK_NAMESPACE)
            .param("year", year)
            .query((rs, rowNum) -> 1)
            .single();
    }

    public Map<LocalDate, HolidaySource> sourcesOfYear(int year) {
        var rows = jdbc.sql("SELECT day, source FROM public_holiday WHERE day >= :from AND day < :to")
            .param("from", LocalDate.of(year, 1, 1))
            .param("to", LocalDate.of(year + 1, 1, 1))
            .query((rs, rowNum) -> Map.entry(
                rs.getObject("day", LocalDate.class),
                HolidaySource.valueOf(rs.getString("source"))
            ))
            .list();

        var result = new HashMap<LocalDate, HolidaySource>();
        for (var row : rows) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }

    public void insertSynced(LocalDate day, String name, HolidayKind kind) {
        jdbc.sql("""
                INSERT INTO public_holiday (day, name, kind, source)
                VALUES (:day, :name, :kind, 'CALENDARIFIC')
                """)
            .param("day", day)
            .param("name", name)
            .param("kind", kind.name())
            .update();
    }

    /**
     * Ghi đè dòng seed/đồng bộ bằng dữ liệu mới, giữ nguyên {@code enabled}.
     */
    public void updateSynced(LocalDate day, String name, HolidayKind kind) {
        jdbc.sql("""
                UPDATE public_holiday
                SET name = :name, kind = :kind, source = 'CALENDARIFIC', updated_by = NULL, updated_at = now()
                WHERE day = :day
                """)
            .param("day", day)
            .param("name", name)
            .param("kind", kind.name())
            .update();
    }

    public void delete(LocalDate day) {
        jdbc.sql("DELETE FROM public_holiday WHERE day = :day")
            .param("day", day)
            .update();
    }

    public void upsertManual(LocalDate day, String name, HolidayKind kind, UUID adminId) {
        jdbc.sql("""
                INSERT INTO public_holiday (day, name, kind, source, enabled, updated_by)
                VALUES (:day, :name, :kind, 'MANUAL', TRUE, :admin)
                ON CONFLICT (day) DO UPDATE
                SET name = EXCLUDED.name, kind = EXCLUDED.kind, source = 'MANUAL', enabled = TRUE,
                    updated_by = EXCLUDED.updated_by, updated_at = now()
                """)
            .param("day", day)
            .param("name", name)
            .param("kind", kind.name())
            .param("admin", adminId)
            .update();
    }

    /**
     * Bỏ ngày: giữ dòng với {@code enabled = false}, nguồn MANUAL để đồng bộ không thêm lại. Trả số dòng đổi.
     */
    public int disable(LocalDate day, UUID adminId) {
        return jdbc.sql("""
                UPDATE public_holiday
                SET enabled = FALSE, source = 'MANUAL', updated_by = :admin, updated_at = now()
                WHERE day = :day
                """)
            .param("day", day)
            .param("admin", adminId)
            .update();
    }

    public void recordSync(int year, String status, String message, int holidayCount) {
        jdbc.sql("""
                INSERT INTO holiday_sync (year, status, message, holiday_count, synced_at)
                VALUES (:year, :status, :message, :count, now())
                ON CONFLICT (year) DO UPDATE
                SET status = EXCLUDED.status, message = EXCLUDED.message,
                    holiday_count = EXCLUDED.holiday_count, synced_at = EXCLUDED.synced_at
                """)
            .param("year", year)
            .param("status", status)
            .param("message", message)
            .param("count", holidayCount)
            .update();
    }

    public Optional<HolidaySyncResponse> lastSync(int year) {
        return jdbc.sql("SELECT status, message, holiday_count, synced_at FROM holiday_sync WHERE year = :year")
            .param("year", year)
            .query((rs, rowNum) -> new HolidaySyncResponse(
                rs.getString("status"),
                rs.getString("message"),
                rs.getInt("holiday_count"),
                rs.getObject("synced_at", OffsetDateTime.class).toInstant()
            ))
            .optional();
    }

    private static HolidayResponse toResponse(ResultSet rs, int rowNum) throws SQLException {
        return new HolidayResponse(
            rs.getObject("day", LocalDate.class),
            rs.getString("name"),
            rs.getString("kind"),
            rs.getString("source"),
            rs.getBoolean("enabled"),
            rs.getString("updated_by_email"),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
