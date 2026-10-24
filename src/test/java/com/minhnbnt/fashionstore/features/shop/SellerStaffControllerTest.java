package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.shop.MyPermissionsResponse;
import com.minhnbnt.fashionstore.features.shop.controller.SellerStaffController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerStaffController.class)
@Import(SecurityConfig.class)
class SellerStaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShopStaffService staffService;

    @Test
    void myPermissionsSerializesIsOwnerFlagAndPermissionList() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        when(staffService.myPermissions(userId, shopId))
            .thenReturn(new MyPermissionsResponse(true, List.of("ORDER_READ", "REVENUE_WITHDRAW")));

        mockMvc.perform(get("/api/seller/shops/{shopId}/my-permissions", shopId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isOwner").value(true))
            .andExpect(jsonPath("$.permissions[0]").value("ORDER_READ"))
            .andExpect(jsonPath("$.permissions[1]").value("REVENUE_WITHDRAW"))
            .andExpect(jsonPath("$.owner").doesNotExist());
    }

    @Test
    void myPermissionsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/seller/shops/{shopId}/my-permissions", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }
}
