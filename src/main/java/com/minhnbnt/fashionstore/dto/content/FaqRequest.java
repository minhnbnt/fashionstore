package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.FaqCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaqRequest(
    @Schema(description = "Nhóm câu hỏi", example = "RETURN")
    @NotNull
    FaqCategory category,

    @Schema(description = "Câu hỏi", example = "Tôi muốn đổi size thì làm thế nào?")
    @NotBlank
    @Size(max = 300)
    String question,

    @Schema(description = "Câu trả lời Markdown")
    @NotBlank
    @Size(max = 5000)
    String answer,

    @Schema(description = "Thứ tự trong nhóm, nhỏ lên trước", example = "1")
    @Min(0)
    @Max(9999)
    int sortOrder,

    @Schema(description = "Hiện trên trang FAQ")
    boolean published
) {
}
