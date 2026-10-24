package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.chat.SendMessageRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.dto.review.ReplyReviewRequest;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.chat.ChatService;
import com.minhnbnt.fashionstore.features.chat.repository.ChatConversationRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.review.ReviewService;
import com.minhnbnt.fashionstore.features.review.repository.ReviewRepository;
import com.minhnbnt.fashionstore.model.ChatConversation;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.Product;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ghim phân quyền chi tiết của các seller service ngoài catalog: đơn hàng, trả hàng,
 * doanh thu, chat, đánh giá và trang trí. Staff chỉ có ORDER_READ + CHAT_ACCESS.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SellerPermissionEnforcementTest {

    @Autowired
    private OrderService orders;

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

    private UUID staffWithOrderReadAndChat(UUID ownerId, UUID shopId) {
        var staffId = mirroredUser();
        var granted = new String[]{
            ShopPermission.ORDER_READ.name(),
            ShopPermission.CHAT_ACCESS.name()
        };
        var role = ShopRole.create(shopId, "Staff " + UUID.randomUUID(), granted, false);
        roles.save(role);
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));

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

    private static void assertPermissionDenied(ResponseStatusException e) {
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(e.getReason()).isEqualTo("Insufficient shop permission");
    }

    @Test
    void staffCanReadOrders() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-order-read-" + UUID.randomUUID());
        var staffId = staffWithOrderReadAndChat(ownerId, shopId);
        pendingOrder(shopId, mirroredUser());

        var listed = orders.listSellerOrdersFiltered(staffId, shopId, null, null, 0, 20);

        assertThat(listed).hasSize(1);
    }

    @Test
    void staffCannotConfirmOrder() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-order-confirm-" + UUID.randomUUID());
        var staffId = staffWithOrderReadAndChat(ownerId, shopId);
        var orderId = pendingOrder(shopId, mirroredUser());

        assertThatThrownBy(() -> orders.confirm(staffId, orderId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffCannotWithdraw() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-withdraw-" + UUID.randomUUID());
        var staffId = staffWithOrderReadAndChat(ownerId, shopId);
        var request = new CreateWithdrawalRequest(shopId, 100000L, "Vietcombank", "0123456789", "NGUYEN VAN A");

        assertThatThrownBy(() -> revenue.createWithdrawal(staffId, request))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffCannotReplyReview() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-review-" + UUID.randomUUID());
        var staffId = staffWithOrderReadAndChat(ownerId, shopId);
        var product = products.save(Product.create(
            shopId,
            null,
            "Ao Perm",
            "perm-review-" + UUID.randomUUID(),
            "",
            ownerId
        ));
        var review = reviewRepository.save(Review.create(product.getId(), mirroredUser(), "Buyer", 5, "Tot"));

        assertThatThrownBy(() -> reviews.reply(staffId, review.getId(), new ReplyReviewRequest("Cam on ban")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }

    @Test
    void staffCanSendChat() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-chat-" + UUID.randomUUID());
        var staffId = staffWithOrderReadAndChat(ownerId, shopId);
        var conversation = conversations.save(ChatConversation.create(shopId, mirroredUser()));

        var sent = chat.sendMessage(staffId, conversation.getId(), new SendMessageRequest("Xin chao", null));

        assertThat(sent.senderType()).isEqualTo("SELLER");
        assertThat(sent.senderId()).isEqualTo(staffId);
    }

    @Test
    void staffCannotSaveDecoration() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "perm-decoration-" + UUID.randomUUID());
        var staffId = staffWithOrderReadAndChat(ownerId, shopId);

        assertThatThrownBy(() -> decoration.saveDraft(staffId, shopId, "[]"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertPermissionDenied((ResponseStatusException) e));
    }
}
