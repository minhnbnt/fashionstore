package com.minhnbnt.fashionstore.features.chat;

import com.minhnbnt.fashionstore.dto.chat.ChatConversationResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatMessageResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatReadEvent;
import com.minhnbnt.fashionstore.dto.chat.ConversationEvent;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.features.chat.repository.ChatConversationRepository;
import com.minhnbnt.fashionstore.features.chat.repository.ChatMessageRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.infrastructure.realtime.ChatRealtimePublisher;
import com.minhnbnt.fashionstore.model.ChatConversation;
import com.minhnbnt.fashionstore.model.ChatMessage;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversations;

    private final ChatMessageRepository messages;

    private final ShopRepository shops;

    private final ShopMemberRepository members;

    private final ShopAccessChecker access;

    private final ChatRealtimePublisher realtime;

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

        var recipients = realtimeRecipients(conv);

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

        realtime.publishChatMessage(recipients, toMessageResponse(msg));
        realtime.publishConversationEvent(
            recipients,
            new ConversationEvent(conversationId, ConversationEvent.Type.NEW_MESSAGE)
        );

        // Auto reply if buyer sent message and shop has auto reply text
        if (isBuyer) {
            broadcastAutoReply(conv, recipients);
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

            var event = new ChatReadEvent(conversationId, userId, Instant.now());
            realtime.publishReadReceipt(
                Set.copyOf(members.findActiveUserIdsByShop(conv.getShopId())),
                event
            );
            return;
        }

        if (isSeller) {
            conv.markReadBySeller();
            conversations.save(conv);

            var event = new ChatReadEvent(conversationId, userId, Instant.now());
            realtime.publishReadReceipt(Set.of(conv.getBuyerId()), event);
        }
    }

    /**
     * Tập người nhận push realtime của một hội thoại: buyer + mọi thành viên ACTIVE của shop.
     * Người gửi cũng nằm trong tập (nhận echo để cập nhật UI không cần refetch).
     */
    private Set<UUID> realtimeRecipients(ChatConversation conv) {
        var recipientIds = new HashSet<UUID>();
        recipientIds.add(conv.getBuyerId());
        recipientIds.addAll(members.findActiveUserIdsByShop(conv.getShopId()));
        return recipientIds;
    }

    /**
     * Auto reply của shop khi buyer nhắn tin: lưu tin nhắn của owner rồi broadcast như
     * một tin nhắn SELLER bình thường. Không cấu hình auto reply / không tìm thấy owner
     * thì bỏ qua lặng lẽ.
     */
    private void broadcastAutoReply(ChatConversation conv, Set<UUID> recipients) {
        var shop = shops.findById(conv.getShopId()).orElse(null);
        if (shop == null) {
            return;
        }

        var autoReplyText = shop.getAutoReplyText();
        if (!StringUtils.hasText(autoReplyText)) {
            return;
        }

        var ownerId = members.findOwner(conv.getShopId()).orElse(null);
        if (ownerId == null) {
            return;
        }

        var autoMsg = ChatMessage.of(
            conv.getId(),
            ownerId,
            "SELLER",
            autoReplyText,
            null
        );
        messages.save(autoMsg);
        conv.recordNewMessage(autoReplyText, false);
        conversations.save(conv);

        realtime.publishChatMessage(recipients, toMessageResponse(autoMsg));
        realtime.publishConversationEvent(
            recipients,
            new ConversationEvent(conv.getId(), ConversationEvent.Type.NEW_MESSAGE)
        );
    }

    private void verifyParticipant(UUID userId, ChatConversation conv) {
        var isBuyer = conv.getBuyerId().equals(userId);
        var isSeller = members.findRole(conv.getShopId(), userId).isPresent();
        if (!isBuyer && !isSeller) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
        }
    }

    private ChatConversationResponse toConversationResponse(ChatConversation c, boolean forSeller) {
        var unread = c.getBuyerUnreadCount();
        if (forSeller) {
            unread = c.getSellerUnreadCount();
        }

        var shopName = shops.findById(c.getShopId()).map(Shop::getName).orElse(null);

        return new ChatConversationResponse(
            c.getId(),
            c.getShopId(),
            c.getBuyerId(),
            shopName,
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
