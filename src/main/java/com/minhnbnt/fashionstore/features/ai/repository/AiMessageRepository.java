package com.minhnbnt.fashionstore.features.ai.repository;

import com.minhnbnt.fashionstore.model.AiMessage;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AiMessageRepository extends CrudRepository<AiMessage, UUID> {

    @Modifying
    @Query("""
        INSERT INTO ai_message (id, conversation_id, role, content, metadata, sent_at)
        VALUES (:id, :conversationId, :role, :content, CAST(:metadata AS jsonb), :sentAt)
        """)
    void insertMessage(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        @Nullable String metadata,
        Instant sentAt
    );

    @Query("""
        SELECT id, conversation_id, role, content, metadata::text AS metadata, sent_at
        FROM ai_message
        WHERE conversation_id = :conversationId
        ORDER BY sent_at ASC
        """)
    List<AiMessage> findByConversationIdOrderBySentAtAsc(UUID conversationId);

    void deleteByConversationId(UUID conversationId);
}
