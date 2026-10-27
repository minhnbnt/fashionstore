# Thiết Kế Chi Tiết: Xem & Xử Lý Đơn Hàng Mới Tại Kênh Người Bán (Seller Center)

**Mã chức năng:** 2.3.1  
**Ngày lập:** 2026-10-27  
**Trạng thái:** Chờ phê duyệt  
**Tác giả:** Antigravity  

---

## 1. Mục tiêu & Bối cảnh (Intent & Why)

### 1.1. Bối cảnh
Kênh Người Bán (Seller Center) của **FashionStore Marketplace** hiện đã có các API và giao diện quản lý đơn hàng cơ bản (`SellerOrdersTab.tsx`, `SellerOrderController.java`, `OrderService.java`). Tuy nhiên, quy trình xử lý đơn hàng còn mang tính đơn lẻ, chưa hỗ trợ thao tác hàng loạt, thiếu trường thông tin quan trọng (phương thức thanh toán, ghi chú của khách, lý do hủy), chưa có bộ đếm hạn xác nhận và cơ chế tự động hủy đơn sau 24 giờ.

Đặc biệt, qua phân tích đối chiếu với các sàn thương mại điện tử hàng đầu (Shopee, TikTok Shop, Lazada), hệ thống được chuẩn hóa cơ chế quản lý tồn kho: **Trừ kho ngay khi Khách hàng bấm Đặt hàng (Deduct on Checkout / Reserve Stock)** và **Tự động hoàn kho khi đơn bị hủy ở bất kỳ trạng thái nào**, nhằm triệt tiêu hoàn toàn rủi ro bán lố (Overselling / Race condition) và giúp việc xác nhận đơn hàng của Shop diễn ra mượt mà, không bị lỗi thiếu kho bất ngờ.

### 1.2. Mục tiêu
- **Phân loại trạng thái chuẩn sàn:** Cung cấp thanh điều hướng 7 tab trực quan: *Tất cả*, *Chờ xác nhận*, *Đang chuẩn bị*, *Đã giao cho ĐVVC*, *Hoàn thành*, *Đã hủy*, *Đổi trả*.
- **Xem chi tiết đơn hàng toàn diện:** Hiển thị sản phẩm kèm ảnh, size, màu, SKU, số lượng, đơn giá, thông tin người nhận (tên, SĐT, địa chỉ), phương thức thanh toán (COD), ghi chú của khách, lý do từ chối/hủy và **Dòng thời gian đơn hàng (Order Timeline)**.
- **Chuẩn hóa vòng đời tồn kho theo chuẩn sàn TMĐT:**
  - *Lúc Khách đặt mua (Checkout):* Trừ kho nguyên tử ngay lập tức (`stock_quantity = stock_quantity - qty`) và ghi log biến động kho (`stock_movement`) lý do `ORDER_CREATED`. Đảm bảo khách đặt được là chắc chắn có hàng.
  - *Lúc Shop xác nhận đơn (Confirm):* Chuyển trạng thái từ `PENDING` sang `CONFIRMED` ("Đang chuẩn bị hàng"). Kho đã được giữ sẵn nên thao tác xác nhận đơn diễn ra tức thời, không bị nghẽn kho.
  - *Lúc Đơn bị hủy (Khách hủy / Shop từ chối / Hệ thống tự hủy 24h):* Bất kể đơn đang ở `PENDING` hay `CONFIRMED`, hệ thống **LUÔN LUÔN TỰ ĐỘNG HOÀN KHO NGUYÊN TỬ** (`stock_quantity = stock_quantity + qty`) và ghi log biến động kho với lý do tương ứng (`BUYER_CANCEL`, `SELLER_CANCEL`, `SYSTEM_AUTO_CANCEL`).
- **Xử lý hàng loạt (Bulk Confirm):** Cho phép chọn nhiều đơn chờ xác nhận cùng lúc. Áp dụng cơ chế **Partial Success**: Đơn nào hợp lệ thì xác nhận thành công; đơn nào lỗi (sai trạng thái, thiếu quyền) thì báo cáo cụ thể mã đơn và lý do, không hủy bỏ cả mẻ.
- **Từ chối đơn hàng (Reject / Cancel):** Nhập lý do từ chối chuẩn sàn ("Hết hàng trong kho", "Sai giá / thông tin sản phẩm", "Không thể liên hệ khách hàng", "Người mua yêu cầu hủy") + ghi chú thêm, tự động hoàn trả kho và gửi thông báo chi tiết cho khách hàng.
- **Hạn xác nhận 24 giờ (SLA & Auto-Cancel):**
  - Hiển thị nhãn đếm ngược thời gian còn lại trực quan trên từng đơn `PENDING` (chuyển màu cam khi < 6h, đỏ khi < 2h).
  - Background Job định kỳ tự động hủy các đơn `PENDING` quá 24h kể từ lúc tạo, ghi nhận `cancelled_by = 'SYSTEM'`, lý do `Quá thời hạn 24 giờ xác nhận`, hoàn trả lại kho cho khách khác mua, lưu vết lịch sử và gửi thông báo cho cả khách hàng và chủ shop.

### 1.3. Non-goals & Anti-goals
- **Non-goals:**
  - Không in phiếu đóng gói hàng (Packing slip / Shipping label) theo yêu cầu lược bỏ của người dùng.
  - Không tích hợp webhook thời gian thực với bên thứ ba (GHTK, GHN, ViettelPost) trong đợt này; việc bàn giao vận chuyển `SHIPPING` vẫn do shop/hệ thống thao tác.
  - Không tách thêm trạng thái trung gian "Đang giao" và "Đã giao cho ĐVVC"; gộp chung thành `SHIPPING` ("Đã giao cho ĐVVC").
- **Anti-goals:**
  - Không để xảy ra tình trạng bán lố tồn kho (Overselling).
  - Không rollback toàn bộ mẻ khi xác nhận hàng loạt nếu chỉ có 1 vài đơn bị lỗi trạng thái.

---

## 2. Các Phương Án Kỹ Thuật & Lý Do Lựa Chọn (Approaches)

### Phương án 1 (Đã được chọn): Kiến trúc chuẩn sàn TMĐT, Trừ kho khi Checkout, Background Job với Migration V31
- Chuẩn hóa vòng đời tồn kho: Trừ kho ngay khi Checkout, hoàn kho khi hủy ở mọi trạng thái (`PENDING` & `CONFIRMED`).
- Bổ sung giá trị `'SYSTEM'` vào CHECK constraint `orders.cancelled_by` qua Flyway Migration `V31`.
- Thêm API chuyên biệt `POST /api/seller/orders/bulk-confirm` hỗ trợ Partial Success.
- Thêm API `GET /api/seller/orders/{id}/timeline` tận dụng query sẵn có từ `OrderMonitoringQueryRepository`.
- Bổ sung các trường `paymentMethod`, `note`, `cancelReason`, `cancelNote`, `cancelledBy` vào `OrderResponse`.
- Dùng `@Scheduled` chạy mỗi 5 phút quét đơn quá hạn 24h, tự động hủy và hoàn trả kho.
- **Lý do chọn:** Đảm bảo toàn vẹn dữ liệu, trải nghiệm khách hàng và người bán đạt chuẩn các sàn TMĐT lớn (Shopee, TikTok Shop).

---

## 3. Thiết Kế Chi Tiết Backend (`fashionstore`)

### 3.1. Database Migration: `V31__order_auto_cancel_system.sql`
```sql
-- Cho phép SYSTEM trong cột cancelled_by của bảng orders
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cancelled_by_check;
ALTER TABLE orders ADD CONSTRAINT orders_cancelled_by_check 
    CHECK (cancelled_by IN ('BUYER', 'SELLER', 'ADMIN', 'SYSTEM'));

-- Index hỗ trợ background job quét đơn PENDING quá hạn
CREATE INDEX IF NOT EXISTS idx_orders_pending_created ON orders (created_at) WHERE status = 'PENDING';
```

### 3.2. Cập nhật Model & Enum
1. **`OrderCanceller.java`**:
   Thêm enum value `SYSTEM`:
   ```java
   public enum OrderCanceller {
       BUYER,
       SELLER,
       ADMIN,
       SYSTEM
   }
   ```
2. **`Order.java`**:
   Thêm phương thức hủy tự động bởi hệ thống:
   ```java
   public void cancelBySystem(String reason, @Nullable String note) {
       if (status != OrderStatus.PENDING) {
           throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending orders can be cancelled by system");
       }
       status = OrderStatus.CANCELLED;
       cancelReason = reason;
       cancelNote = note;
       cancelledBy = OrderCanceller.SYSTEM;
       cancelledAt = Instant.now();
       updatedAt = Instant.now();
   }
   ```

### 3.3. Cập nhật DTO
1. **`OrderResponse.java`**:
   Mở rộng thêm các trường:
   ```java
   public record OrderResponse(
       UUID id,
       String code,
       UUID shopId,
       UUID buyerId,
       String status,
       String paymentStatus,
       String paymentMethod,
       long subtotal,
       long shippingFee,
       long total,
       String shippingName,
       String shippingPhone,
       String shippingAddress,
       String note,
       @Nullable String cancelReason,
       @Nullable String cancelNote,
       @Nullable String cancelledBy,
       @Nullable Instant confirmedAt,
       @Nullable Instant cancelledAt,
       List<OrderItemResponse> items,
       Instant createdAt
   )
   ```
2. **DTO xác nhận hàng loạt**:
   - `BulkConfirmOrderRequest.java`:
     ```java
     public record BulkConfirmOrderRequest(
         @NotEmpty(message = "Danh sách đơn hàng không được để trống")
         List<UUID> orderIds
     )
     ```
   - `BulkConfirmOrderResponse.java`:
     ```java
     public record BulkConfirmOrderResponse(
         int totalRequested,
         int successCount,
         int failureCount,
         List<UUID> successIds,
         List<BulkConfirmFailureItem> failures
     )
     ```
   - `BulkConfirmFailureItem.java`:
     ```java
     public record BulkConfirmFailureItem(
         UUID orderId,
         String orderCode,
         String reason
     )
     ```

### 3.4. Cập nhật Nghiệp Vụ Tồn Kho trong `OrderService.java`

1. **Khi Checkout (`checkout`)**:
   - Trừ tồn kho nguyên tử ngay lập tức cho từng biến thể:
     ```java
     for (var item : request.items()) {
         if (!productStock.decrementVariantStock(item.variantId(), item.quantity())) {
             throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for variant " + item.variantId());
         }
     }
     ```
   - Ghi nhật ký biến động kho (`stock_movement`) lý do `ORDER_CREATED`.

2. **Khi Xác nhận đơn (`confirm` & `confirmSingleOrder`)**:
   - Kiểm tra quyền `ShopPermission.ORDER_PROCESS`.
   - Kiểm tra trạng thái đơn phải là `PENDING`.
   - Chuyển trạng thái sang `CONFIRMED`, gán `confirmedAt = now()`.
   - Ghi nhận `OrderStatusHistory`, audit log shop, và gửi thông báo in-app cho người mua (`"Shop đã xác nhận đơn #[CODE], đang chuẩn bị hàng"`).

3. **Khi Xác nhận hàng loạt (`bulkConfirm`)**:
   - Lặp qua từng `orderId`.
   - Với mỗi đơn:
     - Kiểm tra quyền `ORDER_PROCESS`.
     - Nếu đơn không ở trạng thái `PENDING`: ghi nhận lỗi vào `failures`.
     - Nếu hợp lệ: chuyển sang `CONFIRMED`, lưu history, audit, notification, thêm vào `successIds`.
   - Trả về `BulkConfirmOrderResponse`.

4. **Khi Hủy đơn (Buyer Cancel, Seller Cancel, Admin Cancel, System Auto-Cancel)**:
   - Thay thế hàm `restockIfConfirmed` bằng `restockOrderItems(Order, List<OrderItem>, StockMovementReason)`:
   - **Tất cả các trường hợp hủy đơn `PENDING` và `CONFIRMED` đều hoàn trả tồn kho nguyên tử**:
     ```java
     for (var item : orderItems) {
         if (item.getVariantId() != null) {
             productStock.incrementVariantStock(item.getVariantId(), item.getQuantity());
             stockMovements.record(...);
         }
     }
     ```
   - Ghi nhận `OrderStatusHistory` và gửi thông báo in-app cho khách hàng / shop.

5. **Phương thức `systemCancelExpiredOrder(UUID orderId)`**:
   - Kiểm tra đơn còn `PENDING` và `createdAt < now - 24 hours`.
   - Gọi `order.cancelBySystem("Quá thời hạn 24 giờ xác nhận", null)`.
   - Hoàn trả lại tồn kho nguyên tử cho các biến thể trong đơn.
   - Lưu `OrderStatusHistory.record(order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED, null, "Quá thời hạn 24 giờ xác nhận")`.
   - Ghi audit log shop: `ORDER_AUTO_CANCEL_EXPIRED`.
   - Bắn thông báo in-app:
     - Cho người mua (`ORDER_CANCELLED`): `"Đơn #[CODE] đã tự động hủy do người bán không xác nhận trong vòng 24 giờ."`
     - Cho chủ shop (`ORDER_CANCELLED`): `"Đơn #[CODE] đã tự động hủy do quá thời hạn xác nhận 24 giờ."`

6. **Cập nhật `toResponse(Order, List<OrderItem>)`**: Truyền đầy đủ `paymentMethod`, `note`, `cancelReason`, `cancelNote`, `cancelledBy`, `confirmedAt`, `cancelledAt`.

### 3.5. Background Job: `OrderAutoCancelJob.java`
- Tạo class `com.minhnbnt.fashionstore.features.order.OrderAutoCancelJob`:
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderAutoCancelJob {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${fashionstore.order.auto-cancel-interval-ms:300000}", initialDelay = 30000)
    public void scanAndCancelExpiredOrders() {
        var cutoff = clock.instant().minus(Duration.ofHours(24));
        var expiredOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);
        for (var order : expiredOrders) {
            try {
                orderService.systemCancelExpiredOrder(order.getId());
            } catch (Exception e) {
                log.error("Failed to auto-cancel order {}", order.getId(), e);
            }
        }
    }
}
```

### 3.6. Cập nhật `SellerOrderController.java`
- Thêm endpoint xác nhận hàng loạt:
  - `POST /api/seller/orders/bulk-confirm`
- Thêm endpoint lấy lịch sử timeline đơn hàng:
  - `GET /api/seller/orders/{id}/timeline` -> kiểm tra quyền `ORDER_READ`, gọi `monitoring.timeline(id)`.

---

## 4. Thiết Kế Chi Tiết Frontend (`fashionstore-ui`)

### 4.1. Hệ Thống 7 Tab Trạng Thái
Tại `SellerOrdersTab.tsx`, cấu hình mảng tab chuẩn:
1. `ALL`: **Tất cả**
2. `PENDING`: **Chờ xác nhận** (kèm số lượng badge đếm số đơn đang chờ)
3. `CONFIRMED`: **Đang chuẩn bị** (chờ lấy hàng / đóng gói)
4. `SHIPPING`: **Đã giao cho ĐVVC** (đang giao đến khách)
5. `DELIVERED`: **Hoàn thành** (đã giao thành công & thu tiền)
6. `CANCELLED`: **Đã hủy** (hủy bởi khách, shop từ chối, hoặc hệ thống tự hủy)
7. `RETURNS`: **Đổi trả** (kết nối dữ liệu từ `return_request` của shop)

*Quy tắc hiển thị tab Đổi trả:*
Khi chọn tab `RETURNS`, giao diện chuyển sang hiển thị danh sách các đơn có yêu cầu đổi trả (gọi `useList4` từ `@generated/endpoints/seller-returns/seller-returns`), hiển thị lý do, bằng chứng hình ảnh và các nút duyệt/từ chối đổi trả.

### 4.2. Bộ Đếm Ngược SLA 24 Giờ (SLA Countdown Badge)
- Tính toán thời gian còn lại: `timeLeftMs = (new Date(order.createdAt).getTime() + 24 * 3600 * 1000) - Date.now()`.
- Hiển thị badge trực quan trên từng thẻ đơn hàng ở tab `Chờ xác nhận`:
  - `timeLeftMs <= 0`: Badge màu đỏ đậm: `Đã quá hạn 24h (Chờ hệ thống hủy)`.
  - `timeLeftMs < 2 * 3600 * 1000` (< 2h): Badge màu đỏ nhấp nháy: `Hết hạn sau ${mm} phút` kèm icon cảnh báo `WarningCircle`.
  - `timeLeftMs < 6 * 3600 * 1000` (< 6h): Badge màu hổ phách (amber): `Hết hạn sau ${hh}h ${mm}p`.
  - `timeLeftMs >= 6 * 3600 * 1000`: Badge màu xám/indigo nhạt: `Thời hạn xác nhận: còn ${hh}h`.
- Ở tab `Chờ xác nhận`, danh sách đơn được sắp xếp mặc định theo `createdAt` tăng dần (FIFO - đơn đặt trước lên trước) để shop xử lý đơn sắp hết hạn trước.

### 4.3. Chọn Hàng Loạt & Thanh Tác Vụ Nổi (Bulk Selection & Floating Bar)
- Ở tab `Chờ xác nhận`:
  - Mỗi đơn hàng có một checkbox chọn đơn.
  - Header danh sách có checkbox "Chọn tất cả ([N] đơn)".
  - Khi có ít nhất 1 đơn được chọn, hiển thị **Thanh tác vụ nổi (Floating Action Bar)** cố định ở góc dưới màn hình:
    - Hiển thị: `"Đã chọn {selectedCount} đơn hàng"`.
    - Nút `Bỏ chọn`.
    - Nút `Xác nhận tất cả ({selectedCount})` (nút màu indigo/primary nổi bật, có loading spinner khi đang xử lý).
- Khi bấm "Xác nhận tất cả":
  - Gọi mutation `bulkConfirmMutation.mutate({ data: { orderIds: selectedOrderIds } })`.
  - Nếu tất cả thành công: Hiển thị Toast thông báo thành công, bỏ tick chọn, reload danh sách đơn hàng.
  - Nếu có đơn thất bại (Partial Success): Mở Modal tổng kết chi tiết:
    - `"Xác nhận thành công X đơn hàng."`
    - `"Có Y đơn hàng không thể xác nhận:"` (kèm danh sách mã đơn và lý do: sai trạng thái...).

### 4.4. Modal Từ Chối Đơn Hàng (Reject / Cancel Order)
- Mở khi bấm nút "Từ chối" / "Hủy đơn" trên đơn `PENDING` hoặc `CONFIRMED`.
- Lựa chọn lý do chuẩn qua Radio / Select:
  - `Hết hàng trong kho`
  - `Sai giá hoặc thông tin sản phẩm`
  - `Không liên lạc được với khách hàng`
  - `Người mua yêu cầu hủy đơn`
  - `Lý do khác`
- Ô nhập text ghi chú bổ sung (`cancelNote`).
- Dòng lưu ý: *"Hệ thống sẽ gửi thông báo lý do hủy trực tiếp đến khách hàng và tự động hoàn trả tồn kho nguyên tử."*

### 4.5. Modal Xem Chi Tiết Đơn Hàng & Dòng Thời Gian (Order Detail & Timeline)
Khi bấm "Xem chi tiết" trên bất kỳ đơn hàng nào, mở Modal chi tiết gồm:
1. **Thông tin chung:** Mã đơn, trạng thái hiện tại (StatusBadge), thời gian đặt hàng, nhãn đếm ngược 24h (nếu PENDING).
2. **Thông tin giao nhận & Khách hàng:** Tên người nhận, Số điện thoại, Địa chỉ giao hàng chi tiết.
3. **Phương thức thanh toán:** COD (Thanh toán khi nhận hàng) - Trạng thái: Chưa thanh toán / Đã thu tiền.
4. **Ghi chú của khách hàng:** Hiển thị nổi bật nội dung khách dặn dò khi đặt hàng (hoặc ghi *"Không có ghi chú"* nếu rỗng).
5. **Danh sách mặt hàng chi tiết:**
   - Ảnh sản phẩm (nếu có), Tên sản phẩm.
   - Phân loại rõ ràng: Màu sắc, Kích cỡ (Size), Mã SKU.
   - Đơn giá x Số lượng = Thành tiền từng dòng.
6. **Tổng kết thanh toán:** Tiền hàng, Phí vận chuyển, Tổng thanh toán đơn hàng.
7. **Thông tin hủy đơn (nếu status = CANCELLED):** Ai hủy (`Khách hàng` / `Người bán` / `Hệ thống`), Lý do hủy, Ghi chú hủy.
8. **Dòng thời gian đơn hàng (Order Timeline):**
   - Danh sách các bước dạng trục thời gian dọc:
     - 🕒 *Khách hàng đặt đơn* (ngày giờ)
     - 📦 *Shop xác nhận đơn* (ngày giờ, người xác nhận)
     - 🚚 *Bàn giao cho đơn vị vận chuyển* (ngày giờ)
     - ✅ *Giao hàng thành công & Thu tiền COD* (ngày giờ)
     - ❌ *Đơn bị hủy* (ngày giờ, lý do, người thao tác)

---

## 5. Quy Ước Code & Ràng Buộc (Constraints)
- **Tuyệt đối cấm toán tử 3 ngôi (`? :`)** ở cả Java Backend và React Frontend (dùng `if` + early return, trong JSX dùng `&&`).
- Backend:
  - Spring Boot 4 + Spring Data JDBC. Mọi chuyển trạng thái đơn hàng đều khóa dòng `SELECT ... FOR UPDATE` và chạy trong Transaction.
  - Sử dụng RFC 9457 `ProblemDetail` cho mã lỗi HTTP (400, 403, 404, 409).
  - NullAway: non-null mặc định, chỉ dùng `@Nullable` khi thật sự optional.
  - TDD: Viết unit/integration test cho trừ kho lúc checkout, hoàn kho lúc hủy, `bulkConfirm`, `cancelBySystem`, `OrderAutoCancelJob`.
- Frontend:
  - React 19 + Tailwind CSS v4 + `@phosphor-icons/react`.
  - Chạy `pnpm codegen` sau khi backend export `openapi.json`.
  - Chạy `pnpm format`, `pnpm lint` (0 error, 0 warning), `pnpm test --run`, `pnpm build` xanh trước khi hoàn thành.
