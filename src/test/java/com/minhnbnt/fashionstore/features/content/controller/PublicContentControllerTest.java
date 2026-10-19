package com.minhnbnt.fashionstore.features.content.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.content.PublicBannerResponse;
import com.minhnbnt.fashionstore.dto.content.PublicFaqResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPageResponse;
import com.minhnbnt.fashionstore.dto.content.PublicPopupResponse;
import com.minhnbnt.fashionstore.features.content.BannerService;
import com.minhnbnt.fashionstore.features.content.FaqService;
import com.minhnbnt.fashionstore.features.content.PopupService;
import com.minhnbnt.fashionstore.features.content.SitePageService;
import com.minhnbnt.fashionstore.model.BannerPlacement;
import com.minhnbnt.fashionstore.model.FaqCategory;
import com.minhnbnt.fashionstore.model.PopupAudience;
import com.minhnbnt.fashionstore.model.PopupFrequency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicContentController.class)
@Import(SecurityConfig.class)
class PublicContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private BannerService banners;

    @MockitoBean
    private PopupService popups;

    @MockitoBean
    private SitePageService pages;

    @MockitoBean
    private FaqService faqs;

    @Test
    void bannersNeedNoLogin() throws Exception {
        var id = UUID.randomUUID();
        when(banners.running(BannerPlacement.HOME_CAROUSEL))
            .thenReturn(List.of(new PublicBannerResponse(id, "Sale", "a.webp", null, "/search")));

        mockMvc.perform(get("/api/public/banners").param("placement", "HOME_CAROUSEL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].imageKey").value("a.webp"));
    }

    @Test
    void unknownPlacementIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/public/banners").param("placement", "SIDEBAR"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void popupsNeedNoLogin() throws Exception {
        when(popups.running()).thenReturn(List.of(new PublicPopupResponse(
            UUID.randomUUID(), "Giảm 50%", null, null, "Mua ngay", "/search",
            PopupAudience.GUEST, PopupFrequency.ONCE, 3
        )));

        mockMvc.perform(get("/api/public/popups"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].audience").value("GUEST"))
            .andExpect(jsonPath("$[0].frequency").value("ONCE"));
    }

    @Test
    void pageBySlug() throws Exception {
        when(pages.getPublished("chinh-sach-doi-tra")).thenReturn(new PublicPageResponse(
            "chinh-sach-doi-tra", "Chính sách đổi trả", "# Đổi trả", null, Instant.now()
        ));

        mockMvc.perform(get("/api/public/pages/chinh-sach-doi-tra"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Chính sách đổi trả"));
    }

    @Test
    void draftPageIsNotFound() throws Exception {
        when(pages.getPublished("nhap"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));

        mockMvc.perform(get("/api/public/pages/nhap"))
            .andExpect(status().isNotFound());
    }

    @Test
    void faqsNeedNoLogin() throws Exception {
        when(faqs.published()).thenReturn(List.of(
            new PublicFaqResponse(UUID.randomUUID(), FaqCategory.RETURN, "Đổi size?", "Được")
        ));

        mockMvc.perform(get("/api/public/faqs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("RETURN"));
    }
}
