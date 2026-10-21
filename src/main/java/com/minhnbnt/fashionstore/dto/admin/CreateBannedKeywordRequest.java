package com.minhnbnt.fashionstore.dto.admin;

import com.minhnbnt.fashionstore.model.ProductViolationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBannedKeywordRequest(
    @Schema(description = "Từ hoặc cụm từ, khớp nguyên từ, không phân biệt hoa thường", example = "replica")
    @NotBlank
    @Size(min = 2, max = 100)
    String keyword,

    @Schema(description = "Loại vi phạm gán cho cờ hệ thống", example = "COUNTERFEIT")
    @NotNull
    ProductViolationType violationType
) {
}
