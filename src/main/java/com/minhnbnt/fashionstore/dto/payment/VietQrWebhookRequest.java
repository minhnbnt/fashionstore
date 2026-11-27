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
        sb.append("\"").append(escapeJsonString(value)).append("\"");
    }

    private static String escapeJsonString(String value) {
        var out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        return out.toString();
    }

    private static void appendJsonNumber(StringBuilder sb, String key, long value) {
        sb.append(",\"").append(key).append("\":").append(value);
    }
}
