package com.minhnbnt.fashionstore.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

public record VietQrWebhookRequest(
    @Schema(description = "Mã định danh giao dịch từ cổng webhook", example = "FT2633012345")
    @Nullable String id,

    @Schema(description = "Mã tham chiếu giao dịch ngân hàng", example = "FT2633012345")
    @Nullable String referenceCode,

    @Schema(description = "Nội dung chuyển khoản (chuẩn SePay)", example = "CHUYEN TIEN DHMQ1234567890")
    @Nullable String content,

    @Schema(description = "Nội dung chuyển khoản (chuẩn Casso)", example = "CHUYEN TIEN DHMQ1234567890")
    @Nullable String description,

    @Schema(description = "Số tiền giao dịch (chuẩn SePay, VND)", example = "360000")
    @Nullable Long transferAmount,

    @Schema(description = "Số tiền giao dịch (chuẩn Casso, VND)", example = "360000")
    @Nullable Long amount,

    @Schema(description = "Loại biến động số dư (in/out)", example = "in")
    @Nullable String transferType
) {

    public VietQrWebhookRequest(
        @Nullable String content,
        long transferAmount,
        @Nullable String referenceCode,
        @Nullable String transferType
    ) {
        this(referenceCode, referenceCode, content, null, transferAmount, null, transferType);
    }

    public String resolvedContent() {
        if (content != null && !content.isBlank()) {
            return content;
        }
        if (description != null && !description.isBlank()) {
            return description;
        }
        return "";
    }

    public long resolvedAmount() {
        if (transferAmount != null) {
            return transferAmount;
        }
        if (amount != null) {
            return amount;
        }
        return 0L;
    }

    public @Nullable String resolvedBankTransactionId() {
        if (referenceCode != null && !referenceCode.isBlank()) {
            return referenceCode.trim();
        }
        if (id != null && !id.isBlank()) {
            return id.trim();
        }
        return null;
    }

    public String toRawPayloadJson() {
        var sb = new StringBuilder("{");
        appendJsonField(sb, "id", id, true);
        appendJsonField(sb, "referenceCode", referenceCode, false);
        appendJsonField(sb, "content", resolvedContent(), false);
        appendJsonNumber(sb, "transferAmount", resolvedAmount());
        appendJsonField(sb, "transferType", transferType, false);
        sb.append("}");
        return sb.toString();
    }

    private static void appendJsonField(StringBuilder sb, String key, @Nullable String value, boolean first) {
        if (!first) {
            sb.append(",");
        }
        sb.append("\"").append(key).append("\":");
        if (value == null) {
            sb.append("null");
            return;
        }
        var escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        sb.append("\"").append(escaped).append("\"");
    }

    private static void appendJsonNumber(StringBuilder sb, String key, long value) {
        sb.append(",\"").append(key).append("\":").append(value);
    }
}
