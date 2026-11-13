package com.minhnbnt.fashionstore.features.ai.controller;

import com.minhnbnt.fashionstore.dto.ai.AiConversationResponse;
import com.minhnbnt.fashionstore.dto.ai.AiMessageResponse;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.features.ai.AiChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
    @Operation(
        summary = "Danh sách cuộc trò chuyện AI của người dùng",
        description = "Trả danh sách các cuộc trò chuyện với trợ lý AI thuộc tài khoản hiện tại, sắp xếp theo lần cập nhật mới nhất."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách cuộc trò chuyện AI")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<AiConversationResponse> listConversations(@AuthenticationPrincipal Jwt jwt) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.listConversations(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Tạo mới cuộc trò chuyện AI",
        description = "Khởi tạo một cuộc trò chuyện AI mới; nếu bỏ trống title sẽ dùng tiêu đề mặc định."
    )
    @ApiResponse(responseCode = "201", description = "Cuộc trò chuyện AI vừa tạo")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
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
    @Operation(
        summary = "Xóa cuộc trò chuyện AI",
        description = "Xóa cuộc trò chuyện AI và toàn bộ tin nhắn thuộc cuộc trò chuyện đó nếu thuộc quyền sở hữu người dùng."
    )
    @ApiResponse(responseCode = "204", description = "Đã xóa cuộc trò chuyện")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public void deleteConversation(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id cuộc trò chuyện AI") @PathVariable UUID id
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        aiChatService.deleteConversation(userId, id);
    }

    @GetMapping("/{id}/messages")
    @Operation(
        summary = "Lấy danh sách tin nhắn của cuộc trò chuyện AI",
        description = "Trả về lịch sử tin nhắn theo thứ tự thời gian tăng dần trong một cuộc trò chuyện AI."
    )
    @ApiResponse(responseCode = "200", description = "Danh sách tin nhắn")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public List<AiMessageResponse> listMessages(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id cuộc trò chuyện AI") @PathVariable UUID id
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.listMessages(userId, id);
    }

    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Lưu tin nhắn vào cuộc trò chuyện AI",
        description = "Lưu tin nhắn người dùng hoặc trợ lý AI (kèm metadata parts nếu có) vào cuộc trò chuyện."
    )
    @ApiResponse(responseCode = "201", description = "Tin nhắn đã lưu")
    @ApiResponse(responseCode = "400", description = "Dữ liệu tin nhắn không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Không tìm thấy cuộc trò chuyện AI")
    @SecurityRequirement(name = "bearerAuth")
    public AiMessageResponse saveMessage(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id cuộc trò chuyện AI") @PathVariable UUID id,
        @Valid @RequestBody SaveAiMessageRequest request
    ) {
        var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
        return aiChatService.saveMessage(userId, id, request);
    }
}
