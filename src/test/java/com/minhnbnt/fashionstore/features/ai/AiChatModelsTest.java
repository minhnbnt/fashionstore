package com.minhnbnt.fashionstore.features.ai;

import com.minhnbnt.fashionstore.dto.ai.AiConversationResponse;
import com.minhnbnt.fashionstore.dto.ai.AiMessageResponse;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.model.AiConversation;
import com.minhnbnt.fashionstore.model.AiMessage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatModelsTest {

    @Test
    void testAiConversationCreationAndGetters() {
        UUID userId = UUID.randomUUID();
        AiConversation conv = AiConversation.of(userId, "Gợi ý váy hoa dự tiệc");

        assertThat(conv.getId()).isNotNull();
        assertThat(conv.getUserId()).isEqualTo(userId);
        assertThat(conv.getTitle()).isEqualTo("Gợi ý váy hoa dự tiệc");
        assertThat(conv.getCreatedAt()).isNotNull();
        assertThat(conv.getUpdatedAt()).isNotNull();
        assertThat(conv.isNewRecord()).isTrue();
    }

    @Test
    void testAiMessageCreationAndGetters() {
        UUID convId = UUID.randomUUID();
        AiMessage message = AiMessage.of(convId, "user", "Tìm áo polo nam", "{\"filters\":{}}");

        assertThat(message.getId()).isNotNull();
        assertThat(message.getConversationId()).isEqualTo(convId);
        assertThat(message.getRole()).isEqualTo("user");
        assertThat(message.getContent()).isEqualTo("Tìm áo polo nam");
        assertThat(message.getMetadata()).isEqualTo("{\"filters\":{}}");
        assertThat(message.getSentAt()).isNotNull();
        assertThat(message.isNewRecord()).isTrue();
    }

    @Test
    void testAiDtos() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        AiConversationResponse convRes = new AiConversationResponse(id, "Title", now, now);
        assertThat(convRes.id()).isEqualTo(id);
        assertThat(convRes.title()).isEqualTo("Title");

        UUID msgId = UUID.randomUUID();
        AiMessageResponse msgRes = new AiMessageResponse(msgId, id, "assistant", "Lời đáp", "{}", now);
        assertThat(msgRes.id()).isEqualTo(msgId);
        assertThat(msgRes.role()).isEqualTo("assistant");

        CreateAiConversationRequest createReq = new CreateAiConversationRequest("Title");
        assertThat(createReq.title()).isEqualTo("Title");

        SaveAiMessageRequest saveReq = new SaveAiMessageRequest("user", "Content", "{}");
        assertThat(saveReq.content()).isEqualTo("Content");
    }
}
