package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.RatingSummary;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.ProductStockService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.features.shop.ShopAuditService;
import com.minhnbnt.fashionstore.model.Review;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviews;

    private final ProductStockService productStock;

    private final OrderService orderService;

    private final UserService users;

    private final ShopAccessChecker access;

    private final ShopAuditService audit;

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(@NonNull UUID productId) {
        requireVisible(productId);

        var responses = new ArrayList<ReviewResponse>();
        for (var review : reviews.findByProductIdOrderByCreatedAtDesc(productId)) {
            responses.add(toResponse(review));
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public RatingSummary ratingSummary(@NonNull UUID productId) {
        var all = reviews.findByProductIdOrderByCreatedAtDesc(productId);
        if (all.isEmpty()) {
            return new RatingSummary(0, null);
        }

        var sum = 0L;
        for (var review : all) {
            sum += review.getStars();
        }

        return new RatingSummary(all.size(), (double) sum / all.size());
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
        if (reviews.findByProductIdAndBuyerId(productId, buyerId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        }
        if (!orderService.hasDeliveredAndReceivedProduct(buyerId, productId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only delivered products can be reviewed");
        }

        var displayName = users.getById(buyerId).displayName();
        var review = Review.create(productId, buyerId, displayName, request.stars(), request.comment());

        return toResponse(reviews.save(review));
    }

    @Transactional
    public ReviewResponse updateReview(
        @NonNull UUID buyerId,
        @NonNull UUID productId,
        @NonNull UpdateReviewRequest request
    ) {
        var review = requireOwnReview(buyerId, productId);
        review.update(request.stars(), request.comment());

        return toResponse(reviews.save(review));
    }

    @Transactional
    public void deleteReview(@NonNull UUID buyerId, @NonNull UUID productId) {
        reviews.delete(requireOwnReview(buyerId, productId));
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

        var responses = new ArrayList<SellerReviewResponse>();
        for (var review : list) {
            responses.add(toSellerResponse(review));
        }

        return responses;
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

        return toSellerResponse(saved);
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

        return toSellerResponse(saved);
    }

    private void requireVisible(UUID productId) {
        productStock.requireActiveProduct(productId);
    }

    private Review requireOwnReview(UUID buyerId, UUID productId) {
        var review = reviews.findByProductIdAndBuyerId(productId, buyerId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        return review;
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

    private record ShopReview(Review review, UUID shopId) {
    }

    private static ReviewResponse toResponse(Review review) {
        return new ReviewResponse(
            review.getId(),
            review.getProductId(),
            review.getBuyerDisplayName(),
            review.getStars(),
            review.getComment(),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }

    private static SellerReviewResponse toSellerResponse(Review review) {
        return new SellerReviewResponse(
            review.getId(),
            review.getProductId(),
            review.getBuyerDisplayName(),
            review.getStars(),
            review.getComment(),
            review.getSellerReply(),
            review.getRepliedAt(),
            review.isReported(),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }
}
