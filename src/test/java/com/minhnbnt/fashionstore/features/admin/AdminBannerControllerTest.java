package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.content.BannerRequest;
import com.minhnbnt.fashionstore.dto.content.BannerResponse;
import com.minhnbnt.fashionstore.features.content.BannerService;
import com.minhnbnt.fashionstore.model.BannerPlacement;
import com.minhnbnt.fashionstore.model.ScheduleState;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBannerController.class)
@Import(SecurityConfig.class)
class AdminBannerControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BannerService banners;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(ADMIN_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String body(String linkUrl) {
        return """
            {"placement":"HOME_CAROUSEL","title":"Sale thu","imageKey":"a.webp",
             "linkUrl":"%s","sortOrder":1,"active":true}
            """.formatted(linkUrl);
    }

    private static BannerResponse response(UUID id) {
        return new BannerResponse(
            id, BannerPlacement.HOME_CAROUSEL, "Sale thu", "a.webp", null, "/search", 1, true,
            null, null, ScheduleState.RUNNING, Instant.now()
        );
    }

    @Test
    void listWithoutPlacementMeansAll() throws Exception {
        var id = UUID.randomUUID();
        when(banners.listForAdmin(isNull())).thenReturn(List.of(response(id)));

        mockMvc.perform(get("/api/admin/banners").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].scheduleState").value("RUNNING"));
    }

    @Test
    void createReturnsCreatedWithAdminFromJwt() throws Exception {
        var id = UUID.randomUUID();
        when(banners.create(eq(ADMIN_ID), any(BannerRequest.class))).thenReturn(response(id));

        mockMvc.perform(post("/api/admin/banners")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("/search?q=ao")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void createAcceptsHttpsLink() throws Exception {
        when(banners.create(eq(ADMIN_ID), any(BannerRequest.class))).thenReturn(response(UUID.randomUUID()));

        mockMvc.perform(post("/api/admin/banners")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("https://example.com/sale")))
            .andExpect(status().isCreated());
    }

    @Test
    void createRejectsScriptAndProtocolRelativeLinks() throws Exception {
        mockMvc.perform(post("/api/admin/banners")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("javascript:alert(1)")))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/banners")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("//evil.example")))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(banners);
    }

    @Test
    void createRejectsMissingImage() throws Exception {
        mockMvc.perform(post("/api/admin/banners")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"placement\":\"HOME_STRIP\",\"title\":\"x\",\"sortOrder\":0,\"active\":true}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(banners);
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete("/api/admin/banners/{id}", id).with(admin()))
            .andExpect(status().isNoContent());

        verify(banners).delete(id);
    }

    @Test
    void needsAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/banners").with(jwt()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/banners"))
            .andExpect(status().isUnauthorized());
    }
}
