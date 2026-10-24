package com.minhnbnt.fashionstore.features.chat.controller;

import com.minhnbnt.fashionstore.dto.chat.ChatConversationResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatMessageResponse;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.dto.chat.StartConversationRequest;
import com.minhnbnt.fashionstore.features.chat.ChatService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Tin nhắn trực tiếp giữa khách hàng và shop")
public class ChatController {

    private final ChatService chat;

    @PostMapping("/api/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Bắt đầu hội thoại với shop", description = "Buyer tạo hoặc lấy hội thoại hiện có với shop.")
    @ApiResponse(responseCode = "201", description = "Thông tin hội thoại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Shop không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ChatConversationResponse startConversation(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody StartConversationRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return chat.startConversation(buyerId, request.shopId());
    }

    @GetMapping("/api/conversations/mine")
    @Operation(summary = "Danh sách hội thoại của tôi (Buyer)")
    @ApiResponse(responseCode = "200", description = "Danh sách hội thoại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @SecurityRequirement(name = "bearerAuth")
    public List<ChatConversationResponse> myConversations(@AuthenticationPrincipal Jwt jwt) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var buyerId = UUID.fromString(subject);

        return chat.listBuyerConversations(buyerId);
    }

    @GetMapping("/api/seller/conversations")
    @Operation(summary = "Danh sách hội thoại của shop (Seller)", description = "Lọc các hội thoại có tin nhắn chưa đọc (unread=true).")
    @ApiResponse(responseCode = "200", description = "Danh sách hội thoại")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải thành viên shop hoặc thiếu quyền CHAT_ACCESS")
    @SecurityRequirement(name = "bearerAuth")
    public List<ChatConversationResponse> sellerConversations(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id shop") @RequestParam UUID shopId,
        @Parameter(description = "Chỉ xem hội thoại chưa đọc") @RequestParam(defaultValue = "false") boolean unread
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return chat.listSellerConversations(userId, shopId, unread);
    }

    @GetMapping("/api/conversations/{id}/messages")
    @Operation(summary = "Xem tin nhắn trong hội thoại", description = "Người tham gia hội thoại (Buyer hoặc nhân viên shop) xem lịch sử chat.")
    @ApiResponse(responseCode = "200", description = "Danh sách tin nhắn theo thứ tự thời gian")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải người tham gia hội thoại hoặc thiếu quyền CHAT_ACCESS")
    @ApiResponse(responseCode = "404", description = "Hội thoại không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public List<ChatMessageResponse> messages(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id hội thoại") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return chat.getMessages(userId, id);
    }

    @PostMapping("/api/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Gửi tin nhắn", description = "Gửi tin nhắn văn bản kèm ảnh tùy chọn. Tự động kích hoạt phản hồi tự động nếu shop cài đặt.")
    @ApiResponse(responseCode = "201", description = "Tin nhắn vừa gửi")
    @ApiResponse(responseCode = "400", description = "Body không hợp lệ")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "403", description = "Không phải người tham gia hội thoại hoặc thiếu quyền CHAT_ACCESS")
    @ApiResponse(responseCode = "404", description = "Hội thoại không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public ChatMessageResponse sendMessage(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id hội thoại") @PathVariable UUID id,
        @Valid @RequestBody SendMessageRequest request
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        return chat.sendMessage(userId, id, request);
    }

    @PatchMapping("/api/conversations/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đánh dấu đã đọc hội thoại")
    @ApiResponse(responseCode = "204", description = "Đã đánh dấu đọc")
    @ApiResponse(responseCode = "401", description = "Thiếu hoặc sai JWT")
    @ApiResponse(responseCode = "404", description = "Hội thoại không tồn tại")
    @SecurityRequirement(name = "bearerAuth")
    public void markRead(
        @AuthenticationPrincipal Jwt jwt,
        @Parameter(description = "Id hội thoại") @PathVariable UUID id
    ) {
        var subject = Objects.requireNonNull(jwt.getSubject());
        var userId = UUID.fromString(subject);

        chat.markRead(userId, id);
    }
}
