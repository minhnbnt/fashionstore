package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.review.ReviewReplyTemplateResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewProductOptionResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewTrendResponse;
import com.minhnbnt.fashionstore.features.review.controller.SellerReviewController;
import com.minhnbnt.fashionstore.model.ReviewTag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerReviewController.class)
@Import(SecurityConfig.class)
class SellerReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ReviewService reviews;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        var shopId = UUID.randomUUID();

        mockMvc.perform(get("/api/seller/reviews").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/reviews/products").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/reviews/trend").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/seller/reviews/templates").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/seller/reviews/templates")
                .param("shopId", shopId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Mẫu\",\"content\":\"Nội dung\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listsSellerReviewsWithStarAndProductFilters() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var reviewId = UUID.randomUUID();

        var item = new SellerReviewResponse(
            reviewId,
            productId,
            "Áo linen dáng suông",
            UUID.randomUUID(),
            UUID.randomUUID(), // buyerId
            "M***h",
            "M · Kem",
            5,
            "Áo mặc thoáng mát lắm.",
            List.of(),
            null,
            List.of(ReviewTag.QUALITY_GOOD),
            true,
            3,
            null,
            null,
            false,
            false,
            null,
            Instant.now(),
            Instant.now()
        );
        when(reviews.listForShop(userId, shopId, true, 5, productId)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/seller/reviews")
                .param("shopId", shopId.toString())
                .param("unreplied", "true")
                .param("stars", "5")
                .param("productId", productId.toString())
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(reviewId.toString()))
            .andExpect(jsonPath("$[0].productName").value("Áo linen dáng suông"))
            .andExpect(jsonPath("$[0].stars").value(5));

        when(reviews.listForShop(userId, shopId, false, 6, null))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stars filter must be between 1 and 5"));

        mockMvc.perform(get("/api/seller/reviews")
                .param("shopId", shopId.toString())
                .param("stars", "6")
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void listsProductsAndTrendAndValidatesPeriod() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        when(reviews.listShopReviewProducts(userId, shopId)).thenReturn(List.of(
            new SellerReviewProductOptionResponse(productId, "Quần jeans cạp cao", 4L)
        ));

        mockMvc.perform(get("/api/seller/reviews/products")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("Quần jeans cạp cao"))
            .andExpect(jsonPath("$[0].reviewCount").value(4));

        var from = LocalDate.of(2026, 10, 1);
        var to = LocalDate.of(2026, 10, 31);
        when(reviews.shopReviewTrend(userId, shopId, "week", from, to))
            .thenReturn(new SellerReviewTrendResponse(
                "week",
                List.of(new SellerReviewTrendResponse.Point("2026-W40", 5L, 4.4))
            ));

        mockMvc.perform(get("/api/seller/reviews/trend")
                .param("shopId", shopId.toString())
                .param("period", "week")
                .param("from", "2026-10-01")
                .param("to", "2026-10-31")
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("week"))
            .andExpect(jsonPath("$.points[0].label").value("2026-W40"))
            .andExpect(jsonPath("$.points[0].averageRating").value(4.4));

        when(reviews.shopReviewTrend(eq(userId), eq(shopId), eq("year"), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period"));

        mockMvc.perform(get("/api/seller/reviews/trend")
                .param("shopId", shopId.toString())
                .param("period", "year")
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void managesReplyTemplatesAndPropagatesValidationAndConflicts() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var templateId = UUID.randomUUID();
        var now = Instant.now();
        var template = new ReviewReplyTemplateResponse(
            templateId,
            shopId,
            "Cảm ơn",
            "Cảm ơn bạn đã ủng hộ shop!",
            now,
            now
        );

        when(reviews.listReplyTemplates(userId, shopId)).thenReturn(List.of(template));
        when(reviews.createReplyTemplate(eq(userId), eq(shopId), any())).thenReturn(template);
        when(reviews.updateReplyTemplate(eq(userId), eq(templateId), any())).thenReturn(template);

        mockMvc.perform(get("/api/seller/reviews/templates")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("Cảm ơn"));

        mockMvc.perform(post("/api/seller/reviews/templates")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(token -> token.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Cảm ơn\",\"content\":\"Cảm ơn bạn đã ủng hộ shop!\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(templateId.toString()));

        mockMvc.perform(post("/api/seller/reviews/templates")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(token -> token.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"   \",\"content\":\"Nội dung\"}"))
            .andExpect(status().isBadRequest());

        when(reviews.createReplyTemplate(eq(userId), eq(shopId), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate title"));

        mockMvc.perform(post("/api/seller/reviews/templates")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(token -> token.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Cảm ơn\",\"content\":\"Trùng\"}"))
            .andExpect(status().isConflict());

        mockMvc.perform(put("/api/seller/reviews/templates/" + templateId)
                .with(jwt().jwt(token -> token.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Cảm ơn\",\"content\":\"Nội dung mới\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(templateId.toString()));

        mockMvc.perform(delete("/api/seller/reviews/templates/" + templateId)
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(reviews).deleteReplyTemplate(userId, templateId);

        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission"))
            .when(reviews).deleteReplyTemplate(userId, templateId);

        mockMvc.perform(delete("/api/seller/reviews/templates/" + templateId)
                .with(jwt().jwt(token -> token.subject(userId.toString()))))
            .andExpect(status().isForbidden());
    }
}

