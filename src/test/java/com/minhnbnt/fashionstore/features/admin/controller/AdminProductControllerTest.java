package com.minhnbnt.fashionstore.features.admin.controller;

import com.minhnbnt.fashionstore.features.admin.service.AdminProductService;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.admin.AdminProductDetailResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminProductResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordCreatedResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordResponse;
import com.minhnbnt.fashionstore.dto.admin.DismissReportsResponse;
import com.minhnbnt.fashionstore.dto.admin.ProductModerationHistoryResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import org.jspecify.annotations.Nullable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminProductController.class, AdminBannedKeywordController.class})
@Import(SecurityConfig.class)
class AdminProductControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminProductService adminProducts;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(ADMIN_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static AdminProductResponse product(
        UUID id,
        String status,
        @Nullable String violationType,
        @Nullable String reason
    ) {
        var now = Instant.now();
        return new AdminProductResponse(
            id, UUID.randomUUID(), "Shop An", "shop-an", "APPROVED",
            null, null, "Ao thun", "ao-thun", status, violationType, reason,
            100000L, 150000L, 12, 2, null, now, now,
            0, null, null, false
        );
    }

    @Test
    void listFiltersByStatusShopAndQuery() throws Exception {
        var id = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        when(adminProducts.search(eq(ProductStatus.BANNED), eq(shopId), eq("ao"), eq(false), eq(0), eq(20)))
            .thenReturn(new PageResponse<>(List.of(product(id, "BANNED", "SPAM", "Đăng trùng lặp")), 1, 0, 20));

        mockMvc.perform(get("/api/admin/products")
                .param("status", "BANNED")
                .param("shopId", shopId.toString())
                .param("q", "ao")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].violationType").value("SPAM"))
            .andExpect(jsonPath("$.items[0].shopName").value("Shop An"));
    }

    @Test
    void listWithoutFiltersMeansAll() throws Exception {
        when(adminProducts.search(isNull(), isNull(), isNull(), eq(false), eq(0), eq(20)))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/admin/products").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/admin/products").param("size", "500").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listNeedsAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/products").with(jwt()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/products"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void detailReturnsHistory() throws Exception {
        var id = UUID.randomUUID();
        var history = new ProductModerationHistoryResponse(
            "ACTIVE", "BANNED", "COUNTERFEIT", ADMIN_ID, "admin@t.local", "Hàng nhái", Instant.now()
        );
        when(adminProducts.getDetail(id)).thenReturn(new AdminProductDetailResponse(
            product(id, "BANNED", "COUNTERFEIT", "Hàng nhái"),
            "Mo ta",
            List.of(),
            List.of(),
            List.of(history),
            List.of()
        ));

        mockMvc.perform(get("/api/admin/products/{id}", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.product.status").value("BANNED"))
            .andExpect(jsonPath("$.history[0].violationType").value("COUNTERFEIT"))
            .andExpect(jsonPath("$.history[0].changedByEmail").value("admin@t.local"));
    }

    @Test
    void banUsesAdminIdFromTokenAndPassesViolation() throws Exception {
        var id = UUID.randomUUID();
        when(adminProducts.ban(ADMIN_ID, id, ProductViolationType.COUNTERFEIT, "Hàng nhái thương hiệu"))
            .thenReturn(product(id, "BANNED", "COUNTERFEIT", "Hàng nhái thương hiệu"));

        mockMvc.perform(patch("/api/admin/products/{id}/ban", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"violationType\":\"COUNTERFEIT\",\"reason\":\"Hàng nhái thương hiệu\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("BANNED"))
            .andExpect(jsonPath("$.statusReason").value("Hàng nhái thương hiệu"));
    }

    @Test
    void banRequiresReason() throws Exception {
        mockMvc.perform(patch("/api/admin/products/{id}/ban", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"violationType\":\"SPAM\",\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminProducts);
    }

    @Test
    void banRequiresViolationType() throws Exception {
        mockMvc.perform(patch("/api/admin/products/{id}/ban", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Đăng trùng lặp\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminProducts);
    }

    @Test
    void banRejectsUnknownViolationType() throws Exception {
        mockMvc.perform(patch("/api/admin/products/{id}/ban", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"violationType\":\"UGLY\",\"reason\":\"Đăng trùng lặp\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminProducts);
    }

    @Test
    void restoreUsesAdminIdFromToken() throws Exception {
        var id = UUID.randomUUID();
        when(adminProducts.restore(ADMIN_ID, id)).thenReturn(product(id, "HIDDEN", null, null));

        mockMvc.perform(patch("/api/admin/products/{id}/restore", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("HIDDEN"));
    }

    @Test
    void listReportedQueue() throws Exception {
        var id = UUID.randomUUID();
        var now = Instant.now();
        var row = new AdminProductResponse(
            id, UUID.randomUUID(), "Shop An", "shop-an", "APPROVED",
            null, null, "Ao thun", "ao-thun", "ACTIVE", null, null,
            100000L, 100000L, 5, 1, null, now, now,
            4, "COUNTERFEIT", now, true
        );
        when(adminProducts.search(isNull(), isNull(), isNull(), eq(true), eq(0), eq(20)))
            .thenReturn(new PageResponse<>(List.of(row), 1, 0, 20));

        mockMvc.perform(get("/api/admin/products").param("reported", "true").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].openReportCount").value(4))
            .andExpect(jsonPath("$.items[0].topReportReason").value("COUNTERFEIT"))
            .andExpect(jsonPath("$.items[0].systemFlagged").value(true));
    }

    @Test
    void dismissReportsPassesNote() throws Exception {
        var id = UUID.randomUUID();
        when(adminProducts.dismissReports(ADMIN_ID, id, "Ảnh đúng thực tế"))
            .thenReturn(new DismissReportsResponse(3));

        mockMvc.perform(patch("/api/admin/products/{id}/reports/dismiss", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Ảnh đúng thực tế\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dismissedCount").value(3));
    }

    @Test
    void dismissReportsWithoutBodyHasNoNote() throws Exception {
        var id = UUID.randomUUID();
        when(adminProducts.dismissReports(ADMIN_ID, id, null)).thenReturn(new DismissReportsResponse(1));

        mockMvc.perform(patch("/api/admin/products/{id}/reports/dismiss", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dismissedCount").value(1));
    }

    @Test
    void addKeywordReturnsFlaggedCount() throws Exception {
        var keyword = new BannedKeywordResponse(
            UUID.randomUUID(), "replica", "COUNTERFEIT", "admin@t.local", Instant.now()
        );
        when(adminProducts.addKeyword(eq(ADMIN_ID), any()))
            .thenReturn(new BannedKeywordCreatedResponse(keyword, 7));

        mockMvc.perform(post("/api/admin/banned-keywords")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"replica\",\"violationType\":\"COUNTERFEIT\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.keyword.keyword").value("replica"))
            .andExpect(jsonPath("$.flaggedProducts").value(7));
    }

    @Test
    void addKeywordValidatesBody() throws Exception {
        mockMvc.perform(post("/api/admin/banned-keywords")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"a\",\"violationType\":\"COUNTERFEIT\"}"))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/banned-keywords")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyword\":\"replica\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(adminProducts);
    }

    @Test
    void listAndDeleteKeywords() throws Exception {
        var id = UUID.randomUUID();
        when(adminProducts.listKeywords())
            .thenReturn(List.of(new BannedKeywordResponse(id, "fake", "COUNTERFEIT", null, Instant.now())));

        mockMvc.perform(get("/api/admin/banned-keywords").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].keyword").value("fake"));

        mockMvc.perform(delete("/api/admin/banned-keywords/{id}", id).with(admin()))
            .andExpect(status().isNoContent());

        verify(adminProducts).deleteKeyword(id);
    }

    @Test
    void keywordsNeedAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/banned-keywords").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void banNeedsAdmin() throws Exception {
        mockMvc.perform(patch("/api/admin/products/{id}/ban", UUID.randomUUID())
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"violationType\":\"SPAM\",\"reason\":\"Đăng trùng lặp\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(adminProducts);
    }
}
