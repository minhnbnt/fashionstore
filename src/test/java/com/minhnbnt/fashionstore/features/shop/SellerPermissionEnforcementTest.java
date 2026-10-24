package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CancelOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.chat.ChatService;
import com.minhnbnt.fashionstore.features.chat.repository.ChatConversationRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.ReturnRequestService;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.ReturnRequestRepository;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.model.ChatConversation;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ReturnRequest;
import com.minhnbnt.fashionstore.model.Review;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ghim phân quyền chi tiết của các seller service ngoài catalog: đơn hàng, trả hàng,
 * doanh thu, chat, đánh giá và trang trí.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SellerPermissionEnforcementTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private ReturnRequestService returns;

    @Autowired
    private RevenueService revenue;

    @Autowired
    private ChatService chat;

    @Autowired
    private ReviewService reviews;

    @Autowired
    private ShopDecorationService decoration;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private ShopMemberRepository members;

    @Autowired
    private ShopRoleRepository roles;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReturnRequestRepository returnRepository;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ChatConversationRepository conversations;

    private UUID mirroredUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("perm-" + id + "@t.local", "Perm User", null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = mirroredUser();
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private void grant(UUID staffId, UUID ownerId, UUID shopId, ShopPermission... granted) {
        var permissions = Arrays.stream(granted).map(Enum::name).toArray(String[]::new);
        var role = ShopRole.create(shopId, "Staff " + UUID.randomUUID(), permissions, false);
        roles.save(role);
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));
    }

    private UUID staffWith(UUID ownerId, UUID shopId, ShopPermission... granted) {
        var staffId = mirroredUser();
        grant(staffId, ownerId, shopId, granted);

        return staffId;
    }

    private UUID pendingOrder(UUID shopId, UUID buyerId) {
        var order = Order.create(
            "ORD-PERM-" + UUID.randomUUID(),
            shopId,
            buyerId,
            100000L,
            0L,
            "An",
            "0901",
            "Ha Noi"
        );

        return orderRepository.save(order).getId();
    }

    private UUID pendingReturn(UUID shopId, UUID buyerId) {
        var orderId = pendingOrder(shopId, buyerId);
        var request = ReturnRequest.create(orderId, shopId, buyerId, "DEFECT", null, new String[0]);

        return returnRepository.save(request).getId();
    }

    private UUID reviewFor(UUID shopId, UUID ownerId) {
        var product = products.save(Product.create(
            shopId,
            null,
            "Ao Perm",
            "perm-review-" + UUID.randomUUID(),
            "",
            ownerId
        ));
        var review = Review.create(product.getId(), mirroredUser(), "Buyer", 5, "Tot");

        return reviewRepository.save(review).getId();
    }

    private static void assertPermissionDenied(ResponseStatusException e) {
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getReason()).isEqualTo("Insufficient shop permission");
    }

    @Test
    void staffCanReadOrders() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-order-read-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.ORDER_READ);
        pendingOrder(shopId, mirroredUser());

        var listed = orders.listSellerOrdersFiltered(staffId, shopId, null, null, 0, 20);

        assertThat(listed).hasSize(1);
    }

    @Test
    void staffCannotConfirmOrder() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-order-confirm-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.ORDER_READ);
        var orderId = pendingOrder(shopId, mirroredUser());

        assertThatThrownBy(() -> orders.confirm(staffId, orderId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffCannotCancelOrder() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-order-cancel-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.ORDER_READ, ShopPermission.ORDER_PROCESS);
        var orderId = pendingOrder(shopId, mirroredUser());

        assertThatThrownBy(() -> orders.sellerCancel(staffId, orderId, new CancelOrderRequest("OUT_OF_STOCK", null)))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffAggregateOrdersOnlyIncludeGrantedShops() {
        var ownerA = mirroredUser();
        var shopA = approvedShop(ownerA, "perm-order-agg-a-" + UUID.randomUUID());
        var ownerB = mirroredUser();
        var shopB = approvedShop(ownerB, "perm-order-agg-b-" + UUID.randomUUID());
        var staffId = staffWith(ownerA, shopA, ShopPermission.ORDER_READ);
        grant(staffId, ownerB, shopB);
        pendingOrder(shopA, mirroredUser());
        pendingOrder(shopB, mirroredUser());

        var listed = orders.listSellerOrdersFiltered(staffId, null, null, null, 0, 20);

        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().shopId()).isEqualTo(shopA);
    }

    @Test
    void staffAggregateOrdersEmptyWhenOnlyOtherShopGranted() {
        var ownerA = mirroredUser();
        var shopA = approvedShop(ownerA, "perm-order-agg-empty-a-" + UUID.randomUUID());
        var ownerB = mirroredUser();
        var shopB = approvedShop(ownerB, "perm-order-agg-empty-b-" + UUID.randomUUID());
        var staffId = staffWith(ownerB, shopB, ShopPermission.ORDER_READ);
        pendingOrder(shopA, mirroredUser());

        var listed = orders.listSellerOrdersFiltered(staffId, null, null, null, 0, 20);

        assertThat(listed).isEmpty();
    }

    @Test
    void staffCanListReturns() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-return-read-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.RETURN_PROCESS);
        pendingReturn(shopId, mirroredUser());

        var listed = returns.listSellerReturns(staffId, shopId, null);

        assertThat(listed).hasSize(1);
    }

    @Test
    void staffCannotListReturns() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-return-deny-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.ORDER_READ);
        pendingReturn(shopId, mirroredUser());

        assertThatThrownBy(() -> returns.listSellerReturns(staffId, shopId, null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffAggregateReturnsOnlyIncludeGrantedShops() {
        var ownerA = mirroredUser();
        var shopA = approvedShop(ownerA, "perm-return-agg-a-" + UUID.randomUUID());
        var ownerB = mirroredUser();
        var shopB = approvedShop(ownerB, "perm-return-agg-b-" + UUID.randomUUID());
        var staffId = staffWith(ownerA, shopA, ShopPermission.RETURN_PROCESS);
        grant(staffId, ownerB, shopB);
        pendingReturn(shopA, mirroredUser());
        pendingReturn(shopB, mirroredUser());

        var listed = returns.listSellerReturns(staffId, null, null);

        assertThat(listed).hasSize(1);
        assertThat(listed.getFirst().shopId()).isEqualTo(shopA);
    }

    @Test
    void staffAggregateReturnsEmptyWhenOnlyOtherShopGranted() {
        var ownerA = mirroredUser();
        var shopA = approvedShop(ownerA, "perm-return-agg-empty-a-" + UUID.randomUUID());
        var ownerB = mirroredUser();
        var shopB = approvedShop(ownerB, "perm-return-agg-empty-b-" + UUID.randomUUID());
        var staffId = staffWith(ownerB, shopB, ShopPermission.RETURN_PROCESS);
        pendingReturn(shopA, mirroredUser());

        var listed = returns.listSellerReturns(staffId, null, null);

        assertThat(listed).isEmpty();
    }

    @Test
    void staffCanReadRevenue() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-revenue-read-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.REVENUE_READ);

        var summary = revenue.getSummary(staffId, shopId);

        assertThat(summary.totalRevenue()).isZero();
    }

    @Test
    void staffCannotWithdraw() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-withdraw-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.REVENUE_READ);
        var request = new CreateWithdrawalRequest(shopId, 100000L, "Vietcombank", "0123456789", "NGUYEN VAN A");

        assertThatThrownBy(() -> revenue.createWithdrawal(staffId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffCanListReviews() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-review-read-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.REVIEW_READ);
        reviewFor(shopId, ownerId);

        var listed = reviews.listForShop(staffId, shopId, false);

        assertThat(listed).hasSize(1);
    }

    @Test
    void staffCannotReplyReview() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-review-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.REVIEW_READ);
        var reviewId = reviewFor(shopId, ownerId);

        assertThatThrownBy(() -> reviews.reply(staffId, reviewId, new ReplyReviewRequest("Cam on ban")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffCanSendChat() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-chat-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.CHAT_ACCESS);
        var conversation = conversations.save(ChatConversation.create(shopId, mirroredUser()));

        var sent = chat.sendMessage(staffId, conversation.getId(), new SendMessageRequest("Xin chao", null));

        assertThat(sent.senderType()).isEqualTo("SELLER");
        assertThat(sent.senderId()).isEqualTo(staffId);
    }

    @Test
    void staffCanSaveDecoration() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-decoration-write-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.DECORATION_WRITE);

        var saved = decoration.saveDraft(staffId, shopId, "[]");

        assertThat(saved.draft()).isEqualTo("[]");
    }

    @Test
    void staffCannotSaveDecoration() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-decoration-" + UUID.randomUUID());
        var staffId = staffWith(ownerId, shopId, ShopPermission.ORDER_READ);

        assertThatThrownBy(() -> decoration.saveDraft(staffId, shopId, "[]"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }
}
