package com.minhnbnt.fashionstore.features.chat.repository;

import com.minhnbnt.fashionstore.model.ChatConversation;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends CrudRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByShopIdAndBuyerId(UUID shopId, UUID buyerId);

    List<ChatConversation> findByShopIdOrderByLastMessageAtDesc(UUID shopId);

    List<ChatConversation> findByBuyerIdOrderByLastMessageAtDesc(UUID buyerId);
}
