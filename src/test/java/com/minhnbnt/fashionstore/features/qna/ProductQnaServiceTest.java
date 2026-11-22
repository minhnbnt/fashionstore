package com.minhnbnt.fashionstore.features.qna;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.dto.qna.CreateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.CreateQuestionRequest;
import com.minhnbnt.fashionstore.dto.qna.UpdateAnswerRequest;
import com.minhnbnt.fashionstore.dto.qna.UpdateQuestionRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.notification.NotificationCategory;
import com.minhnbnt.fashionstore.features.notification.NotificationRepository;
import com.minhnbnt.fashionstore.features.notification.NotificationService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.qna.repository.ProductAnswerRepository;
import com.minhnbnt.fashionstore.features.qna.repository.ProductQnaQueryRepository;
import com.minhnbnt.fashionstore.features.qna.repository.ProductQuestionRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberAuditRepository;
import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;
import com.minhnbnt.fashionstore.features.shop.ShopRoleRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.model.ProductAnswerRole;
import com.minhnbnt.fashionstore.model.ProductQuestionSort;
import com.minhnbnt.fashionstore.model.ProductQuestionStatus;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopPermission;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductQnaServiceTest {

    @Autowired
    private ProductQnaService qnaService;

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ShopMemberRepository shopMembers;

    @Autowired
    private ShopRoleRepository shopRoles;

    @Autowired
    private ShopMemberAuditRepository shopAudits;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private ProductQuestionRepository questions;

    @Autowired
    private ProductQnaQueryRepository qnaQuery;

    @Autowired
    private ProductAnswerRepository answers;

    @Autowired
    private JdbcClient jdbc;

    private record NotificationRecord(
        UUID id,
        UUID userId,
        String category,
        String type,
        String title,
        String body,
        @Nullable String link,
        @Nullable UUID refId
    ) {
    }

    private List<NotificationRecord> findNotificationsByRefId(UUID refId) {
        return jdbc.sql("""
            SELECT id, user_id, category, type, title, body, link, ref_id
            FROM user_notification
            WHERE ref_id = :ref
            """)
            .param("ref", refId)
            .query((rs, rowNum) -> new NotificationRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("category"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("link"),
                rs.getObject("ref_id", UUID.class)
            ))
            .list();
    }

    private UUID staffWith(UUID ownerId, UUID shopId, ShopPermission... granted) {
        var staffId = mirroredUser();
        var permissions = Arrays.stream(granted).map(Enum::name).toArray(String[]::new);
        var role = ShopRole.create(shopId, "Staff " + UUID.randomUUID(), permissions, false);
        shopRoles.save(role);
        shopMembers.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));
        return staffId;
    }

    private UUID mirroredUser() {
        return mirroredUser("Người dùng " + UUID.randomUUID().toString().substring(0, 6));
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

    private UUID createProduct(UUID ownerId, UUID shopId, String name, String slug, String sku) {
        var created = catalog.createProduct(
            ownerId,
            shopId,
            ProductTestFixtures.request(
                name,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest(sku, "M", "Đen", 100000L, null, 100, true))
            )
        );
        return created.id();
    }

    private record PurchasedItem(UUID orderId, UUID productId, UUID variantId) {
    }

    private void deliverExistingProduct(UUID ownerId, UUID buyerId, UUID shopId, UUID productId) {
        var detail = catalog.getDetail(productId);
        var variantId = detail.variants().getFirst().id();
        var orderId = orders.checkout(
            buyerId,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        ).id();
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        orders.confirmReceipt(buyerId, orderId);
    }

    private PurchasedItem purchaseProduct(UUID ownerId, UUID buyerId, UUID shopId, String slug, String sku) {
        var productId = createProduct(ownerId, shopId, "Sản phẩm " + slug, slug, sku);
        deliverExistingProduct(ownerId, buyerId, shopId, productId);
        var variantId = catalog.getDetail(productId).variants().getFirst().id();
        return new PurchasedItem(UUID.randomUUID(), productId, variantId);
    }

    private static void assertStatus(ResponseStatusException e, HttpStatus status) {
        assertThat(e.getStatusCode()).isEqualTo(status);
    }

    @Test
    void customerAsksQuestionAndNotifiesShopIdempotently() {
        var ownerId = mirroredUser("Chủ Shop A");
        var shopId = approvedShop(ownerId, "shop-qna-1-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(
            ownerId,
            shopId,
            "Áo sơ mi oxford",
            "ao-oxford-" + UUID.randomUUID(),
            "SKU-OXF"
        );
        var askerId = mirroredUser("Nguyễn Văn Hỏi");

        var response = qnaService.createQuestion(
            askerId,
            productId,
            new CreateQuestionRequest("Sản phẩm này có size XL không?")
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.code()).startsWith("QA-").hasSize(15);
        assertThat(response.productId()).isEqualTo(productId);
        assertThat(response.askerDisplayName()).isEqualTo("Nguyễn Văn Hỏi");
        assertThat(response.content()).isEqualTo("Sản phẩm này có size XL không?");
        assertThat(response.status()).isEqualTo(ProductQuestionStatus.UNANSWERED);
        assertThat(response.mine()).isTrue();
        assertThat(response.canEdit()).isTrue();
        assertThat(response.canDelete()).isTrue();
        assertThat(response.answers()).isEmpty();

        var notifs = findNotificationsByRefId(response.id());
        assertThat(notifs).hasSize(1);
        var notif = notifs.getFirst();
        assertThat(notif.userId()).isEqualTo(ownerId);
        assertThat(notif.category()).isEqualTo("SELLING");
        assertThat(notif.type()).isEqualTo(ProductQnaService.NOTIF_NEW_QUESTION);
        assertThat(notif.title()).isEqualTo("Sản phẩm có câu hỏi mới");
        assertThat(notif.body()).contains("Nguyễn Văn Hỏi hỏi về Áo sơ mi oxford");
        assertThat(notif.link()).isEqualTo("/shop?shopId=" + shopId + "&tab=qna&questionId=" + response.id());

        // Idempotency check: duplicate call does not create extra notification
        notificationService.createIdempotent(
            Set.of(ownerId),
            NotificationCategory.SELLING,
            ProductQnaService.NOTIF_NEW_QUESTION,
            "Sản phẩm có câu hỏi mới",
            "Nguyễn Văn Hỏi hỏi về Áo sơ mi oxford",
            "/shop?shopId=" + shopId,
            response.id()
        );
        var notifsAfterDuplicate = findNotificationsByRefId(response.id());
        assertThat(notifsAfterDuplicate).hasSize(1);
    }

    @Test
    void conditionalStatusUpdatesNeverResurrectClosedQuestion() {
        var ownerId = mirroredUser("Chủ Shop Race");
        var shopId = approvedShop(ownerId, "shop-qna-race-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(
            ownerId,
            shopId,
            "Áo race",
            "ao-race-" + UUID.randomUUID(),
            "SKU-RACE"
        );
        var askerId = mirroredUser("Người Hỏi Race");
        var staffId = staffWith(ownerId, shopId, ShopPermission.REVIEW_READ, ShopPermission.REVIEW_REPLY);

        var question = qnaService.createQuestion(askerId, productId, new CreateQuestionRequest("Câu hỏi race?"));
        var answered = qnaService.createShopAnswer(staffId, question.id(), new CreateAnswerRequest("Trả lời của shop"));
        assertThat(answered.status()).isEqualTo(ProductQuestionStatus.ANSWERED);

        qnaService.closeQuestion(staffId, question.id());

        // Close đã commit trước: câu UPDATE ANSWERED không được ghi đè CLOSED.
        assertThat(qnaQuery.markAnsweredIfOpen(question.id())).isZero();
        var afterLateAnswer = questions.findById(question.id()).orElseThrow();
        assertThat(afterLateAnswer.getStatus()).isEqualTo(ProductQuestionStatus.CLOSED);
        assertThat(afterLateAnswer.getClosedAt()).isNotNull();

        // Path xoá answer SHOP cuối cũng không được revert CLOSED -> UNANSWERED.
        var answerId = answers.findByQuestionIdOrderByCreatedAtAscIdAsc(question.id()).getFirst().getId();
        answers.deleteById(answerId);
        assertThat(qnaQuery.markUnansweredIfNoShopAnswer(question.id())).isZero();
        var afterLateDelete = questions.findById(question.id()).orElseThrow();
        assertThat(afterLateDelete.getStatus()).isEqualTo(ProductQuestionStatus.CLOSED);
        assertThat(afterLateDelete.getClosedAt()).isNotNull();
    }

    @Test
    void rejectsBlankWhitespaceContentAndChecksProductAvailability() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-qna-2-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(ownerId, shopId, "Áo khoác gió", "ao-khoac-" + UUID.randomUUID(), "SKU-WIND");
        var askerId = mirroredUser();

        // Blank content -> 400
        assertThatThrownBy(() -> qnaService.createQuestion(askerId, productId, new CreateQuestionRequest("")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        // Whitespace only -> 400
        assertThatThrownBy(() -> qnaService.createQuestion(askerId, productId, new CreateQuestionRequest("   \n\t  ")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        // Length is enforced at the DTO/DB boundary, not in the service.
        // Boundary length 1000 -> accepts
        var exact1000 = "B".repeat(1000);
        var okQuestion = qnaService.createQuestion(askerId, productId, new CreateQuestionRequest(exact1000));
        assertThat(okQuestion.content()).hasSize(1000);

        // Non-existent product -> 404
        var nonExistentId = UUID.randomUUID();
        assertThatThrownBy(() -> qnaService.createQuestion(
            askerId,
            nonExistentId,
            new CreateQuestionRequest("Hỏi sản phẩm không tồn tại")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));

        // Inactive / hidden product -> 404
        jdbc.sql("UPDATE product SET status = 'HIDDEN' WHERE id = :id")
            .param("id", productId)
            .update();
        assertThatThrownBy(() -> qnaService.createQuestion(
            askerId,
            productId,
            new CreateQuestionRequest("Hỏi sản phẩm ẩn")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.NOT_FOUND));
    }

    @Test
    void listsQuestionsNewestFirstSearchesQuestionAndAnswerAndEscapesWildcards() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-qna-3-" + UUID.randomUUID().toString().substring(0, 6));
        var productA = createProduct(ownerId, shopId, "Áo thun cotton", "ao-thun-" + UUID.randomUUID(), "SKU-AT");
        var productB = createProduct(ownerId, shopId, "Quần jean denim", "quan-jean-" + UUID.randomUUID(), "SKU-QJ");
        var askerId = mirroredUser("Asker 3");

        // Ask on productA
        var q1 = qnaService.createQuestion(
            askerId,
            productA,
            new CreateQuestionRequest("Có mẫu 50% cotton không?")
        );
        var q2 = qnaService.createQuestion(
            askerId,
            productA,
            new CreateQuestionRequest("Chất liệu áo_thun này tốt ko?")
        );

        // Ask on productB (isolation test)
        createProduct(ownerId, shopId, "Váy maxi", "vay-maxi-" + UUID.randomUUID(), "SKU-VM");
        qnaService.createQuestion(askerId, productB, new CreateQuestionRequest("Quần này co giãn 50% không?"));

        // List productA -> newest first, isolated from productB
        var listA = qnaService.listForProduct(productA, askerId, null, 0, 10);
        assertThat(listA.total()).isEqualTo(2);
        assertThat(listA.items()).extracting(q -> q.id()).containsExactly(q2.id(), q1.id());
        assertThat(listA.canAnswerAsBuyer()).isFalse();

        // Search literal %
        var searchPercent = qnaService.listForProduct(productA, askerId, "50%", 0, 10);
        assertThat(searchPercent.items()).extracting(q -> q.id()).containsExactly(q1.id());

        // Search literal _
        var searchUnderscore = qnaService.listForProduct(productA, askerId, "áo_thun", 0, 10);
        assertThat(searchUnderscore.items()).extracting(q -> q.id()).containsExactly(q2.id());

        // Shop answers q2 with distinct keyword
        qnaService.createShopAnswer(
            ownerId,
            q2.id(),
            new CreateAnswerRequest("Chất liệu bamboo cao cấp siêu êm")
        );

        // Search in answer content ("bamboo") finds q2
        var searchInAnswer = qnaService.listForProduct(productA, askerId, "bamboo", 0, 10);
        assertThat(searchInAnswer.items()).extracting(q -> q.id()).containsExactly(q2.id());

        // Buyer who purchased productA gets canAnswerAsBuyer = true
        var buyer = mirroredUser("Buyer Verified");
        var detail = catalog.getDetail(productA);
        var variantId = detail.variants().getFirst().id();
        var orderId = orders.checkout(
            buyer,
            new CheckoutRequest(shopId, List.of(new CheckoutItemRequest(variantId, 1)), "An", "0901", "Ha Noi", 0L)
        ).id();
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        orders.confirmReceipt(buyer, orderId);

        var listForBuyer = qnaService.listForProduct(productA, buyer, null, 0, 10);
        assertThat(listForBuyer.canAnswerAsBuyer()).isTrue();
    }

    @Test
    void askerCanEditAndDeleteOnlyWhileUnansweredAndNonOwnerForbidden() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-qna-4-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(ownerId, shopId, "Áo polo trắng", "ao-polo-" + UUID.randomUUID(), "SKU-POLO");
        var askerId = mirroredUser("Người hỏi 4");
        var strangerId = mirroredUser("Người lạ 4");

        var q = qnaService.createQuestion(askerId, productId, new CreateQuestionRequest("Có màu đỏ không shop?"));

        // Stranger cannot edit or delete
        assertThatThrownBy(() -> qnaService.updateQuestion(
            strangerId,
            productId,
            q.id(),
            new UpdateQuestionRequest("Sửa bậy")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> qnaService.deleteQuestion(strangerId, productId, q.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        // Asker edits question while unanswered
        var updated = qnaService.updateQuestion(
            askerId,
            productId,
            q.id(),
            new UpdateQuestionRequest("Có màu xanh navy không shop?")
        );
        assertThat(updated.content()).isEqualTo("Có màu xanh navy không shop?");
        assertThat(updated.editedAt()).isNotNull();

        // Shop answers question
        qnaService.createShopAnswer(ownerId, q.id(), new CreateAnswerRequest("Dạ có màu navy bạn nhé"));

        // Asker cannot edit or delete once answered
        assertThatThrownBy(() -> qnaService.updateQuestion(
            askerId,
            productId,
            q.id(),
            new UpdateQuestionRequest("Cố sửa tiếp")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        assertThatThrownBy(() -> qnaService.deleteQuestion(askerId, productId, q.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        // Asker creates another question and deletes it
        var qToDelete = qnaService.createQuestion(
            askerId,
            productId,
            new CreateQuestionRequest("Câu hỏi cần xóa ngay")
        );
        assertThat(questions.findById(qToDelete.id())).isPresent();
        assertThat(findNotificationsByRefId(qToDelete.id())).isNotEmpty();

        qnaService.deleteQuestion(askerId, productId, qToDelete.id());
        assertThat(questions.findById(qToDelete.id())).isEmpty();
        assertThat(findNotificationsByRefId(qToDelete.id())).isEmpty();
    }

    @Test
    void verifiedBuyerAnswerFlowAndAuthorization() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-qna-5-" + UUID.randomUUID().toString().substring(0, 6));
        var purchased = purchaseProduct(ownerId, mirroredUser(), shopId, "ao-len-" + UUID.randomUUID(), "SKU-LEN");
        var productId = purchased.productId();

        var verifiedBuyerId = mirroredUser("Người Mua Uy Tín");
        // Complete purchase for verifiedBuyerId
        var orderId = orders.checkout(
            verifiedBuyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(purchased.variantId(), 1)),
                "An",
                "0901",
                "Ha Noi",
                0L
            )
        ).id();
        orders.confirm(ownerId, orderId);
        orders.ship(ownerId, orderId);
        orders.deliver(ownerId, orderId);
        orders.confirmReceipt(verifiedBuyerId, orderId);

        // Buyer with DELIVERED but not confirmed receipt
        var deliveredOnlyBuyerId = mirroredUser("Chưa Bấm Nhận");
        var unconfirmedOrderId = orders.checkout(
            deliveredOnlyBuyerId,
            new CheckoutRequest(
                shopId,
                List.of(new CheckoutItemRequest(purchased.variantId(), 1)),
                "Binh",
                "0902",
                "Ha Noi",
                0L
            )
        ).id();
        orders.confirm(ownerId, unconfirmedOrderId);
        orders.ship(ownerId, unconfirmedOrderId);
        orders.deliver(ownerId, unconfirmedOrderId);

        var strangerId = mirroredUser("Chưa Từng Mua");
        var askerId = mirroredUser("Người Hỏi 5");

        var q = qnaService.createQuestion(
            askerId,
            productId,
            new CreateQuestionRequest("Áo này giặt máy bị xù không?")
        );

        // Stranger cannot answer -> 403
        assertThatThrownBy(() -> qnaService.createBuyerAnswer(
            strangerId,
            productId,
            q.id(),
            new CreateAnswerRequest("Tôi chưa mua nhưng trả lời")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        // Delivered-only buyer cannot answer -> 403
        assertThatThrownBy(() -> qnaService.createBuyerAnswer(
            deliveredOnlyBuyerId,
            productId,
            q.id(),
            new CreateAnswerRequest("Chưa bấm đã nhận hàng")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        // Verified buyer answers -> succeeds
        var answer = qnaService.createBuyerAnswer(
            verifiedBuyerId,
            productId,
            q.id(),
            new CreateAnswerRequest("Mình giặt máy 3 lần rồi không xù nhé!")
        );

        assertThat(answer.authorRole()).isEqualTo(ProductAnswerRole.VERIFIED_BUYER);
        assertThat(answer.verifiedBuyer()).isTrue();
        assertThat(answer.authorDisplayName()).isEqualTo("Người Mua Uy Tín");
        assertThat(answer.mine()).isTrue();
        assertThat(answer.canEdit()).isTrue();
        assertThat(answer.canDelete()).isTrue();

        // Question status remains UNANSWERED (status tracks Shop replies)
        var refreshedQ = questions.findById(q.id()).orElseThrow();
        assertThat(refreshedQ.getStatus()).isEqualTo(ProductQuestionStatus.UNANSWERED);

        // Notification sent to asker
        var askerNotifs = findNotificationsByRefId(answer.id());
        assertThat(askerNotifs).hasSize(1);
        assertThat(askerNotifs.getFirst().body()).contains("(Đã mua hàng) đã trả lời về");
        assertThat(askerNotifs.getFirst().link())
            .isEqualTo("/product/" + productId + "?questionId=" + q.id() + "#product-qna");

        // Self-notification check: verified buyer asks and answers own question
        var selfQ = qnaService.createQuestion(
            verifiedBuyerId,
            productId,
            new CreateQuestionRequest("Tự hỏi tự trả lời")
        );
        var selfAnswer = qnaService.createBuyerAnswer(
            verifiedBuyerId,
            productId,
            selfQ.id(),
            new CreateAnswerRequest("Tự trả lời câu hỏi của chính mình")
        );
        var selfNotifs = findNotificationsByRefId(selfAnswer.id());
        assertThat(selfNotifs).isEmpty();

        // Stranger cannot edit or delete buyer's answer -> 403
        assertThatThrownBy(() -> qnaService.updateBuyerAnswer(
            strangerId,
            productId,
            q.id(),
            answer.id(),
            new UpdateAnswerRequest("Sửa ké")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> qnaService.deleteBuyerAnswer(strangerId, productId, q.id(), answer.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        // Buyer edits answer
        var editedAnswer = qnaService.updateBuyerAnswer(
            verifiedBuyerId,
            productId,
            q.id(),
            answer.id(),
            new UpdateAnswerRequest("Mình giặt máy 5 lần rồi vẫn êm!")
        );
        assertThat(editedAnswer.content()).isEqualTo("Mình giặt máy 5 lần rồi vẫn êm!");
        assertThat(editedAnswer.editedAt()).isNotNull();

        // Buyer deletes answer
        qnaService.deleteBuyerAnswer(verifiedBuyerId, productId, q.id(), answer.id());
        assertThat(answers.findById(answer.id())).isEmpty();
        assertThat(findNotificationsByRefId(answer.id())).isEmpty();
    }

    @Test
    void sellerFiltersSearchesSortsAndStatsWithCskhStaff() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-qna-6-" + UUID.randomUUID().toString().substring(0, 6));
        var productA = createProduct(ownerId, shopId, "Áo khoác gió", "ao-khoac-" + UUID.randomUUID(), "SKU-KG6");
        var productB = createProduct(ownerId, shopId, "Quần short kaki", "quan-kaki-" + UUID.randomUUID(), "SKU-KK6");

        // CSKH staff has only REVIEW_READ, NO PRODUCT_READ
        var cskhStaffId = staffWith(ownerId, shopId, ShopPermission.REVIEW_READ);

        var asker1 = mirroredUser("Trần Văn An");
        var asker2 = mirroredUser("Lê Thị Bích");

        var q1 = qnaService.createQuestion(
            asker1,
            productA,
            new CreateQuestionRequest("Áo này chống nước không?")
        );
        var q2 = qnaService.createQuestion(
            asker2,
            productB,
            new CreateQuestionRequest("Quần dài đến gối không?")
        );

        // Owner answers q1
        qnaService.createShopAnswer(ownerId, q1.id(), new CreateAnswerRequest("Áo kháng nước nhẹ nhé!"));

        // Owner closes q2
        qnaService.closeQuestion(ownerId, q2.id());

        // CSKH lists questions
        var list = qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            null,
            null,
            null,
            null,
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(list.total()).isEqualTo(2);

        // Filter by status ANSWERED
        var answeredList = qnaService.listForShop(
            cskhStaffId,
            shopId,
            ProductQuestionStatus.ANSWERED,
            null,
            null,
            null,
            null,
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(answeredList.total()).isEqualTo(1);
        assertThat(answeredList.items().getFirst().id()).isEqualTo(q1.id());

        // Filter by product
        var productBList = qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            productB,
            null,
            null,
            null,
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(productBList.total()).isEqualTo(1);
        assertThat(productBList.items().getFirst().id()).isEqualTo(q2.id());

        // Date range validation: from > to -> 400
        var today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        assertThatThrownBy(() -> qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            null,
            today,
            today.minusDays(1),
            null,
            ProductQuestionSort.NEWEST,
            0,
            10
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.BAD_REQUEST));

        // Search by asker name
        var searchByName = qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            null,
            null,
            null,
            "Trần Văn An",
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(searchByName.total()).isEqualTo(1);

        // Search by SKU
        var searchBySku = qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            null,
            null,
            null,
            "SKU-KK6",
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(searchBySku.total()).isEqualTo(1);
        assertThat(searchBySku.items().getFirst().id()).isEqualTo(q2.id());

        // Search by question code
        var searchByCode = qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            null,
            null,
            null,
            q1.code(),
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(searchByCode.total()).isEqualTo(1);

        // Sort OLDEST vs NEWEST
        var sortOldest = qnaService.listForShop(
            cskhStaffId,
            shopId,
            null,
            null,
            null,
            null,
            null,
            ProductQuestionSort.OLDEST,
            0,
            10
        );
        assertThat(sortOldest.items().getFirst().id()).isEqualTo(q1.id());

        // CSKH list products option dropdown (safe without PRODUCT_READ)
        var productsDropdown = qnaService.listShopQnaProducts(cskhStaffId, shopId);
        assertThat(productsDropdown).isNotEmpty();

        // CSKH single question detail
        var detailQ1 = qnaService.getShopQuestion(cskhStaffId, q1.id());
        assertThat(detailQ1.id()).isEqualTo(q1.id());
        assertThat(detailQ1.answers()).hasSize(1);

        // CSKH stats calculation (q1 answered, q2 closed without answer)
        var stats = qnaService.shopQnaStats(cskhStaffId, shopId);
        assertThat(stats.totalQuestions()).isEqualTo(2);
        assertThat(stats.answeredCount()).isEqualTo(1);
        assertThat(stats.closedCount()).isEqualTo(1);
        assertThat(stats.unansweredCount()).isEqualTo(0);
        assertThat(stats.responseRate()).isEqualTo(50.0);
    }

    @Test
    void sellerRepliesFollowsUpEditsDeletesAndClosesReopens() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "shop-qna-7-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(
            ownerId,
            shopId,
            "Đầm dạ hội",
            "dam-da-hoi-" + UUID.randomUUID(),
            "SKU-DAM7"
        );
        var askerId = mirroredUser("Người Hỏi 7");

        var staffWithReply = staffWith(ownerId, shopId, ShopPermission.REVIEW_READ, ShopPermission.REVIEW_REPLY);
        var otherShopOwner = mirroredUser();
        approvedShop(otherShopOwner, "shop-qna-7-other-" + UUID.randomUUID().toString().substring(0, 6));

        var q = qnaService.createQuestion(
            askerId,
            productId,
            new CreateQuestionRequest("Đầm này dài bao nhiêu cm?")
        );

        // Cross-shop staff cannot reply -> 403
        assertThatThrownBy(() -> qnaService.createShopAnswer(
            otherShopOwner,
            q.id(),
            new CreateAnswerRequest("Xâm phạm shop khác")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        // Staff with REVIEW_REPLY answers question
        var detailAfterAnswer1 = qnaService.createShopAnswer(
            staffWithReply,
            q.id(),
            new CreateAnswerRequest("Dạ dài 110cm từ vai xuống nhé ạ")
        );
        assertThat(detailAfterAnswer1.status()).isEqualTo(ProductQuestionStatus.ANSWERED);
        assertThat(detailAfterAnswer1.answers()).hasSize(1);
        var answer1 = detailAfterAnswer1.answers().getFirst();
        assertThat(answer1.authorRole()).isEqualTo(ProductAnswerRole.SHOP);
        assertThat(answer1.verifiedBuyer()).isFalse();

        // Check audit log for reply
        var audits = shopAudits.findByShopIdOrderByCreatedAtDesc(shopId);
        assertThat(audits).extracting(a -> a.getAction()).contains("QNA_ANSWER_CREATE");

        // Check notification sent to asker
        var askerNotifs = findNotificationsByRefId(answer1.id());
        assertThat(askerNotifs).hasSize(1);
        assertThat(askerNotifs.getFirst().link())
            .isEqualTo("/product/" + productId + "?questionId=" + q.id() + "#product-qna");

        // Follow-up answer from shop
        var detailAfterAnswer2 = qnaService.createShopAnswer(
            staffWithReply,
            q.id(),
            new CreateAnswerRequest("Thêm nữa là mẫu 1m6 mặc qua bắp chân nha bạn")
        );
        assertThat(detailAfterAnswer2.answers()).hasSize(2);
        var answer2 = detailAfterAnswer2.answers().getLast();

        // Shop edits answer1
        var editedAnswer1 = qnaService.updateShopAnswer(
            staffWithReply,
            answer1.id(),
            new UpdateAnswerRequest("Dạ chính xác dài 112cm nhé bạn")
        );
        assertThat(editedAnswer1.content()).isEqualTo("Dạ chính xác dài 112cm nhé bạn");
        assertThat(editedAnswer1.editedAt()).isNotNull();

        // Verified buyer also answers
        var buyerId = mirroredUser();
        deliverExistingProduct(ownerId, buyerId, shopId, productId);
        var buyerAns = qnaService.createBuyerAnswer(
            buyerId,
            productId,
            q.id(),
            new CreateAnswerRequest("Mình 1m58 mặc rất vừa đẹp")
        );

        // Shop tries to edit/delete buyer's answer -> 403
        assertThatThrownBy(() -> qnaService.updateShopAnswer(
            staffWithReply,
            buyerAns.id(),
            new UpdateAnswerRequest("Shop cố sửa câu trả lời của buyer")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> qnaService.deleteShopAnswer(staffWithReply, buyerAns.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        // Shop deletes answer1 -> 1 shop answer remaining, status remains ANSWERED
        qnaService.deleteShopAnswer(staffWithReply, answer1.id());
        var qMid = questions.findById(q.id()).orElseThrow();
        assertThat(qMid.getStatus()).isEqualTo(ProductQuestionStatus.ANSWERED);

        // Shop deletes answer2 (last shop answer) -> reverts to UNANSWERED even though buyerAns exists
        qnaService.deleteShopAnswer(staffWithReply, answer2.id());
        var qReverted = questions.findById(q.id()).orElseThrow();
        assertThat(qReverted.getStatus()).isEqualTo(ProductQuestionStatus.UNANSWERED);

        // Close question
        var closedDetail = qnaService.closeQuestion(staffWithReply, q.id());
        assertThat(closedDetail.status()).isEqualTo(ProductQuestionStatus.CLOSED);
        assertThat(closedDetail.closedAt()).isNotNull();

        // Replying or editing on CLOSED question -> 409
        assertThatThrownBy(() -> qnaService.createShopAnswer(
            staffWithReply,
            q.id(),
            new CreateAnswerRequest("Trả lời câu hỏi đã đóng")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        // Reopen question (0 shop answers -> UNANSWERED)
        var reopenedDetail = qnaService.reopenQuestion(staffWithReply, q.id());
        assertThat(reopenedDetail.status()).isEqualTo(ProductQuestionStatus.UNANSWERED);
        assertThat(reopenedDetail.closedAt()).isNull();

        // Shop replies again -> ANSWERED
        var replyAgain = qnaService.createShopAnswer(
            staffWithReply,
            q.id(),
            new CreateAnswerRequest("Trả lời lại sau khi reopen")
        );
        assertThat(replyAgain.status()).isEqualTo(ProductQuestionStatus.ANSWERED);

        // Close again, then reopen -> restores ANSWERED
        qnaService.closeQuestion(staffWithReply, q.id());
        var reopenedWithAnswer = qnaService.reopenQuestion(staffWithReply, q.id());
        assertThat(reopenedWithAnswer.status()).isEqualTo(ProductQuestionStatus.ANSWERED);

        // Verify all audit actions recorded
        var finalAudits = shopAudits.findByShopIdOrderByCreatedAtDesc(shopId);
        assertThat(finalAudits).extracting(a -> a.getAction())
            .contains(
                "QNA_ANSWER_CREATE",
                "QNA_ANSWER_UPDATE",
                "QNA_ANSWER_DELETE",
                "QNA_QUESTION_CLOSE",
                "QNA_QUESTION_REOPEN"
            );
    }

    @Test
    void buyerCannotMutateAnswersOnClosedQuestion() {
        var ownerId = mirroredUser("Chủ Shop Closed");
        var shopId = approvedShop(ownerId, "shop-qna-closed-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(
            ownerId,
            shopId,
            "Áo closed",
            "ao-closed-" + UUID.randomUUID(),
            "SKU-CLOSED"
        );
        var askerId = mirroredUser("Người Hỏi Closed");
        var buyerId = mirroredUser("Người Mua Closed");
        deliverExistingProduct(ownerId, buyerId, shopId, productId);

        var q = qnaService.createQuestion(askerId, productId, new CreateQuestionRequest("Câu hỏi trước khi đóng?"));
        var answer = qnaService.createBuyerAnswer(
            buyerId,
            productId,
            q.id(),
            new CreateAnswerRequest("Trả lời trước khi đóng")
        );
        qnaService.closeQuestion(ownerId, q.id());

        assertThatThrownBy(() -> qnaService.createBuyerAnswer(
            buyerId,
            productId,
            q.id(),
            new CreateAnswerRequest("Trả lời sau khi đóng")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        assertThatThrownBy(() -> qnaService.updateBuyerAnswer(
            buyerId,
            productId,
            q.id(),
            answer.id(),
            new UpdateAnswerRequest("Sửa sau khi đóng")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));

        assertThatThrownBy(() -> qnaService.deleteBuyerAnswer(buyerId, productId, q.id(), answer.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.CONFLICT));
    }

    @Test
    void readOnlyStaffSeesNoEditFlagsAndCrossShopIsForbidden() {
        var ownerId = mirroredUser("Chủ Shop RO");
        var shopId = approvedShop(ownerId, "shop-qna-ro-" + UUID.randomUUID().toString().substring(0, 6));
        var productId = createProduct(
            ownerId,
            shopId,
            "Áo read only",
            "ao-ro-" + UUID.randomUUID(),
            "SKU-RO"
        );
        var askerId = mirroredUser("Người Hỏi RO");
        var readOnlyStaff = staffWith(ownerId, shopId, ShopPermission.REVIEW_READ);

        var q = qnaService.createQuestion(askerId, productId, new CreateQuestionRequest("Câu hỏi read only?"));
        var answered = qnaService.createShopAnswer(ownerId, q.id(), new CreateAnswerRequest("Trả lời của chủ shop"));
        var shopAnswerId = answered.answers().getFirst().id();

        var detail = qnaService.getShopQuestion(readOnlyStaff, q.id());
        assertThat(detail.answers().getFirst().canEdit()).isFalse();
        assertThat(detail.answers().getFirst().canDelete()).isFalse();
        var listed = qnaService.listForShop(
            readOnlyStaff,
            shopId,
            null,
            null,
            null,
            null,
            null,
            ProductQuestionSort.NEWEST,
            0,
            10
        );
        assertThat(listed.items().getFirst().answers().getFirst().canEdit()).isFalse();

        var otherOwner = mirroredUser("Chủ Shop B");
        var otherShopId = approvedShop(otherOwner, "shop-qna-ro-b-" + UUID.randomUUID().toString().substring(0, 6));
        var otherStaff = staffWith(otherOwner, otherShopId, ShopPermission.REVIEW_READ, ShopPermission.REVIEW_REPLY);

        assertThatThrownBy(() -> qnaService.getShopQuestion(otherStaff, q.id()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> qnaService.updateShopAnswer(
            otherStaff,
            shopAnswerId,
            new UpdateAnswerRequest("Sửa answer shop khác")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> qnaService.deleteShopAnswer(otherStaff, shopAnswerId))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertStatus((ResponseStatusException) e, HttpStatus.FORBIDDEN));
    }
}
