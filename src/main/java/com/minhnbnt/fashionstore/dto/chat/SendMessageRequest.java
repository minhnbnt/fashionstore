package com.minhnbnt.fashionstore.dto.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

public record SendMessageRequest(
    @Schema(description = "Nội dung tin nhắn", nullable = true)
    @Nullable
    @Size(max = 2000, message = "content must be at most 2000 characters")
    String content,

    @Schema(description = "Upload id ảnh đính kèm (từ /api/uploads/presign)", nullable = true, format = "uuid")
    @Nullable
    UUID imageUploadId
) {
    @AssertTrue(message = "Message must have text content or an attached image")
    public boolean hasContentOrImage() {
        return (content != null && !content.isBlank()) || imageUploadId != null;
    }
}
