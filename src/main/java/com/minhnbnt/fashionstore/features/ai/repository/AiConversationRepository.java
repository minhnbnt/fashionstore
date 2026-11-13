package com.minhnbnt.fashionstore.features.ai.repository;

import com.minhnbnt.fashionstore.model.AiConversation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiConversationRepository extends CrudRepository<AiConversation, UUID> {

    List<AiConversation> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<AiConversation> findByIdAndUserId(UUID id, UUID userId);
}
