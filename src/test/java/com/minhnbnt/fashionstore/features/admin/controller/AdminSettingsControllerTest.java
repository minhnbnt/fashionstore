package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.settings.PublicSettingsResponse;
import com.minhnbnt.fashionstore.features.settings.ConfigAuditService;
import com.minhnbnt.fashionstore.features.settings.PublicSettingsController;
import com.minhnbnt.fashionstore.features.settings.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminSettingsController.class, PublicSettingsController.class})
@Import(SecurityConfig.class)
class AdminSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SystemSettingsService settings;

    @MockitoBean
    private ConfigAuditService audits;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(UUID.randomUUID().toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Test
    void getSettingsRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/api/admin/settings").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void updateWithBaseItemsZeroIsBadRequest() throws Exception {
        mockMvc.perform(put("/api/admin/settings").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseItems\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void publicSettingsNeedNoJwt() throws Exception {
        when(settings.publicView()).thenReturn(
            new PublicSettingsResponse("nét", "1900 0000", "support@fashionstore.local", 500000, 2)
        );

        mockMvc.perform(get("/api/public/settings"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.siteName").value("nét"));
    }
}
