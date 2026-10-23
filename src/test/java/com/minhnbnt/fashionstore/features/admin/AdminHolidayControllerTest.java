package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.holiday.HolidayResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidaySyncResultResponse;
import com.minhnbnt.fashionstore.dto.holiday.HolidayYearResponse;
import com.minhnbnt.fashionstore.model.HolidayKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminHolidayController.class)
@Import(SecurityConfig.class)
class AdminHolidayControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    private static final LocalDate DAY = LocalDate.of(2026, 11, 24);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminHolidayService adminHolidays;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(ADMIN_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static HolidayResponse holiday(boolean enabled) {
        return new HolidayResponse(
            DAY, "Ngày Văn hóa Việt Nam", "HOLIDAY", "MANUAL", enabled, "admin@t.local", Instant.now()
        );
    }

    @Test
    void listByYear() throws Exception {
        var year = new HolidayYearResponse(2026, true, null, List.of(holiday(true)));
        when(adminHolidays.listYear(2026)).thenReturn(year);

        mockMvc.perform(get("/api/admin/holidays").param("year", "2026").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.year").value(2026))
            .andExpect(jsonPath("$.syncConfigured").value(true))
            .andExpect(jsonPath("$.holidays[0].day").value("2026-11-24"));
    }

    @Test
    void listRejectsOutOfRangeYear() throws Exception {
        mockMvc.perform(get("/api/admin/holidays").param("year", "1999").with(admin()))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminHolidays);
    }

    @Test
    void saveUsesAdminIdAndBody() throws Exception {
        when(adminHolidays.save(ADMIN_ID, DAY, "Ngày Văn hóa Việt Nam", HolidayKind.HOLIDAY))
            .thenReturn(holiday(true));

        mockMvc.perform(put("/api/admin/holidays/{day}", "2026-11-24")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ngày Văn hóa Việt Nam\",\"kind\":\"HOLIDAY\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("MANUAL"));
    }

    @Test
    void saveRequiresNameAndKind() throws Exception {
        mockMvc.perform(put("/api/admin/holidays/{day}", "2026-11-24")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"kind\":\"HOLIDAY\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/admin/holidays/{day}", "2026-11-24")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nghỉ\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminHolidays);
    }

    @Test
    void deleteDisablesDay() throws Exception {
        when(adminHolidays.disable(ADMIN_ID, DAY)).thenReturn(holiday(false));

        mockMvc.perform(delete("/api/admin/holidays/{day}", "2026-11-24").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void syncReturnsCounts() throws Exception {
        when(adminHolidays.sync(2027)).thenReturn(new HolidaySyncResultResponse(2027, 3, 1, 0, 2));

        mockMvc.perform(post("/api/admin/holidays/sync").param("year", "2027").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.added").value(3))
            .andExpect(jsonPath("$.keptManual").value(2));
    }

    @Test
    void syncWithoutApiKeyIsServiceUnavailable() throws Exception {
        when(adminHolidays.sync(2027))
            .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "not configured"));

        mockMvc.perform(post("/api/admin/holidays/sync").param("year", "2027").with(admin()))
            .andExpect(status().isServiceUnavailable());
    }

    @Test
    void needsAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/holidays").with(jwt()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/holidays/sync").with(jwt()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(adminHolidays);
    }
}
