package com.minhnbnt.fashionstore.features.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minhnbnt.fashionstore.config.SecurityConfig;
import com.minhnbnt.fashionstore.dto.ai.AiConversationResponse;
import com.minhnbnt.fashionstore.dto.ai.AiMessageResponse;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.features.ai.controller.AiChatController;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiChatController.class)
@Import(SecurityConfig.class)
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AiChatService aiChatService;

    @Test
    void listConversations_whenAuthenticated_returns200() throws Exception {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var now = Instant.now();
        var response = new AiConversationResponse(convId, "Tư vấn chọn size", now, now);

        when(aiChatService.listConversations(userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/ai/conversations")
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(convId.toString()))
            .andExpect(jsonPath("$[0].title").value("Tư vấn chọn size"));
    }

    @Test
    void createConversation_whenAuthenticated_returns201() throws Exception {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var now = Instant.now();
        var response = new AiConversationResponse(convId, "Cuộc trò chuyện mới", now, now);

        when(aiChatService.createConversation(eq(userId), any())).thenReturn(response);

        mockMvc.perform(post("/api/ai/conversations")
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Cuộc trò chuyện mới"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(convId.toString()));
    }

    @Test
    void deleteConversation_whenAuthenticated_returns204() throws Exception {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();

        mockMvc.perform(delete("/api/ai/conversations/{id}", convId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isNoContent());

        verify(aiChatService).deleteConversation(userId, convId);
    }

    @Test
    void listMessages_whenAuthenticated_returns200() throws Exception {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var msgId = UUID.randomUUID();
        var now = Instant.now();
        var response = new AiMessageResponse(msgId, convId, "user", "Xin chào", null, now);

        when(aiChatService.listMessages(userId, convId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/ai/conversations/{id}/messages", convId)
                .with(jwt().jwt(j -> j.subject(userId.toString()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].content").value("Xin chào"));
    }

    @Test
    void saveMessage_whenAuthenticated_returns201() throws Exception {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var msgId = UUID.randomUUID();
        var now = Instant.now();
        var request = new SaveAiMessageRequest("assistant", "Tôi có thể giúp gì cho bạn?", "{\"parts\":[]}");
        var response = new AiMessageResponse(msgId, convId, "assistant", "Tôi có thể giúp gì cho bạn?", "{\"parts\":[]}", now);

        when(aiChatService.saveMessage(eq(userId), eq(convId), any())).thenReturn(response);

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", convId)
                .with(jwt().jwt(j -> j.subject(userId.toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.content").value("Tôi có thể giúp gì cho bạn?"));
    }

    @Test
    void listConversations_whenUnauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/conversations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void saveMessageWithInvalidRole_returns400() throws Exception {
        mockMvc.perform(post("/api/ai/conversations/{id}/messages", UUID.randomUUID())
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"hacker\",\"content\":\"xin chao\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createConversationWithTooLongTitle_returns400() throws Exception {
        var longTitle = "a".repeat(256);

        mockMvc.perform(post("/api/ai/conversations")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + longTitle + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
