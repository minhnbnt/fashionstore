package com.minhnbnt.fashionstore.security;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.identity.UserResponse;
import com.minhnbnt.fashionstore.features.identity.AccountProvisioner;
import com.minhnbnt.fashionstore.features.identity.UserController;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopController;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.AccountStatus;
import com.minhnbnt.fashionstore.model.UserRole;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
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
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ShopController.class, UserController.class})
@Import(SecurityConfig.class)
class AccountFilterTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AccountProvisioner provisioner;

    @MockitoBean
    private ShopService shops;

    @MockitoBean
    private UserService users;

    private static RequestPostProcessor user() {
        return jwt().jwt(builder -> builder
            .subject(USER_ID.toString())
            .claim("email", "an@t.local")
            .claim("user_metadata", Map.of("full_name", "Nguyễn An")));
    }

    private void accountIs(AccountStatus status) {
        when(provisioner.ensureAccount(eq(USER_ID), any(), any(), any())).thenReturn(status);
    }

    @Test
    void lockedAccountIsBlockedFromProtectedApis() throws Exception {
        accountIs(AccountStatus.LOCKED);

        mockMvc.perform(post("/api/shops")
                .with(user())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Shop\",\"slug\":\"shop\"}"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void lockedAccountIsBlockedFromMyShops() throws Exception {
        accountIs(AccountStatus.LOCKED);

        mockMvc.perform(get("/api/shops/mine").with(user()))
            .andExpect(status().isForbidden());
    }

    @Test
    void lockedAccountCanStillReadOwnProfile() throws Exception {
        accountIs(AccountStatus.LOCKED);
        when(users.getById(USER_ID)).thenReturn(new UserResponse(
            USER_ID, "an@t.local", "An", null, "CUSTOMER", "LOCKED", "Spam", Instant.now()
        ));

        mockMvc.perform(get("/api/users/me").with(user()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.statusReason").value("Spam"));
    }

    @Test
    void lockedAccountCanStillBrowsePublicPages() throws Exception {
        accountIs(AccountStatus.LOCKED);
        when(shops.getBySlug("shop")).thenReturn(
            ShopTestFixtures.response(UUID.randomUUID(), "APPROVED")
        );

        mockMvc.perform(get("/api/shops/shop").with(user()))
            .andExpect(status().isOk());
    }

    @Test
    void activeAccountPassesAndIsProvisionedFromClaims() throws Exception {
        accountIs(AccountStatus.ACTIVE);
        when(shops.getMine(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/shops/mine").with(user()))
            .andExpect(status().isOk());

        verify(provisioner).ensureAccount(USER_ID, "an@t.local", "Nguyễn An", UserRole.CUSTOMER);
    }

    @Test
    void tokenRoleIsPassedToProvisioner() throws Exception {
        accountIs(AccountStatus.ACTIVE);
        when(shops.getMine(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/shops/mine").with(jwt()
                .jwt(builder -> builder.subject(USER_ID.toString()).claim("email", "an@t.local"))
                .authorities(new SimpleGrantedAuthority("ROLE_SHOP"))))
            .andExpect(status().isOk());

        verify(provisioner).ensureAccount(USER_ID, "an@t.local", "an", UserRole.SHOP);
    }

    @Test
    void nonUuidSubjectSkipsProvisioning() throws Exception {
        when(shops.getBySlug("shop")).thenReturn(
            ShopTestFixtures.response(UUID.randomUUID(), "APPROVED")
        );

        mockMvc.perform(get("/api/shops/shop").with(jwt().jwt(builder -> builder.subject("not-a-uuid"))))
            .andExpect(status().isOk());

        verify(provisioner, never()).ensureAccount(any(), any(), any(), any());
    }
}
