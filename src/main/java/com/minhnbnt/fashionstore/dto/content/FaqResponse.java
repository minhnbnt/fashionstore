package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.FaqCategory;
import com.minhnbnt.fashionstore.model.FaqItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

public record FaqResponse(
    UUID id,

    FaqCategory category,

    String question,

    @Schema(description = "Câu trả lời Markdown")
    String answer,

    int sortOrder,

    boolean published,

    Instant updatedAt
) {

    public static FaqResponse of(FaqItem item) {
        return new FaqResponse(
            item.getId(),
            item.getCategory(),
            item.getQuestion(),
            item.getAnswer(),
            item.getSortOrder(),
            item.isPublished(),
            item.getUpdatedAt()
        );
    }
}
