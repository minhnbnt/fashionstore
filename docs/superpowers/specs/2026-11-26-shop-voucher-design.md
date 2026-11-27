# Thiết kế Hệ thống Voucher Giảm giá của Gian hàng (Shop Voucher)

## 1. Mục đích & Động lực (Intent & Why)

Hệ thống sàn TMĐT `fashionstore` hiện tại mới chỉ hỗ trợ hiển thị giá gốc gạch ngang (`compare_at_price`) trên từng biến thể sản phẩm, chưa có cơ chế phát hành và áp dụng **Mã giảm giá (Voucher/Coupon)** khi mua sắm.

Thiết kế này bổ sung luồng **Quản lý và Áp dụng Voucher Giảm giá của Gian hàng (Shop Voucher)** khép kín từ Kênh Người Bán đến Trang Gian hàng, Trang Chi tiết Sản phẩm, Giỏ hàng / Thanh toán và Đối soát Ví Shop:

1. **Chủ shop & Nhân viên tạo mã giảm giá tại Kênh Người Bán (`/shop?tab=vouchers`):** Hỗ trợ 2 hình thức giảm giá phổ biến nhất là **Giảm theo số tiền cố định (VND)** và **Giảm theo phần trăm (%) có trần giảm tối đa (`maxDiscountAmount`)**, kết hợp điều kiện **Đơn hàng tối thiểu (`minOrderSubtotal`)**, **Phạm vi sản phẩm** (*Toàn gian hàng* hoặc *Sản phẩm chỉ định*), **Chế độ hiển thị** (*Công khai* trên Storefront/PDP/Checkout hoặc *Mã ẩn* chỉ ai nhập đúng mã mới dùng được), **Tổng lượt sử dụng (`usageLimit`)**, **Giới hạn lượt dùng mỗi khách (`perBuyerLimit`)**, và **Khung thời gian hiệu lực (`startsAt` – `endsAt`)**.
2. **Hiển thị & Áp dụng theo từng đơn Shop khi Checkout:** Khớp hoàn toàn với kiến trúc tách mỗi Shop thành 1 `Order` của `CartService`: tại trang Thanh toán (`/checkout`), dưới mỗi khối Shop, người mua có thể bấm chọn từ danh sách Voucher Công khai của Shop đó hoặc nhập mã ẩn. Hệ thống tính toán trực tiếp trên `POST /api/cart/checkout/preview` để hiển thị số tiền giảm của từng Shop và tổng thanh toán cuối cùng.
3. **Giữ lượt dùng nguyên tử & Tự động hoàn lượt khi đơn hủy:** Khi đặt hàng (`POST /api/cart/checkout` hoặc `POST /api/orders`), hệ thống khóa dòng voucher (`SELECT ... FOR UPDATE`), kiểm tra điều kiện và tăng `used_count` nguyên tử trong cùng transaction với lệnh trừ tồn kho. Nếu đơn bị hủy ở bất kỳ luồng nào (Buyer hủy, Seller hủy, Admin hủy, quá hạn 24h xác nhận, hoặc quá hạn 15 phút thanh toán VietQR), hệ thống tự động hoàn lại lượt dùng voucher.
4. **Liên thông Tài chính, VietQR, Escrow 5% & Hoàn tiền Đổi trả:** Do voucher do Shop phát hành và tài trợ, doanh thu tính quyết toán ví Shop (`SellerSettlement.grossAmount`) và trần hoàn tiền khi đổi trả (`ReturnRequestService`) được tính trên doanh thu hàng hóa sau giảm (`subtotal - discountAmount`), đảm bảo số liệu kế toán chính xác tuyệt đối.

---

## 2. Mục tiêu, Ngoài phạm vi & Điều cấm kỵ (Goals, Non-Goals, Anti-Goals)

### Mục tiêu (Goals)
- **Phân quyền & Quản lý Voucher tại Kênh Người Bán:** Bổ sung quyền `VOUCHER_READ` và `VOUCHER_WRITE` vào `ShopPermission`; cung cấp đầy đủ API và giao diện Tạo, Xem, Sửa, Bật/Tắt nhanh, Xóa, Lọc theo trạng thái (`ONGOING`, `UPCOMING`, `PAUSED`, `DEPLETED`, `EXPIRED`) kèm ghi nhật ký `ShopAuditService`.
- **Bảo vệ toàn vẹn lịch sử khi sửa/xóa:** Voucher chưa có đơn nào dùng (`usedCount == 0`) được sửa toàn bộ hoặc xóa hẳn; voucher đã có lượt dùng (`usedCount > 0`) không cho xóa cứng và khóa các trường cốt lõi (`code`, `discountType`, `discountValue`, `maxDiscountAmount`, `minOrderSubtotal`, `productScope`), chỉ cho phép điều chỉnh tên, mô tả, chế độ hiển thị, tăng/giảm `usageLimit >= usedCount`, `perBuyerLimit`, `endsAt` hoặc tạm dừng (`active = false`).
- **Hiển thị Voucher trên Storefront & PDP:** Cung cấp endpoint public `GET /api/shops/{shopId}/vouchers` trả danh sách voucher `PUBLIC` đang có hiệu lực (hoặc sắp diễn ra) của Shop kèm trạng thái người mua hiện tại còn lượt dùng hay không.
- **Xem trước & Đặt hàng có Voucher:** Mở rộng `POST /api/cart/checkout/preview`, `POST /api/cart/checkout` và `POST /api/orders` để nhận mã voucher theo từng Shop (`shopVouchers: Map<UUID, String>` hoặc `voucherCode`), trả về số tiền giảm `discountAmount` và lưu snapshot (`voucher_id`, `voucher_code`, `discount_amount`) vào bảng `orders`.
- **Liên thông Escrow & Hoàn tiền:** Cập nhật `OrderService` và `ReturnRequestService` để `SellerSettlement` tính trên `subtotal - discountAmount`, kiểm tra hạn mức COD và tạo phiên VietQR trên `total = subtotal + shippingFee - discountAmount`.

### Ngoài phạm vi (Non-Goals)
- Không làm Voucher toàn sàn do Admin tài trợ (Platform Voucher) hay Voucher miễn phí vận chuyển (Freeship Voucher) trong phạm vi thiết kế này.
- Không áp dụng cộng dồn nhiều mã voucher của cùng một Shop trên cùng một đơn hàng (mỗi đơn Shop áp dụng tối đa 1 mã voucher của Shop đó).

### Điều cấm kỵ (Anti-Goals)
- **Không bao giờ** cho phép `discountAmount > eligibleSubtotal` hoặc làm `order.total < 0`.
- **Không bao giờ** để xảy ra race condition vượt quá `usageLimit` hoặc vượt quá `perBuyerLimit` khi nhiều request đặt hàng đồng thời.
- **Không bao giờ** tính `SellerSettlement.grossAmount` theo `subtotal` gốc chưa trừ `discountAmount` (vì sẽ làm sàn trả thừa tiền cho Shop đối với đơn VietQR), và không hoàn tiền đổi trả (`REFUND`) vượt quá số tiền hàng khách thực trả (`subtotal - discountAmount`).

---

## 3. Các phương án kiến trúc đã cân nhắc (Approaches)

### Phương án 1 (Đã chọn): Voucher của từng Gian hàng (Shop Voucher) — Áp dụng theo từng đơn Shop
- **Mô tả:** Mỗi Shop tự tạo và quản lý mã giảm giá tại Kênh Người Bán (`/shop?tab=vouchers`). Khi khách mua hàng từ nhiều Shop trong giỏ hàng, mỗi khối Shop trên trang Checkout có thể áp dụng 1 mã giảm giá của chính Shop đó. Chi phí giảm giá trừ trực tiếp vào tiền hàng của đơn Shop đó (`Order.discountAmount`) và khấu trừ vào `SellerSettlement.grossAmount`.
- **Ưu điểm:** Khớp 100% với mô hình `1 Order = 1 Shop` hiện tại; không cần chia tỷ lệ (prorate) 1 mã giảm giá ra nhiều đơn hàng; đối soát ví Shop và hoàn tiền VietQR minh bạch, độc lập giữa các Shop.
- **Đánh đổi:** Chỉ áp dụng cho phạm vi từng Shop.

### Phương án 2 (Loại bỏ): Voucher toàn sàn do Admin tạo (Platform Voucher)
- **Mô tả:** Admin tạo mã giảm giá chung toàn sàn; khi checkout giỏ hàng nhiều Shop, hệ thống chia tỷ lệ số tiền giảm vào từng đơn Shop.
- **Lý do loại bỏ:** Phức tạp hóa việc đối soát ngân sách sàn bù cho Shop và xử lý hoàn lượt khi 1 trong các đơn lẻ bị hủy, trong khi nhu cầu cốt lõi hiện tại là công cụ khuyến mãi cho các Gian hàng.

### Phương án 3 (Loại bỏ cho giai đoạn hiện tại): Hệ thống Voucher Kép (Shop Voucher + Platform Voucher)
- **Mô tả:** Làm đồng thời cả Voucher của Shop lẫn Voucher của Sàn và cho phép áp chồng 2 tầng mã trong 1 lần checkout.
- **Lý do loại bỏ:** Tăng gấp đôi phạm vi UI/API; có thể dễ dàng mở rộng từ Phương án 1 về sau khi cần.

---

## 4. Thiết kế chi tiết (Detailed Design)

### 4.1. Schema Cơ sở dữ liệu (Migration `V50__shop_vouchers.sql`)

1. **Bảng `shop_voucher` và `shop_voucher_product`:**
   ```sql
   CREATE TABLE shop_voucher (
       id UUID PRIMARY KEY,
       shop_id UUID NOT NULL REFERENCES shop (id) ON DELETE CASCADE,
       code TEXT NOT NULL,
       name TEXT NOT NULL,
       description TEXT NOT NULL DEFAULT '',
       discount_type TEXT NOT NULL
           CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
       discount_value BIGINT NOT NULL CHECK (discount_value > 0),
       max_discount_amount BIGINT CHECK (max_discount_amount IS NULL OR max_discount_amount > 0),
       min_order_subtotal BIGINT NOT NULL DEFAULT 0 CHECK (min_order_subtotal >= 0),
       product_scope TEXT NOT NULL DEFAULT 'ALL_PRODUCTS'
           CHECK (product_scope IN ('ALL_PRODUCTS', 'SPECIFIC_PRODUCTS')),
       visibility TEXT NOT NULL DEFAULT 'PUBLIC'
           CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
       usage_limit INT NOT NULL CHECK (usage_limit > 0),
       used_count INT NOT NULL DEFAULT 0 CHECK (used_count >= 0 AND used_count <= usage_limit),
       per_buyer_limit INT NOT NULL DEFAULT 1 CHECK (per_buyer_limit > 0),
       starts_at TIMESTAMPTZ NOT NULL,
       ends_at TIMESTAMPTZ NOT NULL,
       active BOOLEAN NOT NULL DEFAULT TRUE,
       created_by UUID REFERENCES user_profile (id) ON DELETE SET NULL,
       created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
       UNIQUE (shop_id, code),
       CHECK (ends_at > starts_at),
       CHECK (discount_type <> 'PERCENTAGE' OR (discount_value >= 1 AND discount_value <= 99))
   );

   CREATE INDEX idx_shop_voucher_shop_active
       ON shop_voucher (shop_id, active, starts_at, ends_at);

   CREATE TABLE shop_voucher_product (
       voucher_id UUID NOT NULL REFERENCES shop_voucher (id) ON DELETE CASCADE,
       product_id UUID NOT NULL REFERENCES product (id) ON DELETE CASCADE,
       PRIMARY KEY (voucher_id, product_id)
   );
   CREATE INDEX idx_svp_product ON shop_voucher_product (product_id);
   ```

2. **Mở rộng bảng `orders`:**
   ```sql
   ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_id UUID REFERENCES shop_voucher (id) ON DELETE SET NULL;
   ALTER TABLE orders ADD COLUMN IF NOT EXISTS voucher_code TEXT;
   ALTER TABLE orders ADD COLUMN IF NOT EXISTS discount_amount BIGINT NOT NULL DEFAULT 0
       CHECK (discount_amount >= 0);

   ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_check;
   ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_total_math_check;
   ALTER TABLE orders ADD CONSTRAINT orders_total_math_check
       CHECK (discount_amount <= subtotal AND total = subtotal + shipping_fee - discount_amount);

   CREATE INDEX IF NOT EXISTS idx_orders_buyer_voucher
       ON orders (buyer_id, voucher_id)
       WHERE voucher_id IS NOT NULL AND status <> 'CANCELLED';
   ```

---

### 4.2. Luật Nghiệp vụ, Công thức Tính toán & Chiều phụ thuộc Domain

1. **Chiều phụ thuộc một chiều (DAG — chống Circular Dependency):**
   - `CartService` → `OrderService` → `VoucherService` → (`ShopService`, `CatalogService`, `ShopAccessChecker`, `ShopAuditService`).
   - `VoucherService` **tuyệt đối không inject `OrderService` hay `OrderRepository`**: việc đếm số đơn không bị hủy của một người mua đã sử dụng `voucherId` (`countActiveUsagesByBuyer(UUID voucherId, UUID buyerId)`) được thực hiện bằng câu truy vấn SQL đọc trực tiếp trong `ShopVoucherRepository` (`SELECT COUNT(*) FROM orders WHERE voucher_id = :voucherId AND buyer_id = :buyerId AND status <> 'CANCELLED'`).

2. **Định dạng & Ràng buộc khi Tạo / Cập nhật Voucher:**
   - `code`: Chuẩn hóa `trim().toUpperCase(Locale.ROOT)`, khớp regex `^[A-Z0-9]{4,20}$` (sai → `400 Bad Request`). Nếu trùng `(shopId, code)` → `409 Conflict` `"Voucher code already exists in this shop"`.
   - `discountType == PERCENTAGE`: `discountValue` phải từ `1` đến `99` (`400 Bad Request` nếu nằm ngoài).
   - `discountType == FIXED_AMOUNT`: `maxDiscountAmount` được chuẩn hóa về `null`.
   - `endsAt` phải sau `startsAt` (`!endsAt.isAfter(startsAt)` → `400 Bad Request`).
   - `productScope == SPECIFIC_PRODUCTS`: danh sách `productIds` không được rỗng và mọi `productId` phải thuộc đúng `shopId` của voucher (`400 Bad Request` nếu thiếu hoặc chứa sản phẩm của shop khác). Nếu `productScope == ALL_PRODUCTS`, danh sách sản phẩm liên kết trong `shop_voucher_product` được xóa rỗng.
   - **Khi chỉnh sửa voucher đã có lượt dùng (`usedCount > 0`):**
     - Nếu request thay đổi `code`, `discountType`, `discountValue`, `maxDiscountAmount`, `minOrderSubtotal`, hoặc `productScope` khác với giá trị hiện tại → ném `409 Conflict` `"Cannot modify core discount terms of a voucher that has already been used"`.
     - Nếu `request.usageLimit() < voucher.getUsedCount()` → ném `409 Conflict` `"Usage limit cannot be less than current used count"`.
   - **Khi xóa voucher (`DELETE`):**
     - Nếu `voucher.getUsedCount() > 0` → ném `409 Conflict` `"Cannot delete a voucher that has already been used; deactivate it instead"`.

3. **Trạng thái hiển thị dẫn xuất (`VoucherStatus`):**
   - Tính tại thời điểm `now = clock.instant()`:
     - Nếu `!voucher.isActive()` → `PAUSED`
     - Ngược lại nếu `!now.isBefore(voucher.getEndsAt())` → `EXPIRED`
     - Ngược lại nếu `voucher.getUsedCount() >= voucher.getUsageLimit()` → `DEPLETED`
     - Ngược lại nếu `now.isBefore(voucher.getStartsAt())` → `UPCOMING`
     - Còn lại → `ONGOING`

4. **Công thức tính `eligibleSubtotal` và `discountAmount`:**
   - Với danh sách mặt hàng của một Shop trong đơn (`List<VoucherLineItem(UUID productId, long lineTotal)>`):
     - Nếu `productScope == ALL_PRODUCTS`: `eligibleSubtotal = sum(item.lineTotal())`.
     - Nếu `productScope == SPECIFIC_PRODUCTS`: chỉ cộng `item.lineTotal()` của các mặt hàng có `productId` thuộc tập sản phẩm của voucher.
   - Điều kiện hợp lệ:
     1. Voucher thuộc `shopId`, trạng thái tại `now` là `ONGOING`.
     2. Số lượt buyer đã dùng (`countActiveUsagesByBuyer(voucher.getId(), buyerId)`) `< voucher.getPerBuyerLimit()`.
     3. `eligibleSubtotal > 0` (đơn có ít nhất 1 sản phẩm thuộc phạm vi áp dụng).
     4. `eligibleSubtotal >= voucher.getMinOrderSubtotal()`.
   - Mức giảm:
     - Với `FIXED_AMOUNT`: `raw = voucher.getDiscountValue()`.
     - Với `PERCENTAGE`: `raw = (eligibleSubtotal * voucher.getDiscountValue()) / 100L`; nếu `voucher.getMaxDiscountAmount() != null` thì `raw = Math.min(raw, voucher.getMaxDiscountAmount())`.
     - `discountAmount = Math.min(raw, eligibleSubtotal)`.

5. **Phân biệt hành vi khi Xem trước (`previewCheckout`) vs Đặt hàng (`checkout`):**
   - **Khi Xem trước (`POST /api/cart/checkout/preview`):** Không làm gagal cả request nếu mã voucher của một Shop không hợp lệ hoặc không còn đủ điều kiện (ví dụ khách vừa bỏ bớt sản phẩm trong giỏ làm `subtotal < minOrderSubtotal`). Thay vào đó, `ShopCheckoutPreview` của Shop đó trả `discountAmount = 0`, `voucherCode = code`, và `voucherError = "<Lý do tiếng Việt dễ hiểu cho UI>"` để giao diện hiển thị cảnh báo nhẹ và cho khách gỡ/đổi mã.
   - **Khi Đặt hàng (`POST /api/cart/checkout` hoặc `POST /api/orders`):** Nếu khách gửi `voucherCode` mà mã không tồn tại, hết hạn, hết lượt, quá giới hạn cá nhân hoặc không đủ điều kiện đơn tối thiểu → chặn ngay lập tức với `409 Conflict` (hoặc `404 Not Found` nếu mã không tồn tại), không tạo đơn hàng.

---

### 4.3. Giữ lượt Nguyên tử khi Đặt hàng & Hoàn lượt khi Hủy đơn

1. **Giữ lượt nguyên tử (`VoucherService.redeemForOrder`):**
   - Khi `OrderService.prepareCheckout` xử lý đơn hàng có `voucherCode` (khác null/blank):
     - Khóa dòng `shop_voucher` bằng `SELECT * FROM shop_voucher WHERE shop_id = :shopId AND code = :code FOR UPDATE`.
     - Kiểm tra đầy đủ điều kiện hiệu lực, `usedCount < usageLimit`, `countActiveUsagesByBuyer < perBuyerLimit`, `eligibleSubtotal >= minOrderSubtotal`.
     - Tính `discountAmount`, tăng `voucher.incrementUsedCount(now)` và lưu vào DB.
     - Trả về `AppliedVoucher(UUID voucherId, String code, long discountAmount)` để `OrderService` lưu vào `Order` (`total = subtotal + shippingFee - discountAmount`).
2. **Hoàn lượt dùng khi Hủy đơn (`VoucherService.releaseVoucherUsage`):**
   - Trong `OrderService.restockOrderItems(Order order, List<OrderItem> orderItems)` (hàm dùng chung cho cả 5 luồng hủy đơn: Buyer hủy, Seller hủy, Admin hủy, `OrderAutoCancelJob` tự hủy sau 24h, và `PaymentExpiryJob` tự hủy đơn VietQR quá hạn 15 phút):
     - Nếu `order.getVoucherId() != null`, gọi `vouchers.releaseVoucherUsage(order.getVoucherId())` thực thi câu lệnh SQL nguyên tử:
       `UPDATE shop_voucher SET used_count = GREATEST(0, used_count - 1), updated_at = :now WHERE id = :voucherId`.

---

### 4.4. Liên thông Quyết toán Ví Shop (`SellerSettlement`), VietQR & Hoàn tiền

1. **Doanh thu hàng hóa thực thu của Shop (`order.netMerchandiseTotal()`):**
   - Trong `Order.java`, thêm method `public long netMerchandiseTotal() { return Math.max(0L, subtotal - discountAmount); }`.
2. **Tạo `SellerSettlement` (`OrderService.applyVietQrPaymentSuccess` và `OrderService.deliver`):**
   - Thay vì truyền `order.getSubtotal()`, truyền `order.netMerchandiseTotal()` vào `revenue.createSettlementForOrder(order.getShopId(), order.getId(), order.netMerchandiseTotal())`.
   - Khi đó:
     - `grossAmount = subtotal - discountAmount`
     - `platformFee = (grossAmount * 5) / 100`
     - `netAmount = grossAmount - platformFee`
3. **Đổi trả & Hoàn tiền (`ReturnRequestService.complete`):**
   - Khi `resolution == "REFUND"` và `saved.getRefundAmount() == null`, số tiền hoàn mặc định lấy `order.netMerchandiseTotal()` thay vì `order.getSubtotal()`.
   - Khi `ReturnRequestService.approve` kiểm tra số tiền hoàn: không cho phép `refundAmount > order.netMerchandiseTotal()`.

---

### 4.5. Danh mục API Endpoints

1. **Seller Center API (`SellerVoucherController` — `/api/seller/shops/{shopId}/vouchers`):**
   - `GET /api/seller/shops/{shopId}/vouchers?status=&q=&page=&size=` (`operationId = "listSellerVouchers"`): Yêu cầu quyền `VOUCHER_READ` hoặc `VOUCHER_WRITE`. Trả `PageResponse<SellerVoucherResponse>`.
   - `GET /api/seller/shops/{shopId}/vouchers/{voucherId}` (`operationId = "getSellerVoucher"`): Yêu cầu `VOUCHER_READ` hoặc `VOUCHER_WRITE`. Trả `SellerVoucherDetailResponse` (kèm danh sách sản phẩm đã chọn nếu `SPECIFIC_PRODUCTS`).
   - `POST /api/seller/shops/{shopId}/vouchers` (`operationId = "createSellerVoucher"`): Yêu cầu Shop đã `APPROVED` và quyền `VOUCHER_WRITE`. Ghi `ShopAuditService` (`action = "VOUCHER_CREATE"`, `targetType = "VOUCHER"`).
   - `PUT /api/seller/shops/{shopId}/vouchers/{voucherId}` (`operationId = "updateSellerVoucher"`): Yêu cầu `VOUCHER_WRITE`. Ghi `ShopAuditService` (`action = "VOUCHER_UPDATE"`).
   - `PATCH /api/seller/shops/{shopId}/vouchers/{voucherId}/active` (`operationId = "toggleSellerVoucherActive"`): Yêu cầu `VOUCHER_WRITE`. Body `ToggleVoucherActiveRequest(boolean active)`. Ghi `ShopAuditService` (`action = "VOUCHER_STATUS"`).
   - `DELETE /api/seller/shops/{shopId}/vouchers/{voucherId}` (`operationId = "deleteSellerVoucher"`): Yêu cầu `VOUCHER_WRITE`. Chỉ xóa khi `usedCount == 0`. Ghi `ShopAuditService` (`action = "VOUCHER_DELETE"`).

2. **Public / Buyer Storefront API (`PublicShopVoucherController` — `/api/shops/{shopId}/vouchers`):**
   - `GET /api/shops/{shopId}/vouchers` (`operationId = "listPublicShopVouchers"`): Public GET (nằm dưới `/api/shops/**` đã mở trong `SecurityConfig`). Nếu có `@AuthenticationPrincipal @Nullable Jwt jwt` hợp lệ thì kiểm tra luôn số lượt `buyerId` đã dùng để gắn cờ `exhaustedByBuyer: boolean`. Chỉ trả các voucher có `visibility == PUBLIC`, `active == true`, chưa hết hạn (`endsAt > now`) và còn lượt (`usedCount < usageLimit`), sắp xếp ưu tiên `ONGOING` trước rồi tới mức giảm cao nhất.

3. **Mở rộng Cart Preview & Checkout DTOs:**
   - `CheckoutPreviewRequest`: thêm `@Nullable Map<UUID, String> shopVouchers` (constructor cũ 1 tham số `List<UUID> variantIds` giữ nguyên để tương thích ngược).
   - `ShopCheckoutPreview`: thêm `@Nullable String voucherCode`, `long discountAmount`, `@Nullable String voucherError` (giữ constructor cũ 8 tham số làm alias mặc định `null, 0L, null`).
   - `CheckoutPreviewResponse`: thêm `long discountAmount` (tổng tiền giảm của tất cả các đơn Shop trong lần preview; giữ constructor cũ 4 tham số làm alias).
   - `CartCheckoutRequest`: thêm `@Nullable Map<UUID, String> shopVouchers` (giữ các constructor cũ làm alias).
   - `CheckoutRequest`: thêm `@Nullable String voucherCode` (giữ các constructor cũ làm alias).
   - `OrderResponse`: thêm `@Nullable UUID voucherId`, `@Nullable String voucherCode`, `long discountAmount` (giữ các constructor cũ làm alias).

---

### 4.6. Thiết kế Giao diện (`fashionstore-ui`)

1. **Kênh Người Bán — Tab "Mã giảm giá" (`/shop?tab=vouchers` — `SellerVouchersTab.tsx`):**
   - Bổ sung `'VOUCHERS'` vào `SellerTabId`, `sellerNavigation.ts`, `sellerPermissions.ts` (`VOUCHER_READ`, `VOUCHER_WRITE`) và nhóm menu trên `SellerLayout.tsx` (icon `Ticket`).
   - Hiển thị 4 thẻ thống kê nhanh (Tổng mã, Đang diễn ra, Sắp diễn ra, Tổng lượt đã dùng), thanh lọc theo trạng thái (`Tất cả`, `Đang diễn ra`, `Sắp diễn ra`, `Tạm dừng`, `Hết lượt`, `Đã kết thúc`) và ô tìm kiếm mã/tên.
   - Bảng danh sách voucher hiển thị: Mã code (kèm nút copy) + Tên chương trình + Nhãn Công khai/Mã ẩn, Mức giảm & Đơn tối thiểu, Phạm vi (*Toàn shop* hoặc *N sản phẩm*), Tiến độ sử dụng (`usedCount / usageLimit`), Thời gian hiệu lực, Trạng thái, và các nút thao tác (Bật/Tạm dừng, Chỉnh sửa, Xóa khi `usedCount === 0`).
   - Modal Tạo / Chỉnh sửa Voucher (`VoucherFormModal.tsx`): hỗ trợ sinh mã ngẫu nhiên, chọn loại giảm (`FIXED_AMOUNT` / `PERCENTAGE`), chọn phạm vi sản phẩm (có bộ chọn sản phẩm của Shop khi chọn `SPECIFIC_PRODUCTS`), và tự động khóa các trường cốt lõi khi chỉnh sửa voucher đã có `usedCount > 0`.

2. **Dải Voucher trên Trang Gian hàng (`ShopStorefront.tsx`) & Chi tiết Sản phẩm (`ProductDetail.tsx`):**
   - Component `ShopVoucherStrip.tsx` gọi `useListPublicShopVouchers(shopId)`:
     - Trên `ShopStorefront.tsx`: hiển thị toàn bộ voucher công khai của Shop.
     - Trên `ProductDetail.tsx`: lọc hiển thị các voucher áp dụng cho `ALL_PRODUCTS` hoặc có chứa `productId` hiện tại trong `applicableProductIds`.
     - Mỗi thẻ hiển thị mức giảm, điều kiện đơn tối thiểu, hạn dùng và nút **"Sao chép mã"** (đổi thành *"Đã lưu mã"* và thông báo toast).

3. **Chọn / Nhập Voucher theo từng Shop tại Trang Thanh toán (`Checkout.tsx`) & Chi tiết Đơn (`OrderDetail.tsx`):**
   - Dưới mỗi khối Shop trên `Checkout.tsx`:
     - Thêm hàng **"Voucher của Shop"**: hiển thị mã đang áp dụng kèm số tiền giảm (`-formatVnd(shopQuote.discountAmount)`) hoặc cảnh báo lỗi (`shopQuote.voucherError`), cùng nút **"Chọn hoặc nhập mã"**.
     - Khi bấm mở `ShopVoucherModal.tsx`: cho phép nhập mã bất kỳ (hỗ trợ cả mã `PRIVATE`) hoặc bấm chọn từ danh sách voucher `PUBLIC` của Shop đó (tự động tính xem giỏ hàng của Shop đã đủ `minOrderSubtotal` và thuộc sản phẩm áp dụng chưa).
   - Ở cột phải **Chi tiết thanh toán** (`Checkout.tsx`): thêm dòng **"Voucher giảm giá từ Shop: -XX.XXX ₫"** màu xanh lá (`emerald`) khi tổng `preview.discountAmount > 0`.
   - Trên `OrderDetail.tsx`, `SellerOrdersTab.tsx`, và `AdminOrderDetailPanel.tsx`: hiển thị dòng `Voucher Shop (MÃ_CODE): -XX.XXX ₫` khi `order.discountAmount > 0`.

---

## 5. Phần để người thực thi tự quyết định (Left to the Builder)

- Cách tách nhỏ component con cho `SellerVouchersTab.tsx` và `ShopVoucherModal.tsx` theo bộ token giao diện có sẵn (`PANEL_CLASS`, `INPUT_CLASS`, `Modal.tsx`, Phosphor Icons).
- Bố cục chi tiết của thẻ ticket răng cưa trong `ShopVoucherStrip.tsx` miễn hiển thị rõ mã code, mức giảm, đơn tối thiểu và nút sao chép.
