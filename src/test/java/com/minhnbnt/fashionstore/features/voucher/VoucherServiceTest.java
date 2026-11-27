package com.minhnbnt.fashionstore.features.voucher;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.voucher.CreateShopVoucherRequest;
import com.minhnbnt.fashionstore.dto.voucher.PublicShopVoucherResponse;
import com.minhnbnt.fashionstore.dto.voucher.SellerVoucherResponse;
import com.minhnbnt.fashionstore.dto.voucher.UpdateShopVoucherRequest;
import com.minhnbnt.fashionstore.dto.voucher.VoucherLineItem;
import com.minhnbnt.fashionstore.dto.voucher.VoucherProductBrief;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopMemberAuditRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.features.upload.repository.UploadRepository;
import com.minhnbnt.fashionstore.features.voucher.repository.ShopVoucherRepository;
import com.minhnbnt.fashionstore.features.voucher.service.VoucherService;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ShopMemberAudit;
import com.minhnbnt.fashionstore.model.VoucherDiscountType;
import com.minhnbnt.fashionstore.model.VoucherProductScope;
import com.minhnbnt.fashionstore.model.VoucherStatus;
import com.minhnbnt.fashionstore.model.VoucherVisibility;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class VoucherServiceTest {

    @Autowired
    private VoucherService vouchers;

    @Autowired
    private ShopVoucherRepository voucherRepository;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private UploadRepository uploads;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ShopMemberAuditRepository audits;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Clock clock;

    private UUID createUser() {
        var id = UUID.randomUUID();
        users.upsert(id, new CreateUserRequest("u-" + id + "@t.local", "User"));
        return id;
    }

    private UUID createApprovedShop(UUID ownerId, String namePrefix) {
        var suffix = UUID.randomUUID().toString().substring(0, 8);
        var created = shops.register(
            ownerId,
            ShopTestFixtures.createRequest(uploads, namePrefix + " " + suffix, "shop-" + suffix)
        );
        var adminId = createUser();
        shops.approve(adminId, created.id());
        return created.id();
    }

    private Product createShopProduct(UUID shopId, UUID ownerId, String name) {
        var slug = "prod-" + UUID.randomUUID().toString().substring(0, 8);
        var product = Product.create(shopId, null, name, slug, "Mo ta", ownerId);
        return products.save(product);
    }

    private void insertOrderWithVoucher(UUID shopId, UUID buyerId, UUID voucherId, String code, String status) {
        var orderId = UUID.randomUUID();
        var orderCode = "ORD-" + orderId.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        jdbc.sql("""
            INSERT INTO orders (
                id, code, shop_id, buyer_id, status, payment_method, payment_status,
                subtotal, shipping_fee, discount_amount, total,
                shipping_name, shipping_phone, shipping_address, note,
                voucher_id, voucher_code
            ) VALUES (
                :id, :code, :shopId, :buyerId, :status, 'COD', 'UNPAID',
                200000, 30000, 20000, 210000,
                'Buyer', '0901234567', '123 Street', '',
                :voucherId, :voucherCode
            )
            """)
            .param("id", orderId)
            .param("code", orderCode)
            .param("shopId", shopId)
            .param("buyerId", buyerId)
            .param("status", status)
            .param("voucherId", voucherId)
            .param("voucherCode", code)
            .update();
    }

    @Test
    void createAndListSellerVouchersEnforcesUniqueCodeAndProductOwnership() {
        var ownerId = createUser();
        var shopId = createApprovedShop(ownerId, "Voucher Shop");
        var otherShopId = createApprovedShop(ownerId, "Other Shop");

        var productA = createShopProduct(shopId, ownerId, "Áo Sơ Mi Lụa");
        var productB = createShopProduct(shopId, ownerId, "Quần Tây Âu");
        var foreignProduct = createShopProduct(otherShopId, ownerId, "Áo Khoác Shop Khác");

        var now = clock.instant();
        var startsAt = now.minusSeconds(300);
        var endsAt = now.plusSeconds(7200);

        var allProductsVoucher = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "  allshop50k ",
                "Giảm 50K toàn shop",
                "Áp dụng mọi sản phẩm",
                VoucherDiscountType.FIXED_AMOUNT,
                50_000L,
                null,
                200_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                100,
                1,
                startsAt,
                endsAt,
                true,
                List.of(productA.getId())
            )
        );

        assertThat(allProductsVoucher.code()).isEqualTo("ALLSHOP50K");
        assertThat(allProductsVoucher.status()).isEqualTo(VoucherStatus.ONGOING);
        assertThat(allProductsVoucher.productIds()).isEmpty();
        assertThat(allProductsVoucher.products()).isEmpty();

        var specificVoucher = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "SPEC15PCT",
                "Giảm 15% sản phẩm chọn lọc",
                "Tối đa 40k",
                VoucherDiscountType.PERCENTAGE,
                15L,
                40_000L,
                150_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PUBLIC,
                50,
                2,
                startsAt,
                endsAt,
                true,
                List.of(productA.getId(), productB.getId())
            )
        );

        assertThat(specificVoucher.productIds()).containsExactlyInAnyOrder(productA.getId(), productB.getId());
        assertThat(specificVoucher.products())
            .extracting(VoucherProductBrief::productName)
            .containsExactlyInAnyOrder("Áo Sơ Mi Lụa", "Quần Tây Âu");

        var fetched = vouchers.getForSeller(ownerId, shopId, specificVoucher.id());
        assertThat(fetched.code()).isEqualTo("SPEC15PCT");
        assertThat(fetched.products()).hasSize(2);

        // Duplicate code in same shop -> 409 CONFLICT
        assertThatThrownBy(() -> vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "allshop50k",
                "Trùng mã",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                10_000L,
                null,
                0L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                startsAt,
                endsAt,
                true,
                null
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // SPECIFIC_PRODUCTS with empty productIds -> 400 BAD_REQUEST
        assertThatThrownBy(() -> vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "EMPTYPROD",
                "Không chọn sản phẩm",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                10_000L,
                null,
                0L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                startsAt,
                endsAt,
                true,
                List.of()
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        // SPECIFIC_PRODUCTS with product from another shop -> 400 BAD_REQUEST
        assertThatThrownBy(() -> vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "FOREIGNPROD",
                "Sản phẩm shop khác",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                10_000L,
                null,
                0L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                startsAt,
                endsAt,
                true,
                List.of(productA.getId(), foreignProduct.getId())
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listForSellerFiltersByStatusAndSearchQuery() {
        var ownerId = createUser();
        var shopId = createApprovedShop(ownerId, "Filter Shop");
        var now = clock.instant();

        // 1. ONGOING
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "ONGOING10",
                "Đang diễn ra mùa hè",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                10_000L,
                null,
                50_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                true,
                null
            )
        );

        // 2. UPCOMING
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "UPCOMING20",
                "Sắp diễn ra thu đông",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                20_000L,
                null,
                50_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.plusSeconds(1800),
                now.plusSeconds(7200),
                true,
                null
            )
        );

        // 3. PAUSED
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "PAUSED30",
                "Tạm dừng flash",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                30_000L,
                null,
                50_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                false,
                null
            )
        );

        // 4. DEPLETED
        var depleted = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "DEPLETED40",
                "Hết lượt nhanh",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                40_000L,
                null,
                50_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                1,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                true,
                null
            )
        );
        vouchers.redeemForOrder(
            shopId,
            createUser(),
            depleted.code(),
            List.of(new VoucherLineItem(UUID.randomUUID(), 100_000L))
        );

        // 5. EXPIRED
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "EXPIRED50",
                "Đã kết thúc năm cũ",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                50_000L,
                null,
                50_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.minusSeconds(7200),
                now.minusSeconds(60),
                true,
                null
            )
        );

        assertThat(vouchers.listForSeller(ownerId, shopId, null, null, 0, 20).total()).isEqualTo(5);
        assertThat(vouchers.listForSeller(ownerId, shopId, "ONGOING", null, 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("ONGOING10");
        assertThat(vouchers.listForSeller(ownerId, shopId, "UPCOMING", null, 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("UPCOMING20");
        assertThat(vouchers.listForSeller(ownerId, shopId, "PAUSED", null, 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("PAUSED30");
        assertThat(vouchers.listForSeller(ownerId, shopId, "DEPLETED", null, 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("DEPLETED40");
        assertThat(vouchers.listForSeller(ownerId, shopId, "EXPIRED", null, 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("EXPIRED50");

        // Search by code or name
        assertThat(vouchers.listForSeller(ownerId, shopId, null, "upcoming", 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("UPCOMING20");
        assertThat(vouchers.listForSeller(ownerId, shopId, null, "mùa hè", 0, 20).items())
            .extracting(SellerVoucherResponse::code)
            .containsExactly("ONGOING10");
    }

    @Test
    void updateToggleDeleteAndAuditBehaviorWhenUnusedVersusUsed() {
        var ownerId = createUser();
        var shopId = createApprovedShop(ownerId, "Audit Shop");
        var productA = createShopProduct(shopId, ownerId, "Đầm Công Sở");
        var productB = createShopProduct(shopId, ownerId, "Chân Váy Xếp Ly");
        var now = clock.instant();
        var startsAt = now.minusSeconds(300);
        var endsAt = now.plusSeconds(3600);

        var created = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "EDITME10",
                "Khuyến mãi ban đầu",
                "Mô tả 1",
                VoucherDiscountType.FIXED_AMOUNT,
                10_000L,
                null,
                100_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                5,
                1,
                startsAt,
                endsAt,
                true,
                null
            )
        );

        // Update while usedCount == 0 allows changing core fields
        var updatedUnused = vouchers.update(
            ownerId,
            shopId,
            created.id(),
            new UpdateShopVoucherRequest(
                "EDITME20",
                "Khuyến mãi đã đổi",
                "Mô tả 2",
                VoucherDiscountType.PERCENTAGE,
                20L,
                50_000L,
                150_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PRIVATE,
                10,
                2,
                startsAt,
                endsAt,
                true,
                List.of(productA.getId())
            )
        );
        assertThat(updatedUnused.code()).isEqualTo("EDITME20");
        assertThat(updatedUnused.discountType()).isEqualTo(VoucherDiscountType.PERCENTAGE);
        assertThat(updatedUnused.productIds()).containsExactly(productA.getId());

        // Toggle active -> records VOUCHER_STATUS
        var toggled = vouchers.toggleActive(ownerId, shopId, created.id(), false);
        assertThat(toggled.active()).isFalse();
        assertThat(toggled.status()).isEqualTo(VoucherStatus.PAUSED);

        vouchers.toggleActive(ownerId, shopId, created.id(), true);

        // Redeem once so usedCount == 1
        vouchers.redeemForOrder(
            shopId,
            createUser(),
            "EDITME20",
            List.of(new VoucherLineItem(productA.getId(), 200_000L))
        );

        // Updating core field when usedCount > 0 -> 409 CONFLICT
        assertThatThrownBy(() -> vouchers.update(
            ownerId,
            shopId,
            created.id(),
            new UpdateShopVoucherRequest(
                "EDITME20",
                "Đổi mức giảm khi đã dùng",
                "Mô tả 2",
                VoucherDiscountType.PERCENTAGE,
                30L,
                50_000L,
                150_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PRIVATE,
                10,
                2,
                startsAt,
                endsAt,
                true,
                List.of(productA.getId())
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Updating productIds of SPECIFIC_PRODUCTS when usedCount > 0 -> 409 CONFLICT
        assertThatThrownBy(() -> vouchers.update(
            ownerId,
            shopId,
            created.id(),
            new UpdateShopVoucherRequest(
                "EDITME20",
                "Đổi sản phẩm áp dụng khi đã dùng",
                "Mô tả 2",
                VoucherDiscountType.PERCENTAGE,
                20L,
                50_000L,
                150_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PRIVATE,
                10,
                2,
                startsAt,
                endsAt,
                true,
                List.of(productB.getId())
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Updating startsAt when usedCount > 0 -> 409 CONFLICT
        assertThatThrownBy(() -> vouchers.update(
            ownerId,
            shopId,
            created.id(),
            new UpdateShopVoucherRequest(
                "EDITME20",
                "Đổi thời gian bắt đầu khi đã dùng",
                "Mô tả 2",
                VoucherDiscountType.PERCENTAGE,
                20L,
                50_000L,
                150_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PRIVATE,
                10,
                2,
                startsAt.plusSeconds(600),
                endsAt,
                true,
                List.of(productA.getId())
            )
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Deleting when usedCount > 0 -> 409 CONFLICT
        assertThatThrownBy(() -> vouchers.delete(ownerId, shopId, created.id()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Creating an unused voucher and deleting it succeeds
        var disposable = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "DELME100",
                "Mã xóa được",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                15_000L,
                null,
                50_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                startsAt,
                endsAt,
                true,
                null
            )
        );
        vouchers.delete(ownerId, shopId, disposable.id());
        assertThat(voucherRepository.findById(disposable.id())).isEmpty();

        // Check audit log entries
        var shopAudits = audits.findByShopIdOrderByCreatedAtDesc(shopId);
        assertThat(shopAudits)
            .filteredOn(a -> "VOUCHER".equals(a.getTargetType()))
            .extracting(ShopMemberAudit::getAction)
            .contains("VOUCHER_CREATE", "VOUCHER_UPDATE", "VOUCHER_STATUS", "VOUCHER_DELETE");
    }

    @Test
    void publicListHidesPrivatePausedExpiredAndDepletedVouchersAndPopulatesExhaustedByBuyer() {
        var ownerId = createUser();
        var buyerId = createUser();
        var shopId = createApprovedShop(ownerId, "Storefront Shop");
        var productA = createShopProduct(shopId, ownerId, "Áo Thun Trắng");
        var now = clock.instant();

        var ongoingPublic = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "PUBONGOING",
                "Công khai đang chạy",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                25_000L,
                null,
                100_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                true,
                List.of(productA.getId())
            )
        );

        var upcomingPublic = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "PUBUPCOMING",
                "Công khai sắp mở",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                50_000L,
                null,
                200_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.plusSeconds(600),
                now.plusSeconds(7200),
                true,
                null
            )
        );

        // Private voucher (should be hidden)
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "SECRET99",
                "Mã ẩn",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                99_000L,
                null,
                100_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PRIVATE,
                10,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                true,
                null
            )
        );

        // Paused voucher (should be hidden)
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "PUBPAUSED",
                "Tạm dừng",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                20_000L,
                null,
                100_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                false,
                null
            )
        );

        // Expired voucher (should be hidden)
        vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "PUBEXPIRED",
                "Hết hạn",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                20_000L,
                null,
                100_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                10,
                1,
                now.minusSeconds(7200),
                now.minusSeconds(60),
                true,
                null
            )
        );

        // Depleted voucher (should be hidden)
        var depleted = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "PUBDEPLETED",
                "Hết lượt",
                null,
                VoucherDiscountType.FIXED_AMOUNT,
                20_000L,
                null,
                100_000L,
                VoucherProductScope.ALL_PRODUCTS,
                VoucherVisibility.PUBLIC,
                1,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                true,
                null
            )
        );
        vouchers.redeemForOrder(
            shopId,
            createUser(),
            depleted.code(),
            List.of(new VoucherLineItem(productA.getId(), 150_000L))
        );

        // Buyer has already used ongoingPublic in a non-cancelled order
        insertOrderWithVoucher(shopId, buyerId, ongoingPublic.id(), ongoingPublic.code(), "PENDING");

        var publicList = vouchers.listPublicForShop(shopId, buyerId);
        assertThat(publicList)
            .extracting(PublicShopVoucherResponse::code)
            .containsExactly(ongoingPublic.code(), upcomingPublic.code());

        var first = publicList.get(0);
        assertThat(first.status()).isEqualTo(VoucherStatus.ONGOING);
        assertThat(first.applicableProductIds()).containsExactly(productA.getId());
        assertThat(first.exhaustedByBuyer()).isTrue();

        var second = publicList.get(1);
        assertThat(second.status()).isEqualTo(VoucherStatus.UPCOMING);
        assertThat(second.applicableProductIds()).isEmpty();
        assertThat(second.exhaustedByBuyer()).isFalse();
    }

    @Test
    void quoteForPreviewAndRedeemForOrderCalculateSpecificProductSubtotalAndReleaseUsage() {
        var ownerId = createUser();
        var buyerId = createUser();
        var shopId = createApprovedShop(ownerId, "Checkout Shop");
        var productA = createShopProduct(shopId, ownerId, "Áo Len");
        var productB = createShopProduct(shopId, ownerId, "Quần Jeans");
        var now = clock.instant();

        var voucher = vouchers.create(
            ownerId,
            shopId,
            new CreateShopVoucherRequest(
                "LEN20PCT",
                "Giảm 20% Áo Len",
                null,
                VoucherDiscountType.PERCENTAGE,
                20L,
                50_000L,
                100_000L,
                VoucherProductScope.SPECIFIC_PRODUCTS,
                VoucherVisibility.PUBLIC,
                2,
                1,
                now.minusSeconds(300),
                now.plusSeconds(3600),
                true,
                List.of(productA.getId())
            )
        );

        // Cart has productA (120,000) and productB (200,000) -> eligibleSubtotal = 120,000 -> 20% = 24,000
        var mixedLines = List.of(
            new VoucherLineItem(productA.getId(), 120_000L),
            new VoucherLineItem(productB.getId(), 200_000L)
        );
        var quote = vouchers.quoteForPreview(shopId, buyerId, "  len20pct ", mixedLines);
        assertThat(quote.voucherId()).isEqualTo(voucher.id());
        assertThat(quote.voucherCode()).isEqualTo("LEN20PCT");
        assertThat(quote.discountAmount()).isEqualTo(24_000L);
        assertThat(quote.error()).isNull();

        // Cart only has productB (200,000) -> eligibleSubtotal = 0 -> quote returns 0 + error, redeem throws 409
        var ineligibleLines = List.of(new VoucherLineItem(productB.getId(), 200_000L));
        var badQuote = vouchers.quoteForPreview(shopId, buyerId, "LEN20PCT", ineligibleLines);
        assertThat(badQuote.discountAmount()).isZero();
        assertThat(badQuote.error()).isNotNull();

        assertThatThrownBy(() -> vouchers.redeemForOrder(shopId, buyerId, "LEN20PCT", ineligibleLines))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Unknown code -> quote returns 0 + error, redeem throws 404 NOT_FOUND
        var missingQuote = vouchers.quoteForPreview(shopId, buyerId, "NOTEXIST", mixedLines);
        assertThat(missingQuote.discountAmount()).isZero();
        assertThat(missingQuote.error()).isNotNull();

        assertThatThrownBy(() -> vouchers.redeemForOrder(shopId, buyerId, "NOTEXIST", mixedLines))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);

        // Redeem with valid mixedLines -> increments usedCount to 1
        var applied = vouchers.redeemForOrder(shopId, buyerId, "LEN20PCT", mixedLines);
        assertThat(applied.voucherId()).isEqualTo(voucher.id());
        assertThat(applied.voucherCode()).isEqualTo("LEN20PCT");
        assertThat(applied.discountAmount()).isEqualTo(24_000L);
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isEqualTo(1);

        // Record active order for buyerId -> perBuyerLimit (1) reached
        insertOrderWithVoucher(shopId, buyerId, voucher.id(), voucher.code(), "CONFIRMED");
        var exhaustedQuote = vouchers.quoteForPreview(shopId, buyerId, "LEN20PCT", mixedLines);
        assertThat(exhaustedQuote.discountAmount()).isZero();
        assertThat(exhaustedQuote.error()).isNotNull();

        assertThatThrownBy(() -> vouchers.redeemForOrder(shopId, buyerId, "LEN20PCT", mixedLines))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);

        // Release usage decrements usedCount back to 0 (and never below 0)
        vouchers.releaseVoucherUsage(voucher.id());
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isZero();

        vouchers.releaseVoucherUsage(voucher.id());
        assertThat(voucherRepository.findById(voucher.id()).orElseThrow().getUsedCount()).isZero();
    }
}
