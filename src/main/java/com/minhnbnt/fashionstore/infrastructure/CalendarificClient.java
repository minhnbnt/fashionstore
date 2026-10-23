package com.minhnbnt.fashionstore.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.minhnbnt.fashionstore.model.HolidayKind;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Gọi Calendarific (GET /holidays) lấy ngày lễ Việt Nam của 1 năm, chỉ giữ ngày nghỉ quốc gia và ngày đi làm bù.
 * Gói free 500 lượt/tháng, lỗi cũng tính lượt: chỉ gọi khi đồng bộ, không gọi lúc tính SLA.
 */
@Component
@Slf4j
public class CalendarificClient {

    private final RestClient rest;

    private final String apiKey;

    @Autowired
    public CalendarificClient(
        @Value("${calendarific.url:https://calendarific.com/api/v2}") String url,
        @Value("${calendarific.api-key:}") String apiKey
    ) {
        this(RestClient.builder(), url, apiKey);
    }

    CalendarificClient(RestClient.Builder builder, String url, String apiKey) {
        this.rest = builder.baseUrl(url).build();
        this.apiKey = apiKey.trim();
    }

    public record FetchedHoliday(LocalDate day, String name, HolidayKind kind) {
    }

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    /**
     * Ngày trùng nhau thì HOLIDAY thắng WORKING_DAY. Kết quả sắp theo ngày.
     */
    public List<FetchedHoliday> fetchVietnam(int year) {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Calendarific API key is not configured");
        }

        ApiResponse body;
        try {
            body = rest.get()
                .uri(uri -> uri.path("/holidays")
                    .queryParam("api_key", apiKey)
                    .queryParam("country", "VN")
                    .queryParam("year", year)
                    .build())
                .retrieve()
                .body(ApiResponse.class);
        } catch (RestClientException e) {
            // Không log/ghi e.getMessage(): lỗi mạng kèm nguyên URL, mà URL chứa api_key.
            var reason = failureReasonOf(e);
            log.error("Calendarific request failed for year {}: {}", year, reason);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Calendarific request failed: " + reason);
        }

        if (body == null || body.response() == null || body.response().holidays() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Calendarific returned no holidays");
        }

        var byDay = new TreeMap<LocalDate, FetchedHoliday>();
        for (var holiday : body.response().holidays()) {
            var fetched = toFetched(holiday);
            if (fetched == null) {
                continue;
            }

            var current = byDay.get(fetched.day());
            if (current == null || current.kind() == HolidayKind.WORKING_DAY) {
                byDay.put(fetched.day(), fetched);
            }
        }

        var result = new ArrayList<>(byDay.values());
        result.sort(Comparator.comparing(FetchedHoliday::day));
        return result;
    }

    /**
     * Calendarific trả lẫn ngày kỷ niệm không nghỉ (Observance, Season, lễ tôn giáo), bỏ hết trừ
     * ngày đi làm bù (tên/loại có "working day") và ngày nghỉ quốc gia.
     */
    private static @Nullable FetchedHoliday toFetched(ApiHoliday holiday) {
        if (holiday.name() == null || holiday.date() == null || holiday.date().iso() == null) {
            return null;
        }

        var types = holiday.type();
        if (types == null) {
            types = List.of();
        }

        // iso có thể kèm giờ ("2026-03-20T21:46:00+07:00"), ngày là 10 ký tự đầu.
        var day = LocalDate.parse(holiday.date().iso().substring(0, 10));
        var name = holiday.name();

        if (containsIgnoreCase(name, "working day") || anyContains(types, "working day")) {
            return new FetchedHoliday(day, name, HolidayKind.WORKING_DAY);
        }

        if (anyContains(types, "national holiday") || anyContains(types, "public holiday")) {
            return new FetchedHoliday(day, name, HolidayKind.HOLIDAY);
        }

        return null;
    }

    private static String failureReasonOf(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        return "network error";
    }

    private static boolean anyContains(List<String> values, String needle) {
        for (var value : values) {
            if (containsIgnoreCase(value, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String value, String needle) {
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiResponse(@Nullable ApiBody response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiBody(@Nullable List<ApiHoliday> holidays) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiHoliday(@Nullable String name, @Nullable ApiDate date, @Nullable List<String> type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ApiDate(@Nullable String iso) {
    }
}
