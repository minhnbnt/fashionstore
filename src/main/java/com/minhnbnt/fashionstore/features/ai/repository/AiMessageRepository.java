package com.minhnbnt.fashionstore.features.ai.repository;

import com.minhnbnt.fashionstore.model.AiMessage;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends CrudRepository<AiMessage, UUID> {

    List<AiMessage> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
