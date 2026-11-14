package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.RatingSummary;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewHelpfulResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewMediaResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.ReviewSummaryResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.review.repository.ReviewHelpfulRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewMediaRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.features.upload.S3UrlService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.infrastructure.media.VideoMetadataService;
import com.minhnbnt.fashionstore.model.Review;
import com.minhnbnt.fashionstore.model.ReviewMedia;
import com.minhnbnt.fashionstore.model.ReviewSort;
import com.minhnbnt.fashionstore.model.ReviewTag;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.Upload;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    public static final int MIN_COMMENT_LENGTH = 10;
    public static final int MAX_COMMENT_LENGTH = 2000;
    public static final int MAX_REVIEW_IMAGES = 5;
    public static final double MAX_REVIEW_VIDEO_SECONDS = 30.0;

    private final ReviewRepository reviews;

    private final ReviewHelpfulRepository helpfulRepository;

    private final ReviewMediaRepository media;

    private final ProductStockService productStock;

    private final OrderService orderService;

    private final UserService users;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    private final NotificationService notifications;

    private final VideoMetadataService videoMetadataService;

    private final UploadRepository uploads;

    private final S3UrlService s3Urls;

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
        var imageUploadIds = validateImageUploads(request.imageUploadIds());
        var videoUpload = validateVideoUpload(request.videoUploadId());
        if (videoUpload != null) {
            videoMetadataService.validateVideoDuration(videoUpload.getS3Key(), MAX_REVIEW_VIDEO_SECONDS);
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
            normalizedTags,
            request.anonymous()
        );
        var saved = reviews.save(review);
        saveMedia(saved.getId(), imageUploadIds, videoUpload);

        return toSingleResponse(saved, buyerId, false, Set.of());
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
        var imageUploadIds = validateImageUploads(request.imageUploadIds());
        var videoUpload = validateVideoUpload(request.videoUploadId());
        if (videoUpload != null && !videoUpload.getId().equals(videoUploadIdOf(reviewId))) {
            videoMetadataService.validateVideoDuration(videoUpload.getS3Key(), MAX_REVIEW_VIDEO_SECONDS);
        }
        var normalizedTags = ReviewTag.normalizeAndValidate(request.tags());

        review.update(
            request.stars(),
            normalizedComment,
            normalizedTags,
            request.anonymous()
        );

        var saved = reviews.save(review);
        media.deleteByReviewId(saved.getId());
        saveMedia(saved.getId(), imageUploadIds, videoUpload);

        var helpfulIds = helpfulRepository.findHelpfulReviewIds(buyerId, List.of(saved.getId()));
        return toSingleResponse(saved, buyerId, false, helpfulIds);
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
        access.requireShopPermission(userId, shopId, ShopPermission.REVIEW_READ);

        List<Review> list;
        if (onlyUnreplied) {
            list = reviews.findUnrepliedByShopIdOrderByCreatedAtDesc(shopId);
        } else {
            list = reviews.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        var grouped = mediaByReviewId(list);
        var urls = urlMapForGroups(grouped);

        var responses = new ArrayList<SellerReviewResponse>();
        for (var review : list) {
            responses.add(toSellerResponse(review, grouped.getOrDefault(review.getId(), List.of()), urls));
        }

        return responses;
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
        found.review().reply(request.reply());

        var saved = reviews.save(found.review());
        audit.record(
            found.shopId(),
            userId,
            "REVIEW_REPLY",
            "REVIEW",
            saved.getId(),
            "Phản hồi đánh giá: " + request.reply()
        );

        notifications.create(
            Set.of(saved.getBuyerId()),
            NotificationCategory.ACTIVITY,
            "REVIEW_REPLIED",
            "Shop đã phản hồi đánh giá của bạn",
            request.reply(),
            "/product/" + saved.getProductId(),
            saved.getId()
        );

        return toSingleSellerResponse(saved);
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

        return toSingleSellerResponse(saved);
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

        var grouped = mediaByReviewId(list);
        var urls = urlMapForGroups(grouped);

        var responses = new ArrayList<ReviewResponse>(list.size());
        for (var review : list) {
            responses.add(
                toResponse(review, grouped.getOrDefault(review.getId(), List.of()), urls, currentUserId, maskAnonymous, helpfulIds));
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

        return new ShopReview(review, product.getShopId());
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

    /**
     * Chỉ nhận ảnh trình duyệt render được. Bỏ {@code image/svg+xml}: bucket public read,
     * mở thẳng URL thì script trong file chạy được (cùng lý do với avatar, ảnh sản phẩm).
     */
    private static boolean isSupportedImage(String contentType) {

        return contentType.startsWith("image/") && !contentType.equals("image/svg+xml");
    }

    private List<UUID> validateImageUploads(@Nullable List<UUID> rawIds) {

        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        // Giữ behavior cũ của normalizeAndValidateImageKeys: dedupe giữ thứ tự.
        var ids = new LinkedHashSet<UUID>(rawIds);
        if (ids.size() > MAX_REVIEW_IMAGES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum 5 review images allowed");
        }
        for (var id : ids) {
            var upload = uploads.findById(id).orElse(null);
            if (upload == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review image upload not found");
            }
            if (!isSupportedImage(upload.getContentType())) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Review image upload must be a supported image");
            }
        }
        return List.copyOf(ids);
    }

    private @Nullable Upload validateVideoUpload(@Nullable UUID videoUploadId) {

        if (videoUploadId == null) {
            return null;
        }
        var upload = uploads.findById(videoUploadId).orElse(null);
        if (upload == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review video upload not found");
        }
        if (!upload.getContentType().startsWith("video/")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Review video upload must be a video");
        }
        return upload;
    }

    private void saveMedia(UUID reviewId, List<UUID> imageUploadIds, @Nullable Upload videoUpload) {

        var rows = new ArrayList<ReviewMedia>(imageUploadIds.size() + 1);
        for (var i = 0; i < imageUploadIds.size(); i++) {
            rows.add(ReviewMedia.create(reviewId, imageUploadIds.get(i), ReviewMedia.KIND_IMAGE, i));
        }
        if (videoUpload != null) {
            // Video xếp sau toàn bộ ảnh trong thứ tự hiển thị.
            rows.add(ReviewMedia.create(reviewId, videoUpload.getId(), ReviewMedia.KIND_VIDEO, imageUploadIds.size()));
        }
        media.saveAll(rows);
    }

    private @Nullable UUID videoUploadIdOf(UUID reviewId) {

        for (var m : media.findByReviewIdOrderBySortOrderAsc(reviewId)) {
            if (ReviewMedia.KIND_VIDEO.equals(m.getKind())) {
                return m.getUploadId();
            }
        }
        return null;
    }

    private record ShopReview(Review review, UUID shopId) {
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

    private Map<UUID, List<ReviewMedia>> mediaByReviewId(List<Review> list) {

        if (list.isEmpty()) {
            return Map.of();
        }
        var ids = new ArrayList<UUID>(list.size());
        for (var review : list) {
            ids.add(review.getId());
        }
        var grouped = new HashMap<UUID, List<ReviewMedia>>();
        for (var m : media.findByReviewIdIn(ids)) {
            grouped.computeIfAbsent(m.getReviewId(), key -> new ArrayList<>()).add(m);
        }
        for (var group : grouped.values()) {
            group.sort(Comparator.comparingInt(ReviewMedia::getSortOrder));
        }
        return grouped;
    }

    /** Batch uploadId -> URL public cho một nhóm media. */
    private Map<UUID, String> urlMapFor(Collection<ReviewMedia> mediaList) {

        var uploadIds = new ArrayList<UUID>(mediaList.size());
        for (var m : mediaList) {
            uploadIds.add(m.getUploadId());
        }
        var urls = new HashMap<UUID, String>();
        for (var upload : uploads.findAllById(uploadIds)) {
            urls.put(upload.getId(), s3Urls.publicUrl(upload.getS3Key()));
        }
        return urls;
    }

    private Map<UUID, String> urlMapForGroups(Map<UUID, List<ReviewMedia>> grouped) {

        var all = new ArrayList<ReviewMedia>();
        for (var group : grouped.values()) {
            all.addAll(group);
        }
        return urlMapFor(all);
    }

    private List<ReviewMediaResponse> toMediaResponses(List<ReviewMedia> mediaList, Map<UUID, String> urls) {

        var responses = new ArrayList<ReviewMediaResponse>(mediaList.size());
        for (var m : mediaList) {
            // FK + validate phía request bảo đảm upload tồn tại; thiếu là lỗi dữ liệu, fail loud.
            responses.add(new ReviewMediaResponse(
                Objects.requireNonNull(urls.get(m.getUploadId())),
                m.getKind(),
                m.getUploadId()));
        }
        return responses;
    }

    private ReviewResponse toSingleResponse(
        Review review,
        @Nullable UUID currentUserId,
        boolean maskAnonymous,
        Set<UUID> helpfulIds
    ) {
        var mediaList = media.findByReviewIdOrderBySortOrderAsc(review.getId());
        return toResponse(review, mediaList, urlMapFor(mediaList), currentUserId, maskAnonymous, helpfulIds);
    }

    private SellerReviewResponse toSingleSellerResponse(Review review) {

        var mediaList = media.findByReviewIdOrderBySortOrderAsc(review.getId());
        return toSellerResponse(review, mediaList, urlMapFor(mediaList));
    }

    private ReviewResponse toResponse(
        Review review,
        List<ReviewMedia> mediaList,
        Map<UUID, String> urls,
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
            toMediaResponses(mediaList, urls),
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

    private SellerReviewResponse toSellerResponse(
        Review review,
        List<ReviewMedia> mediaList,
        Map<UUID, String> urls
    ) {
        var editedAt = review.getEditedAt();
        var edited = editedAt != null;
        var displayName = review.getBuyerDisplayName();
        if (review.isAnonymous()) {
            displayName = maskDisplayName(displayName);
        }

        return new SellerReviewResponse(
            review.getId(),
            review.getProductId(),
            review.getOrderId(),
            displayName,
            review.getVariantLabel(),
            review.getStars(),
            review.getComment(),
            toMediaResponses(mediaList, urls),
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
