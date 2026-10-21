package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.dto.admin.BannedKeywordCreatedResponse;
import com.minhnbnt.fashionstore.dto.admin.BannedKeywordResponse;
import com.minhnbnt.fashionstore.dto.admin.CreateBannedKeywordRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.BannedKeywordRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductAdminQueryRepository;
import com.minhnbnt.fashionstore.model.BannedKeyword;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Danh sách từ khóa cấm do admin quản lý. Thêm từ khóa thì quét lại hàng đang bán/đang ẩn ngay,
 * không chờ shop sửa sản phẩm.
 */
@Service
@RequiredArgsConstructor
public class BannedKeywordService {

    private static final String DUPLICATE_KEYWORD = "Keyword already exists";

    private final BannedKeywordRepository keywords;

    private final ProductAdminQueryRepository adminQueries;

    private final KeywordScanner scanner;

    @Transactional(readOnly = true)
    public List<BannedKeywordResponse> list() {
        return adminQueries.keywords();
    }

    @Transactional
    public BannedKeywordCreatedResponse add(@NonNull UUID adminId, @NonNull CreateBannedKeywordRequest request) {
        var keyword = BannedKeyword.create(request.keyword(), request.violationType(), adminId);
        if (keywords.existsByKeywordIgnoringCase(keyword.getKeyword())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_KEYWORD);
        }

        try {
            keywords.save(keyword);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, DUPLICATE_KEYWORD);
        }

        var flagged = 0;
        for (var candidate : adminQueries.scanCandidates(keyword.getKeyword())) {
            var text = candidate.name() + "\n" + candidate.description();
            if (scanner.flagIfMatched(candidate.id(), text, List.of(keyword))) {
                flagged++;
            }
        }

        var created = adminQueries.findKeyword(keyword.getId()).orElseThrow();
        return new BannedKeywordCreatedResponse(created, flagged);
    }

    /**
     * Xóa từ khóa không đóng các cờ đã tạo: admin vẫn xử lý nốt những sản phẩm đang trong hàng đợi.
     */
    @Transactional
    public void delete(@NonNull UUID keywordId) {
        if (!keywords.existsById(keywordId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Keyword not found");
        }
        keywords.deleteById(keywordId);
    }
}
