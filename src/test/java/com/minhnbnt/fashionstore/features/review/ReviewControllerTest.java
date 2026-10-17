package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.features.review.controller.ReviewController;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ReviewService reviews;

    private static ReviewResponse response(UUID productId) {
        return new ReviewResponse(
            UUID.randomUUID(),
            productId,
            "An",
            5,
            "Vai dep",
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void createsReview() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(reviews.createReview(any(), any(), any())).thenReturn(response(productId));

        mockMvc.perform(post("/api/products/{productId}/reviews", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\": 5, \"comment\": \"Vai dep\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stars").value(5));
    }

    @Test
    void listsReviews() throws Exception {
        var productId = UUID.randomUUID();
        when(reviews.getReviews(productId)).thenReturn(List.of(response(productId)));

        mockMvc.perform(get("/api/products/{productId}/reviews", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void editsReview() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        when(reviews.updateReview(any(), any(), any())).thenReturn(response(productId));

        mockMvc.perform(put("/api/products/{productId}/reviews", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\": 4}"))
            .andExpect(status().isOk());
    }

    @Test
    void deletesReview() throws Exception {
        var userId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        mockMvc.perform(delete("/api/products/{productId}/reviews", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());
    }

    @Test
    void reviewEndpointsRequireAuthentication() throws Exception {
        var productId = UUID.randomUUID();

        mockMvc.perform(post("/api/products/{productId}/reviews", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"stars\": 5}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/products/{productId}/reviews", productId)
                .with(jwt()))
            .andExpect(status().isOk());
    }
}
