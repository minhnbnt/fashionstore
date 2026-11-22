package com.minhnbnt.fashionstore.features.qna;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.common.PageResponse;
import com.minhnbnt.fashionstore.dto.qna.CreateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.ProductAnswerResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQnaProductOptionResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQnaStatsResponse;
import com.minhnbnt.fashionstore.dto.qna.SellerQuestionResponse;
import com.minhnbnt.fashionstore.dto.qna.UpdateAnswerRequest;
import com.minhnbnt.fashionstore.features.qna.controller.SellerQnaController;
import com.minhnbnt.fashionstore.model.ProductAnswerRole;
import com.minhnbnt.fashionstore.model.ProductQuestionSort;
import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SellerQnaController.class)
@Import(SecurityConfig.class)
class SellerQnaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductQnaService qnaService;

    private static SellerQuestionResponse sellerQuestionResponse(UUID questionId, UUID productId) {
        var now = Instant.now();
        return new SellerQuestionResponse(
            questionId,
            "QA-TEST02",
            productId,
            "Áo thun cotton",
            "http://s3.test/fashionstore/img.jpg",
            "Hoang",
            "Áo có co giãn không shop?",
            ProductQuestionStatus.UNANSWERED,
            0L,
            0L,
            null,
            null,
            now,
            now,
            List.of()
        );
    }

    private static ProductAnswerResponse answerResponse(UUID questionId, UUID answerId, String content) {
        var now = Instant.now();
        return new ProductAnswerResponse(
            answerId,
            questionId,
            "Shop Official",
            ProductAnswerRole.SHOP,
            false,
            content,
            true,
            true,
            true,
            null,
            now,
            now
        );
    }

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        var shopId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerId = UUID.randomUUID();

        mockMvc.perform(get("/api/seller/qna/questions").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/seller/qna/questions/{questionId}", questionId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/seller/qna/products").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/seller/qna/stats").param("shopId", shopId.toString()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/seller/qna/questions/{questionId}/answers", questionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Dạ có co giãn 4 chiều ạ.\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/seller/qna/answers/{answerId}", answerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Dạ co giãn nhẹ ạ.\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/seller/qna/answers/{answerId}", answerId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/seller/qna/questions/{questionId}/close", questionId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/seller/qna/questions/{questionId}/reopen", questionId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listsSellerQuestionsWithFilters() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var from = LocalDate.of(2026, 11, 1);
        var to = LocalDate.of(2026, 11, 15);

        var pageResponse = new PageResponse<>(
            List.of(sellerQuestionResponse(questionId, productId)),
            1L,
            0,
            20
        );

        when(qnaService.listForShop(
            eq(userId),
            eq(shopId),
            eq(ProductQuestionStatus.UNANSWERED),
            eq(productId),
            eq(from),
            eq(to),
            eq("co gian"),
            eq(ProductQuestionSort.NEWEST),
            eq(0),
            eq(20)
        )).thenReturn(pageResponse);

        mockMvc.perform(get("/api/seller/qna/questions")
                .param("shopId", shopId.toString())
                .param("status", "UNANSWERED")
                .param("productId", productId.toString())
                .param("from", "2026-11-01")
                .param("to", "2026-11-15")
                .param("q", "co gian")
                .param("sort", "NEWEST")
                .param("page", "0")
                .param("size", "20")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(questionId.toString()))
            .andExpect(jsonPath("$.items[0].productName").value("Áo thun cotton"))
            .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void getsSellerQuestionById() throws Exception {
        var userId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        when(qnaService.getShopQuestion(userId, questionId))
            .thenReturn(sellerQuestionResponse(questionId, productId));

        mockMvc.perform(get("/api/seller/qna/questions/{questionId}", questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(questionId.toString()))
            .andExpect(jsonPath("$.code").value("QA-TEST02"));
    }

    @Test
    void listsSellerQnaProducts() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        when(qnaService.listShopQnaProducts(userId, shopId))
            .thenReturn(List.of(new SellerQnaProductOptionResponse(productId, "Áo sơ mi lụa", 5L)));

        mockMvc.perform(get("/api/seller/qna/products")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(productId.toString()))
            .andExpect(jsonPath("$[0].name").value("Áo sơ mi lụa"))
            .andExpect(jsonPath("$[0].questionCount").value(5));
    }

    @Test
    void getsSellerQnaStats() throws Exception {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();

        when(qnaService.shopQnaStats(userId, shopId))
            .thenReturn(new SellerQnaStatsResponse(10L, 2L, 7L, 1L, 70.0));

        mockMvc.perform(get("/api/seller/qna/stats")
                .param("shopId", shopId.toString())
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalQuestions").value(10))
            .andExpect(jsonPath("$.unansweredCount").value(2))
            .andExpect(jsonPath("$.answeredCount").value(7))
            .andExpect(jsonPath("$.closedCount").value(1))
            .andExpect(jsonPath("$.responseRate").value(70.0));
    }

    @Test
    void createSellerAnswerCreatesAndValidates() throws Exception {
        var userId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        when(qnaService.createShopAnswer(eq(userId), eq(questionId), any(CreateAnswerRequest.class)))
            .thenReturn(sellerQuestionResponse(questionId, productId));

        mockMvc.perform(post("/api/seller/qna/questions/{questionId}/answers", questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Dạ vải cotton 100% thấm hút mồ hôi rất tốt ạ.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(questionId.toString()));

        mockMvc.perform(post("/api/seller/qna/questions/{questionId}/answers", questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"   \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateSellerAnswerUpdatesAndValidates() throws Exception {
        var userId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerId = UUID.randomUUID();

        when(qnaService.updateShopAnswer(eq(userId), eq(answerId), any(UpdateAnswerRequest.class)))
            .thenReturn(answerResponse(questionId, answerId, "Nội dung phản hồi cập nhật"));

        mockMvc.perform(put("/api/seller/qna/answers/{answerId}", answerId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Nội dung phản hồi cập nhật\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(answerId.toString()))
            .andExpect(jsonPath("$.content").value("Nội dung phản hồi cập nhật"));

        mockMvc.perform(put("/api/seller/qna/answers/{answerId}", answerId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"   \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSellerAnswerDeletes() throws Exception {
        var userId = UUID.randomUUID();
        var answerId = UUID.randomUUID();

        mockMvc.perform(delete("/api/seller/qna/answers/{answerId}", answerId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(qnaService).deleteShopAnswer(userId, answerId);
    }

    @Test
    void closeSellerQuestionCloses() throws Exception {
        var userId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        when(qnaService.closeQuestion(userId, questionId))
            .thenReturn(sellerQuestionResponse(questionId, productId));

        mockMvc.perform(patch("/api/seller/qna/questions/{questionId}/close", questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(questionId.toString()));

        verify(qnaService).closeQuestion(userId, questionId);
    }

    @Test
    void reopenSellerQuestionReopens() throws Exception {
        var userId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        when(qnaService.reopenQuestion(userId, questionId))
            .thenReturn(sellerQuestionResponse(questionId, productId));

        mockMvc.perform(patch("/api/seller/qna/questions/{questionId}/reopen", questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(questionId.toString()));

        verify(qnaService).reopenQuestion(userId, questionId);
    }
}
