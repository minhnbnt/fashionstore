package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartConversationRequest(
    @Schema(description = "Id shop cần chat")
    @NotNull
    UUID shopId
) {
}
