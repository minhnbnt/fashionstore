package com.minhnbnt.fashionstore.features.review;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.review.CreateReviewRequest;
import com.minhnbnt.fashionstore.dto.review.UpdateReviewRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
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
    private ReviewService reviews;

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "U", null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, new CreateShopRequest("Shop " + slug, slug, null, null, null));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID variantInShop(UUID ownerId, UUID shopId, String slug, String sku) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            new CreateProductRequest(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Den", 100000L, null, 10, true))
            )
        );
        var detail = catalog.getDetail(created.id());
        return detail.variants().get(0).id();
    }

    private UUID checkout(UUID buyerId, UUID shopId, UUID variantId) {
        return orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        ).id();
    }

    private void deliverAndReceive(UUID ownerId, UUID buyerId, UUID orderId) {
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        orders.confirmReceipt(buyerId, orderId);
    }

    private UUID deliveredProduct(UUID ownerId, UUID buyerId, UUID shopId, String slug, String sku) {
        var variantId = variantInShop(ownerId, shopId, slug, sku);
        return purchaseVariant(ownerId, buyerId, shopId, variantId);
    }

    private UUID purchaseVariant(UUID ownerId, UUID buyerId, UUID shopId, UUID variantId) {
        var orderId = checkout(buyerId, shopId, variantId);
        deliverAndReceive(ownerId, buyerId, orderId);
        return Objects.requireNonNull(orders.getDetail(buyerId, orderId).items().get(0).productId());
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void createReviewAfterDeliveryAndReceipt() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-a");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-a", "SKU-REV-A");

        var response = reviews.createReview(buyerId, productId, new CreateReviewRequest(5, "Vai dep"));

        assertThat(response.stars()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Vai dep");
        assertThat(response.buyerDisplayName()).isEqualTo("U");
    }

    @Test
    void createWithoutPurchaseFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-b");
        var productId = deliveredProduct(ownerId, mirroredUser(), shopId, "ao-rev-b", "SKU-REV-B");

        assertThatThrownBy(() -> reviews.createReview(buyerId, productId, new CreateReviewRequest(5, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));
    }

    @Test
    void createWithPendingOrderFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-c");
        var variantId = variantInShop(ownerId, shopId, "ao-rev-c", "SKU-REV-C");
        checkout(buyerId, shopId, variantId);
        var productId = Objects.requireNonNull(
            orders.getDetail(buyerId, orders.getMine(buyerId).get(0).id()).items().get(0).productId()
        );

        assertThatThrownBy(() -> reviews.createReview(buyerId, productId, new CreateReviewRequest(4, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));
    }

    @Test
    void createDuplicateFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-d");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-d", "SKU-REV-D");
        reviews.createReview(buyerId, productId, new CreateReviewRequest(5, null));

        assertThatThrownBy(() -> reviews.createReview(buyerId, productId, new CreateReviewRequest(4, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void updateOwnReview() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-e");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-e", "SKU-REV-E");
        reviews.createReview(buyerId, productId, new CreateReviewRequest(3, "Tam on"));

        var updated = reviews.updateReview(buyerId, productId, new UpdateReviewRequest(5, "Xuat sac"));

        assertThat(updated.stars()).isEqualTo(5);
        assertThat(updated.comment()).isEqualTo("Xuat sac");
    }

    @Test
    void updateOthersReviewFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-f");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-f", "SKU-REV-F");
        reviews.createReview(buyerId, productId, new CreateReviewRequest(3, null));

        assertThatThrownBy(() -> reviews.updateReview(mirroredUser(), productId, new UpdateReviewRequest(5, null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteOwnReview() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-g");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-g", "SKU-REV-G");
        reviews.createReview(buyerId, productId, new CreateReviewRequest(4, null));

        reviews.deleteReview(buyerId, productId);

        assertThat(reviews.getReviews(productId)).isEmpty();
    }

    @Test
    void deleteOthersReviewFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-h");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-h", "SKU-REV-H");
        reviews.createReview(buyerId, productId, new CreateReviewRequest(4, null));

        assertThatThrownBy(() -> reviews.deleteReview(mirroredUser(), productId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void listShowsNewestFirst() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-i");
        var buyerA = mirroredUser();
        var buyerB = mirroredUser();
        var variantId = variantInShop(ownerId, shopId, "ao-rev-i", "SKU-REV-I");
        var productA = purchaseVariant(ownerId, buyerA, shopId, variantId);
        purchaseVariant(ownerId, buyerB, shopId, variantId);
        reviews.createReview(buyerA, productA, new CreateReviewRequest(3, "Dau tien"));
        reviews.createReview(buyerB, productA, new CreateReviewRequest(5, "Thu hai"));

        var list = reviews.getReviews(productA);

        assertThat(list).hasSize(2);
        assertThat(list.get(0).comment()).isEqualTo("Thu hai");
        assertThat(list.get(1).comment()).isEqualTo("Dau tien");
    }

    @Test
    void listHiddenProductFails() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-j");
        var productId = deliveredProduct(ownerId, buyerId, shopId, "ao-rev-j", "SKU-REV-J");
        reviews.createReview(buyerId, productId, new CreateReviewRequest(4, null));
        var created = catalog.getDetail(productId);
        catalog.updateProduct(ownerId, shopId, productId, new UpdateProductRequest(null, null, null, "HIDDEN", null));
        assertThat(created.id()).isNotNull();

        assertThatThrownBy(() -> reviews.getReviews(productId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void confirmReceiptRequiresDelivered() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-k");
        var variantId = variantInShop(ownerId, shopId, "ao-rev-k", "SKU-REV-K");
        var orderId = checkout(buyerId, shopId, variantId);

        assertThatThrownBy(() -> orders.confirmReceipt(buyerId, orderId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void productDetailShowsRating() {
        var ownerId = mirroredUser();
        var buyerA = mirroredUser();
        var buyerB = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-rev-l");
        var variantId = variantInShop(ownerId, shopId, "ao-rev-l", "SKU-REV-L");
        var productA = purchaseVariant(ownerId, buyerA, shopId, variantId);
        purchaseVariant(ownerId, buyerB, shopId, variantId);
        reviews.createReview(buyerA, productA, new CreateReviewRequest(5, null));
        reviews.createReview(buyerB, productA, new CreateReviewRequest(3, null));

        var detail = catalog.getDetail(productA);

        assertThat(detail.reviewCount()).isEqualTo(2L);
        assertThat(detail.averageRating()).isEqualTo(4.0);
    }
}
