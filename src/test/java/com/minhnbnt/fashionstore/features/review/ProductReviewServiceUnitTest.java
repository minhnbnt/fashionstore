package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.dto.review.CreateProductReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReportReviewRequest;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.review.repository.ProductReviewRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRepository;
import com.minhnbnt.fashionstore.model.MemberRole;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.ProductReview;
import com.minhnbnt.fashionstore.model.Shop;
import com.minhnbnt.fashionstore.security.ShopAccessChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceUnitTest {

    @Mock
    private ProductReviewRepository reviews;

    @Mock
    private OrderRepository orders;

    @Mock
    private ShopRepository shops;

    @Mock
    private ShopAccessChecker access;

    @InjectMocks
    private ProductReviewService reviewService;

    @Test
    void createReviewThrowsConflictWhenOrderNotDelivered() {
        var buyerId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var productId = UUID.randomUUID();

        var order = Order.create("ORD-01", shopId, buyerId, 100000L, 0L, "Buyer", "0900", "Address");

        when(orders.findById(orderId)).thenReturn(Optional.of(order));

        var req = new CreateProductReviewRequest(productId, (short) 5, "Great product!", List.of());

        assertThatThrownBy(() -> reviewService.create(buyerId, orderId, req))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createReviewSucceedsAndUpdatesShopRating() {
        var buyerId = UUID.randomUUID();
        var orderId = UUID.randomUUID();
        var productId = UUID.randomUUID();
        var shop = Shop.create("Fashion Hub", "fashion-hub", "Mo ta", null, null);
        var shopId = shop.getId();

        var order = Order.create("ORD-02", shopId, buyerId, 100000L, 0L, "Buyer", "0900", "Address");
        order.confirm();
        order.ship();
        order.deliver();

        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(reviews.findByOrderIdAndProductId(orderId, productId)).thenReturn(Optional.empty());
        when(shops.findById(shopId)).thenReturn(Optional.of(shop));
        when(reviews.avgRatingForShop(shopId)).thenReturn(5.0);
        when(reviews.countReviewsForShop(shopId)).thenReturn(1);

        var req = new CreateProductReviewRequest(productId, (short) 5, "High quality clothing", List.of("img.jpg"));
        var resp = reviewService.create(buyerId, orderId, req);

        assertThat(resp.rating()).isEqualTo((short) 5);
        assertThat(resp.comment()).isEqualTo("High quality clothing");
        verify(reviews).save(any(ProductReview.class));
        verify(shops).save(shop);
    }

    @Test
    void sellerReplyUpdatesReviewResponse() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var reviewId = UUID.randomUUID();

        var review = ProductReview.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            shopId,
            UUID.randomUUID(),
            (short) 5,
            "Good",
            new String[0]
        );

        when(reviews.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviews.save(any(ProductReview.class))).thenAnswer(i -> i.getArgument(0));

        var req = new ReplyReviewRequest("Thank you for your purchase!");
        var resp = reviewService.reply(userId, reviewId, req);

        verify(access).requireShopRole(userId, shopId, MemberRole.OWNER, MemberRole.STAFF);
        assertThat(resp.sellerReply()).isEqualTo("Thank you for your purchase!");
        assertThat(resp.repliedAt()).isNotNull();
    }

    @Test
    void sellerReportSetsReportFlag() {
        var userId = UUID.randomUUID();
        var shopId = UUID.randomUUID();
        var reviewId = UUID.randomUUID();

        var review = ProductReview.create(
            UUID.randomUUID(),
            UUID.randomUUID(),
            shopId,
            UUID.randomUUID(),
            (short) 1,
            "Competitor spamming",
            new String[0]
        );

        when(reviews.findById(reviewId)).thenReturn(Optional.of(review));
        when(reviews.save(any(ProductReview.class))).thenAnswer(i -> i.getArgument(0));

        var req = new ReportReviewRequest("Spam review from rival store");
        var resp = reviewService.report(userId, reviewId, req);

        assertThat(resp.isReported()).isTrue();
    }
}
