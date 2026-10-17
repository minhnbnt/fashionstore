package com.minhnbnt.fashionstore.features.chat;

import com.minhnbnt.fashionstore.dto.chat.ChatConversationResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatMessageResponse;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.features.chat.repository.ChatConversationRepository;
import com.minhnbnt.fashionstore.features.chat.repository.ChatMessageRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.ChatConversation;
import com.minhnbnt.fashionstore.model.ChatMessage;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversations;

    private final ChatMessageRepository messages;

    private final ShopRepository shops;

    private final ShopMemberRepository members;

    private final ShopAccessChecker access;

    @Transactional
    public ChatConversationResponse startConversation(@NonNull UUID buyerId, @NonNull UUID shopId) {
        var shop = shops.findById(shopId).orElse(null);
        if (shop == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
        }

        var existing = conversations.findByShopIdAndBuyerId(shopId, buyerId).orElse(null);
        if (existing != null) {
            return toConversationResponse(existing, false);
        }

        var created = conversations.save(ChatConversation.create(shopId, buyerId));
        return toConversationResponse(created, false);
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> listSellerConversations(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        boolean onlyUnread
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        var list = conversations.findByShopIdOrderByLastMessageAtDesc(shopId);
        if (onlyUnread) {
            list = list.stream().filter(c -> c.getSellerUnreadCount() > 0).toList();
        }

        return list.stream()
            .map(c -> toConversationResponse(c, true))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatConversationResponse> listBuyerConversations(@NonNull UUID buyerId) {
        return conversations.findByBuyerIdOrderByLastMessageAtDesc(buyerId).stream()
            .map(c -> toConversationResponse(c, false))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(@NonNull UUID userId, @NonNull UUID conversationId) {
        var conv = conversations.findById(conversationId).orElse(null);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        verifyParticipant(userId, conv);

        return messages.findByConversationIdOrderBySentAtAsc(conversationId).stream()
            .map(ChatService::toMessageResponse)
            .toList();
    }

    @Transactional
    public ChatMessageResponse sendMessage(
        @NonNull UUID userId,
        @NonNull UUID conversationId,
        @NonNull SendMessageRequest request
    ) {
        var conv = conversations.findById(conversationId).orElse(null);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        var isBuyer = conv.getBuyerId().equals(userId);
        var isSeller = members.findRole(conv.getShopId(), userId).isPresent();

        if (!isBuyer && !isSeller) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
        }

        var senderType = "SELLER";
        if (isBuyer) {
            senderType = "BUYER";
        }

        var msg = ChatMessage.of(
            conversationId,
            userId,
            senderType,
            request.content(),
            request.imageKey()
        );
        messages.save(msg);

        conv.recordNewMessage(request.content(), isBuyer);
        conversations.save(conv);

        // Auto reply if buyer sent message and shop has auto reply text
        if (isBuyer) {
            var shop = shops.findById(conv.getShopId()).orElse(null);
            if (shop != null && shop.getAutoReplyText() != null && !shop.getAutoReplyText().isBlank()) {
                var autoMsg = ChatMessage.of(
                    conversationId,
                    shop.getId(),
                    "SELLER",
                    shop.getAutoReplyText(),
                    null
                );
                messages.save(autoMsg);
                conv.recordNewMessage(shop.getAutoReplyText(), false);
                conversations.save(conv);
            }
        }

        return toMessageResponse(msg);
    }

    @Transactional
    public void markRead(@NonNull UUID userId, @NonNull UUID conversationId) {
        var conv = conversations.findById(conversationId).orElse(null);
        if (conv == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        var isBuyer = conv.getBuyerId().equals(userId);
        var isSeller = members.findRole(conv.getShopId(), userId).isPresent();

        if (isBuyer) {
            conv.markReadByBuyer();
            conversations.save(conv);
            return;
        }

        if (isSeller) {
            conv.markReadBySeller();
            conversations.save(conv);
        }
    }

    private void verifyParticipant(UUID userId, ChatConversation conv) {
        var isBuyer = conv.getBuyerId().equals(userId);
        var isSeller = members.findRole(conv.getShopId(), userId).isPresent();
        if (!isBuyer && !isSeller) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
        }
    }

    private static ChatConversationResponse toConversationResponse(ChatConversation c, boolean forSeller) {
        var unread = c.getBuyerUnreadCount();
        if (forSeller) {
            unread = c.getSellerUnreadCount();
        }

        return new ChatConversationResponse(
            c.getId(),
            c.getShopId(),
            c.getBuyerId(),
            c.getLastMessageText(),
            c.getLastMessageAt(),
            unread,
            c.getCreatedAt()
        );
    }

    private static ChatMessageResponse toMessageResponse(ChatMessage m) {
        return new ChatMessageResponse(
            m.getId(),
            m.getConversationId(),
            m.getSenderId(),
            m.getSenderType(),
            m.getContent(),
            m.getImageKey(),
            m.getSentAt(),
            m.isRead()
        );
    }
}
