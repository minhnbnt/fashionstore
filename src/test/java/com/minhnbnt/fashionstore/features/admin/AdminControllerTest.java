package com.minhnbnt.fashionstore.features.admin;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.admin.AdminDailyStatResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminShopResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminStatsResponse;
import com.minhnbnt.fashionstore.dto.admin.AdminUserResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import com.minhnbnt.fashionstore.model.ShopStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminShopController.class, AdminUserController.class, AdminStatsController.class})
@Import(SecurityConfig.class)
class AdminControllerTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AdminShopService adminShops;

    @MockitoBean
    private AdminUserService adminUsers;

    @MockitoBean
    private AdminStatsService stats;

    private static RequestPostProcessor admin() {
        return jwt()
            .jwt(builder -> builder.subject(ADMIN_ID.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static AdminShopResponse adminShop(UUID id, String status) {
        var now = Instant.now();
        return new AdminShopResponse(
            id, "Shop", "shop", "", null, null, status, null,
            UUID.randomUUID(), "an@t.local", "An", "ACTIVE", now, null, now, now
        );
    }

    private static ShopResponse shop(UUID id, String status, @Nullable String reason) {
        return new ShopResponse(id, "Shop", "shop", "", null, null, status, reason, Instant.now());
    }

    private static UserResponse user(UUID id, String status) {
        return new UserResponse(id, "an@t.local", "An", null, "CUSTOMER", status, null, Instant.now());
    }

    @Test
    void listShopsReturnsPage() throws Exception {
        var id = UUID.randomUUID();
        when(adminShops.search(eq(ShopStatus.PENDING), eq("an"), eq(0), eq(20)))
            .thenReturn(new PageResponse<>(List.of(adminShop(id, "PENDING")), 1, 0, 20));

        mockMvc.perform(get("/api/admin/shops")
                .param("status", "PENDING")
                .param("q", "an")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].ownerEmail").value("an@t.local"))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void listShopsWithoutStatusMeansAll() throws Exception {
        when(adminShops.search(isNull(), isNull(), eq(0), eq(20)))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 20));

        mockMvc.perform(get("/api/admin/shops").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void listShopsRejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/api/admin/shops").param("size", "500").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listShopsWithoutAdminClaimIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/shops").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void listShopsWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/shops"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void approveUsesAdminIdFromToken() throws Exception {
        var id = UUID.randomUUID();
        when(adminShops.approve(ADMIN_ID, id)).thenReturn(shop(id, "APPROVED", null));

        mockMvc.perform(patch("/api/admin/shops/{id}/approve", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void rejectRequiresReason() throws Exception {
        mockMvc.perform(patch("/api/admin/shops/{id}/reject", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectPassesReason() throws Exception {
        var id = UUID.randomUUID();
        when(adminShops.reject(ADMIN_ID, id, "Thiếu mô tả shop"))
            .thenReturn(shop(id, "REJECTED", "Thiếu mô tả shop"));

        mockMvc.perform(patch("/api/admin/shops/{id}/reject", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Thiếu mô tả shop\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusReason").value("Thiếu mô tả shop"));
    }

    @Test
    void suspendAndReinstate() throws Exception {
        var id = UUID.randomUUID();
        when(adminShops.suspend(ADMIN_ID, id, "Bán hàng giả")).thenReturn(shop(id, "SUSPENDED", "Bán hàng giả"));
        when(adminShops.reinstate(ADMIN_ID, id)).thenReturn(shop(id, "APPROVED", null));

        mockMvc.perform(patch("/api/admin/shops/{id}/suspend", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Bán hàng giả\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(patch("/api/admin/shops/{id}/reinstate", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void listUsersFiltersByStatus() throws Exception {
        var id = UUID.randomUUID();
        var row = new AdminUserResponse(id, "an@t.local", "An", null, "CUSTOMER", "LOCKED", "Spam", Instant.now());
        when(adminUsers.search(any(), any(), any(), eq(0), eq(20)))
            .thenReturn(new PageResponse<>(List.of(row), 1, 0, 20));

        mockMvc.perform(get("/api/admin/users").param("status", "LOCKED").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].status").value("LOCKED"));
    }

    @Test
    void lockUserPassesReason() throws Exception {
        var id = UUID.randomUUID();
        when(adminUsers.lock(ADMIN_ID, id, "Gian lận hoàn tiền")).thenReturn(user(id, "LOCKED"));

        mockMvc.perform(patch("/api/admin/users/{id}/lock", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Gian lận hoàn tiền\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LOCKED"));

        verify(adminUsers).lock(ADMIN_ID, id, "Gian lận hoàn tiền");
    }

    @Test
    void lockUserRequiresReason() throws Exception {
        mockMvc.perform(patch("/api/admin/users/{id}/lock", UUID.randomUUID())
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unlockUser() throws Exception {
        var id = UUID.randomUUID();
        when(adminUsers.unlock(ADMIN_ID, id)).thenReturn(user(id, "ACTIVE"));

        mockMvc.perform(patch("/api/admin/users/{id}/unlock", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void statsNeedsAdmin() throws Exception {
        var trend = List.of(new AdminDailyStatResponse(LocalDate.of(2026, 10, 16), 3, 1, 2, 450000L));
        when(stats.get()).thenReturn(new AdminStatsResponse(
            10, 1, 6, 3, 1,
            5, 2, 2, 1, 0,
            17, 4, 3, 2, 7, 1,
            1600000L, 700000L, 6L,
            trend
        ));

        mockMvc.perform(get("/api/admin/stats").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingShops").value(2))
            .andExpect(jsonPath("$.totalOrders").value(17))
            .andExpect(jsonPath("$.collectedRevenue").value(700000))
            .andExpect(jsonPath("$.openProductReports").value(6))
            .andExpect(jsonPath("$.dailyTrend[0].date").value("2026-10-16"))
            .andExpect(jsonPath("$.dailyTrend[0].orderValue").value(450000));

        mockMvc.perform(get("/api/admin/stats").with(jwt()))
            .andExpect(status().isForbidden());
    }
}
