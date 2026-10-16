package com.minhnbnt.fashionstore.features.chat.repository;

import com.minhnbnt.fashionstore.model.ChatMessage;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends CrudRepository<ChatMessage, UUID> {

    List<ChatMessage> findByConversationIdOrderBySentAtAsc(UUID conversationId);
}
