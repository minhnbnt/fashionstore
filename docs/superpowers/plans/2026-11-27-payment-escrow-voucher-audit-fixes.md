# Payment, Escrow, Return & Voucher Audit Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khắc phục toàn bộ 18 lỗi bảo mật, toàn vẹn tài chính (Escrow/Refund/Withdrawal), race condition/deadlock, và hiệu năng (N+1, dead code) từ kết quả rà soát PR VietQR Escrow & Shop Voucher.

**Architecture:** Chuẩn hóa thứ tự khóa bi quan toàn cục (`return_request` → `orders` theo `id ASC` → `payment_transaction` theo `id ASC` → `seller_settlement`) để triệt tiêu deadlock và race condition trên mọi luồng tiền tệ. Bổ sung ràng buộc DB (`UNIQUE INDEX` trên `seller_settlement(order_id)`, random webhook secret), cộng dồn chuyển khoản thiếu nhiều lần có xét phần tiền đã hoàn (`alreadyRefunded`) và chống đếm trùng retry cùng `bankTransactionId`, khép kín luồng hoàn tiền cho cả đơn VietQR lẫn COD, và gom truy vấn batch cho các đường đọc/ghi N+1.

**Tech Stack:** Java 25, Spring Boot 4, Spring Data JDBC / `JdbcClient`, PostgreSQL + Flyway, JUnit 5, Testcontainers.

**Spec:**
- `docs/superpowers/specs/2026-11-26-vietqr-escrow-payment-design.md`
- `docs/superpowers/specs/2026-11-26-shop-voucher-design.md`

## Global Constraints

- Không bao giờ chấp nhận xử lý Webhook `/api/public/payments/vietqr/webhook` khi `webhookSecret` đang là giá trị demo mặc định (`demo-vietqr-secret-key`) hoặc rỗng, và không cho phép tắt `sandboxMode` (`sandboxMode = false`) khi `enabled = true` nếu chưa đổi `webhookSecret` khỏi giá trị demo.
- Thứ tự khóa dòng (Lock Ordering) bắt buộc trên mọi transaction chạm đồng thời đơn hàng và giao dịch: khóa `orders` (sắp xếp `orderId ASC`) **trước**, khóa `payment_transaction` **sau** (kể cả trong `processIncomingTransfer`, `confirmTransferByAdmin`, và `markTransactionRefunded`).
- Không ghi đè `receivedAmount` hoặc `refundAmount` đang `PENDING` khi khách chuyển thiếu nhiều lần; bỏ qua webhook retry trùng `bankTransactionId`; cộng dồn `receivedAmount` và chỉ kích hoạt đơn khi số tiền thực giữ tại sàn (`receivedAmount - alreadyRefunded >= amount`).
- Không cho phép tạo `ReturnRequest` sau khi đơn đã xác nhận nhận hàng (`receivedAt != null`), đồng thời xử lý cả `SellerSettlement` ở trạng thái `RELEASED` trong `adjustSettlementOnReturn` và `cancelSettlementForOrder`.
- Mọi thao tác rút tiền (`createWithdrawal`), xử lý trả hàng (`create`, `approve`, `reject`, `complete`), thanh toán lại (`retryVietQrPayment`), và đặt giỏ hàng (`CartService.checkout`) phải giữ khóa `FOR UPDATE` (truy vấn không chứa hàm aggregate vì PostgreSQL cấm `FOR UPDATE` đi kèm `SUM`) trước khi kiểm tra trạng thái/số dư.

## Review Focus

- Webhook giả mạo bằng `demo-vietqr-secret-key`, payload thiếu `transferAmount` (`<= 0`), hoặc chứa ký tự điều khiển (`\n`, `\r`, `\t`, `< 0x20`) trong nội dung chuyển khoản ngân hàng.
- Timeline khách bấm `confirmReceipt` (`settlement` → `RELEASED`) rồi gọi `POST /api/orders/{id}/returns`, hoặc 2 request `confirmReceipt` và `createReturn` chạy song song.
- Khách chuyển thiếu 2 lần liên tiếp (150k + 210k trên đơn 360k) qua webhook hoặc qua `confirmTransferByAdmin`: tổng nhận đủ 360k phải kích hoạt đơn `PAID` và xóa cờ hoàn tiền chuyển thiếu; nếu webhook provider retry lại đúng `bankTransactionId` của lần chuyển thiếu 150k thì không được cộng dồn nhầm thành 300k.
- Hai luồng song song gọi `createWithdrawal` cùng rút toàn bộ số dư khả dụng, hoặc gọi `ReturnRequestService.complete()` đúp, hoặc gọi `CartService.checkout()` đúp, hoặc `processIncomingTransfer` chạy đồng thời với `PaymentExpiryJob`.
- Đơn COD hoàn tất trả hàng hoàn tiền (`complete()`) không có bản ghi `payment_transaction` cần đường đóng hoàn tiền rõ ràng (`PATCH /api/admin/orders/{id}/refunded`), không bị kẹt vĩnh viễn ở `REFUND_PENDING`.

---

## File Map

- **Migrations:**
  - Modify: `src/main/resources/db/migration/V49__vietqr_escrow_payment.sql` (seed random `gen_random_uuid()::text` cho `webhook_secret` của `VIETQR`, thêm `CREATE UNIQUE INDEX IF NOT EXISTS uq_seller_settlement_order ON seller_settlement (order_id)`)
- **Domain Models & DTOs:**
  - Modify: `src/main/java/com/minhnbnt/fashionstore/model/PaymentGateway.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/model/PaymentTransaction.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/model/ShopVoucher.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/dto/payment/VietQrWebhookRequest.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/dto/payment/UpdatePaymentGatewayRequest.java`
- **Repositories:**
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/ReturnRequestRepository.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/SellerSettlementRepository.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderRepository.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/cart/repository/CartRepository.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/repository/PaymentTransactionRepository.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/voucher/repository/ShopVoucherRepository.java`
- **Services & Controllers:**
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentGatewayService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/controller/PublicPaymentController.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentFlowService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentTransactionService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/ReturnRequestService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/RevenueService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/controller/OrderController.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/admin/controller/AdminOrderController.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/admin/service/AdminOrderService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/voucher/service/VoucherService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/cart/CartService.java`
  - Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`

---

### Task 1: Bảo mật Webhook Secret VietQR & Xử lý Payload Webhook (C-1, MINOR-2, NIT-3, NIT-4)

**Files:**
- Modify: `src/main/resources/db/migration/V49__vietqr_escrow_payment.sql:35-46`
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/PaymentGateway.java:48-64`
- Modify: `src/main/java/com/minhnbnt/fashionstore/dto/payment/UpdatePaymentGatewayRequest.java:28-35`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentGatewayService.java:112-128`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/controller/PublicPaymentController.java:38-103`
- Modify: `src/main/java/com/minhnbnt/fashionstore/dto/payment/VietQrWebhookRequest.java:68-94`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentGatewayServiceTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentControllerTest.java`

**Interfaces:**
- Produces:
  - `PaymentGateway.INSECURE_DEMO_WEBHOOK_SECRET = "demo-vietqr-secret-key"`
  - `PaymentGateway.hasCustomWebhookSecret(): boolean` (`hasWebhookSecret() && !INSECURE_DEMO_WEBHOOK_SECRET.equals(webhookSecret)`)

- [ ] **Step 1: Viết các bài test thất bại cho C-1, MINOR-2, NIT-3 trong `PaymentGatewayServiceTest` và `PaymentControllerTest`**

  - Trong `PaymentGatewayServiceTest`:
    - Cập nhật `@BeforeEach resetState()` dùng secret hợp lệ (ví dụ `'rotated-test-vietqr-secret'`) cho các test cấu hình thông thường.
    - Thêm test `disablingSandboxModeWhileKeepingInsecureDemoWebhookSecretIsRejectedWithConflict`: đặt `webhook_secret = 'demo-vietqr-secret-key'`, gọi `gateways.update(..., PaymentMethod.VIETQR, new UpdatePaymentGatewayRequest(true, null, null, null, null, null, false))` → ném `409 Conflict`.
  - Trong `PaymentControllerTest`:
    - Cập nhật `activeVietQrGateway()` dùng secret đã rotate (`"configured-vietqr-webhook-secret"`).
    - Thêm test `vietQrWebhookRejectsInsecureDefaultDemoSecretEvenWhenHeaderMatches`: khi gateway vẫn giữ `"demo-vietqr-secret-key"`, gọi `POST /api/public/payments/vietqr/webhook` với `Authorization: Apikey demo-vietqr-secret-key` → trả `401 Unauthorized`.
    - Thêm test `vietQrWebhookIgnoresZeroOrNegativeTransferAmount`: payload có `transferAmount: 0` hoặc `transferAmount: -50000` hoặc thiếu `transferAmount`/`amount` → trả `200 OK` với `outcome = "IGNORED"`, `verify(paymentFlow, never()).processIncomingTransfer(...)`.
    - Thêm test `toRawPayloadJsonEscapesControlCharactersInBankNarration`: `content` chứa `"CK\nDHMQ123\r\t\"\\"` và `\u0007` → `toRawPayloadJson()` parse được bằng Jackson `ObjectMapper` không lỗi.

- [ ] **Step 2: Chạy test để xác nhận thất bại**

  Run: `./mvnw test -Dtest=PaymentGatewayServiceTest,PaymentControllerTest`
  Expected: FAIL

- [ ] **Step 3: Cập nhật `V49__vietqr_escrow_payment.sql`, `PaymentGateway`, `PaymentGatewayService`, `PublicPaymentController`, `VietQrWebhookRequest`**

  - Trong `V49__vietqr_escrow_payment.sql`: đổi giá trị seed `webhook_secret` của `VIETQR` từ `'demo-vietqr-secret-key'` sang `gen_random_uuid()::text`.
  - Trong `PaymentGateway`: thêm `public static final String INSECURE_DEMO_WEBHOOK_SECRET = "demo-vietqr-secret-key";` và `public boolean hasCustomWebhookSecret()`.
  - Trong `PaymentGatewayService.update`: khi `code == PaymentMethod.VIETQR && newEnabled && !newSandboxMode`, nếu `PaymentGateway.INSECURE_DEMO_WEBHOOK_SECRET.equals(newWebhookSecret)` thì ném `ResponseStatusException(HttpStatus.CONFLICT, "VietQR webhook secret must be rotated before disabling sandbox mode")`.
  - Trong `PublicPaymentController`:
    - Thay `ObjectProvider<PaymentFlowService>` bằng constructor injection trực tiếp `private final PaymentFlowService paymentFlow;`.
    - Trong `verifyWebhookSecret`: kiểm tra `!gateway.hasCustomWebhookSecret()` → ném `ResponseStatusException(HttpStatus.UNAUTHORIZED, "Webhook secret is not configured or still using insecure default")`.
    - Trong `vietQrWebhook`: nếu `request.resolvedAmount() <= 0L` → trả ngay `new VietQrWebhookResponse(true, TransferOutcome.IGNORED.name(), null)`.
  - Trong `VietQrWebhookRequest.appendJsonField`: viết hàm `escapeJsonString(String value)` xử lý `\\`, `\"`, `\b`, `\f`, `\n`, `\r`, `\t` và các ký tự `ch < 0x20` thành `String.format("\\u%04x", (int) ch)`.

- [ ] **Step 4: Chạy lại test để xác nhận PASS**

  Run: `./mvnw test -Dtest=PaymentGatewayServiceTest,PaymentControllerTest,AdminPaymentControllerTest`
  Expected: PASS

---

### Task 2: Cộng dồn chuyển khoản thiếu nhiều lần & Chuẩn hóa thứ tự khóa chống Deadlock (M-1, MINOR-1, MINOR-3)

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/PaymentTransaction.java:76-200`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentFlowService.java:40-231`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentTransactionService.java:83-207`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/repository/PaymentTransactionRepository.java:161-180`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java:485-613`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentFlowServiceTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/payment/VietQrUrlBuilderTest.java`

**Interfaces:**
- Produces:
  - `PaymentTransactionRepository.findLockedTransactionsByOrderId(UUID orderId): List<PaymentTransaction>` (`SELECT pt.* FROM payment_transaction pt JOIN payment_transaction_order pto ON pto.transaction_id = pt.id WHERE pto.order_id = :orderId ORDER BY pt.created_at DESC, pt.id DESC FOR UPDATE`)
  - `PaymentTransactionService.findById(UUID id): Optional<PaymentTransaction>` (đọc không khóa để lấy `orderIds` trước khi khóa theo thứ tự `orders` → `payment_transaction`)
  - `OrderService.lockOrdersInOrder(List<UUID> orderIds): void` (khóa `orders.findLockedById(orderId)` theo thứ tự `orderId ASC`)

- [ ] **Step 1: Viết test thất bại trong `PaymentFlowServiceTest` cho chuyển thiếu nhiều lần, retry đồng thời và chống deadlock**

  - Test 1 (`multiplePartialTransfersAccumulateAndPromoteToSuccessWhenTotalReached`): đơn 360.000₫, lần 1 chuyển 150.000₫ (`FT-1`) → `UNDERPAID` (`receivedAmount = 150_000`, `refundAmount = 150_000`), retry lại đúng `FT-1` → `ALREADY_PROCESSED` (không cộng đúp), lần 2 chuyển thêm 100.000₫ (`FT-2`) → vẫn `UNDERPAID` (`receivedAmount = 250_000`, `refundAmount = 250_000`), lần 3 chuyển thêm 110.000₫ (`FT-3`) → tổng `receivedAmount = 360_000` → promote lên `SUCCESS`, đơn chuyển sang `PENDING`/`PAID`, `refundStatus = NONE`, `refundAmount = 0`.
  - Test 2 (`multiplePartialTransfersOverpayingOnFinalTransferFlagsOnlyOverpaidRemainder`): đơn 360.000₫, lần 1 chuyển 150.000₫ (`FT-A`), lần 2 chuyển 250.000₫ (`FT-B`, tổng 400.000₫) → `SUCCESS`, đơn `PAID`, `refundStatus = PENDING`, `refundAmount = 40_000L`, `refundReason = "Khách chuyển dư tiền"`.
  - Test 3 (`concurrentWebhookAndExpiryJobDoNotDeadlock`): dùng `ExecutorService` + `CountDownLatch` bắn đồng thời `paymentFlow.processIncomingTransfer` và `orders.systemExpireAwaitingPaymentOrder` trên cùng đơn hàng → cả hai hoàn tất không ném lỗi deadlock.
  - Test 4 (`concurrentRetryVietQrForSameOrderReturnsSinglePendingSession`): 2 thread gọi `paymentFlow.retryForOrder(buyerId, orderId)` đồng thời qua `CountDownLatch` → trả về cùng 1 `txnRef`, trong DB chỉ có 1 phiên `PENDING`.

- [ ] **Step 2: Chạy test để xác nhận thất bại**

  Run: `./mvnw test -Dtest=PaymentFlowServiceTest`
  Expected: FAIL

- [ ] **Step 3: Triển khai cộng dồn `receivedAmount`, khóa theo thứ tự `orders` → `payment_transaction`, và khóa khi `retryVietQrPayment`**

  - Trong `PaymentTransaction`:
    - Nếu một lệnh chuyển khoản đến có `transferAmount <= 0`, không thay đổi trạng thái.
    - Tính `long alreadyRefunded = (this.refundStatus == RefundStatus.REFUNDED) ? this.refundAmount : 0L;`.
    - Khi nhận thêm `transferAmount`: tổng tiền đã nhận `long newReceivedAmount = this.receivedAmount + transferAmount;`, số tiền hiệu lực sàn đang giữ `long effectiveHeldAmount = newReceivedAmount - alreadyRefunded;`.
    - Trong `markUnderpaid(long additionalAmount, ...)`: cập nhật `this.receivedAmount = newReceivedAmount;`, `this.refundStatus = RefundStatus.PENDING;`, `this.refundAmount = effectiveHeldAmount;`, `this.refundReason = "Khách chuyển thiếu tiền (" + effectiveHeldAmount + "/" + this.amount + "₫)";`.
    - Trong `markSuccess(long additionalAmount, ...)`: cập nhật `this.receivedAmount = newReceivedAmount;`, `long overpaid = effectiveHeldAmount - this.amount;`. Nếu `overpaid > 0`: `this.refundStatus = RefundStatus.PENDING; this.refundAmount = overpaid; this.refundReason = "Khách chuyển dư tiền";`. Ngược lại nếu `this.refundStatus == RefundStatus.PENDING` (từ lần `UNDERPAID` trước chưa hoàn): reset `this.refundStatus = RefundStatus.NONE; this.refundAmount = 0L; this.refundReason = null;`.
  - Trong `PaymentFlowService`:
    - Trong `processIncomingTransfer`:
      1. Nếu `transferAmount <= 0` → trả `TransferOutcome.IGNORED`.
      2. Tìm `tx` không khóa (`paymentTransactions.findByTxnRef(txnRef)`). Nếu không có → `NOT_FOUND`.
      3. Đọc `orderIds = paymentTransactions.findOrderIdsByTransactionId(tx.getId())` (đã sort `ORDER BY order_id ASC`).
      4. Khóa tất cả `orders` theo thứ tự `orderId ASC` (`orderService.lockOrdersInOrder(orderIds)`).
      5. Khóa `tx = paymentTransactions.findLockedById(tx.getId()).orElseThrow()`.
      6. Nếu `tx.getStatus() == PaymentTransactionStatus.SUCCESS` hoặc (`bankTransactionId != null && !bankTransactionId.isBlank() && bankTransactionId.equals(tx.getBankTransactionId())`) → trả `ALREADY_PROCESSED`.
      7. Tính `alreadyRefunded` và `effectiveHeld = tx.getReceivedAmount() + transferAmount - alreadyRefunded`. Nếu `effectiveHeld < tx.getAmount()` → `tx.markUnderpaid(...)`; ngược lại → `tx.markSuccess(...)` và kích hoạt các đơn.
    - Trong `confirmTransferByAdmin`:
      - Đọc `tx = paymentTransactions.findById(transactionId)` (không khóa trước `processIncomingTransfer` để không vi phạm thứ tự khóa `orders` → `payment_transaction`).
      - Khi `amount == null`: tính `long alreadyRefunded = (tx.getRefundStatus() == RefundStatus.REFUNDED) ? tx.getRefundAmount() : 0L;` và `long remaining = Math.max(1L, tx.getAmount() - (tx.getReceivedAmount() - alreadyRefunded));` làm số tiền chuyển bổ sung.
    - Trong `markTransactionRefunded`:
      - Đọc `orderIds = paymentTransactions.findOrderIdsByTransactionId(transactionId)` trước, gọi `orderService.lockOrdersInOrder(orderIds)`, rồi mới gọi `paymentTransactions.findLockedById(transactionId)` để giữ đúng thứ tự khóa `orders` → `payment_transaction`.
  - Trong `OrderService.retryVietQrPayment` và `PaymentTransactionService.findPendingSessionForOrder`:
    - Dùng `transactions.findLockedTransactionsByOrderId(orderId)` (`FOR UPDATE`) khi đang giữ khóa `order` để tìm phiên `PENDING` còn hạn (và tự động `markExpired` các phiên `PENDING` đã hết hạn).

- [ ] **Step 4: Chạy lại test để xác nhận PASS**

  Run: `./mvnw test -Dtest=PaymentFlowServiceTest,PaymentTransactionServiceTest,VietQrUrlBuilderTest`
  Expected: PASS

---

### Task 3: Chặn tạo trả hàng sau khi đã nhận hàng, xử lý Settlement RELEASED, khóa ReturnRequest & hoàn tiền đơn COD (C-2, M-2, M-4)

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/ReturnRequestRepository.java:12-35`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/ReturnRequestService.java:86-375`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/RevenueService.java:81-106`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentTransactionService.java:163-180`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java:887-912`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/admin/service/AdminOrderService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/admin/controller/AdminOrderController.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/ReturnRequestServiceUnitTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/payment/PaymentRefundFlowTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/admin/controller/AdminOrderControllerTest.java`

**Interfaces:**
- Produces:
  - `ReturnRequestRepository.findLockedById(UUID id): Optional<ReturnRequest>` (`@Lock(LockMode.PESSIMISTIC_WRITE)`)
  - `PaymentTransactionService.flagOrderRefund(UUID orderId, long refundAmount, String reason): boolean`
  - `OrderService.settleOrderRefundByAdmin(UUID adminId, UUID orderId, String note): AdminOrderResponse`
  - `AdminOrderService.settleRefund(UUID adminId, UUID orderId, String note): AdminOrderResponse`
  - `AdminOrderController.markRefunded(Jwt jwt, UUID id, MarkRefundedRequest request): AdminOrderResponse` (`PATCH /api/admin/orders/{id}/refunded`)

- [ ] **Step 1: Viết các bài test thất bại cho C-2, M-2, M-4**

  - Trong `ReturnRequestServiceUnitTest`:
    - Thêm test `createReturnThrowsConflictWhenOrderAlreadyConfirmedReceived`: đơn `DELIVERED` có `order.confirmReceipt()` (`receivedAt != null`) → `returnService.create(...)` ném `409 Conflict`.
  - Trong `PaymentRefundFlowTest`:
    - Thêm test `buyerCannotCreateReturnAfterConfirmingReceipt`: sau `orders.confirmReceipt(buyerId, order.id())`, gọi `returnRequests.create(...)` ném `409 Conflict`.
    - Thêm test `adjustAndCancelSettlementAlsoApplyWhenSettlementAlreadyReleased`: khi `SellerSettlement` đang ở `RELEASED`, gọi `revenue.adjustSettlementOnReturn(orderId, 50_000L)` giảm `grossAmount`/`netAmount`, và gọi với `refundAmount >= grossAmount` chuyển sang `CANCELLED`.
    - Thêm test `codOrderReturnCompletionCanBeSettledViaAdminOrderRefundEndpoint`: đơn COD giao thành công → tạo return `REFUND` → `complete()` chuyển đơn sang `REFUND_PENDING` (`flagOrderRefund` trả `false` và log warning) → Admin gọi `orders.settleOrderRefundByAdmin(adminId, order.id(), "Đã chuyển khoản hoàn tiền COD")` chuyển đơn sang `REFUNDED` và ghi `ConfigAuditService`.
    - Thêm test `concurrentReturnCompleteOnlyExecutesOnce`: 2 thread cùng gọi `returnRequests.complete(ownerId, returnId, "note")` với `CountDownLatch` → đúng 1 thread thành công, 1 thread nhận `409 Conflict`, tồn kho chỉ cộng 1 lần và `refundAmount` không bị nhân đôi.
  - Trong `AdminOrderControllerTest`:
    - Thêm test `markRefundedDelegatesToAdminOrderService`: `PATCH /api/admin/orders/{id}/refunded` với body `{"note":"Đã hoàn tiền ngoài hệ thống"}` trả `200 OK`.

- [ ] **Step 2: Chạy test để xác nhận thất bại**

  Run: `./mvnw test -Dtest=ReturnRequestServiceUnitTest,PaymentRefundFlowTest,AdminOrderControllerTest`
  Expected: FAIL

- [ ] **Step 3: Triển khai fix C-2, M-2, M-4**

  - Trong `ReturnRequestRepository`: thêm `@Lock(LockMode.PESSIMISTIC_WRITE) Optional<ReturnRequest> findLockedById(UUID id);`.
  - Trong `ReturnRequestService`:
    - Thêm helper `findLockedReturnOrNull(UUID id)` ưu tiên `returns.findLockedById(id)` (fallback `returns.findById(id)` khi mock unit test không stub `findLockedById`) và dùng trong `approve()`, `reject()`, `complete()`.
    - Trong `create()`: dùng `findLockedOrderOrNull(orderId)` để khóa đơn hàng; kiểm tra `if (order.getReceivedAt() != null)` → ném `ResponseStatusException(HttpStatus.CONFLICT, "Cannot create return request after order receipt has been confirmed")`.
  - Trong `RevenueService`:
    - Trong `cancelSettlementForOrder` và `adjustSettlementOnReturn`: cho phép cập nhật khi `settlement.getStatus() == SettlementStatus.HOLDING || settlement.getStatus() == SettlementStatus.RELEASED`.
  - Trong `PaymentTransactionService.flagOrderRefund`:
    - Đổi kiểu trả về sang `boolean`, thêm `@Slf4j`, khi không tìm thấy giao dịch `SUCCESS` của `orderId` thì `log.warn("No successful online payment transaction found to flag refund of {} VND for order {}", refundAmount, orderId)` và trả `false`.
  - Trong `OrderService`, `AdminOrderService`, `AdminOrderController`:
    - Thêm `OrderService.settleOrderRefundByAdmin(UUID adminId, UUID orderId, String note)`: khóa đơn `requireOrder(orderId)`, nếu `order.getPaymentStatus() != PaymentStatus.REFUND_PENDING` → ném `ResponseStatusException(HttpStatus.CONFLICT, "Order is not pending refund")`, gọi `settleOrderRefund(adminId, orderId, note.trim())`, ghi `ConfigAuditService` (`ConfigAuditEntity.PAYMENT_TRANSACTION`, `order.getCode()`, `"ORDER_REFUND"`, `"REFUND_PENDING"`, `"REFUNDED: " + note.trim()`, `adminId`), trả về `requireAdminOrder(orderId)`.
    - Expose qua `AdminOrderService.settleRefund` và `PATCH /api/admin/orders/{id}/refunded` trong `AdminOrderController`.

- [ ] **Step 4: Chạy lại test để xác nhận PASS**

  Run: `./mvnw test -Dtest=ReturnRequestServiceUnitTest,PaymentRefundFlowTest,AdminOrderControllerTest,AdminOrderServiceTest`
  Expected: PASS

---

### Task 4: Khóa chống rút tiền vượt số dư, Unique Index `seller_settlement(order_id)`, và tính doanh thu/ví chuẩn xác (M-3, MINOR-5, MINOR-7)

**Files:**
- Modify: `src/main/resources/db/migration/V49__vietqr_escrow_payment.sql`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/RevenueService.java:61-255`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/SellerSettlementRepository.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderRepository.java:62-83`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/RevenueServiceIntegrationTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/RevenueServiceUnitTest.java`

**Interfaces:**
- Produces:
  - `SellerSettlementRepository.lockReleasedByShopId(UUID shopId): List<UUID>` (`SELECT id FROM seller_settlement WHERE shop_id = :shopId AND status IN ('RELEASED', 'WITHDRAWN') FOR UPDATE`)
  - Unique index `uq_seller_settlement_order` trên `seller_settlement (order_id)`

- [ ] **Step 1: Viết các bài test thất bại cho M-3, MINOR-5, MINOR-7 trong `RevenueServiceIntegrationTest` và `RevenueServiceUnitTest`**

  - Trong `RevenueServiceIntegrationTest`:
    - Thêm test `concurrentCreateWithdrawalCannotExceedAvailableBalance`: shop có `walletBalance = 190_000L`, 2 thread đồng thời gọi `createWithdrawal(..., 150_000L)` qua `CountDownLatch` → đúng 1 thread thành công và 1 thread nhận `400 Bad Request` `"Insufficient available balance"`, tổng lệnh rút `REQUESTED` không vượt quá `190_000L`.
    - Thêm test `totalRevenueAndDailyRevenueSubtractVoucherDiscountAndCompletedReturnRefunds`: đơn `DELIVERED` có `subtotal = 200_000, discountAmount = 40_000` và hoàn trả một phần `50_000` (`COMPLETED`) → `totalRevenue` và `dailyRevenue` ghi nhận `110_000L`.
  - Trong `RevenueServiceUnitTest`:
    - Thêm test `getSummaryExposesNegativeWalletBalanceWhenSettlementCancelledAfterWithdrawal` (hoặc kiểm tra `createWithdrawal` từ chối khi `available < 0`).

- [ ] **Step 2: Chạy test để xác nhận thất bại**

  Run: `./mvnw test -Dtest=RevenueServiceIntegrationTest,RevenueServiceUnitTest`
  Expected: FAIL

- [ ] **Step 3: Triển khai khóa khi rút tiền, Unique Index `seller_settlement(order_id)`, và cập nhật công thức doanh thu/số dư**

  - Trong `V49__vietqr_escrow_payment.sql`: thêm `CREATE UNIQUE INDEX IF NOT EXISTS uq_seller_settlement_order ON seller_settlement (order_id);`.
  - Trong `SellerSettlementRepository`:
    - Thêm `@Query("SELECT id FROM seller_settlement WHERE shop_id = :shopId AND status IN ('RELEASED', 'WITHDRAWN') FOR UPDATE") List<UUID> lockReleasedByShopId(UUID shopId);` (không dùng `FOR UPDATE` trên câu `SUM` vì PostgreSQL không cho phép `FOR UPDATE` với hàm aggregate).
    - Xóa hàm trùng lặp không dùng `sumReleasedBalance`.
  - Trong `RevenueService`:
    - Trong `createSettlementForOrder`: bắt `DataIntegrityViolationException` (nếu 2 luồng chèn trùng `order_id`) để đảm bảo idempotent kết hợp với `uq_seller_settlement_order`.
    - Trong `createWithdrawal`: gọi `settlements.lockReleasedByShopId(request.shopId())` trước khi tính `long available = settlements.sumAvailableBalance(request.shopId()) - withdrawals.sumActiveWithdrawals(request.shopId());`. Không dùng `Math.max(0L, ...)`; nếu `request.amount() <= 0 || request.amount() > available` → ném `ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient available balance")`.
    - Trong `getSummary`: bỏ `Math.max(0L, ...)` quanh `walletBalance` để không che giấu số dư âm khi đối soát.
  - Trong `OrderRepository.sumDeliveredRevenue` và `sumDailyRevenue`:
    - Tính doanh thu hàng hóa ròng của từng đơn `DELIVERED`:
      `GREATEST(0, o.subtotal - o.discount_amount - COALESCE((SELECT SUM(CASE WHEN UPPER(COALESCE(rr.resolution, '')) = 'EXCHANGE' THEN 0 WHEN rr.refund_amount IS NOT NULL AND rr.refund_amount > 0 THEN rr.refund_amount WHEN UPPER(COALESCE(rr.resolution, '')) = 'REFUND' THEN (o.subtotal - o.discount_amount) ELSE 0 END) FROM return_request rr WHERE rr.order_id = o.id AND rr.status = 'COMPLETED'), 0))`

- [ ] **Step 4: Chạy lại test để xác nhận PASS**

  Run: `./mvnw test -Dtest=RevenueServiceIntegrationTest,RevenueServiceUnitTest`
  Expected: PASS

---

### Task 5: Khóa `startsAt` của Voucher đã dùng, chống Double-Submit Cart Checkout, dọn N+1 & Dead Code (MINOR-4, MINOR-6, NIT-1, NIT-2, NIT-5)

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/ShopVoucher.java:188-207`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/voucher/service/VoucherService.java:316-358`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/voucher/repository/ShopVoucherRepository.java:152-229`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/repository/PaymentTransactionRepository.java:105-118`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/cart/repository/CartRepository.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/cart/CartService.java:160-377`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java:2180-2190`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java:165-178`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/controller/OrderController.java:39-55`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderRepository.java:22-55`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentTransactionService.java:83-87,193-207`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/payment/PaymentFlowService.java:40-49`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/voucher/VoucherServiceTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/OrderVoucherIntegrationTest.java`

**Interfaces:**
- Produces:
  - `ShopVoucherRepository.countActiveUsagesByBuyerForVouchers(Collection<UUID> voucherIds, UUID buyerId): Map<UUID, Integer>`
  - `CatalogService.findVariantsByIds(Collection<UUID> variantIds): Map<UUID, ProductVariant>`
  - `CartRepository.findLockedByBuyerId(UUID buyerId): Optional<Cart>` (`@Lock(LockMode.PESSIMISTIC_WRITE)`)

- [ ] **Step 1: Viết các bài test thất bại cho MINOR-4 và MINOR-6**

  - Trong `VoucherServiceTest`:
    - Trong `updateToggleDeleteAndAuditBehaviorWhenUnusedVersusUsed`: kiểm tra khi `usedCount > 0`, gọi `vouchers.update(...)` thay đổi `startsAt` (trong khi giữ nguyên `code`, `discountType`, `discountValue`, `maxDiscountAmount`, `minOrderSubtotal`, `productScope`) bị chặn với `409 Conflict` `"Cannot modify core discount terms of a voucher that has already been used"`.
  - Trong `OrderVoucherIntegrationTest`:
    - Thêm test `concurrentCartCheckoutOnlyCreatesOneOrderSet`: 2 thread gọi `cartService.checkout(...)` đồng thời cho cùng 1 buyer (`CountDownLatch`) → đúng 1 thread tạo đơn thành công, thread còn lại nhận `400 Bad Request` (`"Cart is empty"` hoặc `"Some selected items are not in the cart"`), không sinh 2 đơn hay 2 phiên QR trùng.

- [ ] **Step 2: Chạy test để xác nhận thất bại**

  Run: `./mvnw test -Dtest=VoucherServiceTest,OrderVoucherIntegrationTest`
  Expected: FAIL

- [ ] **Step 3: Triển khai khóa `startsAt`, khóa `Cart` khi checkout, gom batch N+1 và xóa dead code**

  - Trong `ShopVoucher.update`: thêm `|| !this.startsAt.equals(startsAt)` vào `coreChanged` khi `this.usedCount > 0`.
  - Trong `CartRepository` & `CartService.checkout`:
    - Thêm `@Lock(LockMode.PESSIMISTIC_WRITE) Optional<Cart> findLockedByBuyerId(UUID buyerId);` vào `CartRepository`.
    - Trong `CartService.checkout`: khóa giỏ hàng bằng `carts.findLockedByBuyerId(buyerId)` trước khi đọc `items.findByCartIdOrderByCreatedAtAsc(cart.getId())`.
  - Trong `CatalogService` & `CartService.previewCheckout`:
    - Thêm `CatalogService.findVariantsByIds(Collection<UUID> variantIds)` trả `Map<UUID, ProductVariant>` qua `variants.findAllById(variantIds)`.
    - Trong `CartService.previewCheckout`: đọc batch map `variantsById` một lần trước vòng lặp `for (var item : selected)`.
  - Trong `ShopVoucherRepository` & `VoucherService.listPublicForShop`:
    - Thêm `countActiveUsagesByBuyerForVouchers(Collection<UUID> voucherIds, UUID buyerId)` dùng 1 câu `SELECT voucher_id, COUNT(*) AS cnt FROM orders WHERE buyer_id = :buyerId AND voucher_id IN (:voucherIds) AND status <> 'CANCELLED' GROUP BY voucher_id`.
    - Trong `ShopVoucherRepository.replaceProductIds` và `PaymentTransactionRepository.linkOrders`: gộp multi-row `INSERT INTO ... VALUES ... ON CONFLICT DO NOTHING` trong 1 câu SQL thay vì chạy từng dòng trong vòng lặp.
  - Dọn dead code (NIT-1) và cập nhật mô tả OpenAPI (NIT-5):
    - Xóa `PaymentTransactionService.createVietQrSession`, 3 overload `getSessionForViewer` thừa trong `PaymentTransactionService` và 1 overload thừa trong `PaymentFlowService`, `OrderService.checkoutDraftVietQr` và 2-arg `checkoutVietQrDraft`, `OrderRepository.findByShopIdAndPlacedAtIsNotNullOrderByCreatedAtDesc`, `OrderRepository.countByShopIdAndPlacedAtIsNotNull`, và 9-arg `PaymentTransaction.create` (cập nhật `VietQrUrlBuilderTest`).
    - Cập nhật `@Tag` và `@Operation` của `POST /api/orders` trong `OrderController`: hỗ trợ cả `COD` (`PENDING`) và `VIETQR` (`AWAITING_PAYMENT` kèm phiên QR), trừ giữ tồn kho ngay khi đặt.

- [ ] **Step 4: Chạy toàn bộ test suite liên quan để xác nhận PASS**

  Run: `./mvnw test -Dtest=PaymentGatewayServiceTest,PaymentControllerTest,PaymentTransactionServiceTest,PaymentFlowServiceTest,PaymentRefundFlowTest,ReturnRequestServiceUnitTest,RevenueServiceUnitTest,RevenueServiceIntegrationTest,VoucherServiceTest,SellerVoucherControllerTest,OrderVoucherIntegrationTest,OrderServiceTest,OrderControllerTest,AdminPaymentControllerTest,AdminOrderControllerTest,AdminOrderServiceTest,VietQrUrlBuilderTest`
  Expected: PASS
