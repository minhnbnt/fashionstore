package com.minhnbnt.fashionstore.features.payment;

import org.jspecify.annotations.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Tiện ích sinh mã tham chiếu giao dịch, trích xuất mã từ nội dung chuyển khoản và dựng URL ảnh VietQR.
 */
public final class VietQrUrlBuilder {

    private static final String PREFIX = "DHMQ";

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private static final int RANDOM_LENGTH = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Pattern TXN_REF_PATTERN = Pattern.compile("(?i)(DHMQ[A-Z0-9]{10})");

    private VietQrUrlBuilder() {
    }

    public static String generateTxnRef() {
        var sb = new StringBuilder(PREFIX.length() + RANDOM_LENGTH);
        sb.append(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            var idx = RANDOM.nextInt(ALPHANUMERIC.length());
            sb.append(ALPHANUMERIC.charAt(idx));
        }
        return sb.toString();
    }

    public static @Nullable String extractTxnRef(@Nullable String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        var matcher = TXN_REF_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).toUpperCase(Locale.ROOT);
    }

    public static String buildQrImageUrl(
        String bankBin,
        String accountNumber,
        String accountName,
        long amount,
        String txnRef
    ) {
        return "https://img.vietqr.io/image/"
            + bankBin
            + "-"
            + accountNumber
            + "-compact2.png?amount="
            + amount
            + "&addInfo="
            + URLEncoder.encode(txnRef, StandardCharsets.UTF_8)
            + "&accountName="
            + URLEncoder.encode(accountName, StandardCharsets.UTF_8);
    }
}
