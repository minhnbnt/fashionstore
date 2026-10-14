package com.minhnbnt.fashionstore.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AttachImagesRequest(
    @Schema(description = "Danh sách ảnh cần gắn")
    @NotEmpty
    @Valid
    List<ImageItemRequest> images
) {
}
