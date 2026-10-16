package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.CreateProductReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ProductReviewResponse;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.review.repository.ProductReviewRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.model.ProductReview;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviews;

    private final OrderRepository orders;

    private final ShopRepository shops;

    private final ShopAccessChecker access;

    @Transactional
    public ProductReviewResponse create(
        @NonNull UUID buyerId,
        @NonNull UUID orderId,
        @NonNull CreateProductReviewRequest request
    ) {
        var order = orders.findById(orderId).orElse(null);
        if (order == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (!order.getBuyerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Review can only be submitted for delivered orders"
            );
        }

        var existing = reviews.findByOrderIdAndProductId(orderId, request.productId()).orElse(null);
        if (existing != null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "This product has already been reviewed for this order"
            );
        }

        String[] imgArr = new String[0];
        if (request.imageKeys() != null) {
            imgArr = request.imageKeys().toArray(new String[0]);
        }

        var review = ProductReview.create(
            order.getId(),
            request.productId(),
            order.getShopId(),
            buyerId,
            request.rating(),
            request.comment(),
            imgArr
        );
        reviews.save(review);

        var shop = shops.findById(order.getShopId()).orElse(null);
        if (shop != null) {
            var avg = reviews.avgRatingForShop(shop.getId());
            var count = reviews.countReviewsForShop(shop.getId());
            shop.updateRating(avg, count);
            shops.save(shop);
        }

        return toResponse(review);
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> listByProduct(@NonNull UUID productId) {
        return reviews.findByProductIdOrderByCreatedAtDesc(productId).stream()
            .map(ProductReviewService::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductReviewResponse> listSellerReviews(
        @NonNull UUID userId,
        @NonNull UUID shopId,
        boolean onlyUnreplied
    ) {
        access.requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);

        List<ProductReview> list;
        if (onlyUnreplied) {
            list = reviews.findByShopIdAndSellerReplyIsNullOrderByCreatedAtDesc(shopId);
        } else {
            list = reviews.findByShopIdOrderByCreatedAtDesc(shopId);
        }

        return list.stream()
            .map(ProductReviewService::toResponse)
            .toList();
    }

    @Transactional
    public ProductReviewResponse reply(
        @NonNull UUID userId,
        @NonNull UUID reviewId,
        @NonNull ReplyReviewRequest request
    ) {
        var review = reviews.findById(reviewId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }
        access.requireShopRole(userId, review.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        review.reply(request.reply());
        return toResponse(reviews.save(review));
    }

    @Transactional
    public ProductReviewResponse report(
        @NonNull UUID userId,
        @NonNull UUID reviewId,
        @NonNull ReportReviewRequest request
    ) {
        var review = reviews.findById(reviewId).orElse(null);
        if (review == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found");
        }
        access.requireShopRole(userId, review.getShopId(), MemberRole.OWNER, MemberRole.STAFF);

        review.report(request.reason());
        return toResponse(reviews.save(review));
    }

    private static ProductReviewResponse toResponse(ProductReview r) {
        List<String> images = List.of();
        if (r.getImageKeys() != null) {
            images = Arrays.asList(r.getImageKeys());
        }

        return new ProductReviewResponse(
            r.getId(),
            r.getOrderId(),
            r.getProductId(),
            r.getShopId(),
            r.getBuyerId(),
            r.getRating(),
            r.getComment(),
            images,
            r.getSellerReply(),
            r.getRepliedAt(),
            r.isReported(),
            r.getCreatedAt()
        );
    }
}
