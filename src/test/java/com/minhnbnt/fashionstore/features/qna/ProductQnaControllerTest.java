package com.minhnbnt.fashionstore.features.qna;

import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.qna.CreateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.CreateQuestionRequest;
import com.minhnbnt.fashionstore.dto.qna.ProductAnswerResponse;
import com.minhnbnt.fashionstore.dto.qna.ProductQuestionPageResponse;
import com.minhnbnt.fashionstore.dto.qna.ProductQuestionResponse;
import com.minhnbnt.fashionstore.dto.qna.UpdateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.UpdateQuestionRequest;
import com.minhnbnt.fashionstore.features.qna.controller.ProductQnaController;
import com.minhnbnt.fashionstore.model.ProductAnswerRole;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductQnaController.class)
@Import(SecurityConfig.class)
class ProductQnaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductQnaService qnaService;

    private static ProductQuestionResponse questionResponse(UUID productId, UUID questionId, String content) {
        var now = Instant.now();
        return new ProductQuestionResponse(
            questionId,
            "QA-TEST01",
            productId,
            "Minh",
            content,
            ProductQuestionStatus.UNANSWERED,
            true,
            true,
            true,
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
            "Lan",
            ProductAnswerRole.VERIFIED_BUYER,
            true,
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
    void listsQuestionsPubliclyWithoutAuthentication() throws Exception {
        var productId = UUID.randomUUID();
        var qId = UUID.randomUUID();
        var pageResponse = new ProductQuestionPageResponse(
            List.of(questionResponse(productId, qId, "Size L con khong shop?")),
            1L,
            0,
            10,
            false,
            1000
        );

        when(qnaService.listForProduct(eq(productId), eq(null), eq(null), eq(0), eq(10)))
            .thenReturn(pageResponse);

        mockMvc.perform(get("/api/products/{productId}/questions", productId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(qId.toString()))
            .andExpect(jsonPath("$.items[0].content").value("Size L con khong shop?"))
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.canAnswerAsBuyer").value(false));
    }

    @Test
    void listsQuestionsWithJwtAndQueryParameters() throws Exception {
        var productId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var pageResponse = new ProductQuestionPageResponse(
            List.of(),
            0L,
            1,
            5,
            true,
            1000
        );

        when(qnaService.listForProduct(eq(productId), eq(userId), eq("chat lieu"), eq(1), eq(5)))
            .thenReturn(pageResponse);

        mockMvc.perform(get("/api/products/{productId}/questions", productId)
                .param("q", "chat lieu")
                .param("page", "1")
                .param("size", "5")
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.canAnswerAsBuyer").value(true));
    }

    @Test
    void createQuestionRejectsUnauthenticated() throws Exception {
        var productId = UUID.randomUUID();

        mockMvc.perform(post("/api/products/{productId}/questions", productId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Sản phẩm còn màu đen không?\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void createQuestionCreatesWithJwtAndValidBody() throws Exception {
        var productId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var questionId = UUID.randomUUID();

        when(qnaService.createQuestion(eq(userId), eq(productId), any(CreateQuestionRequest.class)))
            .thenReturn(questionResponse(productId, questionId, "Sản phẩm còn màu đen không?"));

        mockMvc.perform(post("/api/products/{productId}/questions", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Sản phẩm còn màu đen không?\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(questionId.toString()))
            .andExpect(jsonPath("$.content").value("Sản phẩm còn màu đen không?"));
    }

    @Test
    void createQuestionValidatesBlankContent() throws Exception {
        var productId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/api/products/{productId}/questions", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"   \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createQuestionValidatesContentExceedingMaxLength() throws Exception {
        var productId = UUID.randomUUID();
        var userId = UUID.randomUUID();
        var tooLong = "a".repeat(1001);

        mockMvc.perform(post("/api/products/{productId}/questions", productId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"" + tooLong + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateQuestionRequiresAuthAndUpdates() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(put("/api/products/{productId}/questions/{questionId}", productId, questionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Nội dung cập nhật\"}"))
            .andExpect(status().isUnauthorized());

        when(qnaService.updateQuestion(eq(userId), eq(productId), eq(questionId), any(UpdateQuestionRequest.class)))
            .thenReturn(questionResponse(productId, questionId, "Nội dung cập nhật"));

        mockMvc.perform(put("/api/products/{productId}/questions/{questionId}", productId, questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Nội dung cập nhật\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(questionId.toString()))
            .andExpect(jsonPath("$.content").value("Nội dung cập nhật"));
    }

    @Test
    void updateQuestionValidatesBlankContent() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(put("/api/products/{productId}/questions/{questionId}", productId, questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteQuestionRequiresAuthAndDeletes() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/products/{productId}/questions/{questionId}", productId, questionId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/products/{productId}/questions/{questionId}", productId, questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(qnaService).deleteQuestion(userId, productId, questionId);
    }

    @Test
    void createAnswerRequiresAuthAndCreates() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/api/products/{productId}/questions/{questionId}/answers", productId, questionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Mình đã mua và thấy form chuẩn nhé.\"}"))
            .andExpect(status().isUnauthorized());

        when(qnaService.createBuyerAnswer(eq(userId), eq(productId), eq(questionId), any(CreateAnswerRequest.class)))
            .thenReturn(answerResponse(questionId, answerId, "Mình đã mua và thấy form chuẩn nhé."));

        mockMvc.perform(post("/api/products/{productId}/questions/{questionId}/answers", productId, questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Mình đã mua và thấy form chuẩn nhé.\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(answerId.toString()))
            .andExpect(jsonPath("$.verifiedBuyer").value(true));
    }

    @Test
    void createAnswerValidatesBlankContent() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(post("/api/products/{productId}/questions/{questionId}/answers", productId, questionId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"   \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateAnswerRequiresAuthAndUpdates() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(put("/api/products/{productId}/questions/{questionId}/answers/{answerId}",
                productId, questionId, answerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Nội dung cập nhật phản hồi\"}"))
            .andExpect(status().isUnauthorized());

        when(qnaService.updateBuyerAnswer(
            eq(userId), eq(productId), eq(questionId), eq(answerId), any(UpdateAnswerRequest.class)))
            .thenReturn(answerResponse(questionId, answerId, "Nội dung cập nhật phản hồi"));

        mockMvc.perform(put("/api/products/{productId}/questions/{questionId}/answers/{answerId}",
                productId, questionId, answerId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\": \"Nội dung cập nhật phản hồi\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(answerId.toString()))
            .andExpect(jsonPath("$.content").value("Nội dung cập nhật phản hồi"));
    }

    @Test
    void deleteAnswerRequiresAuthAndDeletes() throws Exception {
        var productId = UUID.randomUUID();
        var questionId = UUID.randomUUID();
        var answerId = UUID.randomUUID();
        var userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/products/{productId}/questions/{questionId}/answers/{answerId}",
                productId, questionId, answerId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/products/{productId}/questions/{questionId}/answers/{answerId}",
                productId, questionId, answerId)
                .with(jwt().jwt(builder -> builder.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(qnaService).deleteBuyerAnswer(userId, productId, questionId, answerId);
    }
}
