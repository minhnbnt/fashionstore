package com.minhnbnt.fashionstore.features.chat;

import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.features.chat.repository.ChatConversationRepository;
import com.minhnbnt.fashionstore.features.chat.repository.ChatMessageRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.ChatConversation;
import com.minhnbnt.fashionstore.model.ChatMessage;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceUnitTest {

    @Mock
    private ChatConversationRepository conversations;

    @Mock
    private ChatMessageRepository messages;

    @Mock
    private ShopRepository shops;

    @Mock
    private ShopMemberRepository members;

    @InjectMocks
    private ChatService chatService;

    private final ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);

    @Test
    void startConversationCreatesNewWhenNotFound() {
        var buyerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var shop = Shop.create("Fashion Hub", "fashion-hub", "Mo ta", null, null);

        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        when(conversations.findByShopIdAndBuyerId(shopId, buyerId)).thenReturn(Optional.empty());
        when(conversations.save(any(ChatConversation.class))).thenAnswer(i -> i.getArgument(0));

        var resp = chatService.startConversation(buyerId, shopId);

        assertThat(resp.shopId()).isEqualTo(shopId);
        assertThat(resp.buyerId()).isEqualTo(buyerId);
        verify(conversations).save(any(ChatConversation.class));
    }

    @Test
    void sendMessageFromBuyerTriggersShopAutoReply() {
        var buyerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var conv = ChatConversation.create(shopId, buyerId);
        var ownerId = UUID.randomUUID();

        var shop = Shop.create("Fashion Hub", "fashion-hub", "Mo ta", null, null);
        shop.updateProfile(
            null, null, null, null, null, null, null,
            "Xin chao, shop se phan hoi ban som nhat!",
            null
        );

        when(conversations.findById(convId)).thenReturn(Optional.of(conv));
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        when(members.findOwner(shopId)).thenReturn(Optional.of(ownerId));

        var req = new SendMessageRequest("Shop oi, mau xanh con size M khong?", null);
        var resp = chatService.sendMessage(buyerId, convId, req);

        assertThat(resp.senderType()).isEqualTo("BUYER");
        assertThat(resp.content()).isEqualTo("Shop oi, mau xanh con size M khong?");
        // 1 user message + 1 auto-reply message
        verify(messages, times(2)).save(captor.capture());
        var autoReply = captor.getAllValues().get(1);
        assertThat(autoReply.getSenderType()).isEqualTo("SELLER");
        // Auto reply must come from a real shop member, not the shop row itself.
        assertThat(autoReply.getSenderId()).isEqualTo(ownerId);
        assertThat(autoReply.getContent()).isEqualTo("Xin chao, shop se phan hoi ban som nhat!");
    }

    @Test
    void sendMessageFromStrangerThrowsForbidden() {
        var strangerId = UUID.randomUUID();
        var buyerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var conv = ChatConversation.create(shopId, buyerId);

        when(conversations.findById(convId)).thenReturn(Optional.of(conv));
        when(members.findRole(shopId, strangerId)).thenReturn(Optional.empty());

        var req = new SendMessageRequest("Hello", null);

        assertThatThrownBy(() -> chatService.sendMessage(strangerId, convId, req))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void markReadResetsUnreadCount() {
        var buyerId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var conv = ChatConversation.create(shopId, buyerId);
        conv.recordNewMessage("Hello", true);

        assertThat(conv.getSellerUnreadCount()).isEqualTo(1);

        var sellerId = UUID.randomUUID();
        when(conversations.findById(convId)).thenReturn(Optional.of(conv));
        when(members.findRole(shopId, sellerId)).thenReturn(Optional.of(MemberRole.OWNER));

        chatService.markRead(sellerId, convId);

        assertThat(conv.getSellerUnreadCount()).isEqualTo(0);
        verify(conversations).save(conv);
    }
}
