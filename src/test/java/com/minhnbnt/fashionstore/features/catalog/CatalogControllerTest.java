package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.catalog.CategoryResponse;
import com.minhnbnt.fashionstore.dto.catalog.PresignResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.ProductSummaryResponse;
import com.minhnbnt.fashionstore.features.catalog.controller.CategoryController;
import com.minhnbnt.fashionstore.features.catalog.controller.PublicProductController;
import com.minhnbnt.fashionstore.features.catalog.controller.SearchController;
import com.minhnbnt.fashionstore.features.catalog.controller.ShopProductController;
import com.minhnbnt.fashionstore.features.catalog.controller.UploadController;
import com.minhnbnt.fashionstore.infrastructure.S3Storage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({
    CategoryController.class,
    ShopProductController.class,
    PublicProductController.class,
    SearchController.class,
    UploadController.class
})
@Import(SecurityConfig.class)
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private CatalogService catalog;

    @MockitoBean
    private S3Storage storage;

    private static ProductResponse product(UUID id, UUID shopId) {
        return new ProductResponse(
            id,
            shopId,
            "Áo thun",
            "ao-thun",
            "Mô tả",
            "ACTIVE",
            List.of(),
            List.of(),
            Instant.now()
        );
    }

    @Test
    void listCategoriesIsPublic() throws Exception {
        var id = UUID.randomUUID();
        when(catalog.listCategories())
            .thenReturn(List.of(new CategoryResponse(id, "Áo", "ao", 0)));

        mockMvc.perform(get("/api/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("ao"));
    }

    @Test
    void createProductNeedsAuth() throws Exception {
        var shopId = UUID.randomUUID();

        mockMvc.perform(post("/api/shops/{shopId}/products", shopId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Áo\",\"slug\":\"ao\",\"variants\":[]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createProductReturns201() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        when(catalog.createProduct(any(), any(), any())).thenReturn(product(UUID.randomUUID(), shopId));

        mockMvc.perform(post("/api/shops/{shopId}/products", shopId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Áo thun","slug":"ao-thun",
                     "variants":[{"sku":"SKU-1","size":"M","color":"Đen",
                      "price":100000,"stockQuantity":10,"defaultVariant":true}]}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value("ao-thun"));
    }

    @Test
    void createProductRejectsEmptyVariants() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        mockMvc.perform(post("/api/shops/{shopId}/products", shopId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Áo\",\"slug\":\"ao\",\"variants\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateProductNeedsAuth() throws Exception {
        mockMvc.perform(put("/api/shops/{shopId}/products/{id}", UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Tên mới\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void attachImagesNeedsAuth() throws Exception {
        mockMvc.perform(post("/api/shops/{shopId}/products/{id}/images", UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"images\":[{\"s3Key\":\"k.jpg\",\"sortOrder\":0}]}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void getDetailIsPublic() throws Exception {
        var id = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        when(catalog.getDetail(id)).thenReturn(product(id, shopId));

        mockMvc.perform(get("/api/products/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("ao-thun"));
    }

    @Test
    void searchIsPublic() throws Exception {
        var id = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        when(catalog.search(anyString(), anyInt()))
            .thenReturn(List.of(new ProductSummaryResponse(
                id,
                shopId,
                "Áo thun",
                "ao-thun",
                100000L,
                null,
                0.5
            )));

        mockMvc.perform(get("/api/search/products").param("q", "ao thun"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].slug").value("ao-thun"));
    }

    @Test
    void searchWithoutQueryIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/search/products"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void presignNeedsAuth() throws Exception {
        mockMvc.perform(post("/api/uploads/presign")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void presignRejectsNonImage() throws Exception {
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/api/uploads/presign")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"text/plain\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void presignReturnsKeyAndUrl() throws Exception {
        var userId = UUID.randomUUID();
        var now = Instant.now();
        when(storage.presign("image/jpeg"))
            .thenReturn(new S3Storage.PresignedUpload("abc.jpg", "http://x/abc.jpg", now));

        mockMvc.perform(post("/api/uploads/presign")
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contentType\":\"image/jpeg\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.key").value("abc.jpg"));
    }
}
