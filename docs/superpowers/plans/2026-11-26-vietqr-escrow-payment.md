# VietQR Online Payment, Escrow & Refund Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bổ sung thanh toán trực tuyến qua mã QR ngân hàng chuẩn NAPAS (VietQR) hỗ trợ thanh toán gộp nhiều Shop, kết hợp cơ chế Sàn giữ tiền trung gian (Escrow — cắt 5% phí sàn và giải ngân 95% cho Shop khi khách bấm "Đã nhận hàng") và quy trình Chờ hoàn tiền (`REFUND_PENDING` → Admin xác nhận `REFUNDED`) khi hủy đơn đã thanh toán hoặc hoàn tất đổi trả hàng.

**Architecture:** Backend Spring Boot 4 + Spring Data JDBC thêm package `features/payment/` (`PaymentGatewayService`, `VietQrUrlBuilder`, `PaymentTransactionService`, `PaymentFlowService`, `PaymentController`, `PublicPaymentController`, `PaymentExpiryJob`) và `features/admin/controller/AdminPaymentController`, đồng thời mở rộng `OrderService`, `CartService`, `RevenueService`, `ReturnRequestService`. Frontend React 19 + TanStack Query + Orval cập nhật `Checkout.tsx`, `Orders.tsx`, `OrderDetail.tsx` và thêm trang quét mã QR `/payment/qr/:txnRef` (`VietQrPayment.tsx`) cùng trang quản trị `/admin/payments` (`AdminPayments.tsx`).

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Data JDBC, JdbcClient, Flyway, PostgreSQL (Testcontainers), JUnit 5, AssertJ, MockMvc; React 19, TypeScript, TanStack Query, Orval, Tailwind CSS v4, Vitest, pnpm.

**Spec:** `docs/superpowers/specs/2026-11-26-vietqr-escrow-payment-design.md`

**Workspace Paths:**
- Backend repo: `d:\fashionstore\fashionstore` (`B = src/main/java/com/minhnbnt/fashionstore`, `T = src/test/java/com/minhnbnt/fashionstore`)
- Frontend repo: `d:\fashionstore\fashionstore-ui`

## Global Constraints

- Tuân thủ nghiêm ngặt `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md` (BE) và `AGENTS.md`, `docs/design-system.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/pre-commit.md` (FE):
  - **Cấm toán tử 3 ngôi (`? :`)** trong cả Java và TypeScript/JSX (`if` + early `return`, JSX dùng `&&`).
  - **Java / Spring Data JDBC:** Indent 4 spaces, dòng tối đa 120 ký tự; entity implement `Persistable<UUID>`, sinh UUID tại factory `create(...)`, chỉ dùng Lombok trong allowlist (`@Getter`, `@RequiredArgsConstructor`, `@AllArgsConstructor(onConstructor_ = @PersistenceCreator)`), cấm `@Data/@Setter/@Builder/@EqualsAndHashCode`; hạn chế tối đa `@Nullable` (chỉ gắn ở field/param thật sự optional theo nghiệp vụ); tiền VND dùng `long`; thời gian dùng `Instant`.
  - **Cấm Derived Query sắp xếp nhiều cột (`OrderByAAscBAsc`)** trên Spring Data JDBC repository (làm vỡ AOT compile) — luôn viết `@Query` SQL tường minh khi sắp xếp nhiều cột.
  - **Cấm gọi Repository xuyên domain & Cấm phụ thuộc vòng (Circular Dependency):**
    - Service domain này chỉ gọi Service domain khác, tuyệt đối không inject Repository của domain khác.
    - Chiều phụ thuộc một chiều (DAG): `PaymentFlowService` → `OrderService` → `ReturnRequestService` → `PaymentTransactionService` → `PaymentGatewayService`. `OrderService` và `ReturnRequestService` chỉ gọi `PaymentTransactionService.flagOrderRefund(...)`, **tuyệt đối không inject `PaymentFlowService`**.
  - **Springdoc & API:** Mỗi endpoint khai báo đủ `@Tag`, `@Operation(operationId, summary, description)`, `@ApiResponse`, `@Parameter`, `@Schema`, `@SecurityRequirement(name = "bearerAuth")`. Exception message bằng **tiếng Anh**. Không sửa `SecurityConfig`.
  - **Isolate DB trong Test:** Test class nào sửa cấu hình toàn cục (`payment_gateway`, `system_setting`) phải gắn `@TestPropertySource(properties = "test.isolation=<TênClass>")`. Testcontainers giữ nguyên image Postgres của dự án (`ghcr.io/minhnbnt/postgresql:latest`).
  - **Frontend Design System & Structure:** Chỉ dùng `pnpm`; trang mới đặt trong `src/pages/checkout/` hoặc `src/pages/admin/`, export `lazy(...)` trong `src/pages/index.ts`; dùng semantic status tokens (`emerald`, `amber`, `rose`, `sky` — cấm trộn `red-*` hoặc `orange-*`); dùng chung `src/components/Modal.tsx` cho hộp thoại; ảnh QR phải có `onError` fallback; chạy `pnpm format && pnpm lint && pnpm build && pnpm test` xanh (0 error, 0 warning).

## Review Focus

1. **Khách hàng chuyển thiếu tiền (`transferAmount < transaction.amount`):** Hệ thống tuyệt đối không được chuyển đơn sang `PAID` / `PENDING`; giao dịch phải chuyển sang `UNDERPAID` kèm `refundStatus = PENDING` và `refundAmount = transferAmount`, đơn giữ nguyên `AWAITING_PAYMENT` (kiểm thử tại Task 4).
2. **Webhook hoặc nút Giả lập gọi lặp nhiều lần cùng một `txnRef` (Idempotency):** Lần gọi thứ 2 trở đi phải trả `ALREADY_PROCESSED`, không tạo bản ghi `OrderStatusHistory` thứ 2 và không cộng trùng `SellerSettlement` (kiểm thử tại Task 4).
3. **Khách chuyển khoản thành công đúng lúc đơn vừa hết hạn 15 phút hoặc vừa bị hủy (`LATE_PAID`):** Đơn giữ nguyên `CANCELLED` (không trừ kho lại vì đã hoàn kho lúc hủy), `order.paymentStatus` chuyển sang `REFUND_PENDING`, giao dịch gắn `refundStatus = PENDING` với toàn bộ số tiền để Admin hoàn lại (kiểm thử tại Task 4).
4. **Đơn `AWAITING_PAYMENT` chưa thanh toán (hoặc hết hạn bị hủy):** Tuyệt đối không được xuất hiện trong `OrderService.listSellerOrders*`, `countByShop`, hay thống kê doanh thu của Shop (`placed_at IS NOT NULL`) (kiểm thử tại Task 3).
5. **Shop tạo nhiều lệnh rút tiền liên tiếp hoặc đơn đã hoàn tất trả hàng (`ReturnRequest` → `COMPLETED`):** Tạo lệnh rút tiền phải giảm ngay `walletBalance` khả dụng để không rút vượt số dư; hoàn tất trả hàng phải cộng lại tồn kho (`ORDER_RETURN`), hủy/khấu trừ `SellerSettlement` đang `HOLDING` và chuyển đơn sang `REFUND_PENDING` (kiểm thử tại Task 5 và Task 6).

---

### Task 1: Migration `V49` & Cấu hình cổng thanh toán (`COD`, `VIETQR`)

**Files:**
- Create: `src/main/resources/db/migration/V49__vietqr_escrow_payment.sql`
- Create: `B/model/PaymentMethod.java`, `B/model/PaymentGateway.java`
- Create: `B/dto/payment/PaymentGatewayResponse.java`, `B/dto/payment/UpdatePaymentGatewayRequest.java`, `B/dto/payment/PaymentMethodOption.java`, `B/dto/payment/PaymentMethodsResponse.java`
- Create: `B/features/payment/repository/PaymentGatewayRepository.java`
- Create: `B/features/payment/PaymentGatewayService.java`
- Create: `B/features/payment/controller/PublicPaymentController.java`
- Create: `B/features/admin/controller/AdminPaymentController.java`
- Test: `T/features/payment/PaymentGatewayServiceTest.java`, `T/features/admin/controller/AdminPaymentControllerTest.java`

**Interfaces:**
- Consumes: `BankService.listBanks()`, `SystemSettingsService.current()`, `ConfigAuditService.record(ConfigAuditEntity, String, String, @Nullable Object, @Nullable Object, UUID)`
- Produces:
  - `enum PaymentMethod { COD, VIETQR }`
  - `PaymentGatewayService.list(): List<PaymentGatewayResponse>`
  - `PaymentGatewayService.update(UUID adminId, PaymentMethod code, UpdatePaymentGatewayRequest request): PaymentGatewayResponse`
  - `PaymentGatewayService.publicMethods(): PaymentMethodsResponse`
  - `PaymentGatewayService.requireAvailable(PaymentMethod method, long totalAmount): void` (throws `409 CONFLICT` `"Payment method is disabled"` hoặc `"Order total is outside COD limits"`)
  - `PaymentGatewayService.requireActiveVietQrGateway(): PaymentGateway` (throws `409 CONFLICT` nếu `VIETQR` đang tắt hoặc thiếu thông tin ngân hàng)
  - `record PaymentGatewayResponse(String code, String name, boolean enabled, @Nullable String bankBin, @Nullable String bankCode, @Nullable String bankName, @Nullable String accountNumber, @Nullable String accountName, boolean webhookSecretSet, boolean sandboxMode, Instant updatedAt)`
  - `record UpdatePaymentGatewayRequest(@Nullable Boolean enabled, @Nullable String name, @Nullable String bankBin, @Nullable String accountNumber, @Nullable String accountName, @Nullable String webhookSecret, @Nullable Boolean sandboxMode)`
  - `record PaymentMethodOption(String code, String name, @Nullable String bankCode, @Nullable String bankName, boolean sandboxMode)`
  - `record PaymentMethodsResponse(List<PaymentMethodOption> methods, long codMinTotal, long codMaxTotal, int paymentHoldMinutes)`

- [ ] **Step 1: Tạo migration `V49__vietqr_escrow_payment.sql` và các test đỏ `PaymentGatewayServiceTest` + `AdminPaymentControllerTest`**

Trong `V49__vietqr_escrow_payment.sql`, tạo bảng `payment_gateway` (seed `COD` và `VIETQR` bật sẵn với `bank_bin='970422'`, `bank_code='MB'`, `bank_name='Ngân hàng TMCP Quân đội (MB Bank)'`, `account_number='0900123456789'`, `account_name='SAN TMDT FASHIONSTORE'`, `webhook_secret='demo-vietqr-secret-key'`, `sandbox_mode=TRUE`), mở rộng CHECK constraints và thêm cột `payment_expires_at`, `placed_at` cho bảng `orders` (backfill `placed_at = created_at`), tạo bảng `payment_transaction` + `payment_transaction_order`, và mở rộng CHECK constraint `seller_settlement.status` thêm `'CANCELLED'` đúng theo mục §4.1 của Spec.

Viết test trong `PaymentGatewayServiceTest` (gắn `@TestPropertySource(properties = "test.isolation=PaymentGatewayServiceTest")`):
```java
@Test
void seededGatewaysIncludeCodAndVietQrWithoutExposingSecret() {
    var list = gateways.list();
    assertThat(list).extracting(PaymentGatewayResponse::code).containsExactly("COD", "VIETQR");
    var vietQr = list.get(1);
    assertThat(vietQr.enabled()).isTrue();
    assertThat(vietQr.webhookSecretSet()).isTrue();
    assertThat(vietQr.bankCode()).isEqualTo("MB");
}

@Test
void updateVietQrValidatesBankBinAndKeepsOldSecretWhenBlank() {
    var updated = gateways.update(adminId, PaymentMethod.VIETQR,
        new UpdatePaymentGatewayRequest(true, "VietQR NAPAS", "970436", "0011223344", "CONG TY DHMQ", "", false));
    assertThat(updated.bankCode()).isEqualTo("VCB");
    assertThat(updated.webhookSecretSet()).isTrue();
    assertThat(gateways.requireActiveVietQrGateway().getWebhookSecret()).isEqualTo("demo-vietqr-secret-key");
}

@Test
void cannotDisableLastEnabledGateway() {
    gateways.update(adminId, PaymentMethod.VIETQR,
        new UpdatePaymentGatewayRequest(false, null, null, null, null, null, null));
    assertThatThrownBy(() -> gateways.update(adminId, PaymentMethod.COD,
        new UpdatePaymentGatewayRequest(false, null, null, null, null, null, null)))
        .isInstanceOf(ResponseStatusException.class)
        .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
}

@Test
void requireAvailableEnforcesCodLimitsAndDisabledGateway() {
    settings.update(adminId, new UpdateSettingsRequest(null, null, null, null, null, null, null, null, null, 100_000L, 500_000L));
    assertThatThrownBy(() -> gateways.requireAvailable(PaymentMethod.COD, 50_000L))
        .isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(() -> gateways.requireAvailable(PaymentMethod.COD, 600_000L))
        .isInstanceOf(ResponseStatusException.class);
    gateways.requireAvailable(PaymentMethod.COD, 300_000L);
}
```

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*PaymentGateway*" --tests "*AdminPaymentControllerTest"` (tại `d:\fashionstore\fashionstore`)
Expected: FAIL do các class chưa tồn tại.

- [ ] **Step 3: Implement `PaymentMethod`, `PaymentGateway`, `PaymentGatewayRepository`, `PaymentGatewayService`, `PublicPaymentController`, `AdminPaymentController`**

- `PaymentGatewayRepository` dùng `@Lock(LockMode.PESSIMISTIC_WRITE)` hoặc `JdbcClient` với `FOR UPDATE` khi cập nhật.
- `AdminPaymentController`: `GET /api/admin/payment-gateways` (`operationId = "listPaymentGateways"`), `PUT /api/admin/payment-gateways/{code}` (`operationId = "updatePaymentGateway"`).
- `PublicPaymentController`: `GET /api/public/payment-methods` (`operationId = "listPaymentMethods"`).
- Khi ghi `ConfigAuditService.record(ConfigAuditEntity.PAYMENT_GATEWAY, ...)`, luôn che `webhookSecret` thành `"****"`.

- [ ] **Step 4: Chạy lại test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*PaymentGateway*" --tests "*AdminPaymentControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/db/migration/V49__vietqr_escrow_payment.sql src/main/java/com/minhnbnt/fashionstore/model/Payment* src/main/java/com/minhnbnt/fashionstore/dto/payment/ src/main/java/com/minhnbnt/fashionstore/features/payment/ src/main/java/com/minhnbnt/fashionstore/features/admin/controller/AdminPaymentController.java src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentGatewayServiceTest.java src/test/java/com/minhnbnt/fashionstore/features/admin/controller/AdminPaymentControllerTest.java
git commit -m "feat(payment): add V49 migration and payment gateway config for COD and VietQR"
```

---

### Task 2: Sinh URL VietQR & Quản lý phiên giao dịch (`VietQrUrlBuilder`, `PaymentTransactionService`)

**Files:**
- Create: `B/model/PaymentTransaction.java`, `B/model/PaymentTransactionStatus.java`, `B/model/RefundStatus.java`
- Create: `B/features/payment/VietQrUrlBuilder.java`
- Create: `B/features/payment/repository/PaymentTransactionRepository.java`
- Create: `B/features/payment/PaymentTransactionService.java`
- Create: `B/dto/payment/VietQrOrderBrief.java`, `B/dto/payment/VietQrSessionResponse.java`
- Test: `T/features/payment/VietQrUrlBuilderTest.java`, `T/features/payment/PaymentTransactionServiceTest.java`

**Interfaces:**
- Consumes: `PaymentGatewayService.requireActiveVietQrGateway()` (Task 1) — **không inject `OrderRepository` hay `OrderService` để giữ chiều phụ thuộc 1 chiều**
- Produces:
  - `enum PaymentTransactionStatus { PENDING, SUCCESS, UNDERPAID, FAILED, EXPIRED }`
  - `enum RefundStatus { NONE, PENDING, REFUNDED }`
  - `VietQrUrlBuilder.generateTxnRef(): String` (`"DHMQ"` + 10 ký tự `A-Z0-9`)
  - `VietQrUrlBuilder.extractTxnRef(@Nullable String content): @Nullable String` (tìm mã `DHMQ[A-Z0-9]{10}` không phân biệt hoa thường, trả về chuỗi in hoa)
  - `VietQrUrlBuilder.buildQrImageUrl(String bankBin, String accountNumber, String accountName, long amount, String txnRef): String`
  - `PaymentTransactionService.createSession(UUID buyerId, List<Order> orders, Instant expiresAt): PaymentTransaction`
  - `PaymentTransactionService.findOrderIdsByTransactionId(UUID transactionId): List<UUID>`
  - `PaymentTransactionService.flagOrderRefund(UUID orderId, long refundAmount, String reason): void` (tìm giao dịch `SUCCESS` gắn với `orderId`, chuyển `refundStatus = PENDING`, cộng `refundAmount`, cập nhật `refundReason`)
  - `record VietQrOrderBrief(UUID orderId, String orderCode, UUID shopId, String shopName, long total, String orderStatus, String paymentStatus)`
  - `record VietQrSessionResponse(UUID transactionId, String txnRef, String status, long amount, long receivedAmount, String bankBin, String bankCode, String bankName, String accountNumber, String accountName, String qrImageUrl, Instant expiresAt, @Nullable Instant paidAt, boolean sandboxMode, String refundStatus, long refundAmount, @Nullable String refundReason, List<VietQrOrderBrief> orders)`

- [ ] **Step 1: Viết failing tests `VietQrUrlBuilderTest` và `PaymentTransactionServiceTest`**

```java
@Test
void generateAndExtractTxnRefFromBankTransferDescription() {
    var ref = VietQrUrlBuilder.generateTxnRef();
    assertThat(ref).matches("^DHMQ[A-Z0-9]{10}$");
    assertThat(VietQrUrlBuilder.extractTxnRef("MBVCB.12345.chuyen tien " + ref.toLowerCase(Locale.ROOT) + "CT tu 0123"))
        .isEqualTo(ref);
    assertThat(VietQrUrlBuilder.extractTxnRef("Chuyen khoan khong co ma")).isNull();
}

@Test
void buildQrImageUrlEncodesAccountNameAndLocksAmountAndAddInfo() {
    var url = VietQrUrlBuilder.buildQrImageUrl("970422", "0900123456789", "SAN TMDT FASHIONSTORE", 350_000L, "DHMQ1234567890");
    assertThat(url).isEqualTo(
        "https://img.vietqr.io/image/970422-0900123456789-compact2.png?amount=350000&addInfo=DHMQ1234567890&accountName=SAN+TMDT+FASHIONSTORE"
    );
}
```

Trong `PaymentTransactionServiceTest`: kiểm tra `createSession` với 2 đơn hàng thuộc 2 shop khác nhau lưu tổng `amount = order1.getTotal() + order2.getTotal()`, liên kết cả 2 đơn trong `payment_transaction_order`, và `getSessionForViewer` trả đủ 2 `VietQrOrderBrief` cho đúng buyer, trả `404` cho buyer khác.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*VietQrUrlBuilderTest" --tests "*PaymentTransactionServiceTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `VietQrUrlBuilder`, `PaymentTransaction`, `PaymentTransactionRepository`, `PaymentTransactionService`**

- Dùng `SecureRandom` cho `generateTxnRef()`.
- `PaymentTransactionRepository` quản lý cả bảng `payment_transaction` và các thao tác chèn/truy vấn `payment_transaction_order` qua `JdbcClient` (bao gồm `findLockedByTxnRef(String txnRef)` với `SELECT * FROM payment_transaction WHERE txn_ref = :txnRef FOR UPDATE`).

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*VietQrUrlBuilderTest" --tests "*PaymentTransactionServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/model/PaymentTransaction* src/main/java/com/minhnbnt/fashionstore/model/RefundStatus.java src/main/java/com/minhnbnt/fashionstore/dto/payment/VietQr* src/main/java/com/minhnbnt/fashionstore/features/payment/VietQrUrlBuilder.java src/main/java/com/minhnbnt/fashionstore/features/payment/repository/PaymentTransactionRepository.java src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentTransactionService.java src/test/java/com/minhnbnt/fashionstore/features/payment/VietQrUrlBuilderTest.java src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentTransactionServiceTest.java
git commit -m "feat(payment): add VietQR URL builder and multi-order payment transaction session"
```

---

### Task 3: Checkout `COD` & `VIETQR` (Đơn nháp `AWAITING_PAYMENT`, Gộp nhiều Shop, Ẩn đơn chưa thanh toán với Shop)

**Files:**
- Modify: `B/model/OrderStatus.java`, `B/model/PaymentStatus.java`, `B/model/Order.java`
- Modify: `B/dto/cart/CartCheckoutRequest.java`, `B/dto/order/CheckoutRequest.java`, `B/dto/order/OrderResponse.java`
- Modify: `B/features/order/OrderService.java`, `B/features/cart/CartService.java`
- Modify: `B/features/order/repository/OrderRepository.java`, `B/features/order/repository/OrderAdminQueryRepository.java`, `B/features/shop/ShopStatsRepository.java`
- Test: `T/features/order/OrderServiceTest.java`, `T/features/cart/CartServiceTest.java`, `T/model/OrderTest.java`

**Interfaces:**
- Consumes: `PaymentGatewayService.requireAvailable(PaymentMethod, long)`, `PaymentTransactionService.createSession(...)`, `SystemSettingsService.current()`
- Produces:
  - `OrderStatus` thêm `AWAITING_PAYMENT`
  - `PaymentStatus` thêm `PAID`, `REFUND_PENDING`, `REFUNDED`
  - `Order.createCod(...)` (giữ chữ ký `Order.create(...)` cũ làm alias gọi sang `createCod` để không vỡ test cũ: `status = PENDING`, `placedAt = now`, `paymentExpiresAt = null`)
  - `Order.createVietQr(String code, UUID shopId, UUID buyerId, long subtotal, long shippingFee, String shippingName, String shippingPhone, String shippingAddress, Instant paymentExpiresAt)` (`status = AWAITING_PAYMENT`, `paymentMethod = "VIETQR"`, `paymentStatus = UNPAID`, `placedAt = null`, `paymentExpiresAt`)
  - `CartCheckoutRequest` và `CheckoutRequest` thêm `@Nullable PaymentMethod paymentMethod` ở cuối record (thêm constructor phụ không có `paymentMethod` mặc định truyền `PaymentMethod.COD` để giữ tương thích toàn bộ test hiện có).
  - `OrderResponse` thêm 3 trường `@Nullable` ở cuối record (hoặc kèm factory phụ): `@Nullable String paymentTxnRef, @Nullable String paymentQrUrl, @Nullable Instant paymentExpiresAt`.

- [ ] **Step 1: Viết failing tests trong `OrderServiceTest` và `CartServiceTest`**

- `cartCheckoutWithVietQrCreatesAwaitingPaymentOrdersAndSingleSharedQrSession`:
  - Giỏ hàng có 2 món từ 2 shop khác nhau → gọi `cartService.checkout(buyerId, new CartCheckoutRequest("An", "0901234567", "Hà Nội", null, PaymentMethod.VIETQR))` → trả về 2 `OrderResponse` đều có `status = "AWAITING_PAYMENT"`, `paymentMethod = "VIETQR"`, `paymentStatus = "UNPAID"`, cùng chung 1 mã `paymentTxnRef` (`DHMQ...`) và `paymentQrUrl` chứa tổng tiền của cả 2 đơn.
  - Tồn kho của cả 2 biến thể đã giảm ngay lập tức.
  - `orderService.listSellerOrders(sellerId)` và `orderService.countByShop(shopId)` **không chứa** các đơn `AWAITING_PAYMENT` này (`placed_at IS NULL`).
- `codCheckoutEnforcesCodMaxTotalLimit`:
  - Đặt `codMaxTotal = 100_000L` → checkout COD đơn `200_000L` ném `409 CONFLICT`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*OrderServiceTest" --tests "*CartServiceTest" --tests "*OrderTest"`
Expected: FAIL.

- [ ] **Step 3: Implement mở rộng `Order`, `OrderService.checkout`, `CartService.checkout` và lọc `placed_at IS NOT NULL` cho Seller**

- Trong `CartService.checkout`:
  - Nếu `request.paymentMethod() == PaymentMethod.VIETQR`: tạo các đơn qua `orders.checkoutDraftVietQr(...)`, sau đó gọi `paymentTransactions.createSession(buyerId, createdOrders, expiresAt)` một lần duy nhất cho cả đợt checkout và gắn `txnRef`, `qrImageUrl`, `expiresAt` vào danh sách `OrderResponse` trả về.
- Trong `OrderRepository`: thêm `findByShopIdAndPlacedAtIsNotNullOrderByCreatedAtDesc(UUID shopId)` và `countByShopIdAndPlacedAtIsNotNull(UUID shopId)` để `OrderService.listSellerOrders*` và `countByShop` chỉ đọc các đơn đã `placed_at IS NOT NULL`.
- Trong `OrderAdminQueryRepository`: `revenueTotals()` lọc `WHERE placed_at IS NOT NULL AND status <> 'CANCELLED'` cho `gmv`, và `WHERE payment_status IN ('COLLECTED', 'PAID')` cho `collected`.

- [ ] **Step 4: Chạy toàn bộ test đơn hàng & giỏ hàng để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*Order*" --tests "*Cart*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/model/Order* src/main/java/com/minhnbnt/fashionstore/model/PaymentStatus.java src/main/java/com/minhnbnt/fashionstore/dto/order/ src/main/java/com/minhnbnt/fashionstore/dto/cart/CartCheckoutRequest.java src/main/java/com/minhnbnt/fashionstore/features/order/ src/main/java/com/minhnbnt/fashionstore/features/cart/CartService.java src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsRepository.java src/test/java/com/minhnbnt/fashionstore/features/order/ src/test/java/com/minhnbnt/fashionstore/features/cart/ src/test/java/com/minhnbnt/fashionstore/model/OrderTest.java
git commit -m "feat(order): support VietQR multi-shop checkout with AWAITING_PAYMENT draft orders"
```

---

### Task 4: Xác nhận Chuyển khoản (Webhook & Sandbox Simulate), Thanh toán lại & Job Hết hạn 15 phút

**Files:**
- Create: `B/features/payment/PaymentFlowService.java`, `B/features/payment/TransferOutcome.java`, `B/features/payment/TransferProcessResult.java`
- Create: `B/features/payment/controller/PaymentController.java`
- Create: `B/features/order/PaymentExpiryJob.java`
- Create: `B/dto/payment/VietQrWebhookRequest.java`, `B/dto/payment/VietQrWebhookResponse.java`, `B/dto/payment/SimulateVietQrRequest.java`
- Modify: `B/features/payment/controller/PublicPaymentController.java`, `B/features/order/controller/OrderController.java`, `B/features/order/OrderService.java`, `B/model/Order.java`
- Test: `T/features/payment/PaymentFlowServiceTest.java`, `T/features/payment/PaymentControllerTest.java`

**Interfaces:**
- Consumes: `VietQrUrlBuilder.extractTxnRef`, `PaymentGatewayService`, `PaymentTransactionService`, `OrderService`, `RevenueService`
- Produces:
  - `enum TransferOutcome { SUCCESS, UNDERPAID, ALREADY_PROCESSED, NOT_FOUND, IGNORED }`
  - `record TransferProcessResult(TransferOutcome outcome, @Nullable String txnRef, long expectedAmount, long receivedAmount, List<UUID> affectedOrderIds)`
  - `PaymentFlowService.processIncomingTransfer(String transferContent, long transferAmount, @Nullable String bankTransactionId, String rawPayload): TransferProcessResult`
  - `PaymentFlowService.simulateTransfer(UUID callerId, boolean isAdmin, String txnRef, @Nullable Long amount): VietQrSessionResponse`
  - `PaymentFlowService.retryForOrder(UUID buyerId, UUID orderId): VietQrSessionResponse`
  - `PaymentFlowService.expireOverdueOrders(): int`
  - Endpoints:
    - `GET /api/payments/vietqr/{txnRef}` (`operationId = "getVietQrSession"`)
    - `POST /api/payments/vietqr/{txnRef}/simulate` (`operationId = "simulateVietQrTransfer"`)
    - `POST /api/orders/{id}/payments/vietqr` (`operationId = "retryVietQrPayment"`)
    - `POST /api/public/payments/vietqr/webhook` (`operationId = "vietQrWebhook"`)

- [ ] **Step 1: Viết failing tests `PaymentFlowServiceTest` và `PaymentControllerTest`**

Kiểm thử đầy đủ các kịch bản trong `PaymentFlowServiceTest`:
1. `exactTransferActivatesAllOrdersInSessionAndCreatesHoldingSettlements`:
   - Phiên gồm 2 đơn `AWAITING_PAYMENT` (tổng `360_000₫`) → `processIncomingTransfer("CK " + txnRef, 360_000L, "FT123", "{}")` → trả `TransferOutcome.SUCCESS`.
   - Cả 2 đơn chuyển sang `status = PENDING`, `paymentStatus = PAID`, `placedAt != null`, hiện trong `listSellerOrders`.
   - Mỗi đơn được tạo ngay 1 bản ghi `SellerSettlement` ở trạng thái `HOLDING` với `platformFee = subtotal * 5 / 100`.
2. `underpaidTransferMarksTransactionUnderpaidForRefundAndKeepsOrdersAwaitingPayment`:
   - Chuyển `150_000₫` cho giao dịch `360_000₫` → `TransferOutcome.UNDERPAID`, giao dịch có `status = UNDERPAID`, `refundStatus = PENDING`, `refundAmount = 150_000L`; cả 2 đơn vẫn là `AWAITING_PAYMENT` / `UNPAID`.
3. `duplicateWebhookCallIsIdempotent`:
   - Gọi `processIncomingTransfer` lần 2 với cùng `txnRef` → `TransferOutcome.ALREADY_PROCESSED`, không ghi thêm `OrderStatusHistory`.
4. `lateTransferAfterOrderExpiredFlagsOrderAndTransactionForRefund`:
   - Đơn hết hạn bị `expireOverdueOrders()` chuyển sang `CANCELLED` (`FAILED`, đã hoàn kho) → sau đó ngân hàng báo nhận tiền `processIncomingTransfer` → đơn giữ `status = CANCELLED`, chuyển `paymentStatus = REFUND_PENDING`, giao dịch có `refundStatus = PENDING`, `refundAmount = total`.
5. `retryAfterUnderpaidCreatesFreshSessionForSingleOrder` & `retryAfterExpiryCancelsAndReturns409`.
6. `buyerCanCancelAwaitingPaymentOrderAndRestocksInventory`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*PaymentFlowServiceTest" --tests "*PaymentControllerTest"`
Expected: FAIL.

- [ ] **Step 3: Implement `PaymentFlowService`, `PaymentController`, webhook trong `PublicPaymentController`, retry trong `OrderController`, và `PaymentExpiryJob`**

- `Order.cancel(...)` cho phép hủy cả khi `status == OrderStatus.PENDING` lẫn `status == OrderStatus.AWAITING_PAYMENT`. Nếu hủy từ `AWAITING_PAYMENT` thì `paymentStatus = PaymentStatus.FAILED`.
- Webhook `/api/public/payments/vietqr/webhook` kiểm tra header `Authorization` (`Apikey <secret>` hoặc `Bearer <secret>`) bằng `MessageDigest.isEqual`; sai → `401 UNAUTHORIZED`.
- `PaymentExpiryJob` chạy `@Scheduled(fixedDelay = 60_000)` gọi `paymentFlow.expireOverdueOrders()`.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*PaymentFlowServiceTest" --tests "*PaymentControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/features/payment/ src/main/java/com/minhnbnt/fashionstore/features/order/ src/main/java/com/minhnbnt/fashionstore/dto/payment/ src/main/java/com/minhnbnt/fashionstore/model/Order.java src/test/java/com/minhnbnt/fashionstore/features/payment/
git commit -m "feat(payment): verify incoming VietQR transfers, sandbox simulation, retry and expiry job"
```

---

### Task 5: Vòng đời Escrow (Thu phí sàn 5%, Giải ngân khi Khách bấm "Đã nhận hàng", Khấu trừ Ví khi Rút tiền & Admin Duyệt Rút tiền)

**Files:**
- Modify: `B/model/SellerSettlement.java`, `B/model/SettlementStatus.java`, `B/model/Order.java`
- Modify: `B/features/order/RevenueService.java`, `B/features/order/OrderService.java`
- Modify: `B/features/order/repository/SellerSettlementRepository.java`, `B/features/order/repository/SellerWithdrawalRepository.java`
- Modify: `B/features/admin/controller/AdminPaymentController.java`
- Create: `B/dto/payment/AdminWithdrawalResponse.java`
- Test: `T/features/order/RevenueServiceUnitTest.java`, `T/features/order/RevenueServiceIntegrationTest.java`

**Interfaces:**
- Consumes: `SellerSettlementRepository`, `SellerWithdrawalRepository`, `ConfigAuditService`
- Produces:
  - `SettlementStatus` thêm `CANCELLED`
  - `SellerSettlement.PLATFORM_FEE_PERCENT = 5`
  - `SellerSettlement.createWithDefaultFee(UUID shopId, UUID orderId, long grossAmount)`: tính `platformFee = (grossAmount * 5) / 100`, `netAmount = grossAmount - platformFee`, `status = HOLDING`
  - `SellerSettlement.adjustAndRelease(long newGrossAmount)`: cập nhật `grossAmount = newGrossAmount`, `platformFee = (newGrossAmount * 5) / 100`, `netAmount = newGrossAmount - platformFee`, `status = RELEASED`, `releasedAt = Instant.now()`
  - `SellerSettlement.cancel()`: chuyển `status = CANCELLED`
  - `RevenueService.releaseSettlementForOrder(UUID orderId): void`
  - `RevenueService.cancelSettlementForOrder(UUID orderId): void`
  - `RevenueService.adjustSettlementOnReturn(UUID orderId, long refundAmount): void`
  - `RevenueService.listAdminWithdrawals(@Nullable String status, int page, int size): PageResponse<AdminWithdrawalResponse>`
  - `RevenueService.approveWithdrawal(UUID adminId, UUID withdrawalId): AdminWithdrawalResponse`
  - `RevenueService.rejectWithdrawal(UUID adminId, UUID withdrawalId): AdminWithdrawalResponse`
  - Admin Endpoints:
    - `GET /api/admin/payments/withdrawals` (`operationId = "listAdminWithdrawals"`)
    - `PATCH /api/admin/payments/withdrawals/{id}/approve` (`operationId = "approveWithdrawal"`)
    - `PATCH /api/admin/payments/withdrawals/{id}/reject` (`operationId = "rejectWithdrawal"`)

- [ ] **Step 1: Viết failing tests trong `RevenueServiceIntegrationTest` và `RevenueServiceUnitTest`**

1. `settlementStaysHoldingAfterDeliverAndReleasesWith5PercentFeeOnlyWhenBuyerConfirmsReceipt`:
   - Đơn hàng `subtotal = 200_000₫`:
     - Khi đang ở `DELIVERED` (chưa `confirmReceipt`): `getSummary` trả `holdingBalance = 190_000L` (đã trừ 5% phí sàn = `10_000₫`), `walletBalance = 0L`.
     - Khi buyer gọi `orderService.confirmReceipt(buyerId, orderId)` (hoặc `systemAutoCompleteOrder`): `SellerSettlement` chuyển sang `RELEASED`, `getSummary` trả `holdingBalance = 0L`, `walletBalance = 190_000L`.
2. `deliverOnPaidVietQrOrderPreservesPaidStatus`:
   - Đơn `VIETQR` đã `PAID`, khi `deliver()` giữ nguyên `paymentStatus = PAID` (không ghi đè thành `COLLECTED`).
3. `creatingWithdrawalImmediatelyDeductsAvailableWalletBalanceAndRejectingRestoresIt`:
   - Ví khả dụng `190_000₫` → Shop tạo lệnh rút `100_000₫` → `walletBalance` còn `90_000₫` (thử rút tiếp `100_000₫` bị chặn `400 BAD_REQUEST`).
   - Admin gọi `rejectWithdrawal` → `walletBalance` khôi phục lại `190_000₫`.
   - Shop tạo lệnh rút `100_000₫` → Admin gọi `approveWithdrawal` → trạng thái `COMPLETED`, `walletBalance` giữ ở `90_000₫`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*RevenueService*"`
Expected: FAIL.

- [ ] **Step 3: Implement cập nhật `SellerSettlement`, `RevenueService`, `OrderService.deliver`, `OrderService.confirmReceipt`, `OrderService.systemAutoCompleteOrder`**

- Trong `Order.deliver()`: chỉ chuyển `paymentStatus = PaymentStatus.COLLECTED` nếu `paymentStatus != PaymentStatus.PAID`.
- Trong `OrderService.confirmReceipt` và `OrderService.systemAutoCompleteOrder`: gọi `revenue.releaseSettlementForOrder(saved.getId())`.
- Trong `SellerSettlementRepository`:
  - `sumReleasedBalance(shopId)`: `SELECT COALESCE(SUM(net_amount), 0) FROM seller_settlement WHERE shop_id = :shopId AND status IN ('RELEASED', 'WITHDRAWN')`
- Trong `SellerWithdrawalRepository`:
  - `sumActiveWithdrawals(shopId)`: `SELECT COALESCE(SUM(amount), 0) FROM seller_withdrawal WHERE shop_id = :shopId AND status IN ('REQUESTED', 'PROCESSING', 'COMPLETED')`
- `RevenueService.getSummary` và `createWithdrawal` tính `available = Math.max(0L, settlements.sumReleasedBalance(shopId) - withdrawals.sumActiveWithdrawals(shopId))`.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*RevenueService*" --tests "*OrderAutoCompleteJobTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/model/SellerSettlement.java src/main/java/com/minhnbnt/fashionstore/model/SettlementStatus.java src/main/java/com/minhnbnt/fashionstore/model/Order.java src/main/java/com/minhnbnt/fashionstore/dto/payment/AdminWithdrawalResponse.java src/main/java/com/minhnbnt/fashionstore/features/order/ src/main/java/com/minhnbnt/fashionstore/features/admin/controller/AdminPaymentController.java src/test/java/com/minhnbnt/fashionstore/features/order/RevenueService*
git commit -m "feat(escrow): release settlement with 5% platform fee on buyer receipt and deduct active withdrawals"
```

---

### Task 6: Luồng Hủy đơn đã trả tiền & Hoàn tất Đổi/Trả hàng → `REFUND_PENDING` → Admin Xác nhận Hoàn tiền (`REFUNDED`)

**Files:**
- Modify: `B/features/order/OrderService.java`, `B/features/order/ReturnRequestService.java`, `B/features/payment/PaymentFlowService.java`, `B/features/payment/PaymentTransactionService.java`
- Modify: `B/features/admin/controller/AdminPaymentController.java`
- Create: `B/dto/payment/PaymentTransactionResponse.java`, `B/dto/payment/MarkRefundedRequest.java`
- Test: `T/features/order/ReturnRequestServiceUnitTest.java`, `T/features/payment/PaymentRefundFlowTest.java`

**Interfaces:**
- Consumes: `ProductStockService`, `RevenueService`, `PaymentTransactionService` (lưu ý: `OrderService` và `ReturnRequestService` chỉ inject `PaymentTransactionService`, **không inject `PaymentFlowService`** để tránh phụ thuộc vòng), `IdentityDirectoryService`, `ConfigAuditService`
- Produces:
  - `Order.markRefundPending()`, `Order.markRefunded()`
  - `PaymentTransactionService.flagOrderRefund(UUID orderId, long refundAmount, String reason): void`
  - `PaymentFlowService.markTransactionRefunded(UUID adminId, UUID transactionId, String note): PaymentTransactionResponse`
  - `PaymentFlowService.searchTransactionsForAdmin(@Nullable PaymentTransactionStatus status, @Nullable RefundStatus refundStatus, @Nullable String q, int page, int size): PageResponse<PaymentTransactionResponse>`
  - `record PaymentTransactionResponse(UUID id, UUID buyerId, String buyerEmail, String provider, String txnRef, long amount, long receivedAmount, String status, String bankCode, String accountNumber, @Nullable String bankTransactionId, @Nullable String transferContent, @Nullable Instant paidAt, Instant expiresAt, String refundStatus, long refundAmount, @Nullable String refundReason, @Nullable Instant refundedAt, @Nullable String refundNote, List<VietQrOrderBrief> orders, Instant createdAt)`
  - `record MarkRefundedRequest(@NotBlank @Size(min = 5, max = 500) String note)`
  - Admin Endpoints:
    - `GET /api/admin/payments/transactions` (`operationId = "listPaymentTransactions"`)
    - `PATCH /api/admin/payments/transactions/{id}/refunded` (`operationId = "markTransactionRefunded"`)

- [ ] **Step 1: Viết failing tests trong `PaymentRefundFlowTest` và `ReturnRequestServiceUnitTest`**

1. `cancellingPaidVietQrOrderCancelsHoldingSettlementAndMovesToRefundPending`:
   - Đơn `VIETQR` đã `PAID` (đang ở `PENDING` hoặc `CONFIRMED`, có `SellerSettlement` `HOLDING`):
   - Khi Buyer hủy, Seller hủy, Admin hủy hoặc `systemCancelExpiredOrder` hủy:
     - Tồn kho được cộng lại (`ORDER_CANCEL`).
     - `order.getPaymentStatus()` chuyển thành `REFUND_PENDING`.
     - `SellerSettlement` của đơn chuyển sang `CANCELLED` (`holdingBalance = 0`).
     - Giao dịch `payment_transaction` của đơn có `refundStatus = PENDING`, `refundAmount = order.getTotal()`.
2. `completingFullReturnRestocksInventoryCancelsSettlementAndFlagsRefundPending`:
   - Đơn đã `DELIVERED` → Buyer tạo `ReturnRequest` → Seller `approve("REFUND", order.getTotal(), "Kho HN")` → Seller gọi `complete(sellerId, returnId, "Đã nhận đủ hàng hoàn")`:
     - Tồn kho biến thể được cộng lại (`incrementVariantStock` + `recordStockMovement(..., "ORDER_RETURN", orderId)`).
     - `SellerSettlement` chuyển sang `CANCELLED`.
     - `order.getPaymentStatus()` chuyển sang `REFUND_PENDING`.
     - Giao dịch `payment_transaction` có `refundStatus = PENDING`, `refundAmount = order.getTotal()`.
3. `completingPartialReturnAdjustsSettlementWith5PercentFeeOnRemainderAndReleasesToShop`:
   - Đơn `subtotal = 200_000₫` → `approve("PARTIAL_REFUND", 50_000L, ...)` → `complete(...)`:
     - `SellerSettlement` được cập nhật `grossAmount = 150_000L`, `platformFee = 7_500L` (5%), `netAmount = 142_500L`, trạng thái `RELEASED` (cộng vào ví khả dụng của Shop).
     - Phần `50_000L` chuyển sang `REFUND_PENDING` cho khách.
4. `adminMarkTransactionRefundedTransitionsTransactionAndOrderToRefunded`:
   - Admin gọi `markTransactionRefunded(adminId, txId, "Đã chuyển khoản hoàn tiền qua MB Bank")` → giao dịch `refundStatus = REFUNDED`, đơn `paymentStatus = REFUNDED`, ghi audit `PAYMENT_TRANSACTION`. Gọi lần 2 → `409 CONFLICT`.

- [ ] **Step 2: Chạy test để xác nhận FAIL**

Run: `.\gradlew.bat test --tests "*PaymentRefundFlowTest" --tests "*ReturnRequestServiceUnitTest"`
Expected: FAIL.

- [ ] **Step 3: Implement logic hoàn tiền trong `OrderService`, `ReturnRequestService`, `PaymentTransactionService`, `PaymentFlowService`, `AdminPaymentController`**

- Trong `OrderService` (`cancel`, `sellerCancel`, `cancelByAdmin`, `systemCancelExpiredOrder`): nếu đơn bị hủy đang có `paymentStatus == PaymentStatus.PAID`, gọi `order.markRefundPending()`, `revenue.cancelSettlementForOrder(order.getId())`, và `paymentTransactions.flagOrderRefund(order.getId(), order.getTotal(), ...)`.
- Trong `ReturnRequestService.complete`:
  - Lấy danh sách `OrderItem` của đơn; nếu `resolution` là `"REFUND"` hoặc `"EXCHANGE"`, cộng lại tồn kho và ghi `stock_movement` `"ORDER_RETURN"`.
  - Gọi `revenue.adjustSettlementOnReturn(order.getId(), refundAmount)`.
  - Nếu `refundAmount > 0`: chuyển `order.markRefundPending()`, gọi `paymentTransactions.flagOrderRefund(order.getId(), refundAmount, ...)`.

- [ ] **Step 4: Chạy test để xác nhận PASS**

Run: `.\gradlew.bat test --tests "*PaymentRefundFlowTest" --tests "*ReturnRequestServiceUnitTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/minhnbnt/fashionstore/dto/payment/ src/main/java/com/minhnbnt/fashionstore/features/order/ src/main/java/com/minhnbnt/fashionstore/features/payment/ src/main/java/com/minhnbnt/fashionstore/features/admin/controller/AdminPaymentController.java src/main/java/com/minhnbnt/fashionstore/model/Order.java src/test/java/com/minhnbnt/fashionstore/features/order/ReturnRequestServiceUnitTest.java src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentRefundFlowTest.java
git commit -m "feat(refund): connect order cancel and return completion to escrow, stock restock, and admin refund confirmation"
```

---

### Task 7: Cập nhật Docs Backend, Xuất OpenAPI Spec, Chạy Orval Codegen & Cập nhật Helper Trạng thái Đơn/Thanh toán trên UI

**Files:**
- Modify (Backend): `docs/data.md` (thêm mục `V49__vietqr_escrow_payment.sql`), `docs/api-endpoints.md` (thêm các endpoint VietQR, Escrow & Admin Payments), `docs/openapi.json` (sinh tự động qua `OpenApiExportTest`)
- Modify (UI): `docs/openapi.json`, `generated/**` (sinh qua `pnpm codegen`)
- Modify (UI): `src/lib/order/orderStatus.ts`, `src/lib/order/orderStatus.test.ts`, `src/lib/order/orderMonitoring.ts`

**Interfaces:**
- Consumes: Backend endpoints từ Task 1–6
- Produces (UI `src/lib/order/orderStatus.ts`):
  - `orderStatusMeta('AWAITING_PAYMENT')` → `{ label: 'Chờ thanh toán', className: '...' }`
  - `paymentStatusLabel(status)`:
    - `'UNPAID'` → `'Chưa thanh toán'`
    - `'PAID'` → `'Đã thanh toán'`
    - `'COLLECTED'` → `'Đã thu tiền'`
    - `'FAILED'` → `'Thanh toán lỗi'`
    - `'REFUND_PENDING'` → `'Chờ hoàn tiền'`
    - `'REFUNDED'` → `'Đã hoàn tiền'`
  - `canCancel(status)`: `status === 'PENDING' || status === 'AWAITING_PAYMENT'`
  - `BUYER_ORDER_TABS` thêm `{ key: 'AWAITING_PAYMENT', label: 'Chờ thanh toán' }` đứng sau `'ALL'`
  - `codUnavailableReason(total: number, min: number, max: number): string | null`

- [ ] **Step 1: Chạy `OpenApiExportTest` ở Backend và `pnpm codegen` ở Frontend, sau đó viết failing test trong `src/lib/order/orderStatus.test.ts`**

Run (Backend): `.\gradlew.bat test --tests "*OpenApiExportTest"`
Run (UI): `pnpm codegen`

Thêm test vào `src/lib/order/orderStatus.test.ts` kiểm tra `orderStatusMeta('AWAITING_PAYMENT')`, `paymentStatusLabel('PAID' | 'REFUND_PENDING' | 'REFUNDED')`, `canCancel('AWAITING_PAYMENT') === true`, và `codUnavailableReason`.

- [ ] **Step 2: Chạy test UI để xác nhận FAIL**

Run (UI): `pnpm test src/lib/order/orderStatus.test.ts`
Expected: FAIL.

- [ ] **Step 3: Implement cập nhật `src/lib/order/orderStatus.ts` và `src/lib/order/orderMonitoring.ts`**

- Bổ sung `AWAITING_PAYMENT`, `PAID`, `REFUND_PENDING`, `REFUNDED`, và hàm `codUnavailableReason(total, min, max)`.

- [ ] **Step 4: Chạy test UI để xác nhận PASS**

Run (UI): `pnpm test src/lib/order/orderStatus.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/openapi.json generated/ src/lib/order/
git commit -m "feat(ui): regenerate openapi hooks and add VietQR order/payment status helpers"
```

---

### Task 8: Giao diện Người mua — Chọn VietQR tại `/checkout`, Trang Quét Mã QR & Sandbox Demo (`/payment/qr/:txnRef`), Thanh toán lại trên Đơn hàng

**Files (UI):**
- Create: `src/pages/checkout/VietQrPayment.tsx`, `src/pages/checkout/VietQrPayment.test.tsx`
- Modify: `src/pages/checkout/Checkout.tsx`, `src/pages/checkout/Checkout.test.tsx`
- Modify: `src/pages/checkout/Orders.tsx`, `src/pages/checkout/Orders.test.tsx`
- Modify: `src/pages/checkout/OrderDetail.tsx`, `src/pages/checkout/OrderDetail.test.tsx`
- Modify: `src/pages/index.ts`, `src/App.tsx`

**Interfaces:**
- Consumes:
  - `useListPaymentMethods` (`GET /api/public/payment-methods`)
  - `useGetVietQrSession` (`GET /api/payments/vietqr/{txnRef}`)
  - `useSimulateVietQrTransfer` (`POST /api/payments/vietqr/{txnRef}/simulate`)
  - `useRetryVietQrPayment` (`POST /api/orders/{id}/payments/vietqr`)
  - `codUnavailableReason`, `orderStatusMeta`, `paymentStatusLabel` (Task 7)
- Produces:
  - Route `/payment/qr/:txnRef` (bọc trong `<RequireAuth>`) render `<VietQrPayment />`

- [ ] **Step 1: Viết failing tests trong `Checkout.test.tsx`, `VietQrPayment.test.tsx`, `OrderDetail.test.tsx`**

1. Trong `Checkout.test.tsx`:
   - Hiển thị 2 lựa chọn radio `Thanh toán khi nhận hàng (COD)` và `Chuyển khoản ngân hàng (VietQR)`.
   - Khi chọn `VIETQR` và bấm `"Đặt Hàng & Quét Mã QR"`, payload gửi lên `cart.checkout` có `paymentMethod: 'VIETQR'` và điều hướng tới `/payment/qr/<paymentTxnRef>`.
   - Khi tổng đơn vượt `codMaxTotal`, radio `COD` bị disabled kèm thông báo lý do từ `codUnavailableReason`.
2. Trong `VietQrPayment.test.tsx`:
   - Hiển thị ảnh QR (`img[src*="img.vietqr.io"]`), số tài khoản, tên chủ tài khoản, số tiền, nội dung chuyển khoản `txnRef` và danh sách các đơn hàng trong phiên.
   - Khi `sandboxMode === true`, hiển thị khối **"Công cụ Demo Sandbox"** với nút **"Giả lập ngân hàng báo đã nhận đủ tiền"** và **"Giả lập chuyển thiếu tiền (50%)"**. Bấm nút giả lập đủ tiền gọi mutation `simulateVietQrTransfer` và hiển thị trạng thái **"Thanh toán thành công!"**.
3. Trong `OrderDetail.test.tsx`:
   - Đơn ở trạng thái `AWAITING_PAYMENT` hiển thị nút **"Thanh toán ngay (Mã QR)"**; bấm vào gọi `retryVietQrPayment` rồi chuyển hướng tới `/payment/qr/<txnRef>`.

- [ ] **Step 2: Chạy test UI để xác nhận FAIL**

Run (UI): `pnpm test src/pages/checkout/`
Expected: FAIL.

- [ ] **Step 3: Implement `VietQrPayment.tsx` và cập nhật `Checkout.tsx`, `Orders.tsx`, `OrderDetail.tsx`, `App.tsx`**

- Trong `VietQrPayment.tsx`: bật `refetchInterval: 3000` trên `useGetVietQrSession` khi `session.status === 'PENDING'`. Không dùng toán tử 3 ngôi (`? :`).
- Trong `Orders.tsx`: thêm bộ đếm `AWAITING_PAYMENT` vào `tabCounts` và nút **"Thanh toán ngay"** trên thẻ đơn `AWAITING_PAYMENT`.

- [ ] **Step 4: Chạy test UI để xác nhận PASS**

Run (UI): `pnpm test src/pages/checkout/`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/pages/checkout/ src/pages/index.ts src/App.tsx
git commit -m "feat(ui): add VietQR selection on checkout, QR payment page with sandbox simulator, and order retry"
```

---

### Task 9: Giao diện Admin — Trang Quản trị Thanh toán, Hoàn tiền & Duyệt Rút tiền (`/admin/payments`)

**Files (UI):**
- Create: `src/pages/admin/AdminPayments.tsx`, `src/pages/admin/AdminPayments.test.tsx`
- Modify: `src/components/admin/AdminShell.tsx`, `src/pages/index.ts`, `src/App.tsx`

**Interfaces:**
- Consumes:
  - `useListPaymentGateways`, `useUpdatePaymentGateway`
  - `useListBanks` (`GET /api/banks`)
  - `useListPaymentTransactions`, `useMarkTransactionRefunded`
  - `useListAdminWithdrawals`, `useApproveWithdrawal`, `useRejectWithdrawal`
- Produces:
  - Route `/admin/payments` (bọc trong `<RequireRole requiredRole="admin">`)
  - Mục menu **"Thanh toán"** (`/admin/payments`, icon `CreditCard`) trong nhóm `VẬN HÀNH SÀN` của `AdminShell.tsx`

- [ ] **Step 1: Viết failing tests trong `AdminPayments.test.tsx`**

1. Tab **Cổng thanh toán**: hiển thị cấu hình `COD` và `VIETQR` (dropdown chọn ngân hàng NAPAS, số tài khoản, tên chủ tài khoản, ô Webhook Secret, công tắc `Chế độ Demo Sandbox`), bấm Lưu gọi `updatePaymentGateway`.
2. Tab **Giao dịch & Hoàn tiền**: hiển thị danh sách giao dịch VietQR, bộ lọc **"Cần hoàn tiền"** (`refundStatus = PENDING`); với giao dịch có `refundStatus === 'PENDING'`, bấm **"Đánh dấu đã hoàn tiền"** mở modal nhập ghi chú (5–500 ký tự) và gọi `markTransactionRefunded`.
3. Tab **Yêu cầu rút tiền Shop**: hiển thị danh sách lệnh rút tiền của các Shop; với lệnh `REQUESTED`, bấm **"Duyệt đã chuyển khoản"** gọi `approveWithdrawal`, bấm **"Từ chối"** gọi `rejectWithdrawal`.

- [ ] **Step 2: Chạy test UI để xác nhận FAIL**

Run (UI): `pnpm test src/pages/admin/AdminPayments.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement `AdminPayments.tsx` và cập nhật `AdminShell.tsx`, `src/pages/index.ts`, `src/App.tsx`, `docs/structure.md`**

- Tuân thủ design system của `AdminSettings.tsx` (`AdminShell`, `PANEL_CLASS`, `INPUT_CLASS`, `segmentedTrackClass`, `segmentedItemClass`, dùng chung `src/components/Modal.tsx`, semantic tokens `emerald`/`amber`/`rose`/`sky` — cấm dùng `red-*` hoặc `orange-*`). Không dùng toán tử 3 ngôi (`? :`).
- Cập nhật `docs/structure.md` của UI để ghi nhận 2 trang mới `VietQrPayment.tsx` và `AdminPayments.tsx`.

- [ ] **Step 4: Chạy toàn bộ kiểm tra Backend & Frontend để xác nhận PASS**

Run (Backend): `.\gradlew.bat test`
Run (UI): `pnpm format && pnpm lint && pnpm build && pnpm test`
Expected: Toàn bộ test, lint và build đều PASS.

- [ ] **Step 5: Commit**

```bash
git add src/pages/admin/AdminPayments* src/components/admin/AdminShell.tsx src/pages/index.ts src/App.tsx
git commit -m "feat(admin-ui): add AdminPayments page for VietQR config, refund reconciliation, and shop withdrawals"
```
