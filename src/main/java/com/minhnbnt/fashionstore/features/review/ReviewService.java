package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.RatingSummary;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewHelpfulResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewReplyTemplateResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewSummaryResponse;
import com.minhnbnt.fashionstore.dto.review.SaveReviewReplyTemplateRequest;
import com.minhnbnt.fashionstore.dto.review.SellerReviewProductOptionResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewTrendResponse;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.review.repository.ReviewHelpfulRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewReplyTemplateRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.features.review.repository.SellerReviewQueryRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.infrastructure.media.VideoMetadataService;
import com.minhnbnt.fashionstore.model.Review;
import com.minhnbnt.fashionstore.model.ReviewReplyTemplate;
import com.minhnbnt.fashionstore.model.ReviewSort;
import com.minhnbnt.fashionstore.model.ReviewTag;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    public static final int MIN_COMMENT_LENGTH = 10;
    public static final int MAX_COMMENT_LENGTH = 2000;
    public static final int MAX_REPLY_LENGTH = 2000;
    public static final int MAX_TEMPLATE_TITLE_LENGTH = 100;
    public static final int MAX_TEMPLATES_PER_SHOP = 50;
    public static final int MAX_REVIEW_IMAGES = 5;
    public static final double MAX_REVIEW_VIDEO_SECONDS = 30.0;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReviewRepository reviews;

    private final ReviewHelpfulRepository helpfulRepository;

    private final ReviewReplyTemplateRepository templates;

    private final SellerReviewQueryRepository sellerQueries;

    private final ProductStockService productStock;

    private final OrderService orderService;

    private final UserService users;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final NotificationService notifications;

    private final VideoMetadataService videoMetadataService;

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(
        @NonNull UUID productId,
        @Nullable UUID currentUserId,
        @NonNull ReviewSort sort,
        @Nullable Integer stars,
        boolean hasMedia
    ) {
        requireVisible(productId);
        if (stars != null && (stars < 1 || stars > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stars filter must be between 1 and 5");
        }

        var minStars = 1;
        var maxStars = 5;
        if (stars != null) {
            minStars = stars;
            maxStars = stars;
        }

        List<Review> list;
        if (sort == ReviewSort.NEWEST) {
            list = reviews.findFilteredOrderByNewest(productId, minStars, maxStars, hasMedia);
        } else {
            list = reviews.findFilteredOrderByHelpful(productId, minStars, maxStars, hasMedia);
        }

        return mapResponsesForViewer(list, currentUserId, true);
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse reviewSummary(@NonNull UUID productId) {
        requireVisible(productId);
        return toSummaryResponse(reviews.summarizeByProductId(productId));
    }

    @Transactional(readOnly = true)
    public RatingSummary ratingSummary(@NonNull UUID productId) {
        var row = reviews.summarizeByProductId(productId);
        return new RatingSummary(row.reviewCount(), row.averageRating());
    }

    /**
     * Batch của ratingSummary cho một trang sản phẩm: 1 query thay vì N.
     * Kết quả luôn chứa đủ key của đầu vào; sản phẩm chưa có review nhận
     * RatingSummary(0, null) giống hệt ratingSummary.
     */
    @Transactional(readOnly = true)
    public Map<UUID, RatingSummary> ratingSummaries(@NonNull Collection<UUID> productIds) {
        var counts = new HashMap<UUID, Long>();
        var starSums = new HashMap<UUID, Long>();
        for (var id : productIds) {
            counts.put(id, 0L);
            starSums.put(id, 0L);
        }
        for (var review : reviews.findByProductIdIn(productIds)) {
            counts.merge(review.getProductId(), 1L, Long::sum);
            starSums.merge(review.getProductId(), (long) review.getStars(), Long::sum);
        }

        var summaries = new HashMap<UUID, RatingSummary>();
        for (var id : productIds) {
            var count = counts.getOrDefault(id, 0L);
            if (count == 0) {
                summaries.put(id, new RatingSummary(0, null));
            } else {
                summaries.put(id, new RatingSummary(count, (double) starSums.getOrDefault(id, 0L) / count));
            }
        }
        return summaries;
    }

    @Transactional
    public ReviewResponse createReview(
        @NonNull UUID buyerId,
        @NonNull UUID productId,
        @NonNull CreateReviewRequest request
    ) {
        if (productStock.findProductById(productId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (reviews.findByOrderIdAndProductId(request.orderId(), productId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product for this order");
        }

        var variantLabel = orderService.resolveCompletedOrderProductVariant(buyerId, request.orderId(), productId);
        var normalizedComment = normalizeAndValidateComment(request.comment());
        var normalizedImages = normalizeAndValidateImageKeys(request.imageKeys());
        var normalizedVideo = normalizeVideoKey(request.videoKey());
        if (normalizedVideo != null) {
            videoMetadataService.validateVideoDuration(normalizedVideo, MAX_REVIEW_VIDEO_SECONDS);
        }
        var normalizedTags = ReviewTag.normalizeAndValidate(request.tags());

        var displayName = users.getById(buyerId).displayName();
        var review = Review.create(
            productId,
            buyerId,
            request.orderId(),
            displayName,
            variantLabel,
            request.stars(),
            normalizedComment,
            normalizedImages,
            normalizedVideo,
            normalizedTags,
            request.anonymous()
        );

        return toResponse(reviews.save(review), buyerId, false, Set.of());
    }

    @Transactional
    public ReviewResponse updateReviewById(
        @NonNull UUID buyerId,
        @NonNull UUID reviewId,
        @NonNull UpdateReviewRequest request
    ) {
        var review = reviews.findByIdAndBuyerId(reviewId, buyerId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        var normalizedComment = normalizeAndValidateComment(request.comment());
        var normalizedImages = normalizeAndValidateImageKeys(request.imageKeys());
        var normalizedVideo = normalizeVideoKey(request.videoKey());
        if (normalizedVideo != null && !normalizedVideo.equals(review.getVideoKey())) {
            videoMetadataService.validateVideoDuration(normalizedVideo, MAX_REVIEW_VIDEO_SECONDS);
        }
        var normalizedTags = ReviewTag.normalizeAndValidate(request.tags());

        review.update(
            request.stars(),
            normalizedComment,
            normalizedImages,
            normalizedVideo,
            normalizedTags,
            request.anonymous()
        );

        var saved = reviews.save(review);
        var helpfulIds = helpfulRepository.findHelpfulReviewIds(buyerId, List.of(saved.getId()));
        return toResponse(saved, buyerId, false, helpfulIds);
    }

    @Transactional
    public ReviewHelpfulResponse toggleHelpful(
        @NonNull UUID userId,
        @NonNull UUID reviewId
    ) {
        var review = reviews.findById(reviewId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }
        requireVisible(review.getProductId());
        if (review.getBuyerId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You cannot mark your own review as helpful");
        }

        boolean helpfulByMe;
        if (helpfulRepository.insertIfAbsent(reviewId, userId)) {
            reviews.incrementHelpfulCount(reviewId);
            helpfulByMe = true;
        } else {
            helpfulRepository.delete(reviewId, userId);
            reviews.decrementHelpfulCount(reviewId);
            helpfulByMe = false;
        }

        var updated = reviews.findById(reviewId).orElse(review);
        return new ReviewHelpfulResponse(reviewId, Math.max(0, updated.getHelpfulCount()), helpfulByMe);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> listMine(@NonNull UUID buyerId) {
        var list = reviews.findByBuyerIdOrderByUpdatedAtDescCreatedAtDesc(buyerId);
        return mapResponsesForViewer(list, buyerId, false);
    }

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listForShop(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        boolean onlyUnreplied
    ) {
        return listForShop(userId, shopId, onlyUnreplied, null, null);
    }

    @Transactional(readOnly = true)
    public List<SellerReviewResponse> listForShop(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        boolean onlyUnreplied,
        @Nullable Integer stars,
        @Nullable UUID productId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);
        if (stars != null && (stars < 1 || stars > 5)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stars filter must be between 1 and 5");
        }

        var rows = sellerQueries.findShopReviews(shopId, onlyUnreplied, stars, productId);
        var responses = new ArrayList<SellerReviewResponse>(rows.size());
        for (var row : rows) {
            responses.add(toSellerResponse(row.review(), row.productName()));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public List<SellerReviewProductOptionResponse> listShopReviewProducts(
        @NonNull UUID userId,
        @NonNull UUID shopId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);
        return sellerQueries.findShopReviewProductOptions(shopId);
    }

    @Transactional(readOnly = true)
    public SellerReviewTrendResponse shopReviewTrend(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull String period,
        @Nullable LocalDate from,
        @Nullable LocalDate to
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);

        var normalizedPeriod = period.trim().toLowerCase(Locale.ROOT);
        if (!"day".equals(normalizedPeriod)
            && !"week".equals(normalizedPeriod)
            && !"month".equals(normalizedPeriod)
        ) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Period must be one of: day, week, month"
            );
        }

        var today = LocalDate.now(VN_ZONE);
        var effectiveTo = to;
        if (effectiveTo == null) {
            effectiveTo = today;
        }
        var effectiveFrom = from;
        if (effectiveFrom == null) {
            if ("week".equals(normalizedPeriod)) {
                effectiveFrom = effectiveTo.minusWeeks(11);
            } else if ("month".equals(normalizedPeriod)) {
                effectiveFrom = effectiveTo.withDayOfMonth(1).minusMonths(11);
            } else {
                effectiveFrom = effectiveTo.minusDays(29);
            }
        }
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "From date must not be after to date"
            );
        }

        var fromInstant = effectiveFrom.atStartOfDay(VN_ZONE).toInstant();
        var toInstant = effectiveTo.plusDays(1).atStartOfDay(VN_ZONE).toInstant();
        var rows = sellerQueries.findShopReviewTrend(shopId, normalizedPeriod, fromInstant, toInstant);
        var points = new ArrayList<SellerReviewTrendResponse.Point>(rows.size());
        for (var row : rows) {
            points.add(new SellerReviewTrendResponse.Point(
                row.label(),
                row.reviewCount(),
                roundTwoDecimals(row.averageRating())
            ));
        }

        return new SellerReviewTrendResponse(normalizedPeriod, points);
    }

    @Transactional(readOnly = true)
    public List<ReviewReplyTemplateResponse> listReplyTemplates(
        @NonNull UUID userId,
        @NonNull UUID shopId
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);
        var list = templates.findByShopIdOrderByUpdatedAtDescCreatedAtDesc(shopId);
        var responses = new ArrayList<ReviewReplyTemplateResponse>(list.size());
        for (var template : list) {
            responses.add(toTemplateResponse(template));
        }
        return responses;
    }

    @Transactional
    public ReviewReplyTemplateResponse createReplyTemplate(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        @NonNull SaveReviewReplyTemplateRequest request
    ) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_REPLY);
        var title = normalizeTemplateTitle(request.title());
        var content = normalizeTemplateContent(request.content());

        if (templates.countByShopId(shopId) >= MAX_TEMPLATES_PER_SHOP) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Maximum 50 reply templates per shop"
            );
        }
        if (templates.existsDuplicateTitle(shopId, title)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Reply template title already exists"
            );
        }

        var saved = templates.save(ReviewReplyTemplate.create(shopId, userId, title, content));
        audit.record(
            shopId,
            userId,
            "REVIEW_TEMPLATE_CREATE",
            "REVIEW_TEMPLATE",
            saved.getId(),
            "Tạo mẫu phản hồi nhanh: " + title
        );

        return toTemplateResponse(saved);
    }

    @Transactional
    public ReviewReplyTemplateResponse updateReplyTemplate(
        @NonNull UUID userId,
        @NonNull UUID templateId,
        @NonNull SaveReviewReplyTemplateRequest request
    ) {
        var template = templates.findById(templateId).orElse(null);
        if (template == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply template not found");
        }
        access.requireShopPermission(userId, template.getShopId(), ShopPermission.REVIEW_REPLY);

        var title = normalizeTemplateTitle(request.title());
        var content = normalizeTemplateContent(request.content());

        if (templates.existsDuplicateTitleExcludingId(template.getShopId(), title, templateId)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Reply template title already exists"
            );
        }

        template.update(title, content);
        var saved = templates.save(template);
        audit.record(
            template.getShopId(),
            userId,
            "REVIEW_TEMPLATE_UPDATE",
            "REVIEW_TEMPLATE",
            saved.getId(),
            "Cập nhật mẫu phản hồi nhanh: " + title
        );

        return toTemplateResponse(saved);
    }

    @Transactional
    public void deleteReplyTemplate(
        @NonNull UUID userId,
        @NonNull UUID templateId
    ) {
        var template = templates.findById(templateId).orElse(null);
        if (template == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reply template not found");
        }
        access.requireShopPermission(userId, template.getShopId(), ShopPermission.REVIEW_REPLY);

        templates.delete(template);
        audit.record(
            template.getShopId(),
            userId,
            "REVIEW_TEMPLATE_DELETE",
            "REVIEW_TEMPLATE",
            template.getId(),
            "Xóa mẫu phản hồi nhanh: " + template.getTitle()
        );
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse shopReviewSummary(@NonNull UUID userId, @NonNull UUID shopId) {
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);
        return toSummaryResponse(reviews.summarizeByShopId(shopId));
    }

    @Transactional
    public SellerReviewResponse reply(
        @NonNull UUID userId,
        @NonNull UUID reviewId,
        @NonNull ReplyReviewRequest request
    ) {
        var found = requireShopReview(userId, reviewId);
        var replyText = normalizeReply(request.reply());
        found.review().reply(replyText);

        var saved = reviews.save(found.review());
        audit.record(
            found.shopId(),
            userId,
            "REVIEW_REPLY",
            "REVIEW",
            saved.getId(),
            "Phản hồi đánh giá: " + replyText
        );

        notifications.create(
            Set.of(saved.getBuyerId()),
            NotificationCategory.ACTIVITY,
            "REVIEW_REPLIED",
            "Shop đã phản hồi đánh giá của bạn",
            replyText,
            "/product/" + saved.getProductId(),
            saved.getId()
        );

        return toSellerResponse(saved, found.productName());
    }

    @Transactional
    public SellerReviewResponse report(
        @NonNull UUID userId,
        @NonNull UUID reviewId,
        @NonNull ReportReviewRequest request
    ) {
        var found = requireShopReview(userId, reviewId);
        found.review().report(request.reason());

        var saved = reviews.save(found.review());
        audit.record(
            found.shopId(),
            userId,
            "REVIEW_REPORT",
            "REVIEW",
            saved.getId(),
            "Báo cáo đánh giá: " + request.reason()
        );

        return toSellerResponse(saved, found.productName());
    }

    /**
     * Che tên người dùng kiểu Shopee (ví dụ "Minh Nguyễn" -> "M***n", "An" -> "A***").
     */
    public static String maskDisplayName(String displayName) {
        var trimmed = displayName.trim();
        if (trimmed.isEmpty()) {
            return "***";
        }
        var length = trimmed.length();
        var first = trimmed.substring(0, 1);
        if (length <= 2) {
            return first + "***";
        }
        var last = trimmed.substring(length - 1);
        return first + "***" + last;
    }

    private List<ReviewResponse> mapResponsesForViewer(
        List<Review> list,
        @Nullable UUID currentUserId,
        boolean maskAnonymous
    ) {
        Set<UUID> helpfulIds = Set.of();
        if (currentUserId != null && !list.isEmpty()) {
            var reviewIds = new ArrayList<UUID>(list.size());
            for (var review : list) {
                reviewIds.add(review.getId());
            }
            helpfulIds = helpfulRepository.findHelpfulReviewIds(currentUserId, reviewIds);
        }

        var responses = new ArrayList<ReviewResponse>(list.size());
        for (var review : list) {
            responses.add(toResponse(review, currentUserId, maskAnonymous, helpfulIds));
        }
        return responses;
    }

    private void requireVisible(UUID productId) {
        productStock.requireActiveProduct(productId);
    }

    private ShopReview requireShopReview(UUID userId, UUID reviewId) {
        var review = reviews.findById(reviewId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        var product = productStock.findProductById(review.getProductId()).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        access.requireShopPermission(userId, product.getShopId(), ShopPermission.REVIEW_REPLY);

        return new ShopReview(review, product.getShopId(), product.getName());
    }

    private static String normalizeReply(String rawReply) {
        var trimmed = rawReply.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REPLY_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Reply must be between 1 and 2000 characters"
            );
        }
        return trimmed;
    }

    private static String normalizeTemplateTitle(String rawTitle) {
        var trimmed = rawTitle.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TEMPLATE_TITLE_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Reply template title must be between 1 and 100 characters"
            );
        }
        return trimmed;
    }

    private static String normalizeTemplateContent(String rawContent) {
        var trimmed = rawContent.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_REPLY_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Reply template content must be between 1 and 2000 characters"
            );
        }
        return trimmed;
    }

    private static double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private static String normalizeAndValidateComment(String rawComment) {
        var trimmed = rawComment.trim();
        if (trimmed.length() < MIN_COMMENT_LENGTH || trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Review comment must be between 10 and 2000 characters"
            );
        }
        return trimmed;
    }

    private static String[] normalizeAndValidateImageKeys(@Nullable List<String> rawImageKeys) {
        if (rawImageKeys == null || rawImageKeys.isEmpty()) {
            return new String[0];
        }
        if (rawImageKeys.size() > MAX_REVIEW_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 5 review images allowed");
        }
        var keys = new LinkedHashSet<String>();
        for (var raw : rawImageKeys) {
            var trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image key must not be blank");
            }
            keys.add(trimmed);
        }
        return keys.toArray(String[]::new);
    }

    private static @Nullable String normalizeVideoKey(@Nullable String rawVideoKey) {
        if (rawVideoKey == null) {
            return null;
        }
        var trimmed = rawVideoKey.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    private record ShopReview(Review review, UUID shopId, String productName) {
    }

    private static ReviewReplyTemplateResponse toTemplateResponse(ReviewReplyTemplate template) {
        return new ReviewReplyTemplateResponse(
            template.getId(),
            template.getShopId(),
            template.getTitle(),
            template.getContent(),
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }

    private static ReviewSummaryResponse toSummaryResponse(ReviewRepository.ReviewSummaryRow row) {
        return new ReviewSummaryResponse(
            row.reviewCount(),
            row.averageRating(),
            row.fiveStarCount(),
            row.fourStarCount(),
            row.threeStarCount(),
            row.twoStarCount(),
            row.oneStarCount(),
            row.withMediaCount()
        );
    }

    private static List<String> toStringList(String @Nullable [] array) {
        if (array == null || array.length == 0) {
            return List.of();
        }
        var list = new ArrayList<String>(array.length);
        Collections.addAll(list, array);
        return Collections.unmodifiableList(list);
    }

    private static ReviewResponse toResponse(
        Review review,
        @Nullable UUID currentUserId,
        boolean maskAnonymous,
        Set<UUID> helpfulIds
    ) {
        var editedAt = review.getEditedAt();
        var edited = editedAt != null;
        var displayName = review.getBuyerDisplayName();
        if (maskAnonymous && review.isAnonymous()) {
            displayName = maskDisplayName(displayName);
        }
        var mine = currentUserId != null && review.getBuyerId().equals(currentUserId);
        var helpfulByMe = helpfulIds.contains(review.getId());

        return new ReviewResponse(
            review.getId(),
            review.getProductId(),
            review.getOrderId(),
            displayName,
            review.getVariantLabel(),
            review.getStars(),
            review.getComment(),
            toStringList(review.getImageKeys()),
            review.getVideoKey(),
            toStringList(review.getTags()),
            review.isAnonymous(),
            mine,
            review.getHelpfulCount(),
            helpfulByMe,
            review.getSellerReply(),
            review.getRepliedAt(),
            edited,
            editedAt,
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }

    private static SellerReviewResponse toSellerResponse(Review review, String productName) {
        var editedAt = review.getEditedAt();
        var edited = editedAt != null;
        var displayName = review.getBuyerDisplayName();
        if (review.isAnonymous()) {
            displayName = maskDisplayName(displayName);
        }

        return new SellerReviewResponse(
            review.getId(),
            review.getProductId(),
            productName,
            review.getOrderId(),
            displayName,
            review.getVariantLabel(),
            review.getStars(),
            review.getComment(),
            toStringList(review.getImageKeys()),
            review.getVideoKey(),
            toStringList(review.getTags()),
            review.isAnonymous(),
            review.getHelpfulCount(),
            review.getSellerReply(),
            review.getRepliedAt(),
            review.isReported(),
            edited,
            editedAt,
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }
}
