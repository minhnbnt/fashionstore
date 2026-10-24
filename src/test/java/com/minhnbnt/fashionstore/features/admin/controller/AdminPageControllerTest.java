package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.content.SitePageRequest;
import com.minhnbnt.fashionstore.dto.content.SitePageResponse;
import com.minhnbnt.fashionstore.features.content.SitePageService;
import com.minhnbnt.fashionstore.model.PageFooterGroup;
import com.minhnbnt.fashionstore.model.PageStatus;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPageController.class)
@Import(SecurityConfig.class)
class AdminPageControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private SitePageService pages;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(ADMIN_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String body(String slug) {
        return """
            {"slug":"%s","title":"Chính sách đổi trả","content":"# Đổi trả",
             "footerGroup":"POLICY","sortOrder":1}
            """.formatted(slug);
    }

    private static SitePageResponse response(UUID id, PageStatus status) {
        return new SitePageResponse(
            id, "chinh-sach-doi-tra", "Chính sách đổi trả", "# Đổi trả", null,
            PageFooterGroup.POLICY, 1, status, null, Instant.now()
        );
    }

    @Test
    void createReturnsDraft() throws Exception {
        var id = UUID.randomUUID();
        when(pages.create(eq(ADMIN_ID), any(SitePageRequest.class))).thenReturn(response(id, PageStatus.DRAFT));

        mockMvc.perform(post("/api/admin/pages")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("chinh-sach-doi-tra")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createRejectsBadSlug() throws Exception {
        for (var slug : new String[] {"Chinh Sach", "chính-sách", "-dau", "cuoi-", "a--b"}) {
            mockMvc.perform(post("/api/admin/pages")
                    .with(admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(slug)))
                .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(pages);
    }

    @Test
    void duplicateSlugIsConflict() throws Exception {
        when(pages.create(eq(ADMIN_ID), any(SitePageRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Slug is already in use"));

        mockMvc.perform(post("/api/admin/pages")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("chinh-sach-doi-tra")))
            .andExpect(status().isConflict());
    }

    @Test
    void publishUsesAdminFromJwt() throws Exception {
        var id = UUID.randomUUID();
        when(pages.publish(ADMIN_ID, id)).thenReturn(response(id, PageStatus.PUBLISHED));

        mockMvc.perform(patch("/api/admin/pages/{id}/publish", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void needsAdmin() throws Exception {
        mockMvc.perform(patch("/api/admin/pages/{id}/publish", UUID.randomUUID()).with(jwt()))
            .andExpect(status().isForbidden());
    }
}
