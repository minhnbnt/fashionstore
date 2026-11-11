package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import org.jspecify.annotations.Nullable;

public record SendMessageRequest(
    @Schema(description = "Nội dung tin nhắn", nullable = true)
    @Nullable
    String content,

    @Schema(description = "S3 key ảnh đính kèm", nullable = true)
    @Nullable
    String imageKey
) {
    @AssertTrue(message = "Tin nhắn phải có nội dung văn bản hoặc ảnh đính kèm")
    public boolean hasContentOrImage() {
        return (content != null && !content.isBlank())
            || (imageKey != null && !imageKey.isBlank());
    }
}
