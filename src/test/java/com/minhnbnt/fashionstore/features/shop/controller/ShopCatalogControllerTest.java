package com.minhnbnt.fashionstore.features.shop.controller;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopCategorySummaryResponse;
import com.minhnbnt.fashionstore.dto.shop.ShopProductQueryRequest;
import com.minhnbnt.fashionstore.features.shop.ShopCatalogService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShopCatalogController.class)
@Import(SecurityConfig.class)
class ShopCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ShopCatalogService shopCatalog;

    @Test
    void getShopProductsReturnsPageResponse() throws Exception {
        var shopId = UUID.randomUUID();
        var prodId = UUID.randomUUID();
        var product = new ProductResponse(
            prodId, shopId, null, "Áo Thun Polo", "ao-thun-polo", "mô tả", "ACTIVE",
            List.of(), List.of(), Instant.now(), 4.8, 12L
        );
        when(shopCatalog.getShopProducts(eq(shopId), any(ShopProductQueryRequest.class)))
            .thenReturn(new PageResponse<>(List.of(product), 1L, 0, 12));

        mockMvc.perform(get("/api/shops/{shopId}/products", shopId)
                .param("q", "Polo")
                .param("page", "0")
                .param("limit", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].name").value("Áo Thun Polo"));
    }

    @Test
    void getShopProductsReturns404WhenShopNotFound() throws Exception {
        var shopId = UUID.randomUUID();
        when(shopCatalog.getShopProducts(eq(shopId), any(ShopProductQueryRequest.class)))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));

        mockMvc.perform(get("/api/shops/{shopId}/products", shopId))
            .andExpect(status().isNotFound());
    }

    @Test
    void getShopCategoriesReturnsCategoryList() throws Exception {
        var shopId = UUID.randomUUID();
        var catId = UUID.randomUUID();
        when(shopCatalog.getShopCategories(shopId))
            .thenReturn(List.of(new ShopCategorySummaryResponse(catId, "Áo Nam", "ao-nam", 8L)));

        mockMvc.perform(get("/api/shops/{shopId}/categories", shopId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Áo Nam"))
            .andExpect(jsonPath("$[0].productCount").value(8));
    }
}
