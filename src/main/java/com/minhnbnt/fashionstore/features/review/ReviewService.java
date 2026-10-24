package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.RatingSummary;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReviewResponse;
import com.minhnbnt.fashionstore.dto.review.SellerReviewResponse;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.OrderItemRepository;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ProductStatus;
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

    private final ProductRepository products;

    private final OrderRepository orders;

    private final OrderItemRepository items;

    private final UserService users;

    private final ShopService shops;

    private final ShopAccessChecker access;

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
        if (products.findById(productId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (reviews.findByProductIdAndBuyerId(productId, buyerId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this product");
        }
        if (!hasDeliveredAndReceived(buyerId, productId)) {
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

        var list = onlyUnreplied
            ? reviews.findUnrepliedByShopIdOrderByCreatedAtDesc(shopId)
            : reviews.findByShopIdOrderByCreatedAtDesc(shopId);

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
        var review = requireShopReview(userId, reviewId);
        review.reply(request.reply());

        return toSellerResponse(reviews.save(review));
    }

    @Transactional
    public SellerReviewResponse report(
        @NonNull UUID userId,
        @NonNull UUID reviewId,
        @NonNull ReportReviewRequest request
    ) {
        var review = requireShopReview(userId, reviewId);
        review.report(request.reason());

        return toSellerResponse(reviews.save(review));
    }

    private boolean hasDeliveredAndReceived(UUID buyerId, UUID productId) {
        for (var order : orders.findByBuyerIdOrderByCreatedAtDesc(buyerId)) {
            if (order.getStatus() != OrderStatus.DELIVERED) {
                continue;
            }
            if (order.getReceivedAt() == null) {
                continue;
            }
            for (var item : items.findByOrderId(order.getId())) {
                if (productId.equals(item.getProductId())) {
                    return true;
                }
            }
        }

        return false;
    }

    private void requireVisible(UUID productId) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (!shops.isShopApproved(product.getShopId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    private Review requireOwnReview(UUID buyerId, UUID productId) {
        var review = reviews.findByProductIdAndBuyerId(productId, buyerId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        return review;
    }

    private Review requireShopReview(UUID userId, UUID reviewId) {
        var review = reviews.findById(reviewId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        var product = products.findById(review.getProductId()).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }

        access.requireShopPermission(userId, product.getShopId(), ShopPermission.REVIEW_REPLY);

        return review;
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
