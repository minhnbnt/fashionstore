package com.minhnbnt.fashionstore.features.ai;

import com.minhnbnt.fashionstore.dto.ai.AiConversationResponse;
import com.minhnbnt.fashionstore.dto.ai.AiMessageResponse;
import com.minhnbnt.fashionstore.dto.ai.CreateAiConversationRequest;
import com.minhnbnt.fashionstore.dto.ai.SaveAiMessageRequest;
import com.minhnbnt.fashionstore.features.ai.repository.AiConversationRepository;
import com.minhnbnt.fashionstore.features.ai.repository.AiMessageRepository;
import com.minhnbnt.fashionstore.model.AiConversation;
import com.minhnbnt.fashionstore.model.AiMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String DEFAULT_TITLE = "Cuộc trò chuyện mới";
    private static final int MAX_TITLE_LENGTH = 50;

    private final AiConversationRepository conversationRepo;
    private final AiMessageRepository messageRepo;

    @Transactional(readOnly = true)
    public List<AiConversationResponse> listConversations(UUID userId) {
        return conversationRepo.findByUserIdOrderByUpdatedAtDesc(userId)
            .stream()
            .map(this::toConversationResponse)
            .toList();
    }

    @Transactional
    public AiConversationResponse createConversation(UUID userId, CreateAiConversationRequest request) {
        String rawTitle = request != null ? request.title() : null;
        String title = (rawTitle != null && !rawTitle.isBlank()) ? rawTitle.trim() : DEFAULT_TITLE;

        AiConversation conv = AiConversation.of(userId, title);
        AiConversation saved = conversationRepo.save(conv);
        return toConversationResponse(saved);
    }

    @Transactional
    public void deleteConversation(UUID userId, UUID conversationId) {
        AiConversation conv = conversationRepo.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI conversation not found"));

        messageRepo.deleteByConversationId(conversationId);
        conversationRepo.delete(conv);
    }

    @Transactional(readOnly = true)
    public List<AiMessageResponse> listMessages(UUID userId, UUID conversationId) {
        var conv = conversationRepo.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI conversation not found"));

        return messageRepo.findByConversationIdOrderBySentAtAsc(conv.getId())
            .stream()
            .map(this::toMessageResponse)
            .toList();
    }

    @Transactional
    public AiMessageResponse saveMessage(UUID userId, UUID conversationId, SaveAiMessageRequest request) {
        AiConversation conv = conversationRepo.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI conversation not found"));

        AiMessage msg = AiMessage.of(conversationId, request.role(), request.content(), request.metadata());
        AiMessage savedMsg = messageRepo.save(msg);

        if (DEFAULT_TITLE.equals(conv.getTitle())
            && "user".equalsIgnoreCase(request.role())
            && request.content() != null
            && !request.content().isBlank()) {
            String title = request.content().trim();
            if (title.length() > MAX_TITLE_LENGTH) {
                title = title.substring(0, MAX_TITLE_LENGTH).trim() + "...";
            }
            conv.updateTitle(title);
        } else {
            conv.touch();
        }
        conversationRepo.save(conv);

        return toMessageResponse(savedMsg);
    }

    private AiConversationResponse toConversationResponse(AiConversation conv) {
        return new AiConversationResponse(
            conv.getId(),
            conv.getTitle(),
            conv.getCreatedAt(),
            conv.getUpdatedAt()
        );
    }

    private AiMessageResponse toMessageResponse(AiMessage msg) {
        return new AiMessageResponse(
            msg.getId(),
            msg.getConversationId(),
            msg.getRole(),
            msg.getContent(),
            msg.getMetadata(),
            msg.getSentAt()
        );
    }
}
