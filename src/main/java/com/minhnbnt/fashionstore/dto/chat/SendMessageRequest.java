package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record SendMessageRequest(
    @Schema(description = "Nội dung tin nhắn", nullable = true)
    @Nullable
    @Size(max = 2000, message = "content must be at most 2000 characters")
    String content,

    @Schema(description = "S3 key ảnh đính kèm", nullable = true)
    @Nullable
    String imageKey
) {
    @AssertTrue(message = "Message must have text content or an attached image")
    public boolean hasContentOrImage() {
        return (content != null && !content.isBlank())
            || (imageKey != null && !imageKey.isBlank());
    }
}
