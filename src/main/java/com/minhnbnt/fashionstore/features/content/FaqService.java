package com.minhnbnt.fashionstore.features.content;

import com.minhnbnt.fashionstore.dto.content.FaqRequest;
import com.minhnbnt.fashionstore.dto.content.FaqResponse;
import com.minhnbnt.fashionstore.dto.content.PublicFaqResponse;
import com.minhnbnt.fashionstore.features.content.repository.FaqItemRepository;
import com.minhnbnt.fashionstore.model.FaqItem;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FaqService {

    /**
     * Nhóm theo thứ tự khai báo enum, trong nhóm theo sortOrder rồi câu tạo trước.
     */
    private static final Comparator<FaqItem> DISPLAY_ORDER = Comparator
        .comparing(FaqItem::getCategory)
        .thenComparingInt(FaqItem::getSortOrder)
        .thenComparing(FaqItem::getCreatedAt);

    private final FaqItemRepository faqs;

    @Transactional(readOnly = true)
    public List<FaqResponse> listForAdmin() {
        var all = new ArrayList<FaqItem>();
        faqs.findAll().forEach(all::add);

        return all.stream()
            .sorted(DISPLAY_ORDER)
            .map(FaqResponse::of)
            .toList();
    }

    @Transactional
    public FaqResponse create(@NonNull UUID adminId, @NonNull FaqRequest request) {
        var item = FaqItem.create(
            request.category(),
            request.question(),
            request.answer(),
            request.sortOrder(),
            request.published(),
            adminId
        );

        return FaqResponse.of(faqs.save(item));
    }

    @Transactional
    public FaqResponse update(@NonNull UUID adminId, @NonNull UUID faqId, @NonNull FaqRequest request) {
        var item = requireFaq(faqId);

        item.update(
            request.category(),
            request.question(),
            request.answer(),
            request.sortOrder(),
            request.published(),
            adminId
        );

        return FaqResponse.of(faqs.save(item));
    }

    @Transactional
    public void delete(@NonNull UUID faqId) {
        var item = requireFaq(faqId);
        faqs.delete(item);
    }

    @Transactional(readOnly = true)
    public List<PublicFaqResponse> published() {
        return faqs.findPublished().stream()
            .sorted(DISPLAY_ORDER)
            .map(PublicFaqResponse::of)
            .toList();
    }

    private FaqItem requireFaq(UUID faqId) {
        var item = faqs.findById(faqId).orElse(null);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ not found");
        }
        return item;
    }
}
