package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.FaqCategory;
import com.minhnbnt.fashionstore.model.FaqItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record PublicFaqResponse(
    UUID id,

    FaqCategory category,

    String question,

    @Schema(description = "Câu trả lời Markdown")
    String answer
) {

    public static PublicFaqResponse of(FaqItem item) {
        return new PublicFaqResponse(item.getId(), item.getCategory(), item.getQuestion(), item.getAnswer());
    }
}
