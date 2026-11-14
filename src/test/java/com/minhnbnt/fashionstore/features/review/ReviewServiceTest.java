package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationRepository;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ReviewSort;
import com.minhnbnt.fashionstore.model.ReviewTag;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReviewServiceTest {

    @Autowired

    private UploadRepository uploads;


    @Autowired
    private ReviewService reviews;

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private NotificationRepository notifications;

    private record PurchasedItem(UUID orderId, UUID productId, UUID variantId) {
    }

    private UUID mirroredUser() {
        return mirroredUser("Minh Nguyễn");
    }

    private UUID mirroredUser(String displayName) {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", displayName));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest(uploads, "Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, String sku) {
        return variantInShop(ownerId, shopId, slug, sku, "M", "Đen");
    }

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, String sku, String size, String color) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, size, color, 100000L, null, 10, true))
            )
        );
        var detail = catalog.getDetail(created.id());
        return detail.variants().get(0).id();
    }

    private UUID checkout(UUID buyerId, UUID shopId, List<CheckoutItemRequest> items) {
        return orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, items, "An", "0901", "Ha Noi", 0L)
        ).id();
    }

    private UUID checkout(UUID buyerId, UUID shopId, UUID variantId) {
        return checkout(buyerId, shopId, List.of(new CheckoutItemRequest(variantId, 1)));
    }

    private void deliverAndReceive(UUID ownerId, UUID buyerId, UUID orderId) {
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        orders.confirmReceipt(buyerId, orderId);
    }

    private PurchasedItem purchaseVariant(UUID ownerId, UUID buyerId, UUID shopId, UUID variantId) {
        var orderId = checkout(buyerId, shopId, variantId);
        deliverAndReceive(ownerId, buyerId, orderId);
        var productId = Objects.requireNonNull(orders.getDetail(buyerId, orderId).items().get(0).productId());
        return new PurchasedItem(orderId, productId, variantId);
    }

    private PurchasedItem deliveredProduct(UUID ownerId, UUID buyerId, UUID shopId, String slug, String sku) {
        var variantId = variantInShop(ownerId, shopId, slug, sku);
        return purchaseVariant(ownerId, buyerId, shopId, variantId);
    }

    private static CreateReviewRequest validRequest(UUID orderId, int stars, String comment) {
        return new CreateReviewRequest(orderId, stars, comment, null, null, null, false);
    }

    private static UpdateReviewRequest validUpdate(int stars, String comment) {
        return new UpdateReviewRequest(stars, comment, null, null, null, false);
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void createReviewAfterDeliveryAndReceiptSnapshotsVariantAndTags() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser("Minh Nguyễn");
        var shopId = approvedShop(ownerId, "shop-rev-a");
        var purchased = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-a", "SKU-REV-A");

        var response = reviews.createReview(
            buyerId,
            purchased.productId(),
            new CreateReviewRequest(
                purchased.orderId(),
                5,
                "Vải dày dặn, đường may rất chắc chắn.",
                List.of("reviews/img-1.jpg", "reviews/img-2.jpg"),
                null,
                List.of(ReviewTag.QUALITY_GOOD, ReviewTag.TRUE_TO_SIZE, ReviewTag.QUALITY_GOOD),
                false
            )
        );

        assertThat(response.orderId()).isEqualTo(purchased.orderId());
        assertThat(response.stars()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Vải dày dặn, đường may rất chắc chắn.");
        assertThat(response.buyerDisplayName()).isEqualTo("Minh Nguyễn");
        assertThat(response.variantLabel()).contains("M").contains("Đen");
        assertThat(response.imageKeys()).containsExactly("reviews/img-1.jpg", "reviews/img-2.jpg");
        assertThat(response.tags()).containsExactly(ReviewTag.QUALITY_GOOD, ReviewTag.TRUE_TO_SIZE);
        assertThat(response.mine()).isTrue();
    }

    @Test
    void createWithDifferentBuyerOrderThrowsNotFound() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-b");
        var purchased = deliveredProduct(ownerId, mirroredUser(), shopId, "ao-rev-b", "SKU-REV-B");

        assertThatThrownBy(() -> reviews.createReview(
            buyerId,
            purchased.productId(),
            validRequest(purchased.orderId(), 5, "Sản phẩm rất đẹp và vừa vặn.")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void createWithDeliveredButUnconfirmedReceiptFailsForbidden() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-c");
        var variantId = variantInShop(ownerId, shopId, "ao-rev-c", "SKU-REV-C");
        var orderId = checkout(buyerId, shopId, variantId);
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        var productId = Objects.requireNonNull(orders.getDetail(buyerId, orderId).items().get(0).productId());

        assertThatThrownBy(() -> reviews.createReview(
            buyerId,
            productId,
            validRequest(orderId, 4, "Đã giao nhưng chưa bấm xác nhận nhận hàng.")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));
    }

    @Test
    void createDuplicateForSameOrderFailsButSecondCompletedOrderSucceeds() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-d");
        var first = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-d", "SKU-REV-D");
        var second = purchaseVariant(ownerId, buyerId, shopId, first.variantId());

        reviews.createReview(buyerId, first.productId(), validRequest(first.orderId(), 5, "Đánh giá lần mua thứ nhất."));

        assertThatThrownBy(() -> reviews.createReview(
            buyerId,
            first.productId(),
            validRequest(first.orderId(), 4, "Đánh giá trùng trong cùng một đơn.")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        var secondReview = reviews.createReview(
            buyerId,
            first.productId(),
            validRequest(second.orderId(), 4, "Mua lại đơn thứ hai và đánh giá lại.")
        );
        assertThat(secondReview.orderId()).isEqualTo(second.orderId());
    }

    @Test
    void createOrderWithMultipleVariantsOfSameProductCombinesVariantLabelsOnce() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-mv");
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                "Ao hai size",
                "ao-hai-size",
                null,
                null,
                List.of(
                    new CreateVariantRequest("SKU-MV-1", "M", "Đen", 100000L, null, 10, true),
                    new CreateVariantRequest("SKU-MV-2", "L", "Trắng", 100000L, null, 10, false)
                )
            )
        );
        var variants = catalog.getDetail(created.id()).variants();
        var orderId = checkout(
            buyerId,
            shopId,
            List.of(
                new CheckoutItemRequest(variants.get(0).id(), 1),
                new CheckoutItemRequest(variants.get(1).id(), 2)
            )
        );
        deliverAndReceive(ownerId, buyerId, orderId);

        var createdReview = reviews.createReview(
            buyerId,
            created.id(),
            validRequest(orderId, 5, "Mua 2 phân loại khác màu đều rất ưng ý.")
        );

        assertThat(createdReview.variantLabel()).contains("M").contains("L");
    }

    @Test
    void createValidatesCommentMinLengthTagsAndMaxImages() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-val");
        var purchased = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-val", "SKU-REV-VAL");

        assertThatThrownBy(() -> reviews.createReview(
            buyerId,
            purchased.productId(),
            validRequest(purchased.orderId(), 5, "  Ngan  ")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> reviews.createReview(
            buyerId,
            purchased.productId(),
            new CreateReviewRequest(
                purchased.orderId(),
                5,
                "Nhận xét đủ 10 ký tự hợp lệ rồi.",
                null,
                null,
                List.of("Tag không hợp lệ"),
                false
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> reviews.createReview(
            buyerId,
            purchased.productId(),
            new CreateReviewRequest(
                purchased.orderId(),
                5,
                "Nhận xét đủ 10 ký tự hợp lệ rồi.",
                List.of("1.jpg", "2.jpg", "3.jpg", "4.jpg", "5.jpg", "6.jpg"),
                null,
                null,
                false
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateOwnReviewOnceByIdAndSecondEditFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-e");
        var purchased = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-e", "SKU-REV-E");
        var created = reviews.createReview(
            buyerId,
            purchased.productId(),
            validRequest(purchased.orderId(), 3, "Tạm ổn so với mức giá.")
        );
        assertThat(created.edited()).isFalse();

        var updated = reviews.updateReviewById(
            buyerId,
            created.id(),
            new UpdateReviewRequest(5, "Sau khi giặt vẫn giữ form xuất sắc.", List.of("a.jpg"), null, List.of(ReviewTag.QUALITY_GOOD), true)
        );

        assertThat(updated.stars()).isEqualTo(5);
        assertThat(updated.comment()).isEqualTo("Sau khi giặt vẫn giữ form xuất sắc.");
        assertThat(updated.tags()).containsExactly(ReviewTag.QUALITY_GOOD);
        assertThat(updated.anonymous()).isTrue();
        assertThat(updated.edited()).isTrue();
        assertThat(updated.editedAt()).isNotNull();

        assertThatThrownBy(() -> reviews.updateReviewById(buyerId, created.id(), validUpdate(4, "Sửa lần hai không được phép đâu.")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void anonymousMasksNameOnPublicAndSellerButNotMine() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser("Minh Nguyễn");
        var shopId = approvedShop(ownerId, "shop-rev-anon");
        var purchased = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-anon", "SKU-REV-ANON");

        reviews.createReview(
            buyerId,
            purchased.productId(),
            new CreateReviewRequest(purchased.orderId(), 5, "Sản phẩm rất đẹp và giao đúng hẹn.", null, null, null, true)
        );

        var publicList = reviews.getReviews(purchased.productId(), null, ReviewSort.HELPFUL, null, false);
        assertThat(publicList).hasSize(1);
        assertThat(publicList.get(0).buyerDisplayName()).isEqualTo("M***n");
        assertThat(publicList.get(0).mine()).isFalse();

        var sellerList = reviews.listForShop(ownerId, shopId, false);
        assertThat(sellerList).hasSize(1);
        assertThat(sellerList.get(0).buyerDisplayName()).isEqualTo("M***n");

        var mine = reviews.listMine(buyerId);
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).buyerDisplayName()).isEqualTo("Minh Nguyễn");
        assertThat(mine.get(0).anonymous()).isTrue();
    }

    @Test
    void helpfulToggleSortingFilteringAndSummariesWorkEndToEnd() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-helpful");
        var buyerA = mirroredUser("Buyer Alpha");
        var buyerB = mirroredUser("Buyer Beta");
        var liker1 = mirroredUser();
        var liker2 = mirroredUser();
        var variantId = variantInShop(ownerId, shopId, "ao-rev-h", "SKU-REV-HELP");
        var pA = purchaseVariant(ownerId, buyerA, shopId, variantId);
        var pB = purchaseVariant(ownerId, buyerB, shopId, variantId);

        var reviewA = reviews.createReview(
            buyerA,
            pA.productId(),
            new CreateReviewRequest(pA.orderId(), 3, "Bài viết đầu tiên có kèm ảnh thực tế.", List.of("rev-a.jpg"), null, null, false)
        );
        var reviewB = reviews.createReview(
            buyerB,
            pA.productId(),
            validRequest(pB.orderId(), 5, "Bài viết thứ hai mới hơn nhưng chưa có lượt thích.")
        );

        assertThatThrownBy(() -> reviews.toggleHelpful(buyerA, reviewA.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        var liked1 = reviews.toggleHelpful(liker1, reviewA.id());
        var liked2 = reviews.toggleHelpful(liker2, reviewA.id());
        assertThat(liked1.helpfulCount()).isEqualTo(1);
        assertThat(liked2.helpfulCount()).isEqualTo(2);
        assertThat(liked2.helpfulByMe()).isTrue();

        var byHelpful = reviews.getReviews(pA.productId(), liker1, ReviewSort.HELPFUL, null, false);
        assertThat(byHelpful).hasSize(2);
        assertThat(byHelpful.get(0).id()).isEqualTo(reviewA.id());
        assertThat(byHelpful.get(0).helpfulCount()).isEqualTo(2);
        assertThat(byHelpful.get(0).helpfulByMe()).isTrue();
        assertThat(byHelpful.get(1).id()).isEqualTo(reviewB.id());

        var byNewest = reviews.getReviews(pA.productId(), null, ReviewSort.NEWEST, null, false);
        assertThat(byNewest.get(0).id()).isEqualTo(reviewB.id());

        var onlyFiveStars = reviews.getReviews(pA.productId(), null, ReviewSort.HELPFUL, 5, false);
        assertThat(onlyFiveStars).extracting(r -> r.id()).containsExactly(reviewB.id());

        var onlyWithMedia = reviews.getReviews(pA.productId(), null, ReviewSort.HELPFUL, null, true);
        assertThat(onlyWithMedia).extracting(r -> r.id()).containsExactly(reviewA.id());

        var productSummary = reviews.reviewSummary(pA.productId());
        assertThat(productSummary.reviewCount()).isEqualTo(2L);
        assertThat(productSummary.averageRating()).isEqualTo(4.0);
        assertThat(productSummary.fiveStarCount()).isEqualTo(1L);
        assertThat(productSummary.threeStarCount()).isEqualTo(1L);
        assertThat(productSummary.withMediaCount()).isEqualTo(1L);

        var shopSummary = reviews.shopReviewSummary(ownerId, shopId);
        assertThat(shopSummary.reviewCount()).isEqualTo(2L);
        assertThat(shopSummary.averageRating()).isEqualTo(4.0);

        var unliked = reviews.toggleHelpful(liker1, reviewA.id());
        assertThat(unliked.helpfulCount()).isEqualTo(1);
        assertThat(unliked.helpfulByMe()).isFalse();
    }

    @Test
    void hiddenProductStillAllowsBuyerReviewButBlocksPublicListAndHelpful() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-hidden");
        var purchased = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-h1", "SKU-REV-H1");
        catalog.updateProduct(ownerId, shopId, purchased.productId(), new UpdateProductRequest(null, null, null, "HIDDEN", null));

        var created = reviews.createReview(buyerId, purchased.productId(), validRequest(purchased.orderId(), 5, "Vẫn đánh giá được khi sản phẩm ẩn."));
        var updated = reviews.updateReviewById(buyerId, created.id(), validUpdate(4, "Sửa lại đánh giá vẫn được cho phép."));
        assertThat(updated.edited()).isTrue();

        assertThatThrownBy(() -> reviews.getReviews(purchased.productId(), null, ReviewSort.HELPFUL, null, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> reviews.toggleHelpful(mirroredUser(), created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void sellerReplyIncludesReplyOnPublicReviewResponseAndNotifiesBuyer() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-notif-1");
        var purchased = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-notif-1", "SKU-REV-NOTIF-1");
        var review = reviews.createReview(buyerId, purchased.productId(), validRequest(purchased.orderId(), 5, "Sản phẩm rất tuyệt vời và đẹp."));

        reviews.reply(ownerId, review.id(), new ReplyReviewRequest("Cam on ban da ung ho shop."));

        var publicList = reviews.getReviews(purchased.productId(), null, ReviewSort.HELPFUL, null, false);
        assertThat(publicList.get(0).sellerReply()).isEqualTo("Cam on ban da ung ho shop.");
        assertThat(publicList.get(0).repliedAt()).isNotNull();

        var buyerPage = notifications.findPage(buyerId, List.of("ACTIVITY"), 0, 10);
        assertThat(buyerPage).hasSize(1);
        assertThat(buyerPage.getFirst().type()).isEqualTo("REVIEW_REPLIED");
    }
}
