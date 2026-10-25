package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.shop.SellerShopMembershipResponse;
import com.minhnbnt.fashionstore.features.shop.ShopStaffService;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.ShopStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerShopMembershipController.class)
@Import(SecurityConfig.class)
class SellerShopMembershipControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShopStaffService staffService;

    @Test
    void getMyShopsReturnsUserMemberships() throws Exception {
        var userId = UUID.randomUUID();
        var shopId1 = UUID.randomUUID();
        var shopId2 = UUID.randomUUID();
        var now = Instant.now();

        var memberships = List.of(
            new SellerShopMembershipResponse(
                shopId1,
                "Shop Owner",
                "shop-owner",
                "logo1.png",
                null,
                ShopStatus.APPROVED,
                MemberRole.OWNER,
                null,
                "Chủ gian hàng",
                "ACTIVE",
                now,
                List.of("ALL")
            ),
            new SellerShopMembershipResponse(
                shopId2,
                "Shop Staff",
                "shop-staff",
                null,
                null,
                ShopStatus.APPROVED,
                MemberRole.STAFF,
                UUID.randomUUID(),
                "Kho vận",
                "ACTIVE",
                now,
                List.of("ORDER_READ")
            )
        );

        when(staffService.getMyMemberships(userId)).thenReturn(memberships);

        mockMvc.perform(get("/api/seller/my-shops")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].shopId").value(shopId1.toString()))
            .andExpect(jsonPath("$[0].shopName").value("Shop Owner"))
            .andExpect(jsonPath("$[0].role").value("OWNER"))
            .andExpect(jsonPath("$[1].shopId").value(shopId2.toString()))
            .andExpect(jsonPath("$[1].shopName").value("Shop Staff"))
            .andExpect(jsonPath("$[1].role").value("STAFF"))
            .andExpect(jsonPath("$[1].roleName").value("Kho vận"));
    }

    @Test
    void getMyShopsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/seller/my-shops"))
            .andExpect(status().isUnauthorized());
    }
}
