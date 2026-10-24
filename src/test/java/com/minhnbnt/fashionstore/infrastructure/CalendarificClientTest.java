package com.minhnbnt.fashionstore.infrastructure;

import com.minhnbnt.fashionstore.model.HolidayKind;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CalendarificClientTest {

    private static final String URL = "http://calendarific.test/api/v2";

    private static final String BODY = """
        {"meta":{"code":200},"response":{"holidays":[
          {"name":"International New Year's Day","description":"","date":{"iso":"2026-01-01"},
           "type":["National holiday"],"primary_type":"National holiday","locations":"All","states":"All"},
          {"name":"Valentine's Day","date":{"iso":"2026-02-14"},"type":["Observance"]},
          {"name":"March Equinox","date":{"iso":"2026-03-20T21:46:00+07:00"},"type":["Season"]},
          {"name":"Tet Holiday","date":{"iso":"2026-02-18"},"type":["National holiday"]},
          {"name":"Day off for Hung Kings Festival","date":{"iso":"2026-04-27T00:00:00+07:00"},
           "type":["National holiday"]},
          {"name":"Working day for Independence Day Holiday","date":{"iso":"2026-08-22"},"type":["Observance"]},
          {"name":"Christmas Day","date":{"iso":"2026-12-25"},"type":["Christian"]}
        ]}}
        """;

    private final RestClient.Builder builder = RestClient.builder();

    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private CalendarificClient client(String key) {
        return new CalendarificClient(builder, URL, key);
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void keepsNationalHolidaysAndWorkingDaysOnly() {
        server.expect(requestTo(URL + "/holidays?api_key=secret&country=VN&year=2026"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        var holidays = client("secret").fetchVietnam(2026);

        assertThat(holidays)
            .extracting(holiday -> holiday.day() + ":" + holiday.kind())
            .containsExactly(
                "2026-01-01:HOLIDAY",
                "2026-02-18:HOLIDAY",
                "2026-04-27:HOLIDAY",
                "2026-08-22:WORKING_DAY"
            );
        assertThat(holidays.get(1).name()).isEqualTo("Tet Holiday");
        server.verify();
    }

    @Test
    void holidayWinsWhenSameDayListedTwice() {
        var body = """
            {"meta":{"code":200},"response":{"holidays":[
              {"name":"Working day for Tet","date":{"iso":"2026-02-18"},"type":["Observance"]},
              {"name":"Tet Holiday","date":{"iso":"2026-02-18"},"type":["National holiday"]}
            ]}}
            """;
        server.expect(requestTo(URL + "/holidays?api_key=secret&country=VN&year=2026"))
            .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        var holidays = client("secret").fetchVietnam(2026);

        assertThat(holidays).hasSize(1);
        assertThat(holidays.get(0).day()).isEqualTo(LocalDate.of(2026, 2, 18));
        assertThat(holidays.get(0).kind()).isEqualTo(HolidayKind.HOLIDAY);
    }

    @Test
    void blankKeyIsNotConfiguredAndNeverCalls() {
        var client = client(" ");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.fetchVietnam(2026))
            .satisfies(e -> assertStatus(e, HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    @Test
    void upstreamErrorBecomesBadGateway() {
        server.expect(requestTo(URL + "/holidays?api_key=secret&country=VN&year=2026"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"meta\":{\"code\":429,\"error_type\":\"rate_limit_exceeded\"},\"response\":[]}"));

        assertThatThrownBy(() -> client("secret").fetchVietnam(2026))
            .satisfies(e -> assertStatus(e, HttpStatus.BAD_GATEWAY))
            .satisfies(e -> assertThat(((ResponseStatusException) e).getReason()).contains("429"))
            .satisfies(e -> assertThat(((ResponseStatusException) e).getReason()).doesNotContain("secret"));
    }
}
