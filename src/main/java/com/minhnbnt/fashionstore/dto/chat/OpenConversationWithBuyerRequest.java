package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OpenConversationWithBuyerRequest(
    @Schema(description = "ID shop") @NotNull UUID shopId,
    @Schema(description = "ID người mua") @NotNull UUID buyerId
) {
}

