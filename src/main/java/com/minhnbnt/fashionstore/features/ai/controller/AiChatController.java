package com.minhnbnt.fashionstore.features.ai.controller;

import com.minhnbnt.fashionstore.dto.ai.AiConversationResponse;
import com.minhnbnt.fashionstore.dto.ai.AiMessageResponse;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.features.ai.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai/conversations")
@RequiredArgsConstructor
@Tag(name = "AI Chat", description = "Quản lý lịch sử và tin nhắn với trợ lý AI")
public class AiChatController {

    private final AiChatService aiChatService;

    @GetMapping
    @Operation(summary = "Danh sách cuộc trò chuyện AI của người dùng")
    @SecurityRequirement(name = "bearerAuth")
    public List<AiConversationResponse> listConversations(@AuthenticationPrincipal Jwt jwt) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.listConversations(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo mới cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public AiConversationResponse createConversation(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody(required = false) CreateAiConversationRequest request
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.createConversation(userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xóa cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteConversation(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID id
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        aiChatService.deleteConversation(userId, id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Lấy danh sách tin nhắn của cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public List<AiMessageResponse> listMessages(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID id
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.listMessages(userId, id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lưu tin nhắn vào cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public AiMessageResponse saveMessage(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID id,
        @Valid @RequestBody SaveAiMessageRequest request
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.saveMessage(userId, id, request);
    }
}
