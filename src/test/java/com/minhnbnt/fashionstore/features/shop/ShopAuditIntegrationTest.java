package com.minhnbnt.fashionstore.features.shop;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ImageItemRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CreateWithdrawalRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.catalog.repository.CategoryRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.OrderService;
import com.minhnbnt.fashionstore.features.order.RevenueService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.order.repository.SellerSettlementRepository;
import com.minhnbnt.fashionstore.model.Category;
import com.minhnbnt.fashionstore.model.Order;
import com.minhnbnt.fashionstore.model.SellerSettlement;
import com.minhnbnt.fashionstore.model.ShopMember;
import com.minhnbnt.fashionstore.model.ShopRole;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ghim nhật ký hoạt động dùng chung: thao tác nghiệp vụ của shop được ghi kèm actor,
 * lọc theo action và trả email/tên thật từ user_profile.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ShopAuditIntegrationTest {

    @Autowired
    private CatalogService catalog;

    @Autowired
    private OrderService orders;

    @Autowired
    private RevenueService revenue;

    @Autowired
    private ShopStaffService staff;

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
    private SellerSettlementRepository settlements;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private ShopAuditService audit;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void auditFailureDoesNotAbortBusinessTransaction() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-savepoint");
        var unknownActor = UUID.randomUUID();
        var category = Category.seed("Audit Savepoint", "audit-savepoint-" + UUID.randomUUID(), 0);

        transactions.executeWithoutResult(status -> {
            audit.record(shopId, unknownActor, "PRODUCT_CREATE", "PRODUCT", null, null);
            categories.save(category);
        });

        assertThat(categories.findById(category.getId())).isPresent();
    }

    @Test
    void staffCannotListAuditLogs() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-forbidden");
        var staffId = staffWith(ownerId, shopId, "ORDER_READ");

        assertThatThrownBy(() -> staff.listAuditLogs(staffId, shopId, null, 0, 50))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void auditPageSizeIsClamped() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-clamp");
        catalog.createProduct(ownerId, shopId, productRequest("audit-clamp"));

        assertThat(staff.listAuditLogs(ownerId, shopId, null, 0, 0).size()).isEqualTo(1);
        assertThat(staff.listAuditLogs(ownerId, shopId, null, 0, 500).size()).isEqualTo(100);
    }

    @Test
    void productCreateIsRecorded() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-product");

        var product = catalog.createProduct(ownerId, shopId, productRequest("audit-product"));

        var page = staff.listAuditLogs(ownerId, shopId, null, 0, 50);
        var row = page.items().stream()
            .filter(item -> "PRODUCT_CREATE".equals(item.action()))
            .findFirst()
            .orElseThrow();

        assertThat(row.actorId()).isEqualTo(ownerId);
        assertThat(row.actorEmail()).isEqualTo(users.getById(ownerId).email());
        assertThat(row.targetType()).isEqualTo("PRODUCT");
        assertThat(row.targetId()).isEqualTo(product.id());
    }

    @Test
    void orderConfirmIsRecorded() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-order");
        var staffId = staffWith(ownerId, shopId, "ORDER_PROCESS");
        var orderId = pendingOrder(shopId, user("buyer", "Buyer"));

        orders.confirm(staffId, orderId);

        var page = staff.listAuditLogs(ownerId, shopId, "ORDER_CONFIRM", 0, 50);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().actorId()).isEqualTo(staffId);
        assertThat(page.items().getFirst().targetType()).isEqualTo("ORDER");
        assertThat(page.items().getFirst().targetId()).isEqualTo(orderId);
    }

    @Test
    void withdrawalIsRecorded() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-withdrawal");
        var orderId = pendingOrder(shopId, user("buyer", "Buyer"));
        var settlement = SellerSettlement.create(shopId, orderId, 200000L, 0L);
        settlement.release();
        settlements.save(settlement);

        revenue.createWithdrawal(
            ownerId,
            new CreateWithdrawalRequest(shopId, 100000L, "Vietcombank", "0123456789", "NGUYEN VAN A")
        );

        var page = staff.listAuditLogs(ownerId, shopId, "WITHDRAWAL_CREATE", 0, 50);
        var row = page.items().getFirst();

        assertThat(page.items()).hasSize(1);
        assertThat(row.targetType()).isEqualTo("WITHDRAWAL");
        assertThat(row.detail()).contains("100000");
    }

    @Test
    void auditPageFiltersByAction() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-filter");
        catalog.createProduct(ownerId, shopId, productRequest("audit-filter-a"));
        catalog.createProduct(ownerId, shopId, productRequest("audit-filter-b"));
        var orderId = pendingOrder(shopId, user("buyer", "Buyer"));
        orders.confirm(ownerId, orderId);

        var page = staff.listAuditLogs(ownerId, shopId, "PRODUCT_CREATE", 0, 1);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items()).allMatch(item -> "PRODUCT_CREATE".equals(item.action()));
    }

    @Test
    void auditReturnsActorEmailAndName() {
        var ownerId = user("owner", "Audit Owner");
        var shopId = approvedShop(ownerId, "audit-actor");
        catalog.createProduct(ownerId, shopId, productRequest("audit-actor"));

        var page = staff.listAuditLogs(ownerId, shopId, null, 0, 50);
        var row = page.items().getFirst();

        assertThat(row.actorId()).isEqualTo(ownerId);
        assertThat(row.actorEmail()).isEqualTo(users.getById(ownerId).email());
        assertThat(row.actorName()).isEqualTo("Audit Owner");
    }

    private UUID user(String prefix, String displayName) {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest(prefix + "-" + id + "@t.local", displayName, null));
        return id;
    }

    private UUID approvedShop(UUID ownerId, String slug) {
        var adminId = user("admin", "Admin");
        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private UUID staffWith(UUID ownerId, UUID shopId, String permission) {
        var staffId = user("staff", "Staff");
        var role = ShopRole.create(shopId, "Audit " + UUID.randomUUID(), new String[]{permission}, false);
        roles.save(role);
        members.add(ShopMember.staff(shopId, staffId, role.getId(), ownerId));
        return staffId;
    }

    private UUID pendingOrder(UUID shopId, UUID buyerId) {
        var order = Order.create(
            "ORD-AUDIT-" + UUID.randomUUID(),
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

    private UUID testCategory() {
        return categories.findAllByOrderBySortOrderAsc().stream()
            .findFirst()
            .map(Category::getId)
            .orElseGet(() -> categories.save(Category.seed("Áo Nam", "ao-nam", 0)).getId());
    }

    private CreateProductRequest productRequest(String slug) {
        return new CreateProductRequest(
            "Áo Thun " + slug,
            slug,
            "Mô tả chi tiết " + slug,
            testCategory(),
            List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true)),
            List.of(
                new ImageItemRequest("img1.jpg", null, 0),
                new ImageItemRequest("img2.jpg", null, 1),
                new ImageItemRequest("img3.jpg", null, 2)
            ),
            null, null, null, null, null, null, null, null, null, null, null, "ACTIVE"
        );
    }
}
