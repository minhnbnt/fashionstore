package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({ShopController.class, AdminShopController.class})
@Import(SecurityConfig.class)
class ShopControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShopService shops;

    private static ShopResponse response(UUID id, String status) {
        return new ShopResponse(id, "Shop", "shop", "Mo ta", null, null, status, Instant.now());
    }

    @Test
    void registerShopReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(shops.register(any(), any())).thenReturn(response(id, "PENDING"));

        mockMvc.perform(post("/api/shops")
                .with(jwt().jwt(builder -> builder.subject(id.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Shop\",\"slug\":\"shop\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void registerShopRejectsBadSlug() throws Exception {
        mockMvc.perform(post("/api/shops")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Shop\",\"slug\":\"Shop Co Dau Cach\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void approveShopNeedsAdmin() throws Exception {
        var id = UUID.randomUUID();
        when(shops.approve(any(), any())).thenReturn(response(id, "APPROVED"));

        mockMvc.perform(patch("/api/admin/shops/{id}/approve", id)
                .with(jwt()
                    .jwt(builder -> builder.subject(id.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void approveShopWithoutAdminClaimIsForbidden() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(patch("/api/admin/shops/{id}/approve", id)
                .with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void getShopBySlugIsPublic() throws Exception {
        var id = UUID.randomUUID();
        when(shops.getBySlug("shop")).thenReturn(response(id, "APPROVED"));

        mockMvc.perform(get("/api/shops/shop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("shop"));
    }

    @Test
    void listShopsNeedsAdmin() throws Exception {
        var id = UUID.randomUUID();
        when(shops.listByStatus(any())).thenReturn(List.of(response(id, "PENDING")));

        mockMvc.perform(get("/api/admin/shops")
                .param("status", "PENDING")
                .with(jwt()
                    .jwt(builder -> builder.subject(id.toString()))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void listShopsWithoutAdminClaimIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/shops")
                .param("status", "PENDING")
                .with(jwt()))
            .andExpect(status().isForbidden());
    }
}
