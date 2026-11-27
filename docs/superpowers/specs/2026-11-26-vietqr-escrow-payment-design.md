# Thiết kế Thanh toán Trực tuyến VietQR, Giữ tiền Trung gian (Escrow) và Hoàn tiền

## 1. Mục đích & Động lực (Intent & Why)

Hệ thống hiện tại chỉ hỗ trợ thanh toán khi nhận hàng (`COD`), tiền tạm giữ của Shop (`SellerSettlement`) đang được tạo ở bước giao hàng (`DELIVERED`) với phí sàn bằng `0₫` nhưng chưa có cơ chế mở khóa sang ví khả dụng (`RELEASED`), và quy trình đổi trả hàng (`ReturnRequest`) chưa cộng lại tồn kho cũng như chưa khấu trừ tiền tạm giữ hay chuyển trạng thái hoàn tiền cho khách.

Thiết kế này bổ sung luồng **Thanh toán trực tuyến qua Mã QR ngân hàng chuẩn NAPAS (VietQR)** kết hợp cơ chế **Sàn giữ tiền trung gian (Escrow)** và **Đối soát hoàn tiền**:

1. **Khi đặt hàng online (`VIETQR`):** Hệ thống tạo đơn nháp ở trạng thái `AWAITING_PAYMENT` (trừ giữ tồn kho trong `order.payment_hold_minutes`, mặc định 15 phút) và **ẩn hoàn toàn với Shop**. Nếu khách thanh toán giỏ hàng có nhiều Shop cùng lúc (`POST /api/cart/checkout`), hệ thống gom tổng tiền tất cả các đơn vừa tạo vào **1 phiên giao dịch VietQR duy nhất** để khách chỉ cần quét QR 1 lần.
2. **Xác nhận đã chuyển tiền & chuyển đủ 100% số tiền:** Hệ thống sinh mã QR VietQR khóa sẵn số tài khoản của sàn, tổng số tiền cần trả và nội dung chuyển khoản là mã giao dịch duy nhất (`txnRef`). Khi ngân hàng báo biến động số dư qua Webhook (hoặc qua nút Giả lập Webhook khi bật chế độ Sandbox để demo), Backend xác thực chữ ký/khóa bí mật, đối chiếu khớp `txnRef` và kiểm tra số tiền thực nhận `receivedAmount >= amount`. Chỉ khi đủ tiền, các đơn trong phiên mới chuyển sang `PENDING` (`paymentStatus = PAID`), hiển thị cho Shop xử lý và tạo bản ghi tạm giữ tiền `SellerSettlement` (`status = HOLDING`).
3. **Sàn giữ tiền (Escrow) cho tới khi khách xác nhận "Đã nhận hàng":** Khoản tiền của đơn hàng được giữ ở trạng thái `HOLDING` trong suốt quá trình Shop chuẩn bị hàng, giao hàng và kể cả khi đã `DELIVERED`. Chỉ khi khách hàng bấm **"Đã nhận hàng"** (`confirmReceipt`) hoặc hệ thống tự động hoàn tất sau `order.auto_complete_days` (7 ngày) không có khiếu nại đổi trả, hệ thống mới **khấu trừ 5% phí sàn** và chuyển 95% doanh thu thực nhận (`netAmount`) từ `HOLDING` sang **`RELEASED`** (cộng vào số dư ví khả dụng của Shop).
4. **Đổi trả hàng & Chờ hoàn tiền (`REFUND_PENDING`):** Khi khách yêu cầu đổi trả ở trạng thái `DELIVERED` (trước khi bấm "Đã nhận hàng"), tiền vẫn đang bị khóa ở `HOLDING`. Chỉ khi Shop bấm **"Xác nhận đã nhận hàng hoàn"** (`ReturnRequest` → `COMPLETED`), hệ thống mới tự động cộng lại tồn kho sản phẩm, hủy/khấu trừ bản ghi `HOLDING` của Shop, và chuyển đơn hàng + giao dịch sang trạng thái **`Chờ hoàn tiền (REFUND_PENDING)`** để Admin kiểm tra và bấm **"Đánh dấu đã hoàn tiền" (`REFUNDED`)**.

---

## 2. Mục tiêu, Ngoài phạm vi & Điều cấm kỵ (Goals, Non-Goals, Anti-Goals)

### Mục tiêu (Goals)
- **Cấu hình cổng thanh toán (`/admin/payments`):** Admin bật/tắt `COD` và `VIETQR`; chọn ngân hàng nhận tiền của sàn từ danh mục NAPAS (`VietQrBanksData`), cấu hình số tài khoản, tên chủ tài khoản, `webhookSecret` (write-only), và chế độ `sandboxMode` phục vụ demo.
- **Kiểm tra giới hạn COD:** Áp dụng cấu hình `order.cod_min_total` và `order.cod_max_total` đã có trong `SystemSettings` vào luồng checkout (`POST /api/cart/checkout` và `POST /api/orders`).
- **Checkout VietQR gộp nhiều đơn & mua lẻ:** Tạo đơn `AWAITING_PAYMENT`, sinh phiên giao dịch `payment_transaction` kèm danh sách đơn liên kết `payment_transaction_order`, trả về thông tin mã VietQR ngay sau khi đặt hàng.
- **Xác thực thanh toán đủ tiền:** Xử lý idempotent với khóa dòng `SELECT ... FOR UPDATE`; phân biệt rõ 3 trường hợp: chuyển đủ tiền (`SUCCESS` → kích hoạt đơn `PAID`), chuyển thiếu tiền (`UNDERPAID` → giữ đơn `AWAITING_PAYMENT`, đánh dấu giao dịch cần hoàn lại số tiền chuyển thiếu), và chuyển tiền muộn sau khi đơn đã hết hạn/hủy (`LATE_PAID` → đơn chuyển `REFUND_PENDING`, đánh dấu giao dịch cần hoàn tiền).
- **Thanh toán lại & Tự động hủy đơn quá hạn:** Cho phép khách tạo phiên VietQR mới cho đơn `AWAITING_PAYMENT` còn hạn (`POST /api/orders/{id}/payments/vietqr`); job chạy mỗi phút (`PaymentExpiryJob`) tự động hủy đơn `AWAITING_PAYMENT` quá hạn (`cancelReason = "PAYMENT_EXPIRED"`, `paymentStatus = FAILED`) và hoàn lại tồn kho.
- **Khép kín vòng đời Escrow & Rút tiền của Shop:**
  - Tính phí sàn 5% (`platformFee = subtotal * 5 / 100`, làm tròn xuống số nguyên VND).
  - Mở khóa `HOLDING` → `RELEASED` ngay tại thời điểm `confirmReceipt` hoặc `systemAutoCompleteOrder`.
  - Tính số dư ví khả dụng của Shop = Tổng `netAmount` của các `SellerSettlement` ở trạng thái `RELEASED` trừ đi tổng `amount` của các lệnh `SellerWithdrawal` không bị `REJECTED` (`REQUESTED`, `PROCESSING`, `COMPLETED`), chống tạo lệnh rút trùng vượt số dư.
  - Admin duyệt hoặc từ chối lệnh rút tiền của Shop trên trang `/admin/payments`.
- **Liên thông Đổi trả hàng (`ReturnRequest`) với Tồn kho, Escrow & Hoàn tiền:**
  - Khi `ReturnRequestService.complete()` được gọi: cộng lại tồn kho cho các mặt hàng của đơn (`restockOrderItems` với lý do `ORDER_RETURN`), cập nhật/hủy `SellerSettlement` đang `HOLDING`, và nếu đơn cần hoàn tiền (`resolution` là `REFUND` hoặc `PARTIAL_REFUND` với `refundAmount > 0`) thì chuyển trạng thái thanh toán của đơn sang `REFUND_PENDING` và tạo/gắn cờ hoàn tiền trên giao dịch để Admin xử lý.

### Ngoài phạm vi (Non-Goals)
- Không thay đổi công thức tính phí vận chuyển phẳng hiện tại (`ShippingFeeService` và `V48__shipping_fee_settings.sql`).
- Không tích hợp API chuyển tiền tự động (Payout/Disbursement API) của ngân hàng khi hoàn tiền cho khách hoặc khi chuyển tiền rút cho Shop; việc chuyển tiền hoàn/rút thực hiện ngoài hệ thống và được Admin bấm xác nhận đối soát trên trang `/admin/payments`.

### Điều cấm kỵ (Anti-Goals)
- **Không bao giờ** chuyển đơn hàng sang `PAID` / `PENDING` khi chữ ký/khóa `webhookSecret` không hợp lệ hoặc khi số tiền thực nhận nhỏ hơn số tiền giao dịch lưu trong Database.
- **Không bao giờ** hiển thị đơn `AWAITING_PAYMENT` (hoặc đơn hủy do hết hạn thanh toán `placed_at IS NULL`) trong danh sách đơn hàng, bộ đếm hay báo cáo doanh thu của Shop.
- **Không bao giờ** cộng tiền vào ví khả dụng (`RELEASED`) của Shop trước khi khách hàng xác nhận đã nhận hàng (hoặc hết 7 ngày tự động hoàn tất), và không để Shop vẫn nhận 100% tiền khi đơn hàng đã trả hàng hoàn tiền.

---

## 3. Các phương án kiến trúc đã cân nhắc (Approaches)

### Phương án 1 (Đã chọn): Phiên giao dịch VietQR gộp nhiều đơn (Multi-Order Transaction) + Liên thông trọn vòng Escrow & Hoàn tiền
- **Mô tả:** Giữ nguyên kiến trúc tách mỗi Shop thành 1 `Order` của `CartService.checkout()`. Một phiên giao dịch `payment_transaction` liên kết với 1 hoặc nhiều `Order` thông qua bảng `payment_transaction_order`. Khi khách đặt giỏ hàng nhiều Shop bằng `VIETQR`, hệ thống sinh 1 mã QR duy nhất với tổng số tiền của tất cả các đơn trong lần đặt đó. Nếu khách chưa thanh toán ngay, sau đó khách có thể vào từng đơn lẻ còn hạn để bấm *"Thanh toán lại"* (tạo 1 giao dịch `payment_transaction` mới chỉ gắn với đơn lẻ đó). Toàn bộ vòng đời tiền từ `PAID` → `HOLDING` → `RELEASED` (trừ 5% phí sàn khi `confirmReceipt`) hoặc `REFUND_PENDING` → `REFUNDED` (khi hủy đơn đã trả tiền / hoàn tất trả hàng) được nối khép kín.
- **Ưu điểm:** Trải nghiệm thanh toán giỏ hàng nhiều Shop liền mạch 1 lần quét QR; hỗ trợ cả thanh toán gộp lúc đầu lẫn thanh toán lại từng đơn lẻ; giải quyết triệt để bài toán giữ tiền và hoàn tiền.
- **Đánh đổi:** Cần thêm bảng liên kết `payment_transaction_order(transaction_id, order_id)`.

### Phương án 2 (Loại bỏ): Mỗi giao dịch chỉ gắn cứng với 1 cột `order_id`
- **Mô tả:** Bảng `payment_transaction` chỉ có 1 khóa ngoại `order_id`. Khi giỏ hàng có sản phẩm từ 2 Shop trở lên, chặn không cho thanh toán online gộp mà bắt khách tick chọn từng Shop một.
- **Lý do loại bỏ:** Phá vỡ trải nghiệm checkout nhiều Shop hiện có của `CartService` và `Checkout.tsx`.

---

## 4. Thiết kế chi tiết (Detailed Design)

### 4.1. Schema Cơ sở dữ liệu (Migration `V49__vietqr_escrow_payment.sql`)

1. **Bảng cấu hình cổng thanh toán `payment_gateway`:**
   ```sql
   CREATE TABLE payment_gateway (
       code TEXT PRIMARY KEY CHECK (code IN ('COD', 'VIETQR')),
       name TEXT NOT NULL,
       enabled BOOLEAN NOT NULL DEFAULT FALSE,
       bank_bin TEXT,
       bank_code TEXT,
       bank_name TEXT,
       account_number TEXT,
       account_name TEXT,
       webhook_secret TEXT,
       sandbox_mode BOOLEAN NOT NULL DEFAULT TRUE,
       updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       updated_by UUID REFERENCES user_profile (id) ON DELETE SET NULL
   );
   ```
   - **Seed mặc định:**
     - `COD`: `name = 'Thanh toán khi nhận hàng (COD)'`, `enabled = TRUE`, `sandbox_mode = FALSE`.
     - `VIETQR`: `name = 'Chuyển khoản ngân hàng (VietQR)'`, `enabled = TRUE`, `bank_bin = '970422'`, `bank_code = 'MB'`, `bank_name = 'Ngân hàng TMCP Quân đội (MB Bank)'`, `account_number = '0900123456789'`, `account_name = 'SAN TMDT FASHIONSTORE'`, `webhook_secret = 'demo-vietqr-secret-key'`, `sandbox_mode = TRUE` (seed sẵn tài khoản mẫu và bật sẵn để chạy demo và test ngay; Admin đổi sang STK thật bất kỳ lúc nào trên UI).

2. **Mở rộng bảng `orders`:**
   - Cập nhật CHECK constraint của `orders.status`:
     `CHECK (status IN ('AWAITING_PAYMENT', 'PENDING', 'CONFIRMED', 'SHIPPING', 'DELIVERED', 'CANCELLED'))`
   - Cập nhật CHECK constraint của `orders.payment_method`:
     `CHECK (payment_method IN ('COD', 'VIETQR'))`
   - Cập nhật CHECK constraint của `orders.payment_status`:
     `CHECK (payment_status IN ('UNPAID', 'PAID', 'COLLECTED', 'FAILED', 'REFUND_PENDING', 'REFUNDED'))`
   - Thêm cột:
     - `payment_expires_at TIMESTAMPTZ` (nullable; chỉ có giá trị khi đặt bằng `VIETQR`).
     - `placed_at TIMESTAMPTZ` (nullable; đơn `COD` gán `placed_at = created_at` ngay lúc tạo; đơn `VIETQR` lúc `AWAITING_PAYMENT` có `placed_at = NULL`, khi thanh toán thành công mới gán `placed_at = now()`).
     - Backfill cho đơn cũ: `UPDATE orders SET placed_at = created_at WHERE placed_at IS NULL AND status <> 'AWAITING_PAYMENT';`
     - Index: `CREATE INDEX idx_orders_awaiting_expiry ON orders (payment_expires_at) WHERE status = 'AWAITING_PAYMENT';`

3. **Bảng giao dịch thanh toán `payment_transaction` và bảng liên kết `payment_transaction_order`:**
   ```sql
   CREATE TABLE payment_transaction (
       id UUID PRIMARY KEY,
       buyer_id UUID NOT NULL REFERENCES user_profile (id),
       provider TEXT NOT NULL DEFAULT 'VIETQR' CHECK (provider IN ('VIETQR')),
       txn_ref TEXT NOT NULL UNIQUE,
       amount BIGINT NOT NULL CHECK (amount >= 0),
       received_amount BIGINT NOT NULL DEFAULT 0 CHECK (received_amount >= 0),
       status TEXT NOT NULL DEFAULT 'PENDING'
           CHECK (status IN ('PENDING', 'SUCCESS', 'UNDERPAID', 'FAILED', 'EXPIRED')),
       bank_bin TEXT NOT NULL,
       bank_code TEXT NOT NULL,
       account_number TEXT NOT NULL,
       account_name TEXT NOT NULL,
       qr_image_url TEXT NOT NULL,
       bank_transaction_id TEXT,
       transfer_content TEXT,
       paid_at TIMESTAMPTZ,
       expires_at TIMESTAMPTZ NOT NULL,
       refund_status TEXT NOT NULL DEFAULT 'NONE'
           CHECK (refund_status IN ('NONE', 'PENDING', 'REFUNDED')),
       refund_amount BIGINT NOT NULL DEFAULT 0 CHECK (refund_amount >= 0),
       refund_reason TEXT,
       refunded_at TIMESTAMPTZ,
       refunded_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
       refund_note TEXT,
       raw_payload TEXT,
       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
   );

   CREATE TABLE payment_transaction_order (
       transaction_id UUID NOT NULL REFERENCES payment_transaction (id) ON DELETE CASCADE,
       order_id UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
       PRIMARY KEY (transaction_id, order_id)
   );
   CREATE INDEX idx_pto_order ON payment_transaction_order (order_id);
   ```

4. **Mở rộng bảng `seller_settlement`:**
   - Cập nhật CHECK constraint của `seller_settlement.status`:
     `CHECK (status IN ('HOLDING', 'RELEASED', 'WITHDRAWN', 'CANCELLED'))`
   - Thêm hằng số phí sàn mặc định: `PLATFORM_FEE_PERCENT = 5` (`platformFee = (grossAmount * 5) / 100`).

---

### 4.2. Cấu hình cổng thanh toán & Phương thức khả dụng

- **Admin API (`/api/admin/payment-gateways`):**
  - `GET /api/admin/payment-gateways` (`operationId = listPaymentGateways`): Trả danh sách cấu hình `COD` và `VIETQR` (`PaymentGatewayResponse`). Trường `webhookSecret` là **write-only** — response chỉ trả `webhookSecretSet: boolean`, tuyệt đối không trả chuỗi secret.
  - `PUT /api/admin/payment-gateways/{code}` (`operationId = updatePaymentGateway`):
    - Nếu cập nhật `VIETQR`: khi `bankBin` được gửi lên, tra `BankService.listBanks()` để đồng bộ `bankCode` và `bankName` chuẩn NAPAS (`400` nếu `bankBin` không tồn tại trong danh sách ngân hàng VietQR).
    - Nếu bật `VIETQR` (`enabled = true`) mà thiếu `bankBin`, `accountNumber`, `accountName`, hoặc chưa có `webhookSecret` → trả `409 Conflict`.
    - Nếu gửi `webhookSecret` là `null` hoặc chuỗi rỗng → giữ nguyên `webhookSecret` cũ.
    - Không cho phép tắt cổng cuối cùng đang bật (phải có ít nhất 1 cổng `enabled = true`, nếu tắt cổng cuối cùng → `409 Conflict`).
    - Ghi lịch sử vào `ConfigAuditService` với `entity = ConfigAuditEntity.PAYMENT_GATEWAY` (che `webhookSecret` thành `"****"`).
- **Public API (`GET /api/public/payment-methods`, `operationId = listPaymentMethods`):**
  - Trả về danh sách các cổng đang `enabled = true`, kèm `codMinTotal`, `codMaxTotal`, `paymentHoldMinutes` từ `SystemSettings`, và nếu `VIETQR` đang bật thì kèm thông tin hiển thị ngắn (`bankCode`, `bankName`, `sandboxMode`).

---

### 4.3. Luồng Checkout (`COD` & `VIETQR`) và Sinh Mã VietQR

1. **Request & Response:**
   - `CartCheckoutRequest` (`POST /api/cart/checkout`) và `CheckoutRequest` (`POST /api/orders`):
     - Thêm trường tùy chọn `@Nullable PaymentMethod paymentMethod` (`COD` | `VIETQR`). Nếu client không truyền (`null`), mặc định là `COD` để tương thích ngược với toàn bộ test cũ.
   - Kiểm tra trước khi đặt đơn (`PaymentGatewayService.requireAvailable(method, totalAmount)`):
     - Cổng được chọn phải đang `enabled = true` (nếu tắt → `409 Conflict` `"Payment method is disabled"`).
     - Nếu `method == COD`: kiểm tra tổng tiền thanh toán (`totalAmount` — với giỏ hàng là tổng tiền các đơn trong lần checkout, với mua lẻ là `total` của đơn) nằm trong khoảng `[codMinTotal, codMaxTotal]` (khi `codMaxTotal > 0`). Nếu nằm ngoài khoảng → `409 Conflict` `"Order total is outside COD limits"`.
2. **Tạo đơn hàng (`OrderService.checkout`):**
   - **Nếu `paymentMethod == COD`:**
     - Tạo đơn với `status = OrderStatus.PENDING`, `paymentMethod = "COD"`, `paymentStatus = PaymentStatus.UNPAID`, `placedAt = now`, `paymentExpiresAt = null`.
     - Trừ tồn kho ngay (`ORDER_CREATED`), ghi `OrderStatusHistory(null -> PENDING)`, gửi thông báo `ORDER_PLACED` cho Shop.
   - **Nếu `paymentMethod == VIETQR`:**
     - Tính `expiresAt = clock.instant().plus(systemSettings.current().paymentHoldMinutes(), ChronoUnit.MINUTES)`.
     - Tạo đơn với `status = OrderStatus.AWAITING_PAYMENT`, `paymentMethod = "VIETQR"`, `paymentStatus = PaymentStatus.UNPAID`, `placedAt = null`, `paymentExpiresAt = expiresAt`.
     - Trừ giữ tồn kho ngay (`ORDER_CREATED`), ghi `OrderStatusHistory(null -> AWAITING_PAYMENT)`.
     - **Chưa gửi thông báo `ORDER_PLACED` cho Shop** và **chưa tạo `SellerSettlement`**.
3. **Khởi tạo phiên giao dịch VietQR (`PaymentTransactionService.createVietQrSession`):**
   - Khi checkout bằng `VIETQR` (từ `CartService.checkout` với danh sách $N$ đơn vừa tạo, hoặc từ `OrderService.checkout` mua lẻ 1 đơn):
     - Sinh mã `txnRef`: `"DHMQ" + 10 ký tự chữ hoa/số ngẫu nhiên` (ví dụ `DHMQ4F9A1C8E2B`, chỉ gồm `A-Z0-9` để mọi ngân hàng giữ nguyên trong nội dung chuyển khoản).
     - Tính `totalAmount = sum(order.getTotal())` của các đơn trong phiên.
     - Dựng URL ảnh VietQR chuẩn:
       `https://img.vietqr.io/image/{bankBin}-{accountNumber}-compact2.png?amount={totalAmount}&addInfo={txnRef}&accountName={urlEncodedAccountName}`
     - Lưu 1 bản ghi `payment_transaction` (`status = PENDING`, `amount = totalAmount`, `expiresAt`) và $N$ dòng vào `payment_transaction_order`.
     - Gắn thông tin phiên thanh toán (`paymentTxnRef`, `paymentQrUrl`, `paymentExpiresAt`) vào mỗi `OrderResponse` trả về để UI chuyển thẳng tới trang quét mã QR `/payment/qr/{txnRef}`.

---

### 4.4. Xác nhận Thanh toán VietQR (Webhook Thật & Giả lập Sandbox)

1. **Tra cứu thông tin phiên thanh toán QR (`GET /api/payments/vietqr/{txnRef}`, `operationId = getVietQrSession`):**
   - Yêu cầu đăng nhập (`bearerAuth`); chỉ `buyerId` sở hữu giao dịch hoặc `ADMIN` mới xem được (`404` nếu không tồn tại hoặc không thuộc buyer).
   - Trả về `VietQrSessionResponse`: `txnRef`, `status` (`PENDING`, `SUCCESS`, `UNDERPAID`, `FAILED`, `EXPIRED`), `amount`, `receivedAmount`, `bankBin`, `bankCode`, `bankName`, `accountNumber`, `accountName`, `qrImageUrl`, `expiresAt`, `paidAt`, `sandboxMode`, và danh sách các đơn hàng thuộc phiên (`List<VietQrOrderBrief>` gồm `orderId`, `orderCode`, `shopName`, `total`, `orderStatus`, `paymentStatus`).

2. **Hàm xử lý kết quả chuyển khoản dùng chung (`PaymentFlowService.processIncomingTransfer`):**
   - Nhận đầu vào: `String transferContent, long transferAmount, @Nullable String bankTransactionId, String rawPayload`.
   - Tìm mã `txnRef` khớp biểu thức `DHMQ[A-Z0-9]{10}` (không phân biệt hoa thường, chuẩn hóa về in hoa) bên trong `transferContent`. Nếu không tìm thấy hoặc không có trong DB → trả kết quả `NOT_FOUND`.
   - Khóa dòng `payment_transaction` bằng `SELECT ... FOR UPDATE`:
     - **Idempotency:** Nếu `transaction.status == SUCCESS` → trả về `ALREADY_PROCESSED` (không xử lý lại).
     - **Kiểm tra số tiền thực nhận (`transferAmount`):**
       - **Trường hợp 1 — Chuyển thiếu tiền (`transferAmount < transaction.amount`):**
         - Cập nhật giao dịch: `status = UNDERPAID`, `receivedAmount = transferAmount`, `bankTransactionId`, `transferContent`, `rawPayload`, `refundStatus = PENDING`, `refundAmount = transferAmount`, `refundReason = "Khách chuyển thiếu tiền (" + transferAmount + "/" + transaction.amount + "₫)"`.
         - **Không kích hoạt đơn hàng** (các đơn vẫn ở `AWAITING_PAYMENT` để khách thanh toán lại đủ hoặc hết hạn).
         - Trả về `UNDERPAID`.
       - **Trường hợp 2 — Chuyển đủ (hoặc dư) tiền (`transferAmount >= transaction.amount`):**
         - Cập nhật giao dịch: `status = SUCCESS`, `receivedAmount = transferAmount`, `paidAt = now`, `bankTransactionId`, `transferContent`, `rawPayload`.
         - Nếu khách chuyển dư (`transferAmount > transaction.amount`): phần dư `overpaid = transferAmount - transaction.amount` được ghi nhận `refundStatus = PENDING`, `refundAmount = overpaid`, `refundReason = "Khách chuyển dư tiền"`.
         - Duyệt từng `orderId` của giao dịch (khóa dòng `orders` `FOR UPDATE`):
           - **Nếu đơn đang ở `AWAITING_PAYMENT`:**
             - Chuyển đơn sang `status = OrderStatus.PENDING`, `paymentStatus = PaymentStatus.PAID`, `placedAt = now`.
             - Ghi `OrderStatusHistory(AWAITING_PAYMENT -> PENDING, buyerId, "Đã thanh toán qua VietQR (" + txnRef + ")")`.
             - Tạo ngay bản ghi `SellerSettlement` ở trạng thái **`HOLDING`** (`grossAmount = order.getSubtotal()`, `platformFee = (order.getSubtotal() * 5) / 100`, `netAmount = grossAmount - platformFee`).
             - Bắn thông báo `ORDER_PLACED` cho Shop và thông báo `PAYMENT_SUCCESS` cho Buyer.
           - **Nếu đơn đã bị `CANCELLED` trước đó (trả tiền muộn sau khi hết hạn 15 phút hoặc sau khi khách vừa bấm hủy):**
             - Giữ `order.status = CANCELLED`, chuyển `order.paymentStatus = PaymentStatus.REFUND_PENDING`.
             - Đánh dấu giao dịch `refundStatus = PENDING`, cộng thêm `order.getTotal()` vào `refundAmount`, `refundReason = "Thanh toán đến muộn sau khi đơn đã hủy"`.
           - **Nếu đơn đã `PAID` bởi một phiên giao dịch khác (thanh toán trùng 2 lần):**
             - Đánh dấu giao dịch hiện tại `refundStatus = PENDING`, cộng thêm `order.getTotal()` vào `refundAmount`, `refundReason = "Thanh toán trùng lặp"`.

3. **Hai cổng kích hoạt hàm `processIncomingTransfer`:**
   - **Webhook chuẩn SePay / Casso (`POST /api/public/payments/vietqr/webhook`, `operationId = vietQrWebhook`):**
     - Xác thực header `Authorization`: hỗ trợ định dạng `Apikey <webhookSecret>` hoặc `Bearer <webhookSecret>` (so sánh constant-time `MessageDigest.isEqual` với `webhookSecret` của cổng `VIETQR`). Sai hoặc thiếu → `401 Unauthorized`.
     - Đọc JSON payload (`content` / `description`, `transferAmount` / `amount`, `id` / `referenceCode`, `transferType`): nếu `transferType` khác `"in"` (giao dịch tiền ra) thì bỏ qua trả `200 OK`. Gọi `processIncomingTransfer`.
   - **API Giả lập Sandbox cho Demo (`POST /api/payments/vietqr/{txnRef}/simulate`, `operationId = simulateVietQrTransfer`):**
     - Yêu cầu đăng nhập (chủ giao dịch hoặc Admin).
     - Kiểm tra cổng `VIETQR` đang có `sandboxMode == true` (`409 Conflict` nếu `sandboxMode == false`).
     - Body `SimulateVietQrRequest(@Nullable Long amount)`: nếu `amount == null` thì mặc định truyền đúng `transaction.getAmount()` (giả lập chuyển đủ 100%); nếu truyền số tiền nhỏ hơn (ví dụ để demo kịch bản chuyển thiếu tiền) thì truyền số tiền đó vào `processIncomingTransfer`.

4. **Thanh toán lại & Tự động hủy đơn quá hạn:**
   - **Thanh toán lại (`POST /api/orders/{id}/payments/vietqr`, `operationId = retryVietQrPayment`):**
     - Chỉ áp dụng cho đơn thuộc buyer đang ở trạng thái `AWAITING_PAYMENT`.
     - Nếu `paymentExpiresAt` đã quá hạn → tự động hủy đơn ngay và trả `409 Conflict` `"Payment time has expired"`.
     - Nếu còn hạn: nếu đơn đã có phiên `payment_transaction` đang `PENDING` và còn hạn thì trả lại mã `txnRef` đó; nếu phiên trước đã `UNDERPAID` / `FAILED` thì tạo phiên `payment_transaction` mới cho riêng đơn này và trả về `VietQrSessionResponse`.
   - **Job hủy đơn quá hạn thanh toán (`PaymentExpiryJob`):**
     - Chạy định kỳ mỗi 60 giây (`@Scheduled(fixedDelay = 60_000)`): tìm các đơn `status = 'AWAITING_PAYMENT'` có `payment_expires_at <= clock.instant()`.
     - Mỗi đơn hủy trong 1 transaction riêng: chuyển `status = CANCELLED`, `paymentStatus = FAILED`, `cancelledBy = SYSTEM`, `cancelReason = "PAYMENT_EXPIRED"`, hoàn lại tồn kho (`restockOrderItems`), ghi `OrderStatusHistory(AWAITING_PAYMENT -> CANCELLED, null, "Hết thời hạn thanh toán")`, đánh dấu các giao dịch `PENDING` của đơn sang `EXPIRED`, gửi thông báo cho người mua.

---

### 4.5. Ẩn đơn `AWAITING_PAYMENT` với Shop & Cập nhật Thống kê

- **Phía Shop (Seller):**
  - Mọi truy vấn đơn hàng và thống kê của Shop (`OrderRepository.findByShopIdOrderByCreatedAtDesc`, `countByShopId`, `ShopStatsRepository.*`) lọc thêm điều kiện **`placed_at IS NOT NULL`** (tức đơn đã đặt COD hoặc đã thanh toán VietQR thành công).
  - Nhờ đó, đơn `AWAITING_PAYMENT` đang chờ khách quét QR (và đơn bị hủy do hết hạn thanh toán `placed_at IS NULL`) **hoàn toàn không xuất hiện** bên phía Shop và không làm sai lệch tỷ lệ hủy đơn của Shop.
- **Phía Admin (`OrderAdminQueryRepository` & `OrderMonitoringQueryRepository`):**
  - `revenueTotals()`: `gmv` chỉ tính các đơn có `placed_at IS NOT NULL AND status <> 'CANCELLED'`; `collected` tính các đơn có `payment_status IN ('COLLECTED', 'PAID')`.
  - Màn giám sát đơn của Admin (`/admin/orders`) vẫn xem được toàn bộ đơn (kể cả `AWAITING_PAYMENT` nếu lọc).

---

### 4.6. Cơ chế Giữ tiền (Escrow), Thu phí sàn 5% và Giải ngân cho Shop

1. **Khi nào tạo bản ghi tạm giữ `SellerSettlement` (`HOLDING`)?**
   - **Với đơn `VIETQR`:** Tạo ngay khi khách thanh toán thành công (`AWAITING_PAYMENT` → `PENDING`, `paymentStatus = PAID`).
   - **Với đơn `COD`:** Tạo khi đơn giao thành công (`SHIPPING` → `DELIVERED`, `paymentStatus = COLLECTED`).
   - Công thức tính khi tạo `SellerSettlement`:
     - `grossAmount = order.getSubtotal()` (tiền hàng của Shop, không bao gồm phí vận chuyển của sàn).
     - `platformFee = (grossAmount * 5) / 100` (5% phí sàn).
     - `netAmount = grossAmount - platformFee` (95% thực nhận của Shop).
     - `status = SettlementStatus.HOLDING`.
2. **Khi nào mở khóa tiền từ `HOLDING` sang `RELEASED` (Cộng vào Ví khả dụng của Shop)?**
   - Ngay tại thời điểm **Khách hàng bấm "Đã nhận hàng"** (`OrderService.confirmReceipt`) hoặc **Job tự động hoàn tất đơn sau 7 ngày** (`OrderService.systemAutoCompleteOrder`):
     - Hệ thống gọi `revenue.releaseSettlementForOrder(order.getId())`: tìm bản ghi `SellerSettlement` của đơn đang ở trạng thái `HOLDING`, gọi `settlement.release()` (`status = RELEASED`, `releasedAt = now`) và lưu vào DB.
3. **Tính số dư Ví khả dụng & Rút tiền về ngân hàng (`SellerWithdrawal`):**
   - Trong `SellerSettlementRepository`:
     - `sumHoldingBalance(shopId)`: tổng `net_amount` của các dòng `status = 'HOLDING'`.
     - `sumReleasedTotal(shopId)`: tổng `net_amount` của các dòng `status IN ('RELEASED', 'WITHDRAWN')`.
   - Trong `SellerWithdrawalRepository`:
     - `sumActiveWithdrawals(shopId)`: tổng `amount` của các lệnh rút có `status IN ('REQUESTED', 'PROCESSING', 'COMPLETED')`.
   - **Số dư ví khả dụng thực tế (`walletBalance`):**
     $$\text{walletBalance} = \max(0, \text{sumReleasedTotal(shopId)} - \text{sumActiveWithdrawals(shopId)})$$
   - Khi Shop tạo lệnh rút tiền (`POST /api/seller/withdrawals`), số tiền rút phải $\le \text{walletBalance}$. Ngay sau khi tạo lệnh rút (`status = REQUESTED`), `sumActiveWithdrawals` tăng lên nên **`walletBalance` giảm xuống tương ứng ngay lập tức**, chặn việc rút trùng.
   - **Admin duyệt/từ chối lệnh rút tiền của Shop:**
     - `GET /api/admin/payments/withdrawals?status=&page=&size=` (`operationId = listAdminWithdrawals`): Danh sách lệnh rút tiền của các Shop.
     - `PATCH /api/admin/payments/withdrawals/{id}/approve` (`operationId = approveWithdrawal`): Chuyển lệnh rút từ `REQUESTED` → `COMPLETED` (`processedAt = now`), ghi `ConfigAuditService`.
     - `PATCH /api/admin/payments/withdrawals/{id}/reject` (`operationId = rejectWithdrawal`): Chuyển lệnh rút từ `REQUESTED` → `REJECTED` (`processedAt = now`) → số tiền tự động hoàn trả lại vào `walletBalance` của Shop.

---

### 4.7. Luồng Hủy đơn đã thanh toán & Đổi trả hàng → Chờ hoàn tiền (`REFUND_PENDING`)

1. **Trường hợp 1 — Đơn `VIETQR` đã thanh toán (`PAID`) bị hủy trước khi giao hàng:**
   - Khi Buyer hủy đơn ở trạng thái `PENDING`, hoặc Seller/Admin hủy đơn ở trạng thái `PENDING`/`CONFIRMED`, hoặc `OrderAutoCancelJob` tự hủy sau 24h Shop không xác nhận:
     - Hoàn lại tồn kho sản phẩm (`restockOrderItems`).
     - Chuyển `order.paymentStatus` từ `PAID` sang **`REFUND_PENDING` (Chờ hoàn tiền)**.
     - Hủy bản ghi `SellerSettlement` đang `HOLDING` của đơn đó (`status = SettlementStatus.CANCELLED`).
     - Đánh dấu bản ghi `payment_transaction` thành công của đơn sang `refundStatus = PENDING`, cộng `order.getTotal()` vào `refundAmount`, `refundReason = "Đơn hàng #" + order.getCode() + " bị hủy sau khi đã thanh toán"`.

2. **Trường hợp 2 — Khách yêu cầu Đổi/Trả hàng ở trạng thái `DELIVERED` và Shop xác nhận đã nhận lại hàng hoàn (`ReturnRequestService.complete`):**
   - Khi khách tạo yêu cầu đổi trả (`REQUESTED`) và Shop phê duyệt (`APPROVED`): đơn hàng bị khóa nút *"Đã nhận hàng"* nên bản ghi `SellerSettlement` của đơn **chắc chắn vẫn đang ở trạng thái `HOLDING`**.
   - Khi Shop bấm **"Xác nhận đã nhận hàng hoàn"** (`PATCH /api/seller/returns/{id}/complete` → `ReturnRequestService.complete`):
     1. **Cộng lại tồn kho sản phẩm:** Nếu phương án giải quyết (`resolution`) là `REFUND` (trả hàng hoàn tiền 100%) hoặc `EXCHANGE` (đổi hàng), gọi `productStock.incrementVariantStock` + `recordStockMovement(..., "ORDER_RETURN", order.getId())` cho các mặt hàng của đơn.
     2. **Xử lý khoản tạm giữ `SellerSettlement` (`HOLDING`) của Shop:**
        - Nếu `resolution == "REFUND"` (hoặc `refundAmount >= order.getSubtotal()`): chuyển `SellerSettlement` sang `status = CANCELLED` (Shop không nhận doanh thu đơn này).
        - Nếu `resolution == "PARTIAL_REFUND"` (hoặc `0 < refundAmount < order.getSubtotal()`): cập nhật `grossAmount = order.getSubtotal() - refundAmount`, tính lại `platformFee = (grossAmount * 5) / 100`, `netAmount = grossAmount - platformFee`, và chuyển trạng thái sang `RELEASED` (giải ngân phần tiền còn lại cho Shop).
        - Nếu `resolution == "EXCHANGE"` (`refundAmount == 0`): chuyển `SellerSettlement` sang `RELEASED` (Shop đã đổi hàng mới cho khách nên nhận doanh thu đơn).
     3. **Chuyển trạng thái Chờ hoàn tiền cho khách (`REFUND_PENDING`):**
        - Nếu yêu cầu đổi trả có `refundAmount > 0`:
          - Chuyển `order.paymentStatus = PaymentStatus.REFUND_PENDING` và cập nhật `order.receivedAt = now` (kết thúc vòng đời đơn).
          - Nếu đơn thanh toán bằng `VIETQR`: tìm `payment_transaction` thành công của đơn, đặt `refundStatus = PENDING`, cộng `refundAmount` vào `transaction.refundAmount`, `refundReason = "Hoàn tiền trả hàng đơn #" + order.getCode()`.

3. **Bước cuối — Admin xác nhận Đã hoàn tiền trên trang Quản trị (`/admin/payments`):**
   - `GET /api/admin/payments/transactions?status=&refundStatus=&q=&page=&size=` (`operationId = listPaymentTransactions`): Admin lọc theo `refundStatus = PENDING` (tab **"Cần hoàn tiền"**) để thấy mọi giao dịch cần hoàn tiền kèm số tiền hoàn `refundAmount` và lý do `refundReason`.
   - `PATCH /api/admin/payments/transactions/{id}/refunded` (`operationId = markTransactionRefunded`, body `MarkRefundedRequest(String note)` dài 5–500 ký tự):
     - Khóa dòng `payment_transaction` `FOR UPDATE` (nếu `refundStatus != PENDING` → `409 Conflict`).
     - Chuyển `transaction.refundStatus = REFUNDED`, `refundedAt = now`, `refundedBy = adminId`, `refundNote = note`.
     - Với mọi đơn thuộc giao dịch đó đang ở trạng thái `paymentStatus == REFUND_PENDING`: chuyển `order.paymentStatus = PaymentStatus.REFUNDED`, ghi `OrderStatusHistory` và gửi thông báo `ORDER_REFUNDED` cho khách hàng.
     - Ghi lịch sử vào `ConfigAuditService` (`entity = PAYMENT_TRANSACTION`).

---

### 4.8. Thiết kế Giao diện (`fashionstore-ui`)

1. **Trang Thanh toán (`/checkout` — `Checkout.tsx`):**
   - Gọi `GET /api/public/payment-methods` để hiển thị danh sách phương thức thanh toán dưới dạng radio cards:
     - **Thanh toán khi nhận hàng (COD):** Nếu tổng tiền đơn nằm ngoài `[codMinTotal, codMaxTotal]`, disable lựa chọn COD và hiển thị lý do (ví dụ: *"Đơn hàng trên 10.000.000 ₫ không hỗ trợ thanh toán COD"*).
     - **Chuyển khoản ngân hàng (VietQR):** Hiển thị tên ngân hàng nhận tiền của sàn và mô tả *"Quét mã QR bằng ứng dụng ngân hàng — Tự động xác nhận"*.
   - Nút đặt hàng đổi nhãn động: `"Đặt Hàng (COD)"` hoặc `"Đặt Hàng & Quét Mã QR"`.
   - Khi đặt bằng `VIETQR` thành công: lấy `paymentTxnRef` từ đơn trả về và điều hướng sang **`/payment/qr/:txnRef`**.

2. **Trang Quét Mã QR Thanh toán (`/payment/qr/:txnRef` — `VietQrPayment.tsx`):**
   - Hiển thị khung thẻ QR trung tâm gồm:
     - **Ảnh mã VietQR** (`qrImageUrl`) rõ nét, có nút tải ảnh QR.
     - Thông tin chuyển khoản thủ công (Ngân hàng, Chủ tài khoản, Số tài khoản có nút Sao chép, Số tiền cần chuyển có nút Sao chép, Nội dung chuyển khoản `txnRef` tô nổi bật có nút Sao chép).
     - Đồng hồ đếm ngược thời gian giữ đơn (`expiresAt`, mặc định 15 phút) và danh sách các đơn hàng (theo từng Shop) nằm trong phiên thanh toán này.
   - **Tự động cập nhật trạng thái:** Poll `GET /api/payments/vietqr/{txnRef}` mỗi 3 giây khi đang `PENDING`. Ngay khi chuyển sang `SUCCESS`, hiển thị màn hình chúc mừng **"Thanh toán thành công!"** kèm nút xem danh sách đơn hàng. Nếu `UNDERPAID`, hiển thị cảnh báo số tiền đã nhận bị thiếu và hướng dẫn liên hệ hỗ trợ/hoàn tiền.
   - **Khối "Công cụ Demo Sandbox" (chỉ hiển thị khi `sandboxMode === true`):**
     - Nút **"Giả lập ngân hàng báo đã nhận đủ tiền"** (`POST /api/payments/vietqr/{txnRef}/simulate` với `amount = session.amount`) → sau 1 giây màn hình tự động nhảy sang *"Thanh toán thành công!"*.
     - Nút **"Giả lập chuyển thiếu tiền (50%)"** (`amount = Math.floor(session.amount / 2)`) → để demo cơ chế bảo mật phát hiện chuyển thiếu tiền.

3. **Trang Đơn hàng của tôi (`/account/orders` & `/account/orders/:id`):**
   - Bổ sung tab lọc **`Chờ thanh toán` (`AWAITING_PAYMENT`)** trên trang danh sách đơn.
   - Hiển thị nhãn trạng thái thanh toán: `Chưa thanh toán` (`UNPAID`), `Đã thanh toán` (`PAID`), `Đã thu tiền COD` (`COLLECTED`), `Thanh toán thất bại` (`FAILED`), `Chờ hoàn tiền` (`REFUND_PENDING`), `Đã hoàn tiền` (`REFUNDED`).
   - Với đơn đang `AWAITING_PAYMENT` và còn hạn: hiển thị nút **"Thanh toán ngay (Mã QR)"** (gọi `retryVietQrPayment` rồi mở `/payment/qr/:txnRef`) và nút **"Hủy đơn hàng"**.

4. **Trang Quản trị Thanh toán (`/admin/payments` — `AdminPayments.tsx`):**
   - Thêm mục **"Thanh toán"** (`/admin/payments`, icon `CreditCard`) vào thanh điều hướng `AdminShell.tsx`.
   - Gồm 3 tab:
     1. **Cổng thanh toán:** Thẻ bật/tắt `COD` + Thẻ cấu hình `VIETQR` (chọn ngân hàng từ dropdown VietQR, nhập STK, tên chủ TK, Webhook Secret che mật khẩu, công tắc bật/tắt **Chế độ Demo Sandbox**, xem trước ảnh QR mẫu).
     2. **Giao dịch & Hoàn tiền:** Bảng đối soát tất cả giao dịch `payment_transaction`, lọc theo trạng thái (`Tất cả`, `Thành công`, `Chuyển thiếu`, `Đang chờ`, `Hết hạn`) và nút lọc nhanh **"Cần hoàn tiền"** (`refundStatus = PENDING`), tìm kiếm theo mã `txnRef` hoặc mã đơn. Với dòng có `refundStatus === 'PENDING'`, hiển thị số tiền cần hoàn, lý do và nút **"Đánh dấu đã hoàn tiền"** (mở modal nhập ghi chú đối soát 5–500 ký tự).
     3. **Yêu cầu rút tiền Shop:** Bảng danh sách lệnh rút tiền (`SellerWithdrawal`) của các Shop với bộ lọc trạng thái (`REQUESTED`, `COMPLETED`, `REJECTED`) và nút **"Duyệt đã chuyển khoản"** / **"Từ chối"**.

---

## 5. Phần để người thực thi tự quyết định (Left to the Builder)

- Cách chia nhỏ các component con trong `AdminPayments.tsx` và `VietQrPayment.tsx` theo hệ thống design token hiện có (`PANEL_CLASS`, `INPUT_CLASS`, `badgeToneClass`, Phosphor Icons).
- Thứ tự các cột phụ trong bảng đối soát giao dịch trên UI miễn hiển thị đầy đủ các trường trong thiết kế.
