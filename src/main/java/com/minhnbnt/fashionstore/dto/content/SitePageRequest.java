package com.minhnbnt.fashionstore.dto.content;

import com.minhnbnt.fashionstore.model.PageFooterGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record SitePageRequest(
    @Schema(description = "Đường dẫn /pages/{slug}, chữ thường không dấu nối bằng -", example = "chinh-sach-doi-tra")
    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "must be lowercase words joined by -")
    String slug,

    @Schema(description = "Tiêu đề trang", example = "Chính sách đổi trả")
    @NotBlank
    @Size(max = 150)
    String title,

    @Schema(description = "Nội dung Markdown")
    @NotBlank
    @Size(max = 50000)
    String content,

    @Schema(description = "Mô tả SEO (thẻ meta description)", example = "Đổi trả trong 30 ngày.")
    @Size(max = 160)
    @Nullable String metaDescription,

    @Schema(description = "Cột footer chứa link trang", example = "POLICY")
    @NotNull
    PageFooterGroup footerGroup,

    @Schema(description = "Thứ tự trong cột footer, nhỏ lên trước", example = "1")
    @Min(0)
    @Max(9999)
    int sortOrder
) {
}
