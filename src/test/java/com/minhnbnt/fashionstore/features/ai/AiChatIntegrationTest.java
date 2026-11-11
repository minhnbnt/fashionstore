package com.minhnbnt.fashionstore.features.ai;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.features.identity.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AiChatIntegrationTest {

    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private UserService users;

    @Test
    void createConversationSaveMessagesWithJsonbMetadataAndReloadRoundtrips() {
        var userId = UUID.randomUUID();
        users.upsert(userId, new CreateUserRequest("ai-int-" + userId + "@t.local", "AI Tester", null));

        var conv = aiChatService.createConversation(userId, new CreateAiConversationRequest("Tìm áo sơ mi"));
        assertThat(conv.id()).isNotNull();
        assertThat(conv.title()).isEqualTo("Tìm áo sơ mi");

        String metadataJson = "{\"parts\":[{\"type\":\"text\",\"text\":\"Tìm áo sơ mi\"}]}";
        var savedUserMsg = aiChatService.saveMessage(
            userId,
            conv.id(),
            new SaveAiMessageRequest("user", "Tìm áo sơ mi", metadataJson)
        );
        assertThat(savedUserMsg.id()).isNotNull();
        assertThat(savedUserMsg.metadata()).contains("Tìm áo sơ mi");

        var savedAssistantMsg = aiChatService.saveMessage(
            userId,
            conv.id(),
            new SaveAiMessageRequest("assistant", "Gợi ý bạn mẫu này", null)
        );
        assertThat(savedAssistantMsg.metadata()).isNull();

        var reloaded = aiChatService.listMessages(userId, conv.id());
        assertThat(reloaded).hasSize(2);
        assertThat(reloaded.get(0).role()).isEqualTo("user");
        assertThat(reloaded.get(0).metadata()).contains("Tìm áo sơ mi");
        assertThat(reloaded.get(1).role()).isEqualTo("assistant");
        assertThat(reloaded.get(1).metadata()).isNull();

        aiChatService.deleteConversation(userId, conv.id());
        assertThat(aiChatService.listConversations(userId)).isEmpty();
    }
}
