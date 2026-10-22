package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductReportRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.catalog.ProductResponse;
import com.minhnbnt.fashionstore.dto.catalog.UpdateProductRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.shop.CreateShopRequest;
import com.minhnbnt.fashionstore.features.admin.AdminProductService;
import com.minhnbnt.fashionstore.features.admin.AdminStatsService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.ProductViolationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProductReportServiceTest {

    @Autowired
    private ProductReportService reports;

    @Autowired
    private AdminProductService adminProducts;

    @Autowired
    private AdminStatsService stats;

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
        var shop = shops.register(ownerId, com.minhnbnt.fashionstore.test.ShopTestFixtures.createRequest("Shop " + slug, slug));
        shops.approve(adminId, shop.id());
        return shop.id();
    }

    private ProductResponse product(UUID ownerId, UUID shopId, String slug) {
        return catalog.createProduct(
            ownerId,
            shopId,
            new CreateProductRequest(
                "Ao " + slug,
                slug,
                null,
                null,
                List.of(new CreateVariantRequest("SKU-" + slug, "M", "Den", 100000L, null, 10, true))
            )
        );
    }

    private static CreateProductReportRequest report(ProductViolationType reason) {
        return new CreateProductReportRequest(reason, "Nhãn mác in sai, chất vải khác ảnh");
    }

    private static void assertStatus(Throwable e, HttpStatus status) {
        assertThat(e).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status);
    }

    @Test
    void buyerReportPutsProductIntoReportedQueue() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-queue");
        var product = product(ownerId, shopId, "report-queue-ao");

        var created = reports.report(mirroredUser(), product.id(), report(ProductViolationType.COUNTERFEIT));
        reports.report(mirroredUser(), product.id(), report(ProductViolationType.COUNTERFEIT));
        reports.report(mirroredUser(), product.id(), report(ProductViolationType.MISLEADING));

        assertThat(created.status()).isEqualTo("OPEN");
        var queue = adminProducts.search(null, shopId, null, true, 0, 20);
        assertThat(queue.total()).isEqualTo(1);
        var row = queue.items().get(0);
        assertThat(row.id()).isEqualTo(product.id());
        assertThat(row.openReportCount()).isEqualTo(3);
        assertThat(row.topReportReason()).isEqualTo("COUNTERFEIT");
        assertThat(row.lastReportedAt()).isNotNull();
        assertThat(row.systemFlagged()).isFalse();
    }

    @Test
    void statsCountOpenReports() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-stats");
        var product = product(ownerId, shopId, "report-stats-ao");
        var before = stats.get().openProductReports();

        reports.report(mirroredUser(), product.id(), report(ProductViolationType.SPAM));

        assertThat(stats.get().openProductReports()).isGreaterThanOrEqualTo(before + 1);
    }

    @Test
    void reportedQueueOrdersByOpenReportCount() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-order");
        var few = product(ownerId, shopId, "report-order-few");
        var many = product(ownerId, shopId, "report-order-many");
        reports.report(mirroredUser(), few.id(), report(ProductViolationType.SPAM));
        reports.report(mirroredUser(), many.id(), report(ProductViolationType.SPAM));
        reports.report(mirroredUser(), many.id(), report(ProductViolationType.SPAM));

        var queue = adminProducts.search(null, shopId, null, true, 0, 20);

        assertThat(queue.items()).extracting(item -> item.id()).containsExactly(many.id(), few.id());
    }

    @Test
    void sameBuyerCannotOpenTwoReports() {
        var ownerId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-dup");
        var product = product(ownerId, shopId, "report-dup-ao");
        reports.report(buyerId, product.id(), report(ProductViolationType.SPAM));

        assertThatThrownBy(() -> reports.report(buyerId, product.id(), report(ProductViolationType.SPAM)))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void shopMemberCannotReportOwnProduct() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-own");
        var product = product(ownerId, shopId, "report-own-ao");

        assertThatThrownBy(() -> reports.report(ownerId, product.id(), report(ProductViolationType.SPAM)))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void hiddenProductCannotBeReported() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-hidden");
        var product = product(ownerId, shopId, "report-hidden-ao");
        catalog.updateProduct(ownerId, shopId, product.id(), new UpdateProductRequest(null, null, null, "HIDDEN", null));

        assertThatThrownBy(() -> reports.report(mirroredUser(), product.id(), report(ProductViolationType.SPAM)))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }

    @Test
    void banResolvesOpenReports() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-ban");
        var product = product(ownerId, shopId, "report-ban-ao");
        reports.report(mirroredUser(), product.id(), report(ProductViolationType.COUNTERFEIT));

        adminProducts.ban(adminId, product.id(), ProductViolationType.COUNTERFEIT, "Xác minh là hàng nhái");

        assertThat(adminProducts.search(null, shopId, null, true, 0, 20).total()).isZero();
        var detail = adminProducts.getDetail(product.id());
        assertThat(detail.reports()).hasSize(1);
        var resolved = detail.reports().get(0);
        assertThat(resolved.status()).isEqualTo("ACTION_TAKEN");
        assertThat(resolved.resolvedByEmail()).isEqualTo("u-" + adminId + "@t.local");
        assertThat(resolved.reporterEmail()).isNotNull();
        assertThat(resolved.source()).isEqualTo("USER");
    }

    @Test
    void dismissClosesReportsAndKeepsProductOnSale() {
        var ownerId = mirroredUser();
        var adminId = mirroredUser();
        var buyerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-dismiss");
        var product = product(ownerId, shopId, "report-dismiss-ao");
        reports.report(buyerId, product.id(), report(ProductViolationType.MISLEADING));
        reports.report(mirroredUser(), product.id(), report(ProductViolationType.MISLEADING));

        var dismissed = adminProducts.dismissReports(adminId, product.id(), "Ảnh đúng thực tế");

        assertThat(dismissed.dismissedCount()).isEqualTo(2);
        var detail = adminProducts.getDetail(product.id());
        assertThat(detail.product().status()).isEqualTo("ACTIVE");
        assertThat(detail.product().openReportCount()).isZero();
        assertThat(detail.reports()).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo("DISMISSED");
            assertThat(r.resolutionNote()).isEqualTo("Ảnh đúng thực tế");
        });

        var again = reports.report(buyerId, product.id(), report(ProductViolationType.MISLEADING));
        assertThat(again.status()).isEqualTo("OPEN");
    }

    @Test
    void dismissWithoutOpenReportsIsConflict() {
        var ownerId = mirroredUser();
        var shopId = approvedShop(ownerId, "report-dismiss-none");
        var product = product(ownerId, shopId, "report-dismiss-none-ao");

        assertThatThrownBy(() -> adminProducts.dismissReports(mirroredUser(), product.id(), null))
            .satisfies(e -> assertStatus(e, HttpStatus.CONFLICT));
    }

    @Test
    void dismissUnknownProductIsNotFound() {
        assertThatThrownBy(() -> adminProducts.dismissReports(mirroredUser(), UUID.randomUUID(), null))
            .satisfies(e -> assertStatus(e, HttpStatus.NOT_FOUND));
    }
}
