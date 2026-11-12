package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.review.ReviewHelpfulResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewSummaryResponse;
import com.minhnbnt.fashionstore.features.review.controller.MyReviewController;
import com.minhnbnt.fashionstore.features.review.controller.ReviewController;
import com.minhnbnt.fashionstore.model.ReviewSort;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({ReviewController.class, MyReviewController.class})
@Import(SecurityConfig.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ReviewService reviews;

    private static ReviewResponse response(UUID productId, UUID orderId) {
        return new ReviewResponse(
            UUID.randomUUID(),
            productId,
            orderId,
            "An",
            "M · Đen",
            5,
            "Vai day dan va rat dep.",
            List.of("img-1.jpg"),
            null,
            List.of(ReviewTag.QUALITY_GOOD),
            false,
            true,
            2,
            false,
            null,
            null,
            false,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void createsReview() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        when(reviews.createReview(any(), any(), any())).thenReturn(response(productId, orderId));

        mockMvc.perform(post("/api/products/{productId}/reviews", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\": \"" + orderId + "\", \"stars\": 5, \"comment\": \"Vai day dan va rat dep.\", \"anonymous\": false}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stars").value(5))
            .andExpect(jsonPath("$.orderId").value(orderId.toString()))
            .andExpect(jsonPath("$.variantLabel").value("M · Đen"));
    }

    @Test
    void listsReviewsAndSummaryPublicly() throws Exception {
        var productId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        when(reviews.getReviews(eq(productId), any(), eq(ReviewSort.HELPFUL), eq(null), eq(false)))
            .thenReturn(List.of(response(productId, orderId)));
        when(reviews.reviewSummary(productId))
            .thenReturn(new ReviewSummaryResponse(1L, 5.0, 1L, 0L, 0L, 0L, 0L, 1L));

        mockMvc.perform(get("/api/products/{productId}/reviews", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].helpfulCount").value(2));

        mockMvc.perform(get("/api/products/{productId}/reviews/summary", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reviewCount").value(1))
            .andExpect(jsonPath("$.fiveStarCount").value(1))
            .andExpect(jsonPath("$.withMediaCount").value(1));
    }

    @Test
    void listsReviewsPassesFilterParams() throws Exception {
        var productId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        when(reviews.getReviews(eq(productId), eq(userId), eq(ReviewSort.NEWEST), eq(5), eq(true)))
            .thenReturn(List.of(response(productId, orderId)));

        mockMvc.perform(get("/api/products/{productId}/reviews", productId)
                .param("sort", "NEWEST")
                .param("stars", "5")
                .param("hasMedia", "true")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void editsReviewByReviewId() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var reviewId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        when(reviews.updateReviewById(eq(userId), eq(reviewId), any())).thenReturn(response(productId, orderId));

        mockMvc.perform(put("/api/reviews/{reviewId}", reviewId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\": 4, \"comment\": \"Cap nhat nhan xet du 10 ky tu.\", \"anonymous\": false}"))
            .andExpect(status().isOk());
    }

    @Test
    void editReviewSecondTimeReturnsConflict() throws Exception {
        var userId = UUID.randomUUID();
        var reviewId = UUID.randomUUID();
        when(reviews.updateReviewById(any(), any(), any()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Review has already been edited once"));

        mockMvc.perform(put("/api/reviews/{reviewId}", reviewId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\": 4, \"comment\": \"Cap nhat lan hai khong duoc phep\", \"anonymous\": false}"))
            .andExpect(status().isConflict());
    }

    @Test
    void toggleHelpfulRequiresAuthAndReturnsCount() throws Exception {
        var userId = UUID.randomUUID();
        var reviewId = UUID.randomUUID();

        mockMvc.perform(post("/api/reviews/{reviewId}/helpful", reviewId))
            .andExpect(status().isUnauthorized());

        when(reviews.toggleHelpful(userId, reviewId))
            .thenReturn(new ReviewHelpfulResponse(reviewId, 3, true));

        mockMvc.perform(post("/api/reviews/{reviewId}/helpful", reviewId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.helpfulCount").value(3))
            .andExpect(jsonPath("$.helpfulByMe").value(true));
    }

    @Test
    void deleteReviewReturnsMethodNotAllowed() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        mockMvc.perform(delete("/api/products/{productId}/reviews", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listsMyReviewsAndRequiresAuth() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        when(reviews.listMine(userId)).thenReturn(List.of(response(productId, orderId)));

        mockMvc.perform(get("/api/reviews/mine"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/reviews/mine")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value(productId.toString()))
            .andExpect(jsonPath("$[0].orderId").value(orderId.toString()))
            .andExpect(jsonPath("$[0].edited").value(false));
    }

    @Test
    void reviewCreateRequiresAuthentication() throws Exception {
        var productId = UUID.randomUUID();

        mockMvc.perform(post("/api/products/{productId}/reviews", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\": \"" + UUID.randomUUID() + "\", \"stars\": 5, \"comment\": \"Nhan xet it nhat 10 ky tu\"}"))
            .andExpect(status().isUnauthorized());
    }
}
