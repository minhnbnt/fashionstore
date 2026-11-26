# Shop Voucher (Seller Discount Vouchers) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng hệ thống tạo, quản lý và áp dụng Mã giảm giá của Gian hàng (Shop Voucher) tại Kênh Người Bán (`/shop?tab=vouchers`), hiển thị trên Trang Gian hàng & Chi tiết Sản phẩm, áp dụng theo từng đơn Shop khi Xem trước & Đặt hàng (`/checkout`), giữ/hoàn lượt dùng nguyên tử khi đặt/hủy đơn, và liên thông chính xác với quyết toán Ví Shop (`SellerSettlement` Escrow 5%) cùng hoàn tiền đổi trả (`ReturnRequest`).

**Architecture:** Backend Spring Boot 4 + Spring Data JDBC bổ sung migration `V50__shop_vouchers.sql`, mở rộng `ShopPermission` (`VOUCHER_READ`, `VOUCHER_WRITE`), thêm package 3 lớp `features/voucher/` (`ShopVoucherRepository`, `VoucherService`, `SellerVoucherController`, `PublicShopVoucherController`) và tích hợp vào `Order`, `OrderService`, `CartService`, `ReturnRequestService` theo đồ thị phụ thuộc một chiều (`CartService` → `OrderService` → `VoucherService`). Frontend React 19 + TanStack Query + Orval cập nhật client từ OpenAPI spec, bổ sung tab quản lý `SellerVouchersTab.tsx`, dải mã giảm giá `ShopVoucherStrip.tsx` trên Storefront/PDP, và bộ chọn/nhập mã theo từng Shop `ShopVoucherModal.tsx` trên `Checkout.tsx`.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JDBC, JdbcClient, Flyway, PostgreSQL (Testcontainers), JUnit 5, AssertJ, MockMvc; React 19, TypeScript, TanStack Query, Orval, Tailwind CSS v4, Vitest, pnpm.

**Spec:** `docs/superpowers/specs/2026-11-26-shop-voucher-design.md`

**Workspace Paths:**
- Backend repo: `d:\fashionstore\fashionstore` (`B = src/main/java/com/minhnbnt/fashionstore`, `T = src/test/java/com/minhnbnt/fashionstore`)
- Frontend repo: `d:\fashionstore\fashionstore-ui`

## Global Constraints

- Tuân thủ nghiêm ngặt `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md` (BE) và `AGENTS.md`, `docs/design-system.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/pre-commit.md` (FE):
  - **Cấm toán tử 3 ngôi (`? :`)** trong cả Java và TypeScript/JSX (`if` + early `return`, JSX dùng `&&`).
  - **Java / Spring Data JDBC:** Indent 4 spaces, dòng tối đa 120 ký tự; entity implement `Persistable<UUID>`, sinh UUID tại factory `create(...)`, chỉ dùng Lombok trong allowlist (`@Getter`, `@RequiredArgsConstructor`, `@AllArgsConstructor(onConstructor_ = @PersistenceCreator)`), cấm `@Data/@Setter/@Builder/@EqualsAndHashCode`; hạn chế tối đa `@Nullable` (chỉ gắn ở field/param thật sự optional theo nghiệp vụ); tiền VND dùng `long`; thời gian dùng `Instant`.
  - **Cấm Derived Query sắp xếp nhiều cột (`OrderByAAscBAsc`)** trên Spring Data JDBC repository — luôn viết `@Query` SQL tường minh hoặc dùng `JdbcClient`.
  - **Cấm gọi Repository xuyên domain & Cấm phụ thuộc vòng (Circular Dependency):**
    - Chiều phụ thuộc một chiều (DAG): `CartService` → `OrderService` → `VoucherService` → (`ShopService`, `CatalogService`, `ShopAccessChecker`, `ShopAuditService`).
    - `VoucherService` **tuyệt đối không inject `OrderService` hay `OrderRepository`**: việc đếm số đơn không bị hủy của một buyer đã dùng `voucherId` được thực hiện bằng truy vấn SQL trong `ShopVoucherRepository.countActiveUsagesByBuyer(voucherId, buyerId)`.
  - **Springdoc & API:** Mỗi endpoint khai báo đủ `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse`, `@Parameter`, `@Schema`, `@SecurityRequirement(name = "bearerAuth")` (trừ GET public). Exception message bằng **tiếng Anh**. Không sửa `SecurityConfig.java` (vì `/api/shops/**` đã mở sẵn cho GET public).
  - **Tương thích ngược cho DTO và Entity:** Khi thêm trường mới (`voucherId`, `voucherCode`, `discountAmount`, `shopVouchers`) vào `Order`, `OrderResponse`, `CheckoutRequest`, `CartCheckoutRequest`, `CheckoutPreviewRequest`, `ShopCheckoutPreview`, `CheckoutPreviewResponse`, luôn giữ lại các constructor/factory cũ làm alias để toàn bộ test hiện có compile và xanh 100%.
  - **Frontend Design System & Structure:** Chỉ dùng `pnpm`; dùng semantic status tokens (`emerald`, `amber`, `rose`, `sky` — cấm trộn `red-*` hoặc `orange-*`); dùng chung `src/components/Modal.tsx` cho hộp thoại; chạy `pnpm format && pnpm lint && pnpm build && pnpm test` xanh (0 error, 0 warning).

## Review Focus

1. **Voucher `SPECIFIC_PRODUCTS` áp dụng cho giỏ hàng có cả sản phẩm hợp lệ lẫn sản phẩm không thuộc danh sách:** Điều kiện `minOrderSubtotal` và mức giảm `%` chỉ được tính trên `eligibleSubtotal` (tổng tiền các sản phẩm thuộc danh sách áp dụng), tuyệt đối không tính trên toàn bộ `shopSubtotal`, và `discountAmount <= eligibleSubtotal` (kiểm thử tại Task 1 & Task 2).
2. **Chỉnh sửa hoặc Xóa voucher đã có khách hàng sử dụng (`usedCount > 0`):** Gọi `DELETE` hoặc cố sửa `code`, `discountType`, `discountValue`, `maxDiscountAmount`, `minOrderSubtotal`, `productScope`, hoặc đặt `usageLimit < usedCount` phải bị chặn `409 CONFLICT`; trong khi đổi `name`, `endsAt`, `active`, hoặc tăng `usageLimit` vẫn thành công (kiểm thử tại Task 1 & Task 2).
3. **Xem trước giỏ hàng (`previewCheckout`) với mã voucher không đủ điều kiện vs Đặt hàng thật (`checkout`):** `previewCheckout` không được ném lỗi làm sập trang Checkout khi khách bỏ bớt món trong giỏ khiến đơn tụt dưới `minOrderSubtotal`, mà phải trả `discountAmount = 0` kèm `voucherError` trong `ShopCheckoutPreview`; ngược lại `checkout` thật với mã không hợp lệ phải chặn ngay với `409 CONFLICT` (kiểm thử tại Task 3).
4. **Hoàn lượt dùng voucher ở cả 5 luồng hủy đơn (bao gồm đơn VietQR hết hạn 15 phút):** Khi đơn có gắn voucher bị Buyer hủy, Seller hủy, Admin hủy, quá hạn 24h xác nhận, hoặc hết hạn 15 phút chờ thanh toán VietQR, `used_count` của voucher phải giảm đi 1 và người mua có `perBuyerLimit = 1` phải dùng lại được mã đó cho đơn mới (kiểm thử tại Task 3).
5. **Quyết toán Ví Shop (`SellerSettlement`) và Hoàn tiền Đổi trả (`ReturnRequest`) cho đơn có Voucher:** `SellerSettlement.grossAmount` phải bằng `subtotal - discountAmount` (không phải `subtotal` gốc), và khi hoàn tất trả hàng hoàn tiền 100% (`REFUND`), số tiền hoàn tối đa không được vượt quá `subtotal - discountAmount` (kiểm thử tại Task 3).

---

### Task 1: Migration `V50__shop_vouchers.sql`, Domain Model (`ShopVoucher`, Enums, `ShopPermission`) & `ShopVoucherRepository`

**Files:**
- Create: `src/main/resources/db/migration/V50__shop_vouchers.sql`
- Modify: `B/model/ShopPermission.java`
- Create: `B/model/VoucherDiscountType.java`, `B/model/VoucherProductScope.java`, `B/model/VoucherVisibility.java`, `B/model/VoucherStatus.java`, `B/model/ShopVoucher.java`
- Create: `B/features/voucher/repository/ShopVoucherRepository.java`
- Test: `T/model/ShopVoucherTest.java`

**Interfaces:**
- Consumes: `Persistable<UUID>`, `JdbcClient`
- Produces:
  - `ShopPermission.VOUCHER_READ`, `ShopPermission.VOUCHER_WRITE`
  - `enum VoucherDiscountType { FIXED_AMOUNT, PERCENTAGE }`
  - `enum VoucherProductScope { ALL_PRODUCTS, SPECIFIC_PRODUCTS }`
  - `enum VoucherVisibility { PUBLIC, PRIVATE }`
  - `enum VoucherStatus { ONGOING, UPCOMING, PAUSED, DEPLETED, EXPIRED }`
  - `ShopVoucher.create(UUID shopId, String code, String name, String description, VoucherDiscountType discountType, long discountValue, @Nullable Long maxDiscountAmount, long minOrderSubtotal, VoucherProductScope productScope, VoucherVisibility visibility, int usageLimit, int perBuyerLimit, Instant startsAt, Instant endsAt, boolean active, UUID createdBy, Instant now): ShopVoucher`
  - `ShopVoucher.statusAt(Instant now): VoucherStatus`
  - `ShopVoucher.calculateDiscount(long eligibleSubtotal): long`
  - `ShopVoucher.update(String code, String name, String description, VoucherDiscountType discountType, long discountValue, @Nullable Long maxDiscountAmount, long minOrderSubtotal, VoucherProductScope productScope, VoucherVisibility visibility, int usageLimit, int perBuyerLimit, Instant startsAt, Instant endsAt, boolean active, Instant now): void`
  - `ShopVoucher.setActive(boolean active, Instant now): void`
  - `ShopVoucher.incrementUsedCount(Instant now): void`
  - `ShopVoucherRepository` (CRUD `shop_voucher` + quản lý `shop_voucher_product` + `findLockedByShopIdAndCode(UUID shopId, String code)` + `decrementUsedCount(UUID voucherId, Instant now)` + `countActiveUsagesByBuyer(UUID voucherId, UUID buyerId)`)

- [ ] **Step 1: Tạo migration `V50__shop_vouchers.sql` và viết unit test đỏ `ShopVoucherTest`**

Tạo `src/main/resources/db/migration/V50__shop_vouchers.sql` đúng theo mục §4.1 của Spec (`shop_voucher`, `shop_voucher_product`, mở rộng `orders` với `voucher_id`, `voucher_code`, `discount_amount` và constraint `orders_total_math_check`).

Viết `T/model/ShopVoucherTest.java`:
```java
@Test
void createNormalizesCodeAndValidatesPercentageAndDates() {
    var now = Instant.parse("2026-11-26T10:00:00Z");
    var voucher = ShopVoucher.create(
        shopId, "  summer15 ", "Chào hè", "", VoucherDiscountType.PERCENTAGE,
        15L, 50_000L, 200_000L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
        100, 1, now.minusSeconds(60), now.plusSeconds(3600), true, sellerId, now
    );
    assertThat(voucher.getCode()).isEqualTo("SUMMER15");
    assertThat(voucher.statusAt(now)).isEqualTo(VoucherStatus.ONGOING);
    assertThat(voucher.calculateDiscount(300_000L)).isEqualTo(45_000L);
    assertThat(voucher.calculateDiscount(500_000L)).isEqualTo(50_000L);
}

@Test
void fixedAmountDiscountNeverExceedsEligibleSubtotal() {
    var now = Instant.parse("2026-11-26T10:00:00Z");
    var voucher = ShopVoucher.create(
        shopId, "GIAM50K", "Giảm 50k", "", VoucherDiscountType.FIXED_AMOUNT,
        50_000L, 999_000L, 0L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
        10, 1, now.minusSeconds(60), now.plusSeconds(3600), true, sellerId, now
    );
    assertThat(voucher.getMaxDiscountAmount()).isNull();
    assertThat(voucher.calculateDiscount(30_000L)).isEqualTo(30_000L);
}

@Test
void updateLocksCoreDiscountFieldsWhenUsedCountGreaterThanZero() {
    var now = Instant.parse("2026-11-26T10:00:00Z");
    var voucher = ShopVoucher.create(
        shopId, "SALE20K", "Sale", "", VoucherDiscountType.FIXED_AMOUNT,
        20_000L, null, 100_000L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
        10, 1, now.minusSeconds(60), now.plusSeconds(3600), true, sellerId, now
    );
    voucher.incrementUsedCount(now);

    assertThatThrownBy(() -> voucher.update(
        "SALE20K", "Sale", "", VoucherDiscountType.FIXED_AMOUNT,
        30_000L, null, 100_000L, VoucherProductScope.ALL_PRODUCTS, VoucherVisibility.PUBLIC,
        10, 1, now.minusSeconds(60), now.plusSeconds(3600), true, now
    ))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
}
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*ShopVoucherTest"` (tại `d:\fashionstore\fashionstore`)
Expected: FAIL do các class model chưa tồn tại.

- [ ] **Step 3: Implement `ShopPermission`, các Enum `Voucher*`, `ShopVoucher` và `ShopVoucherRepository`**

- Trong `ShopPermission.java`: thêm `VOUCHER_READ` và `VOUCHER_WRITE`.
- Trong `ShopVoucher.java`:
  - Kiểm tra `code` khớp `^[A-Z0-9]{4,20}$` sau khi `trim().toUpperCase(Locale.ROOT)`.
  - Kiểm tra `endsAt.isAfter(startsAt)`, `discountValue > 0`, nếu `PERCENTAGE` thì `1 <= discountValue <= 99`, nếu `FIXED_AMOUNT` thì gán `maxDiscountAmount = null`.
  - Khi `usedCount > 0`: trong `update(...)`, nếu `code`, `discountType`, `discountValue`, `!Objects.equals(maxDiscountAmount, normalizedMax)`, `minOrderSubtotal`, hoặc `productScope` thay đổi → ném `409 CONFLICT` `"Cannot modify core discount terms of a voucher that has already been used"`; nếu `usageLimit < usedCount` → ném `409 CONFLICT` `"Usage limit cannot be less than current used count"`.
- Trong `ShopVoucherRepository.java`: dùng `JdbcClient` cho các truy vấn `findByShopIdOrderByCreatedAtDesc`, `findLockedByShopIdAndCode` (`FOR UPDATE`), `replaceVoucherProducts(UUID voucherId, Collection<UUID> productIds)`, `findProductIdsByVoucherId(UUID voucherId)`, `findProductIdsByVoucherIds(Collection<UUID> voucherIds)`, `countActiveUsagesByBuyer(UUID voucherId, UUID buyerId)` (`SELECT COUNT(*) FROM orders WHERE voucher_id = :voucherId AND buyer_id = :buyerId AND status <> 'CANCELLED'`), và `decrementUsedCount(UUID voucherId, Instant now)`.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*ShopVoucherTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V50__shop_vouchers.sql src/main/java/com/minhnbnt/fashionstore/model/ShopPermission.java src/main/java/com/minhnbnt/fashionstore/model/Voucher* src/main/java/com/minhnbnt/fashionstore/model/ShopVoucher.java src/main/java/com/minhnbnt/fashionstore/features/voucher/repository/ShopVoucherRepository.java src/test/java/com/minhnbnt/fashionstore/model/ShopVoucherTest.java
git commit -m "feat(voucher): add V50 migration, ShopVoucher domain model, permissions and repository"
```

---

### Task 2: `VoucherService`, `SellerVoucherController` & `PublicShopVoucherController`

**Files:**
- Create: `B/dto/voucher/CreateShopVoucherRequest.java`, `B/dto/voucher/UpdateShopVoucherRequest.java`, `B/dto/voucher/ToggleVoucherActiveRequest.java`, `B/dto/voucher/VoucherProductBrief.java`, `B/dto/voucher/SellerVoucherResponse.java`, `B/dto/voucher/SellerVoucherDetailResponse.java`, `B/dto/voucher/PublicShopVoucherResponse.java`, `B/dto/voucher/VoucherLineItem.java`, `B/dto/voucher/VoucherQuoteResult.java`, `B/dto/voucher/AppliedVoucher.java`
- Create: `B/features/voucher/service/VoucherService.java`
- Create: `B/features/voucher/controller/SellerVoucherController.java`
- Create: `B/features/voucher/controller/PublicShopVoucherController.java`
- Test: `T/features/voucher/VoucherServiceTest.java`, `T/features/voucher/controller/SellerVoucherControllerTest.java`

**Interfaces:**
- Consumes: `ShopVoucherRepository` (Task 1), `ShopAccessChecker`, `ShopAuditService`, `CatalogService`, `Clock`
- Produces:
  - `record VoucherLineItem(@Nullable UUID productId, long lineTotal)`
  - `record VoucherQuoteResult(@Nullable UUID voucherId, String voucherCode, long discountAmount, @Nullable String error)`
  - `record AppliedVoucher(UUID voucherId, String voucherCode, long discountAmount)`
  - `VoucherService.listForSeller(UUID userId, UUID shopId, @Nullable String status, @Nullable String q, int page, int size): PageResponse<SellerVoucherResponse>`
  - `VoucherService.getForSeller(UUID userId, UUID shopId, UUID voucherId): SellerVoucherDetailResponse`
  - `VoucherService.create(UUID userId, UUID shopId, CreateShopVoucherRequest request): SellerVoucherDetailResponse`
  - `VoucherService.update(UUID userId, UUID shopId, UUID voucherId, UpdateShopVoucherRequest request): SellerVoucherDetailResponse`
  - `VoucherService.toggleActive(UUID userId, UUID shopId, UUID voucherId, boolean active): SellerVoucherResponse`
  - `VoucherService.delete(UUID userId, UUID shopId, UUID voucherId): void`
  - `VoucherService.listPublicForShop(UUID shopId, @Nullable UUID buyerId): List<PublicShopVoucherResponse>`
  - `VoucherService.quoteForPreview(UUID shopId, UUID buyerId, String rawCode, List<VoucherLineItem> lines): VoucherQuoteResult`
  - `VoucherService.redeemForOrder(UUID shopId, UUID buyerId, String rawCode, List<VoucherLineItem> lines): AppliedVoucher`
  - `VoucherService.releaseVoucherUsage(@Nullable UUID voucherId): void`

- [ ] **Step 1: Viết failing tests `VoucherServiceTest` và `SellerVoucherControllerTest`**

Trong `VoucherServiceTest` (chạy trên Testcontainers Postgres thật):
1. `createAndListSellerVouchersEnforcesUniqueCodeAndProductOwnership`:
   - Tạo voucher `ALL_PRODUCTS` và voucher `SPECIFIC_PRODUCTS` gắn với sản phẩm của shop.
   - Tạo trùng `code` trong cùng shop ném `409 CONFLICT`; truyền `productId` của shop khác vào `SPECIFIC_PRODUCTS` ném `400 BAD_REQUEST`.
2. `quoteForPreviewAndRedeemForOrderCalculateSpecificProductSubtotal`:
   - Voucher giảm `20%` tối đa `50_000₫` cho đơn từ `100_000₫`, chỉ áp dụng cho `productA`.
   - Giỏ hàng của shop có `productA` (`120_000₫`) và `productB` (`200_000₫`) → `eligibleSubtotal = 120_000₫` → `discountAmount = 24_000₫` (không tính 20% trên `320_000₫`).
   - Nếu giỏ chỉ có `productB` (`200_000₫`) → `quoteForPreview` trả `discountAmount = 0L` và `error != null`; `redeemForOrder` ném `409 CONFLICT`.
3. `publicListHidesPrivatePausedExpiredAndDepletedVouchers`:
   - `listPublicForShop` chỉ trả voucher `PUBLIC` đang `ONGOING` hoặc `UPCOMING` kèm `applicableProductIds` và `exhaustedByBuyer`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*VoucherServiceTest" --tests "*SellerVoucherControllerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement DTOs, `VoucherService`, `SellerVoucherController`, `PublicShopVoucherController`**

- `SellerVoucherController` (`/api/seller/shops/{shopId}/vouchers`, `@Tag(name = "Seller Vouchers")`, `@SecurityRequirement(name = "bearerAuth")`):
  - `GET ""` (`operationId = "listSellerVouchers"`)
  - `GET "/{voucherId}"` (`operationId = "getSellerVoucher"`)
  - `POST ""` (`operationId = "createSellerVoucher"`, `@ResponseStatus(HttpStatus.CREATED)`)
  - `PUT "/{voucherId}"` (`operationId = "updateSellerVoucher"`)
  - `PATCH "/{voucherId}/active"` (`operationId = "toggleSellerVoucherActive"`)
  - `DELETE "/{voucherId}"` (`operationId = "deleteSellerVoucher"`, `@ResponseStatus(HttpStatus.NO_CONTENT)`)
- `PublicShopVoucherController` (`/api/shops/{shopId}/vouchers`, `@Tag(name = "Shop Vouchers")`):
  - `GET ""` (`operationId = "listPublicShopVouchers"`): nhận `@AuthenticationPrincipal @Nullable Jwt jwt` để nếu khách đã đăng nhập thì trích `buyerId` và tính `exhaustedByBuyer`.
- Ghi `ShopAuditService.record(shopId, userId, action, "VOUCHER", voucherId, detail)` cho mọi thao tác tạo, sửa, bật/tắt, xóa.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*VoucherServiceTest" --tests "*SellerVoucherControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/dto/voucher/ src/main/java/com/minhnbnt/fashionstore/features/voucher/ src/test/java/com/minhnbnt/fashionstore/features/voucher/
git commit -m "feat(voucher): implement VoucherService, SellerVoucherController and PublicShopVoucherController"
```

---

### Task 3: Tích hợp Voucher vào `Order`, `OrderService`, `CartService`, `SellerSettlement` & `ReturnRequestService`

**Files:**
- Modify: `B/model/Order.java`
- Modify: `B/dto/cart/CheckoutPreviewRequest.java`, `B/dto/cart/ShopCheckoutPreview.java`, `B/dto/cart/CheckoutPreviewResponse.java`, `B/dto/cart/CartCheckoutRequest.java`
- Modify: `B/dto/order/CheckoutRequest.java`, `B/dto/order/OrderResponse.java`
- Modify: `B/features/order/OrderService.java`, `B/features/cart/CartService.java`, `B/features/order/ReturnRequestService.java`
- Test: `T/features/order/OrderVoucherIntegrationTest.java`

**Interfaces:**
- Consumes: `VoucherService.quoteForPreview`, `VoucherService.redeemForOrder`, `VoucherService.releaseVoucherUsage` (Task 2)
- Produces:
  - `Order.getVoucherId(): @Nullable UUID`, `Order.getVoucherCode(): @Nullable String`, `Order.getDiscountAmount(): long`, `Order.netMerchandiseTotal(): long`
  - `CheckoutPreviewRequest(@Nullable List<UUID> variantIds, @Nullable Map<UUID, String> shopVouchers)` (giữ constructor 1 tham số `CheckoutPreviewRequest(@Nullable List<UUID> variantIds)`)
  - `ShopCheckoutPreview` thêm `@Nullable String voucherCode, long discountAmount, @Nullable String voucherError` ở cuối (giữ constructor 8 tham số cũ)
  - `CheckoutPreviewResponse` thêm `long discountAmount` ở cuối (giữ constructor 4 tham số cũ)
  - `CartCheckoutRequest` thêm `@Nullable Map<UUID, String> shopVouchers` ở cuối (giữ các constructor 4 và 5 tham số cũ)
  - `CheckoutRequest` thêm `@Nullable String voucherCode` ở cuối (giữ các constructor 5 và 6 tham số cũ)
  - `OrderResponse` thêm `@Nullable UUID voucherId, @Nullable String voucherCode, long discountAmount` ở cuối (giữ các constructor cũ làm alias)

- [ ] **Step 1: Viết failing integration test `OrderVoucherIntegrationTest`**

Kiểm thử trọn vòng đời:
1. `cartPreviewAndMultiShopCheckoutAppliesPerShopVouchersAndUpdatesVietQrTotal`:
   - Giỏ hàng có Shop 1 (`subtotal = 300_000₫`, `shipping = 30_000₫`, mã `SHOP1V50K` giảm `50_000₫`) và Shop 2 (`subtotal = 200_000₫`, `shipping = 30_000₫`, không dùng mã).
   - `previewCheckout` trả `Shop 1: discountAmount = 50_000, total = 280_000`; `Shop 2: discountAmount = 0, total = 230_000`; tổng `CheckoutPreviewResponse: subtotal = 500_000, shippingFee = 60_000, discountAmount = 50_000, total = 510_000`.
   - `cartService.checkout` bằng `VIETQR` tạo 2 đơn và 1 phiên `payment_transaction` có `amount = 510_000L`.
2. `cancellingOrExpiringOrderReleasesVoucherQuotaForReuse`:
   - Voucher có `usageLimit = 1, perBuyerLimit = 1`.
   - Buyer đặt đơn dùng voucher → `usedCount == 1`, đặt đơn thứ 2 dùng cùng mã bị chặn `409 CONFLICT`.
   - Khi đơn 1 bị hủy (hoặc hết hạn thanh toán VietQR qua `systemExpireAwaitingPaymentOrder`), `usedCount` về `0` và Buyer đặt đơn mới với mã đó thành công.
3. `settlementGrossAmountAndReturnRefundUseNetMerchandiseTotalAfterVoucher`:
   - Đơn có `subtotal = 200_000₫`, `discountAmount = 40_000₫`, `shippingFee = 30_000₫`, `total = 190_000₫`.
   - Khi giao hàng / thanh toán thành công → `SellerSettlement` có `grossAmount = 160_000₫`, `platformFee = 8_000₫`, `netAmount = 152_000₫`.
   - Khi khách tạo yêu cầu trả hàng và Shop duyệt hoàn tiền vượt quá `160_000₫` → chặn `400 BAD_REQUEST`; khi hoàn tất `REFUND` mặc định hoàn đúng `160_000₫`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*OrderVoucherIntegrationTest"`
Expected: FAIL.

- [ ] **Step 3: Implement tích hợp trong `Order`, `OrderService`, `CartService`, `ReturnRequestService`**

- Trong `Order.java`: thêm `voucherId`, `voucherCode`, `discountAmount`; tính `total = subtotal + shippingFee - discountAmount`; thêm `netMerchandiseTotal()` trả `Math.max(0L, subtotal - discountAmount)`.
- Trong `OrderService.prepareCheckout`:
  - Nếu `request.voucherCode()` khác null/blank: xây `List<VoucherLineItem>` từ `snapshots`, gọi `vouchers.redeemForOrder(request.shopId(), buyerId, request.voucherCode(), voucherLines)`.
  - Tính `total = subtotal + shippingFee - discountAmount` trước khi gọi `paymentGateways.requireAvailable(method, total)`.
- Trong `OrderService.restockOrderItems`:
  - Nếu `order.getVoucherId() != null`, gọi `vouchers.releaseVoucherUsage(order.getVoucherId())`.
- Trong `OrderService.applyVietQrPaymentSuccess` và `OrderService.deliver`:
  - Gọi `revenue.createSettlementForOrder(order.getShopId(), order.getId(), order.netMerchandiseTotal())`.
- Trong `ReturnRequestService.approve` và `complete`:
  - Kiểm tra `request.refundAmount() <= order.netMerchandiseTotal()`; mặc định hoàn tiền `REFUND` lấy `order.netMerchandiseTotal()`.

- [ ] **Step 4: Chạy toàn bộ test suite Backend để xác nhận PASS**

Run: `.\gradlew.bat test`
Expected: PASS (toàn bộ test cũ và mới đều xanh).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/model/Order.java src/main/java/com/minhnbnt/fashionstore/dto/cart/ src/main/java/com/minhnbnt/fashionstore/dto/order/ src/main/java/com/minhnbnt/fashionstore/features/order/ src/main/java/com/minhnbnt/fashionstore/features/cart/CartService.java src/test/java/com/minhnbnt/fashionstore/features/order/OrderVoucherIntegrationTest.java
git commit -m "feat(order): integrate shop vouchers into cart preview, checkout, settlement and refunds"
```

---

### Task 4: OpenAPI Export, Sinh Orval Client & Cập nhật Phân quyền / Điều hướng Kênh Người Bán

**Files:**
- Modify: `fashionstore-ui/openapi.json`, `fashionstore-ui/src/api/generated/**`
- Modify: `fashionstore-ui/src/types/seller/navigation.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerNavigation.ts`, `fashionstore-ui/src/lib/seller/sellerNavigation.test.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerPermissions.ts`, `fashionstore-ui/src/lib/seller/sellerPermissions.test.ts`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerLayout.tsx`

**Interfaces:**
- Consumes: Backend OpenAPI spec từ `OpenApiExportTest`
- Produces:
  - Orval hooks trong `@generated/endpoints/seller-vouchers/seller-vouchers` và `@generated/endpoints/shop-vouchers/shop-vouchers`
  - `SellerTabId` thêm `'VOUCHERS'`
  - `canSeeTab('VOUCHERS', permissions, isOwner)` trả `true` khi `isOwner || permissions.includes('VOUCHER_READ') || permissions.includes('VOUCHER_WRITE')`
  - Menu mục `"Mã giảm giá"` (`id: 'VOUCHERS'`, icon `Ticket`) trong `SellerLayout.tsx`

- [ ] **Step 1: Xuất `openapi.json`, chạy `pnpm api:generate` và cập nhật unit test `sellerNavigation.test.ts` + `sellerPermissions.test.ts`**

1. Tại `d:\fashionstore\fashionstore`: chạy `.\gradlew.bat test --tests "*OpenApiExportTest"`.
2. Tại `d:\fashionstore\fashionstore-ui`: chạy `pnpm api:generate`.
3. Thêm test trong `src/lib/seller/sellerPermissions.test.ts` và `src/lib/seller/sellerNavigation.test.ts` kiểm tra `parseSellerTab('vouchers') === 'VOUCHERS'`, `canSeeTab('VOUCHERS', ['VOUCHER_READ'], false) === true`, `permissionLabel('VOUCHER_WRITE') === 'Quản lý mã giảm giá'`.

- [ ] **Step 2: Chạy test Frontend để xác nhận FAIL**

Run: `pnpm test src/lib/seller/sellerPermissions.test.ts src/lib/seller/sellerNavigation.test.ts` (tại `d:\fashionstore\fashionstore-ui`)
Expected: FAIL.

- [ ] **Step 3: Cập nhật `navigation.ts`, `sellerNavigation.ts`, `sellerPermissions.ts` và `SellerLayout.tsx`**

- Trong `src/types/seller/navigation.ts`: thêm `| 'VOUCHERS'` vào `SellerTabId`.
- Trong `src/lib/seller/sellerNavigation.ts`: thêm `'VOUCHERS'` vào `SELLER_TAB_IDS` và `DEFAULT_TAB_PREFERENCE`.
- Trong `src/lib/seller/sellerPermissions.ts`:
  - Thêm `VOUCHER_READ: 'Xem mã giảm giá'`, `VOUCHER_WRITE: 'Quản lý mã giảm giá'`.
  - Thêm `VOUCHER_CREATE: 'Tạo mã giảm giá'`, `VOUCHER_UPDATE: 'Cập nhật mã giảm giá'`, `VOUCHER_STATUS: 'Đổi trạng thái mã giảm giá'`, `VOUCHER_DELETE: 'Xóa mã giảm giá'`.
  - Thêm `VOUCHER: 'Mã giảm giá'` vào `TARGET_TYPE_LABELS`.
  - Cập nhật `canSeeTab` cho `tab === 'VOUCHERS'`.
- Trong `src/components/seller-dashboard-tab/SellerLayout.tsx`: thêm mục `{ id: 'VOUCHERS', label: 'Mã giảm giá', icon: Ticket }` vào `menuGroups`, kèm `getPageMeta` và `buildBreadcrumbs` cho `'VOUCHERS'`.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pnpm test src/lib/seller/sellerPermissions.test.ts src/lib/seller/sellerNavigation.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add openapi.json src/api/generated/ src/types/seller/navigation.ts src/lib/seller/ src/components/seller-dashboard-tab/SellerLayout.tsx
git commit -m "feat(ui): generate voucher API hooks and wire VOUCHERS tab into seller navigation"
```

---

### Task 5: Giao diện Quản lý Mã giảm giá tại Kênh Người Bán (`SellerVouchersTab.tsx` & `VoucherFormModal.tsx`)

**Files:**
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/vouchers/VoucherFormModal.tsx`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/vouchers/SellerVouchersTab.tsx`
- Create: `fashionstore-ui/src/components/seller-dashboard-tab/vouchers/SellerVouchersTab.test.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/index.ts`
- Modify: `fashionstore-ui/src/pages/shop/ShopDashboard.tsx`

**Interfaces:**
- Consumes: `useListSellerVouchers`, `useGetSellerVoucher`, `useCreateSellerVoucher`, `useUpdateSellerVoucher`, `useToggleSellerVoucherActive`, `useDeleteSellerVoucher` (`@generated/endpoints/seller-vouchers/seller-vouchers`), `useSellerPermissions(shopId)`, `useAllShopProducts(shopId)`
- Produces:
  - `SellerVouchersTab(props: { shopId: string })`
  - `VoucherFormModal(props: { open: boolean; shopId: string; editingVoucherId: string | null; onClose: () => void; onSaved: () => void })`

- [ ] **Step 1: Viết failing component test `SellerVouchersTab.test.tsx`**

Kiểm tra:
1. Render danh sách voucher với các thẻ trạng thái (`Đang diễn ra`, `Sắp diễn ra`, `Tạm dừng`, `Hết lượt`, `Đã kết thúc`), mức giảm (`15% · Tối đa 50.000 ₫` hoặc `30.000 ₫`), tiến độ lượt dùng (`5 / 100`).
2. Bấm nút **"Tạo mã giảm giá"** mở `VoucherFormModal`, điền Mã code, Tên chương trình, chọn loại giảm và bấm Lưu gọi mutation `createSellerVoucher`.
3. Khi voucher đã có `usedCount > 0`, nút Xóa bị ẩn/disable và khi mở form Sửa thì các trường cốt lõi (`code`, `discountType`, `discountValue`, `minOrderSubtotal`, `productScope`) bị khóa `disabled`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pnpm test src/components/seller-dashboard-tab/vouchers/SellerVouchersTab.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement `VoucherFormModal.tsx`, `SellerVouchersTab.tsx` và gắn vào `ShopDashboard.tsx`**

- Trong `VoucherFormModal.tsx`:
  - Nút **"Tạo mã ngẫu nhiên"** sinh chuỗi 8 ký tự `A-Z0-9`.
  - Chọn loại giảm giá: `FIXED_AMOUNT` (Số tiền cố định) hoặc `PERCENTAGE` (Phần trăm + ô Trần giảm tối đa).
  - Chọn phạm vi: `ALL_PRODUCTS` (Toàn gian hàng) hoặc `SPECIFIC_PRODUCTS` (hiển thị danh sách sản phẩm của shop với ô tìm kiếm & checkbox chọn sản phẩm).
  - Chọn chế độ hiển thị: `PUBLIC` (Hiển thị công khai) hoặc `PRIVATE` (Mã ẩn — chỉ nhập mã mới dùng được).
- Trong `ShopDashboard.tsx`: render `{isTabAllowed && activeTab === 'VOUCHERS' && <SellerVouchersTab key={shopId} shopId={shopId} />}`.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `pnpm test src/components/seller-dashboard-tab/vouchers/SellerVouchersTab.test.tsx src/pages/shop/ShopDashboardNavigation.test.tsx`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/components/seller-dashboard-tab/vouchers/ src/components/seller-dashboard-tab/index.ts src/pages/shop/ShopDashboard.tsx
git commit -m "feat(ui): add SellerVouchersTab and VoucherFormModal in Seller Center"
```

---

### Task 6: Giao diện Voucher cho Người mua trên Storefront, PDP, Checkout & Chi tiết Đơn hàng

**Files:**
- Create: `fashionstore-ui/src/components/shop-storefront/ShopVoucherStrip.tsx`
- Create: `fashionstore-ui/src/components/checkout/ShopVoucherModal.tsx`
- Modify: `fashionstore-ui/src/pages/shop/ShopStorefront.tsx`, `fashionstore-ui/src/pages/ProductDetail.tsx`
- Modify: `fashionstore-ui/src/hooks/useCheckoutPreview.ts`
- Modify: `fashionstore-ui/src/pages/checkout/Checkout.tsx`, `fashionstore-ui/src/pages/checkout/Checkout.test.tsx`
- Modify: `fashionstore-ui/src/pages/checkout/OrderDetail.tsx`, `fashionstore-ui/src/components/seller-dashboard-tab/orders/SellerOrdersTab.tsx`, `fashionstore-ui/src/components/admin/AdminOrderDetailPanel.tsx`

**Interfaces:**
- Consumes: `useListPublicShopVouchers` (`@generated/endpoints/shop-vouchers/shop-vouchers`), `useCheckoutPreview(variantIds: string[], enabled: boolean, shopVouchers?: Record<string, string>)`
- Produces:
  - `ShopVoucherStrip(props: { shopId: string; productId?: string })`
  - `ShopVoucherModal(props: { open: boolean; shopId: string; shopName: string; shopItems: CartItemResponse[]; currentCode: string | null; onApply: (code: string | null) => void; onClose: () => void })`

- [ ] **Step 1: Cập nhật `Checkout.test.tsx` với kịch bản chọn/nhập Voucher của Shop và hiển thị tiền giảm**

Thêm test vào `src/pages/checkout/Checkout.test.tsx`:
1. Hiển thị nút chọn Voucher dưới mỗi khối Shop; khi chọn hoặc nhập mã giảm giá cho Shop, `useCheckoutPreview` được gọi với `shopVouchers: { [shopId]: 'SUMMER50K' }`.
2. Khi preview trả về `discountAmount = 50_000`, khối Shop và cột **Chi tiết thanh toán** hiển thị dòng `Voucher giảm giá từ Shop` với `-50.000 ₫`, và khi bấm Đặt hàng thì payload `CartCheckoutRequest` gửi kèm `shopVouchers: { [shopId]: 'SUMMER50K' }`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `pnpm test src/pages/checkout/Checkout.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement `ShopVoucherStrip.tsx`, `ShopVoucherModal.tsx` và cập nhật `useCheckoutPreview.ts`, `Checkout.tsx`, `ShopStorefront.tsx`, `ProductDetail.tsx`, `OrderDetail.tsx`**

- Trong `useCheckoutPreview.ts`: thêm tham số tùy chọn `shopVouchers?: Record<string, string>` vào `queryKey` và truyền vào `previewCheckout({ variantIds, shopVouchers })`.
- Trong `ShopVoucherStrip.tsx`: hiển thị các thẻ voucher công khai của Shop (lọc theo `productId` nếu truyền vào trên trang `ProductDetail.tsx`) kèm nút **"Sao chép mã"** (`navigator.clipboard.writeText`).
- Trong `ShopVoucherModal.tsx`:
  - Ô nhập mã thủ công ở trên cùng (để nhập cả mã `PRIVATE` hoặc dán mã vừa copy) + nút **"Áp dụng"**.
  - Danh sách voucher `PUBLIC` từ `useListPublicShopVouchers(shopId)`: tự tính `eligibleSubtotal` từ `shopItems` để hiển thị rõ voucher nào đủ điều kiện và voucher nào cần mua thêm bao nhiêu tiền, hoặc đã hết lượt cá nhân (`exhaustedByBuyer`).
- Trong `Checkout.tsx`:
  - Quản lý state `selectedVouchers: Record<string, string>`.
  - Dưới mỗi khối Shop hiển thị dòng chọn Voucher + số tiền giảm hoặc cảnh báo `shopQuote.voucherError`.
  - Trong cột tổng kết thanh toán, hiển thị dòng **"Voucher giảm giá từ Shop: -XX.XXX ₫"** khi `(preview?.discountAmount ?? 0) > 0`.
  - Khi bấm đặt hàng, đính kèm `shopVouchers` (chỉ gồm các mã không có `voucherError`) vào `CartCheckoutRequest`.
- Trong `OrderDetail.tsx`, `SellerOrdersTab.tsx`, và `AdminOrderDetailPanel.tsx`: hiển thị dòng giảm giá `Voucher Shop ({order.voucherCode}): -{formatVnd(order.discountAmount)}` khi `(order.discountAmount ?? 0) > 0`.

- [ ] **Step 4: Chạy kiểm tra chất lượng toàn bộ dự án (FE & BE)**

1. Tại `d:\fashionstore\fashionstore-ui`:
   Run: `pnpm format && pnpm lint && pnpm build && pnpm test`
   Expected: PASS (0 errors, 0 warnings, toàn bộ test xanh).
2. Tại `d:\fashionstore\fashionstore`:
   Run: `.\gradlew.bat test`
   Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/components/shop-storefront/ShopVoucherStrip.tsx src/components/checkout/ShopVoucherModal.tsx src/hooks/useCheckoutPreview.ts src/pages/shop/ShopStorefront.tsx src/pages/ProductDetail.tsx src/pages/checkout/Checkout.tsx src/pages/checkout/Checkout.test.tsx src/pages/checkout/OrderDetail.tsx src/components/seller-dashboard-tab/orders/SellerOrdersTab.tsx src/components/admin/AdminOrderDetailPanel.tsx
git commit -m "feat(ui): add storefront voucher strip, checkout per-shop voucher selector and order discount breakdown"
```
