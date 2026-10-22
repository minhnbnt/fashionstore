package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.shop.ShopResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShopController.class)
@Import(SecurityConfig.class)
class ShopControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShopService shops;

    private static ShopResponse response(UUID id, String status) {
        return new ShopResponse(id, "Shop", "shop", "Mo ta", null, null, status, null, Instant.now());
    }

    private static String validRegisterJson(String name, String slug) {
        return """
            {
              "name": "%s",
              "slug": "%s",
              "description": "Mo ta shop hop le",
              "logoKey": "logo.webp",
              "bannerKey": "banner.webp",
              "phone": "0912345678",
              "address": "123 Pho Hue, Ha Noi",
              "legalName": "NGUYEN VAN A",
              "idNumber": "001202001234",
              "idIssuedDate": "01/01/2021",
              "idIssuedPlace": "Cuc Canh sat QLHC ve TTXH",
              "idFrontKey": "front.webp",
              "idBackKey": "back.webp",
              "idSelfieKey": "selfie.webp",
              "bankName": "Vietcombank",
              "bankAccount": "0123456789",
              "bankHolder": "NGUYEN VAN A"
            }
            """.formatted(name, slug);
    }

    @Test
    void registerShopReturns201() throws Exception {
        var id = UUID.randomUUID();
        when(shops.register(any(), any())).thenReturn(response(id, "PENDING"));

        mockMvc.perform(post("/api/shops")
                .with(jwt().jwt(builder -> builder.subject(id.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRegisterJson("Shop", "shop")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void registerShopRejectsBadSlug() throws Exception {
        mockMvc.perform(post("/api/shops")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validRegisterJson("Shop", "Shop Co Dau Cach")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerShopRejectsMissingMandatoryFields() throws Exception {
        mockMvc.perform(post("/api/shops")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Shop\",\"slug\":\"shop\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerShopRejectsNullImportantFields() throws Exception {
        mockMvc.perform(post("/api/shops")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Shop",
                      "slug": "shop",
                      "description": "desc",
                      "logoKey": "logo",
                      "bannerKey": "banner",
                      "phone": null,
                      "address": null,
                      "legalName": "NGUYEN VAN A",
                      "idNumber": "001202001234",
                      "idIssuedDate": "01/01/2021",
                      "idIssuedPlace": "Cuc QLHC",
                      "idFrontKey": "front",
                      "idBackKey": "back",
                      "idSelfieKey": "selfie",
                      "bankName": "VCB",
                      "bankAccount": null,
                      "bankHolder": "NGUYEN VAN A"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void registerShopRejectsEmptyBody() throws Exception {
        mockMvc.perform(post("/api/shops")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
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
    void resubmitUsesOwnerIdFromToken() throws Exception {
        var ownerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        when(shops.resubmit(ownerId, shopId)).thenReturn(response(shopId, "PENDING"));

        mockMvc.perform(post("/api/shops/{id}/resubmit", shopId)
                .with(jwt().jwt(builder -> builder.subject(ownerId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void resubmitWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/shops/{id}/resubmit", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }
}


