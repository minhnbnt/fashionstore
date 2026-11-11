package com.minhnbnt.fashionstore.features.ai;

import com.minhnbnt.fashionstore.dto.ai.AiConversationResponse;
import com.minhnbnt.fashionstore.dto.ai.AiMessageResponse;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.features.ai.repository.AiConversationRepository;
import com.minhnbnt.fashionstore.features.ai.repository.AiMessageRepository;
import com.minhnbnt.fashionstore.model.AiConversation;
import com.minhnbnt.fashionstore.model.AiMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceUnitTest {

    @Mock
    private AiConversationRepository conversationRepo;

    @Mock
    private AiMessageRepository messageRepo;

    @InjectMocks
    private AiChatService service;

    @Captor
    private ArgumentCaptor<AiConversation> conversationCaptor;

    @Captor
    private ArgumentCaptor<AiMessage> messageCaptor;

    @Test
    void listConversations_returnsListOrderedByUpdatedAt() {
        var userId = UUID.randomUUID();
        var now = Instant.now();
        var c1 = new AiConversation(UUID.randomUUID(), userId, "Hội thoại 1", now, now, false);
        var c2 = new AiConversation(UUID.randomUUID(), userId, "Hội thoại 2", now, now, false);

        when(conversationRepo.findByUserIdOrderByUpdatedAtDesc(userId))
            .thenReturn(List.of(c1, c2));

        var result = service.listConversations(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Hội thoại 1");
        assertThat(result.get(1).title()).isEqualTo("Hội thoại 2");
    }

    @Test
    void createConversation_withCustomTitle_savesAndReturnsResponse() {
        var userId = UUID.randomUUID();
        when(conversationRepo.save(any(AiConversation.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var req = new CreateAiConversationRequest("Tư vấn áo phông");
        var res = service.createConversation(userId, req);

        verify(conversationRepo).save(conversationCaptor.capture());
        var saved = conversationCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getTitle()).isEqualTo("Tư vấn áo phông");
        assertThat(res.title()).isEqualTo("Tư vấn áo phông");
    }

    @Test
    void createConversation_withNullOrBlankTitle_usesDefaultTitle() {
        var userId = UUID.randomUUID();
        when(conversationRepo.save(any(AiConversation.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var req = new CreateAiConversationRequest("   ");
        var res = service.createConversation(userId, req);

        assertThat(res.title()).isEqualTo("Cuộc trò chuyện mới");
    }

    @Test
    void deleteConversation_whenOwnedByUser_deletesMessagesAndConversation() {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var conv = new AiConversation(convId, userId, "Hội thoại cũ", Instant.now(), Instant.now(), false);

        when(conversationRepo.findByIdAndUserId(convId, userId))
            .thenReturn(Optional.of(conv));

        service.deleteConversation(userId, convId);

        verify(messageRepo).deleteByConversationId(convId);
        verify(conversationRepo).delete(conv);
    }

    @Test
    void deleteConversation_whenNotFound_throws404() {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();

        when(conversationRepo.findByIdAndUserId(convId, userId))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConversation(userId, convId))
            .isInstanceOf(ResponseStatusException.class)
            .hasFieldOrPropertyWithValue("status", HttpStatus.NOT_FOUND);
    }

    @Test
    void listMessages_whenConversationExists_returnsMessages() {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var conv = new AiConversation(convId, userId, "Hội thoại", Instant.now(), Instant.now(), false);
        var msg = new AiMessage(UUID.randomUUID(), convId, "user", "Xin chào", null, Instant.now(), false);

        when(conversationRepo.findByIdAndUserId(convId, userId))
            .thenReturn(Optional.of(conv));
        when(messageRepo.findByConversationIdOrderBySentAtAsc(convId))
            .thenReturn(List.of(msg));

        var result = service.listMessages(userId, convId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).content()).isEqualTo("Xin chào");
        assertThat(result.get(0).role()).isEqualTo("user");
    }

    @Test
    void saveMessage_savesMessageAndUpdatesConversation() {
        var userId = UUID.randomUUID();
        var convId = UUID.randomUUID();
        var conv = new AiConversation(convId, userId, "Cuộc trò chuyện mới", Instant.now(), Instant.now(), false);

        when(conversationRepo.findByIdAndUserId(convId, userId))
            .thenReturn(Optional.of(conv));
        when(messageRepo.save(any(AiMessage.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        var req = new SaveAiMessageRequest("user", "Tìm áo khoác nam", "{\"parts\":[]}");
        var res = service.saveMessage(userId, convId, req);

        verify(messageRepo).save(messageCaptor.capture());
        var savedMsg = messageCaptor.getValue();
        assertThat(savedMsg.getContent()).isEqualTo("Tìm áo khoác nam");
        assertThat(savedMsg.getRole()).isEqualTo("user");
        assertThat(savedMsg.getMetadata()).isEqualTo("{\"parts\":[]}");

        verify(conversationRepo).save(conversationCaptor.capture());
        var savedConv = conversationCaptor.getValue();
        assertThat(savedConv.getTitle()).isEqualTo("Tìm áo khoác nam");
        assertThat(res.content()).isEqualTo("Tìm áo khoác nam");
    }
}
