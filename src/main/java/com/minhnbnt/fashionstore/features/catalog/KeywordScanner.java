package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.features.catalog.repository.BannedKeywordRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductReportRepository;
import com.minhnbnt.fashionstore.model.BannedKeyword;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Gắn cờ hệ thống cho sản phẩm có tên/mô tả chứa từ khóa cấm. Chỉ đưa vào hàng đợi cho admin xem,
 * không tự gỡ: từ khóa có thể đa nghĩa.
 */
@Component
@RequiredArgsConstructor
public class KeywordScanner {

    private final BannedKeywordRepository keywords;

    private final ProductReportRepository reports;

    /**
     * Quét theo danh sách từ khóa hiện tại. Trả true nếu vừa tạo cờ mới.
     */
    public boolean scan(UUID productId, String name, String description) {
        return flagIfMatched(productId, name + "\n" + description, keywords.findAllOldestFirst());
    }

    /**
     * Quét với danh sách từ khóa cho sẵn (dùng khi thêm từ khóa rồi quét lại hàng loạt).
     * Trả true nếu vừa tạo cờ mới, false nếu không khớp hoặc sản phẩm đã có cờ hệ thống đang mở.
     */
    public boolean flagIfMatched(UUID productId, String text, List<BannedKeyword> candidates) {
        var matched = matching(text, candidates);
        if (matched.isEmpty()) {
            return false;
        }

        var words = matched.stream()
            .map(BannedKeyword::getKeyword)
            .toList();

        return reports.insertSystemFlagIfAbsent(
            UUID.randomUUID(),
            productId,
            matched.getFirst().getViolationType().name(),
            String.join(", ", words)
        );
    }

    /**
     * Khớp nguyên từ (không dính chữ/số hai bên), không phân biệt hoa thường, chuẩn hóa NFC để chữ Việt
     * gõ kiểu tổ hợp vẫn khớp. Ký tự đặc biệt trong từ khóa được hiểu theo nghĩa đen.
     */
    public static List<BannedKeyword> matching(String text, List<BannedKeyword> candidates) {
        var normalizedText = Normalizer.normalize(text, Normalizer.Form.NFC);
        var matched = new ArrayList<BannedKeyword>();

        for (var candidate : candidates) {
            var word = Normalizer.normalize(candidate.getKeyword(), Normalizer.Form.NFC);
            var pattern = Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
            );
            if (pattern.matcher(normalizedText).find()) {
                matched.add(candidate);
            }
        }

        return matched;
    }
}
