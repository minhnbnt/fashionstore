package com.minhnbnt.fashionstore.features.chat;

import com.minhnbnt.fashionstore.dto.chat.ChatConversationResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatMessageResponse;
import com.minhnbnt.fashionstore.dto.chat.ChatReadEvent;
import com.minhnbnt.fashionstore.dto.chat.ConversationEvent;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.chat.repository.ChatConversationRepository;
import com.minhnbnt.fashionstore.features.chat.repository.ChatMessageRepository;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.infrastructure.realtime.ChatRealtimePublisher;
import com.minhnbnt.fashionstore.model.ChatConversation;
import com.minhnbnt.fashionstore.model.ChatMessage;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Pattern PRODUCT_LINK_PATTERN =
        Pattern.compile("(?:https?://[^\\s/]+)?/products?/([a-zA-Z0-9_-]+)");

    private final ChatConversationRepository conversations;

    private final ChatMessageRepository messages;

    private final ProductRepository products;

    private final ShopService shops;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final NotificationService notifications;

    private final ChatRealtimePublisher realtime;

    private final UploadRepository uploads;

    private final S3UrlService s3Urls;

    @Transactional
    public ChatConversationResponse startConversation(@NonNull UUID buyerId, @NonNull UUID shopId) {
        var shop = shops.findEntityById(shopId).orElse(null);
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
        access.requireShopPermission(userId, shopId, ShopPermission.CHAT_ACCESS);

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
            .map(this::toMessageResponse)
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

        var isSeller = shops.isMember(conv.getShopId(), userId);
        if (isSeller) {
            access.requireShopPermission(userId, conv.getShopId(), ShopPermission.CHAT_ACCESS);
        } else if (!conv.getBuyerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
        }

        var senderType = "SELLER";
        if (!isSeller) {
            senderType = "BUYER";
        }

        var recipients = realtimeRecipients(conv);

        var textContent = "";
        if (request.content() != null && !request.content().isBlank()) {
            textContent = request.content().trim();
        }
        validateProductLinks(conv.getShopId(), textContent);
        validateImageUpload(request.imageUploadId());
        var previewText = buildPreviewText(textContent, request.imageUploadId());

        var msg = ChatMessage.of(
            conversationId,
            userId,
            senderType,
            textContent,
            request.imageUploadId()
        );
        messages.save(msg);

        conv.recordNewMessage(previewText, !isSeller);
        conversations.save(conv);

        if (isSeller) {
            var chatDetail = "Phản hồi tin nhắn trong hội thoại #" + conv.getId();
            var preview = previewText;
            if (preview.length() > 50) {
                preview = preview.substring(0, 50) + "...";
            }
            if (!preview.isBlank()) {
                chatDetail = chatDetail + ": \"" + preview + "\"";
            }
            audit.record(conv.getShopId(), userId, "CHAT_REPLY", "CONVERSATION", conv.getId(), chatDetail);

            var shopName = shops.getShopName(conv.getShopId());

            notifications.create(
                Set.of(conv.getBuyerId()),
                NotificationCategory.ACTIVITY,
                "CHAT_MESSAGE",
                "Tin nhắn mới từ " + shopName,
                previewText,
                null,
                msg.getId()
            );
        }

        realtime.publishChatMessage(recipients, toMessageResponse(msg));
        realtime.publishConversationEvent(
            recipients,
            new ConversationEvent(conversationId, ConversationEvent.Type.NEW_MESSAGE)
        );

        // Auto reply if buyer sent message and shop has auto reply text
        if (!isSeller) {
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

        var isSeller = shops.isMember(conv.getShopId(), userId);
        if (isSeller) {
            access.requireShopPermission(userId, conv.getShopId(), ShopPermission.CHAT_ACCESS);
            conv.markReadBySeller();
            conversations.save(conv);

            var event = new ChatReadEvent(conversationId, userId, Instant.now());
            realtime.publishReadReceipt(Set.of(conv.getBuyerId()), event);
            return;
        }

        if (conv.getBuyerId().equals(userId)) {
            conv.markReadByBuyer();
            conversations.save(conv);

            var event = new ChatReadEvent(conversationId, userId, Instant.now());
            realtime.publishReadReceipt(
                Set.copyOf(shops.findActiveUserIdsByShop(conv.getShopId())),
                event
            );
        }
    }

    /**
     * Tập người nhận push realtime của một hội thoại: buyer + mọi thành viên ACTIVE của shop.
     * Người gửi cũng nằm trong tập (nhận echo để cập nhật UI không cần refetch).
     */
    private Set<UUID> realtimeRecipients(ChatConversation conv) {
        var recipientIds = new HashSet<UUID>();
        recipientIds.add(conv.getBuyerId());
        recipientIds.addAll(shops.findActiveUserIdsByShop(conv.getShopId()));
        return recipientIds;
    }

    /**
     * Auto reply của shop khi buyer nhắn tin: lưu tin nhắn của owner rồi broadcast như
     * một tin nhắn SELLER bình thường. Không cấu hình auto reply / không tìm thấy owner
     * thì bỏ qua lặng lẽ.
     */
    private void broadcastAutoReply(ChatConversation conv, Set<UUID> recipients) {
        var shop = shops.findEntityById(conv.getShopId()).orElse(null);
        if (shop == null) {
            return;
        }

        var autoReplyText = shop.getAutoReplyText();
        if (!StringUtils.hasText(autoReplyText)) {
            return;
        }

        var ownerId = shops.ownerOf(conv.getShopId());
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

        var shopName = shops.getShopName(conv.getShopId());

        notifications.create(
            Set.of(conv.getBuyerId()),
            NotificationCategory.ACTIVITY,
            "CHAT_MESSAGE",
            "Tin nhắn mới từ " + shopName,
            autoReplyText,
            null,
            autoMsg.getId()
        );
    }

    private void verifyParticipant(UUID userId, ChatConversation conv) {
        var isSeller = shops.isMember(conv.getShopId(), userId);
        if (isSeller) {
            access.requireShopPermission(userId, conv.getShopId(), ShopPermission.CHAT_ACCESS);
            return;
        }

        if (!conv.getBuyerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant in this conversation");
        }
    }

    private ChatConversationResponse toConversationResponse(ChatConversation c, boolean forSeller) {
        var unread = c.getBuyerUnreadCount();
        if (forSeller) {
            unread = c.getSellerUnreadCount();
        }

        var shopName = shops.getShopName(c.getShopId());

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

    private void validateProductLinks(UUID conversationShopId, String textContent) {
        if (textContent.isBlank()) {
            return;
        }
        var matcher = PRODUCT_LINK_PATTERN.matcher(textContent);
        while (matcher.find()) {
            var ref = matcher.group(1);
            if (ref == null || ref.isBlank()) {
                continue;
            }
            var productId = tryParseUuid(ref);
            if (productId != null) {
                var product = products.findById(productId).orElse(null);
                if (product != null && !product.getShopId().equals(conversationShopId)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product does not belong to this shop");
                }
                continue;
            }
            var matchedBySlug = products.findBySlug(ref);
            if (!matchedBySlug.isEmpty()
                && matchedBySlug.stream().noneMatch(p -> p.getShopId().equals(conversationShopId))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product does not belong to this shop");
            }
        }
    }

    private static @Nullable UUID tryParseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String buildPreviewText(String textContent, @Nullable UUID imageUploadId) {
        if (textContent.isBlank()) {
            if (imageUploadId != null) {
                return "[Hình ảnh]";
            }
            return "";
        }
        if (PRODUCT_LINK_PATTERN.matcher(textContent).find()) {
            var stripped = PRODUCT_LINK_PATTERN.matcher(textContent).replaceAll("").trim();
            if (stripped.isBlank()) {
                return "[Sản phẩm]";
            }
            return stripped;
        }
        return textContent;
    }

    private ChatMessageResponse toMessageResponse(ChatMessage m) {
        return new ChatMessageResponse(
            m.getId(),
            m.getConversationId(),
            m.getSenderId(),
            m.getSenderType(),
            m.getContent(),
            resolveImageUrl(m.getImageUploadId()),
            m.getSentAt(),
            m.isRead()
        );
    }

    private @Nullable String resolveImageUrl(@Nullable UUID uploadId) {

        if (uploadId == null) {
            return null;
        }
        // FK + validate phía request bảo đảm upload tồn tại; thiếu là lỗi dữ liệu, fail loud.
        return s3Urls.publicUrl(uploads.findById(uploadId).orElseThrow().getS3Key());
    }

    /**
     * Chỉ nhận ảnh trình duyệt render được. Bỏ {@code image/svg+xml}: bucket public read,
     * mở thẳng URL thì script trong file chạy được (cùng lý do với avatar, ảnh sản phẩm).
     */
    private static boolean isSupportedImage(String contentType) {

        return contentType.startsWith("image/") && !contentType.equals("image/svg+xml");
    }

    private void validateImageUpload(@Nullable UUID uploadId) {

        if (uploadId == null) {
            return;
        }
        var upload = uploads.findById(uploadId).orElse(null);
        if (upload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chat image upload not found");
        }
        if (!isSupportedImage(upload.getContentType())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Chat image upload must be a supported image");
        }
    }
}
