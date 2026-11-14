# Thiết kế chức năng 1.4.5 & 1.4.6 — Theo dõi đơn hàng & Lịch sử đơn hàng

- **Ngày:** 2026-11-12
- **Phạm vi repo:** `fashionstore` (Backend Spring Boot + PostgreSQL/Flyway) & `fashionstore-ui` (Frontend React 19 + TypeScript + Tailwind v4 + Orval)
- **Trạng thái:** Đã hoàn tất 2 vòng kiểm tra kỹ thuật (*builder check*) và tích hợp toàn bộ chi tiết đối chiếu codebase — chờ xác nhận bản thiết kế để chuyển sang bước lập kế hoạch chi tiết (`writing-plans`).

---

## 1. Mục tiêu & Ngữ cảnh

Hệ thống hiện chỉ có trang `/account/orders` (`Orders.tsx`) và `/account/orders/:id` (`OrderDetail.tsx`) ở mức tối giản: thiếu thanh tiến trình theo dõi trực quan, thiếu thông tin vận chuyển (đơn vị vận chuyển, mã vận đơn, lịch sử di chuyển gói hàng), thiếu job tự động hoàn thành đơn sau 7 ngày (dù đã có cài đặt `order.auto_complete_days` trong DB), thiếu lý do hủy đơn phía khách hàng, thiếu tìm kiếm, thiếu đồng bộ trạng thái tab và trạng thái đổi trả với phía Shop, thiếu "Mua lại" hợp lệ theo tồn kho và thiếu cơ chế đánh giá 1 lần + sửa 1 lần.

Bản thiết kế này hợp nhất trọn vẹn **1.4.5 (Theo dõi đơn hàng)** và **1.4.6 (Lịch sử đơn hàng)** thành một trải nghiệm xuyên suốt từ lúc đặt đơn COD đến khi hoàn thành / đánh giá / đổi trả.

### Goals (Mục tiêu)
1. **Đồng bộ trạng thái & 7 tab với phía Shop (`SellerOrdersTab`):** `Tất cả`, `Chờ xác nhận`, `Đang chuẩn bị`, `Đang giao`, `Hoàn thành`, `Đã hủy`, `Đổi trả` (ánh xạ 1-1 tới `OrderStatus` + `ReturnRequest`).
2. **Theo dõi tiến trình trực quan (1.4.5):** Thanh progress bar đa chặng (`Đặt hàng` → `Chờ xác nhận` → `Đang chuẩn bị` → `Đang giao` → `Đã giao hàng` → `Hoàn thành`, kèm nhánh `Đã hủy` và trạng thái `Đổi trả` đồng bộ Shop) cùng mốc thời gian và nhật ký trạng thái (`GET /api/orders/{id}/timeline`).
3. **Thông tin vận chuyển đầy đủ (1.4.5):** Hiển thị đơn vị vận chuyển (`shippingCarrier`), mã vận đơn (`trackingCode`, hỗ trợ sao chép), và lịch sử di chuyển gói hàng (`order_tracking_event` qua `GET /api/orders/{id}/tracking`), đồng bộ hai chiều với modal giao hàng & cập nhật hành trình của Shop (`SellerOrdersTab`).
4. **Nút "Đã nhận hàng" & Tự động hoàn thành sau 7 ngày (1.4.5):** Khách bấm xác nhận hoàn thành ngay trên đơn `DELIVERED` (nếu chưa có yêu cầu đổi trả `REQUESTED | APPROVED | COMPLETED`); `OrderAutoCompleteJob` đọc `order.auto_complete_days` (mặc định 7 ngày) tự động hoàn thành khi quá hạn.
5. **Hủy đơn hàng kèm 4 lý do chuẩn (1.4.5):** Nút `"Hủy đơn hàng"` chỉ mở ở trạng thái `"Chờ xác nhận"` (`PENDING`); modal bắt buộc chọn 1 trong 4 lý do (`Đổi ý`, `Tìm được giá rẻ hơn`, `Đặt nhầm`, `Khác`).
6. **Tìm kiếm, Chi tiết, Mua lại & Đánh giá 1 + 1 (1.4.6):**
   - Tìm theo mã đơn hoặc tên sản phẩm; xem đầy đủ sản phẩm (ảnh, tên, phân loại, SKU, số lượng, giá), thông tin giao nhận & tổng tiền COD.
   - `"Mua lại"`: thêm nguyên tử qua `POST /api/cart/reorder/{orderId}` (chuyển `/cart` nếu đủ 100%, ở lại hiện tóm tắt nếu có món thiếu/hết/ngừng bán).
   - `"Đánh giá"`: chỉ mở khi `DELIVERED` và `receivedAt != null`; **viết 1 lần duy nhất + sửa tối đa 1 lần duy nhất**, không cho xóa để lách luật.
7. **Đồng bộ tab Đổi trả Shop ↔ Khách (1.4.6):** Hiển thị song song trạng thái đơn và `ReturnStatus` cùng phương án, số tiền hoàn, ghi chú Shop, toàn bộ ảnh/video bằng chứng và đồng bộ realtime qua WebSocket `/user/queue/notifications`.

### Non-Goals & Anti-Goals
- **Không** tích hợp webhook API trực tiếp từ hãng vận chuyển bên thứ ba (GHN/GHTK) — dùng mô hình dữ liệu ĐVVC + mã vận đơn + hành trình do Shop quản lý & hệ thống tự ghi mốc mặc định khi chuyển trạng thái.
- **Không** thêm trạng thái "Chờ thanh toán" (100% COD).
- **Không** sửa luồng Checkout (`CheckoutRequest` giữ nguyên; `OrderDetail` chỉ render `order.note` nếu khác rỗng).
- **Không** thay đổi DTO `AdminOrderDetailResponse` hay thêm cột vận chuyển/nhận hàng vào giao diện Admin `AdminOrderDetailPanel.tsx` (chỉ chỉnh bộ định dạng tiêu đề `DELIVERED -> DELIVERED` nếu hiển thị timeline chung để không hiện `"Đã giao → Đã giao"`).
- **Không** mở form viết/sửa đánh giá ngay trên trang sản phẩm (`ProductTabs.tsx` chỉ đọc và hiển thị badge `"Đã chỉnh sửa"`).
- **Không** vi phạm `docs/structure.md` (không repository chéo domain, không circular service dependency).

---

## 2. Ràng buộc kiến trúc & Quyết định đã chốt

1. **Đồng bộ 7 tab & hiển thị tab `Hoàn thành` vs `Đổi trả` (Đã chốt Q1 + chuẩn hóa nhãn `SHIPPING`):**
   - 7 tab của Buyer: `ALL` (`Tất cả`), `PENDING` (`Chờ xác nhận`), `CONFIRMED` (`Đang chuẩn bị`), `SHIPPING` (`Đang giao`), `DELIVERED` (`Hoàn thành`), `CANCELLED` (`Đã hủy`), `RETURNS` (`Đổi trả`).
   - Tại `SellerOrdersTab`, giữ nguyên nhãn góc nhìn người bán `"Đã giao cho ĐVVC"` cho tab `SHIPPING` (tránh đổi copy/vỡ `SellerOrdersTab.test.tsx` hiện hành) — cả hai cùng ánh xạ 1-1 tới `OrderStatus.SHIPPING`.
   - Đơn `DELIVERED` có `return_request` vẫn hiển thị ở **cả tab `Hoàn thành` và tab `Đổi trả`**, kèm badge `ReturnStatus` song song ở cả hai nơi (bao gồm cả thẻ `DELIVERED` trên `SellerOrdersTab.tsx`).
   - Trên tab `Đổi trả` của Buyer (`Orders.tsx`), **hiển thị 1 thẻ cho mỗi đơn hàng** có `returnStatus != null` (thể hiện yêu cầu mới nhất), tránh nhân bản thẻ nếu khách từng bị từ chối và gửi lại yêu cầu mới; trên `OrderDetail.tsx`, hiển thị yêu cầu mới nhất kèm lịch sử các lần gửi trước (nếu có). *(Quyết định phụ kỹ thuật).*
2. **Tương tác `"Đã nhận hàng"`, `"Yêu cầu đổi/trả"`, `"Đánh giá"` & Tự động hoàn thành (Đã chốt Q2):**
   - Khách được gửi `"Yêu cầu đổi/trả"` ngay khi đơn `DELIVERED` (chưa cần bấm `"Đã nhận hàng"`, không giới hạn số ngày ở backend).
   - Khi đơn có yêu cầu đổi/trả `REQUESTED | APPROVED | COMPLETED`: ẩn/khóa nút `"Đã nhận hàng"` (API trả `409 Conflict`), chưa cho đánh giá, và `OrderAutoCompleteJob` **không** tự động hoàn thành đơn đó. Nếu yêu cầu đổi trả bị `REJECTED` (và không còn yêu cầu mở khác): nút `"Đã nhận hàng"` mở lại, và nếu đã vượt quá `deliveredAt + autoCompleteDays` thì `OrderAutoCompleteJob` sẽ tự động hoàn thành ở lượt quét kế tiếp; đối soát `SellerSettlement` (`HOLDING` + `holdUntil = now + 3d` tạo lúc `deliver()`) giữ nguyên không bị tác động. *(Quyết định phụ làm rõ edge case).*
   - `confirmReceipt()` chống ghi đè: nếu `receivedAt != null`, trả về đơn hiện tại (idempotent), không ghi đè mốc nhận đầu tiên.
3. **Nguồn dữ liệu vận chuyển Shop ↔ Khách (Đã chốt cho 1.4.5):**
   - Shop chọn/nhập ĐVVC (`shippingCarrier`) & nhập hoặc tự sinh `trackingCode` qua modal *"Giao cho ĐVVC"* (`PATCH /api/seller/orders/{id}/ship`, hỗ trợ optional body & fallback từng trường độc lập), xem & cập nhật mốc di chuyển gói hàng (`GET`/`POST /api/seller/orders/{id}/tracking`) khi đơn `SHIPPING`. Hệ thống tự ghi mốc mặc định khi `ship()` và `deliver()` (không ghi vào `order_tracking_event` lúc `checkout`/`confirm`).
4. **Đánh giá 1 lần + sửa 1 lần:**
   - Bảng `review` giữ `uq_review_one_per_buyer (product_id, buyer_id)`.
   - Migration `V34` thêm `edited_at TIMESTAMPTZ`; sửa lần 1 gán `edited_at = now()`; sửa lần 2 trả `409 Conflict`; gỡ `DELETE /api/products/{productId}/reviews` (`405`).
   - Vẫn cho viết/sửa 1 lần kể cả khi Seller sau đó ẩn sản phẩm sau khi khách đã nhận hàng (`productId != null`).
5. **Điều hướng sau "Mua lại" (Đã chốt Q3):**
   - Thêm đủ 100% (`addedCount > 0 && adjustedCount == 0 && skippedCount == 0`) → chuyển thẳng `/cart` kèm thông báo; có điều chỉnh/bỏ qua → giữ nguyên trang hiện modal/alert tóm tắt kèm nút mở `/cart`.
6. **Ràng buộc dependency & code style trong `fashionstore`:**
   - Java: tuân thủ `docs/code-guide.md` (**cấm toán tử 3 ngôi `? :`**, NullAway non-null mặc định, springdoc đầy đủ, message exception tiếng Anh).
   - Hướng dependency Spring (tuyệt đối không tạo vòng):
     - `OrderService` đã inject `ReturnRequestService` → `ReturnRequestService` **tuyệt đối không** inject `OrderService` (chỉ dùng `OrderRepository` cùng domain + `ShopService` + `NotificationService`).
     - `CatalogService` → `OrderService`, `ReviewService` → `OrderService`, `CartService` → `OrderService`. Vì vậy `OrderService` chỉ inject `ProductStockService`, `ShopService`, `SystemSettingsService`, `NotificationService` (và repositories trong `features/order`).
     - `POST /api/cart/reorder/{orderId}` đặt tại `CartController` / `CartService`; `GET /api/reviews/mine` đặt tại `MyReviewController` trong `features/review`.

---

## 3. Các phương án đã cân nhắc & Lựa chọn

1. **Làm giàu dữ liệu Đơn hàng + Bảng tracking riêng vs Tái cấu trúc bảng `orders`:**
   - *Chọn:* Giữ cấu trúc cốt lõi bảng `orders`, chỉ thêm `shipping_carrier` & `tracking_code` trên `orders` + bảng con `order_tracking_event` (`V35`) và `edited_at` trên `review` (`V34`); làm giàu `OrderResponse`, `OrderItemResponse`, `ReturnRequestResponse` bằng batch query tránh N+1. An toàn với dữ liệu & các module Admin/Seller/Settlement đang chạy.
2. **Lịch sử di chuyển gói hàng từ Shop + tự sinh mốc hệ thống vs Chỉ hiển thị 4 mốc trạng thái có sẵn:**
   - *Chọn:* Bảng `order_tracking_event` kết hợp tự sinh khi `ship()` / `deliver()` + Shop cập nhật trạm trung chuyển, đáp ứng thực chất yêu cầu "mã vận đơn, đơn vị vận chuyển, lịch sử di chuyển gói hàng" của 1.4.5.
3. **Batch `POST /api/cart/reorder/{orderId}` vs Loop client:**
   - *Chọn:* Batch transaction trong `CartService` để kiểm soát shop status, variant tồn tại, sản phẩm `ACTIVE`, và trần tồn kho nguyên tử.
4. **Khóa sửa lần 2 bằng `edited_at` + Gỡ `DELETE` vs Soft-delete:**
   - *Chọn:* `edited_at` + bỏ `DELETE` để ngăn xóa rồi viết lại, giữ đơn giản cho tính `averageRating`.

---

## 4. Kiến trúc chi tiết & Giao diện API / UI

### 4.1. Database & Entity (`fashionstore`)
- **Migration `V34__review_edit_once.sql`:**
  ```sql
  ALTER TABLE review
      ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
  ```
- **Migration `V35__order_tracking_and_auto_complete.sql`:**
  *(Cột `location` dùng `TEXT` để đồng nhất với `orders.shipping_address TEXT` tối đa 1000 ký tự khi `deliver()` ghi địa chỉ giao hàng).*
  ```sql
  ALTER TABLE orders
      ADD COLUMN IF NOT EXISTS shipping_carrier VARCHAR(100),
      ADD COLUMN IF NOT EXISTS tracking_code VARCHAR(100);

  CREATE INDEX IF NOT EXISTS idx_orders_delivered_unreceived
      ON orders (delivered_at)
      WHERE status = 'DELIVERED' AND received_at IS NULL;

  CREATE TABLE IF NOT EXISTS order_tracking_event (
      id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      order_id    UUID NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
      status_code VARCHAR(40) NOT NULL,
      title       VARCHAR(200) NOT NULL,
      location    TEXT,
      description VARCHAR(500),
      event_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
      created_by  UUID,
      created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
  );

  CREATE INDEX IF NOT EXISTS idx_order_tracking_event_order_time
      ON order_tracking_event (order_id, event_time DESC, created_at DESC);
  ```
- **Entity [`Review`](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Review.java):**
  - Thêm `@Nullable Instant editedAt` (mặc định `null`).
  - `update(stars, comment)`: nếu `this.editedAt != null` → `409 Conflict` (`"Review has already been edited once"`); ngược lại cập nhật và đặt `editedAt = Instant.now()`.
- **Entity [`Order`](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Order.java):**
  - Thêm `@Nullable String shippingCarrier`, `@Nullable String trackingCode`.
  - Overload `ship(String shippingCarrier, String trackingCode)` (gán `shippingCarrier`, `trackingCode`, `SHIPPING`, `shippedAt = now()`); giữ `ship()` không tham số gọi fallback `ship("GHN", "VN-" + this.code)` cho các test/seeder cũ.
  - Overload `cancel(@Nullable String reason, @Nullable String note)` lưu `cancelReason` / `cancelNote` khi Buyer cung cấp lý do; giữ `cancel()` không tham số cho tương thích ngược.
- **Entity mới `OrderTrackingEvent`** (`model/OrderTrackingEvent.java`): `id`, `orderId`, `statusCode`, `title`, `@Nullable location`, `@Nullable description`, `eventTime`, `@Nullable createdBy`, `createdAt`.

### 4.2. Backend API, Jobs & Services (`fashionstore`)

#### A. Theo dõi đơn hàng, Vận chuyển, Hủy đơn & Tự động hoàn thành (`features/order`, `features/catalog`, `features/shop`)
- **Batch lookup tránh N+1:**
  - `ShopService`: `findShopSummariesByIds(Collection<UUID> ids)` trả `Map<UUID, ShopSummary>` (`id`, `name`, `slug`, `approved`).
  - `ProductStockService` (inject `ProductImageRepository`): batch trả `imageKey` theo **cùng thứ tự ưu tiên của `CartService.toResponse`** (ưu tiên `ProductImage` gắn đúng `variantId` của `OrderItem`, sau đó mới rơi về ảnh có `sortOrder` nhỏ nhất của `productId`) và `productAvailable` (`product.status == ACTIVE && shopApproved`). *(Áp dụng chung qua `OrderService::toItemResponse`, đồng thời tương thích cho `getForAdmin`).*
  - `OrderService`: batch lấy `ReturnStatus` mới nhất theo `orderIds` (dùng `ReturnRequestRepository` / `OrderMonitoringQueryRepository`, thứ tự `created_at DESC`) + đọc `SystemSettingsService.current().autoCompleteDays()`.
- **`OrderResponse` bổ sung:**
  - `String shopName`, `@Nullable String shopSlug`
  - `@Nullable String shippingCarrier`, `@Nullable String trackingCode`
  - `@Nullable Instant shippedAt`, `@Nullable Instant deliveredAt`, `@Nullable Instant receivedAt`
  - `@Nullable Instant autoCompleteAt` (tính `deliveredAt.plus(autoCompleteDays, DAYS)` khi `status == DELIVERED && receivedAt == null` và không có `returnStatus` thuộc `REQUESTED | APPROVED | COMPLETED`; ngược lại `null`)
  - `@Nullable String returnStatus` (`REQUESTED | APPROVED | REJECTED | COMPLETED` hoặc `null`)
- **`OrderItemResponse` bổ sung:**
  - `@Nullable String imageKey`, `boolean productAvailable`.
- **Giao hàng & Hành trình phía Shop (`SellerOrderController`):**
  - `PATCH /api/seller/orders/{id}/ship`: nhận `@RequestBody(required = false) @Valid @Nullable ShipOrderRequest` (`@Nullable @Size(max = 100) String shippingCarrier`, `@Nullable @Size(max = 100) String trackingCode`). Cho phép nhập tự do `shippingCarrier` (`<= 100` ký tự), và **áp dụng fallback độc lập từng trường** nếu trường đó null/blank (`shippingCarrier` mặc định `"GHN"`, `trackingCode` mặc định `"VN-" + order.getCode()`). Tự động ghi 1 `OrderTrackingEvent` mã `PICKED_UP` (*"Đơn vị vận chuyển đã tiếp nhận kiện hàng"*, mô tả ĐVVC + mã vận đơn), ghi `shippingCarrier` + `trackingCode` vào chi tiết `shop_member_audit` (`ORDER_SHIP`) và nội dung thông báo `ORDER_SHIPPED` gửi Buyer (*"Shop đã bàn giao đơn #... cho <carrier> (Mã vận đơn: <code>).").*
  - `GET /api/seller/orders/{id}/tracking` (quyền `ORDER_READ`): trả `List<OrderTrackingEventResponse>` (sắp `eventTime DESC, createdAt DESC`) để Seller xem lịch sử hành trình trong `SellerOrdersTab`. *(Quyết định phụ đồng bộ).*
  - `POST /api/seller/orders/{id}/tracking` (quyền `ORDER_PROCESS`): nhận `@Valid @RequestBody AddOrderTrackingEventRequest` (`@NotBlank @Size(max = 40) String statusCode` thuộc danh sách hợp lệ `PICKED_UP | IN_TRANSIT | SORTING_FACILITY | OUT_FOR_DELIVERY | CUSTOM`, `@NotBlank @Size(max = 200) String title`, `@Nullable @Size(max = 500) String location`, `@Nullable @Size(max = 500) String description`). Server tự gán `eventTime = Instant.now()`. Chỉ cho phép khi `order.status == SHIPPING` (`409` nếu sai trạng thái). Ghi `shop_member_audit` hành động `ORDER_TRACKING_UPDATE` (có nhãn/badge tương ứng trong `sellerPermissions.ts`) và gửi notification `NotificationCategory.ORDER`, type `ORDER_TRACKING_UPDATED`, link `"/account/orders/" + orderId` cho Buyer.
  - Khi Shop bấm `deliver()` (`PATCH /api/seller/orders/{id}/deliver`): tự động ghi thêm `OrderTrackingEvent` mã `DELIVERED` (*"Giao hàng thành công"*, `location` lấy `order.getShippingAddress()` nếu khác rỗng, ngược lại `"Địa chỉ người nhận"`).
- **Buyer Theo dõi & Hành trình (`OrderController`):**
  - `GET /api/orders/{id}/timeline` → `List<BuyerOrderTimelineResponse(fromStatus, toStatus, actor, note, createdAt)>` (kiểm tra ownership `buyerId`; **ẩn `changedBy` UUID và `changedByEmail`**).
  - `GET /api/orders/{id}/tracking` → `List<OrderTrackingEventResponse(id, statusCode, title, location, description, eventTime, createdAt)>` (kiểm tra ownership `buyerId`, ẩn `createdBy`, sắp theo `eventTime DESC, createdAt DESC`).
- **Buyer Hủy đơn (`PATCH /api/orders/{id}/cancel`):**
  - Nhận `@RequestBody(required = false) @Valid @Nullable BuyerCancelOrderRequest` (`@NotBlank String reason`, `@Nullable @Size(max = 500) String note`).
  - Nếu client không gửi body (null — tương thích ngược client/test cũ), gọi `order.cancel()` như cũ. Nếu có JSON body (kể cả `{}` rỗng), `@NotBlank` validate trả `400 Bad Request`; `reason` phải thuộc `BuyerCancelReason` (`CHANGE_MIND`, `FOUND_CHEAPER`, `ORDERED_BY_MISTAKE`, `OTHER`) và khi chọn `OTHER`, `note` bắt buộc khác rỗng (`400 Bad Request`).
  - Lưu `cancelReason` + `cancelNote` vào `Order` & `OrderStatusHistory.note`, đồng thời đưa lý do tiếng Việt vào nội dung notification `ORDER_CANCELLED` gửi Shop khi Buyer có gửi lý do.
- **Buyer Xác nhận đã nhận hàng (`PATCH /api/orders/{id}/received`):**
  - Nếu đơn đang có `ReturnRequest` ở trạng thái `REQUESTED | APPROVED | COMPLETED` → trả `409 Conflict` (`"Cannot confirm receipt while return request is active or completed"`).
  - Nếu `order.getReceivedAt() != null` → trả về đơn hiện tại (không ghi đè). Ngược lại gọi `order.confirmReceipt()`, ghi `OrderStatusHistory` (`DELIVERED` → `DELIVERED`, actor `BUYER`, note `"Khách hàng xác nhận đã nhận được hàng"`).
- **Job Tự động hoàn thành sau 7 ngày (`OrderAutoCompleteJob` + `OrderService`):**
  - Theo khuôn mẫu [`OrderAutoCancelJob`](file:///d:/fashionstore/fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJob.java) (`@Scheduled` + `ApplicationReadyEvent`, property `${fashionstore.order.auto-complete-interval-ms:300000}`).
  - Đọc `autoCompleteDays = systemSettings.current().autoCompleteDays()` (mặc định 7). Nếu `autoCompleteDays <= 0`, bỏ qua.
  - Truy vấn các đơn `status = 'DELIVERED'`, `received_at IS NULL`, `delivered_at <= now() - autoCompleteDays ngày` và **không có** `return_request` ở trạng thái `REQUESTED | APPROVED | COMPLETED` (những đơn đã từng bị `REJECTED` vẫn được chọn khi quá `deliveredAt + autoCompleteDays`).
  - Gọi `orderService.systemAutoCompleteOrder(orderId)` cho từng đơn trong 1 transaction riêng: gán `receivedAt = now()`, ghi `OrderStatusHistory` (`DELIVERED` → `DELIVERED`, `changedBy = null`, note `"Hệ thống tự động xác nhận hoàn thành sau " + days + " ngày"`), gửi thông báo cho Buyer `NotificationCategory.ORDER`, type `ORDER_COMPLETED`, link `"/account/orders/" + orderId`. Không phát sinh audit/notification cho Seller và giữ nguyên `SellerSettlement`.
- **Cập nhật link thông báo đơn hàng (`OrderService`):** Cập nhật tất cả **6 luồng gửi thông báo cho Buyer** (`confirm`, `ship`, `deliver`, `sellerCancel`, `cancelByAdmin`, `systemCancelExpiredOrder`) cộng với `ORDER_COMPLETED` và `ORDER_TRACKING_UPDATED` sang `"/account/orders/" + order.getId()` (và cập nhật assertions tương ứng trong `OrderServiceTest`).

#### B. Đồng bộ Đổi trả & Thông báo (`features/order/ReturnRequestService`)
- `ReturnRequestService` dùng `OrderRepository`, `ReturnRequestRepository`, `ShopService`, `ShopAccessChecker`, `ShopAuditService`, `NotificationService` (**tuyệt đối không inject `OrderService`** để tránh circular dependency).
- `ReturnRequestResponse` bổ sung `String orderCode` và `String orderStatus` (batch lookup qua `OrderRepository`).
- `create`: validate `reason` thuộc `(WRONG_ITEM, DAMAGED, NOT_AS_DESCRIBED, CHANGE_MIND, OTHER)` (`400 Bad Request` nếu sai; `description` giữ `@Nullable` tùy chọn); chặn `409 Conflict` nếu đã có `return_request` trạng thái `REQUESTED | APPROVED | COMPLETED` (cho gửi lại nếu `REJECTED`). Cập nhật `ReturnRequestServiceUnitTest` tương ứng (`"DEFECTIVE"` → `"DAMAGED"` + verify `NotificationService`).
- Thông báo realtime: `create` gửi `NotificationCategory.SELLING` (`RETURN_REQUESTED`, title/body có `#<orderCode>`, link `"/shop?shopId=" + shopId + "&tab=returns&orderId=" + order.getId()`) tới `shopService.findActiveUserIdsByShop(shopId)`; `approve` / `reject` / `complete` gửi `NotificationCategory.ORDER` (`RETURN_APPROVED` / `RETURN_REJECTED` / `RETURN_COMPLETED`, link `"/account/orders/" + orderId`) tới `buyerId`.

#### C. Mua lại (`features/cart`)
- `POST /api/cart/reorder/{orderId}` tại `CartController` → `CartService.reorder(buyerId, orderId)`.
- Dùng `orders.getDetail(buyerId, orderId)` để kiểm tra quyền sở hữu (`404` nếu không tồn tại hoặc khác `buyerId`).
- Trả `ReorderResponse(addedCount, adjustedCount, skippedCount, List<ReorderItemIssue> skippedItems, List<ReorderItemIssue> adjustedItems)` trong đó `ReorderItemIssue(@Nullable UUID productId, @Nullable UUID variantId, String productName, String variantLabel, int requestedQuantity, int addedQuantity, int availableStock, String reason)`.
- Shop không `APPROVED` → `409 Conflict`. Shop `APPROVED`: tính trực tiếp tồn kho khả dụng (có trừ số lượng đã tồn tại trong giỏ) trong `CartService.reorder` (không đi qua `CartService.addItem` vì `addItem` không kiểm tra trần tồn kho), giữ nguyên `priceAtAdded` nếu biến thể đã có sẵn trong giỏ (hoặc khởi tạo bằng giá hiện tại nếu thêm mới). Trả **`200 OK`** kèm lý do chi tiết ngay cả khi `addedCount == 0 && adjustedCount == 0`:
  - `productId == null || variantId == null` hoặc variant không tồn tại → `"Sản phẩm hoặc phân loại không còn tồn tại"`
  - Product không `ACTIVE` → `"Sản phẩm đã ngừng kinh doanh"`
  - Tồn kho `<= 0` → `"Sản phẩm đã hết hàng"`
  - Đã đạt trần tồn kho trong giỏ → `"Số lượng trong giỏ đã đạt mức tối đa trong kho"`
  - Điều chỉnh một phần (`addedQuantity < requestedQuantity`) → `"Chỉ thêm được <addedQuantity>/<requestedQuantity> sản phẩm do giới hạn tồn kho"`

#### D. Đánh giá 1 lần + Sửa 1 lần (`features/review`)
- `ReviewResponse` và `SellerReviewResponse` bổ sung `boolean edited` và `@Nullable Instant editedAt`.
- Giữ `@Size(max = 2000)` cho `comment` theo DTO hiện tại.
- Thêm `MyReviewController` với `GET /api/reviews/mine` (`@SecurityRequirement(name = "bearerAuth")`), trả danh sách review của `buyerId` sắp xếp mới nhất theo `updatedAt DESC, createdAt DESC`.
- Gỡ `@DeleteMapping` khỏi `ReviewController` & bỏ `deleteReview` buyer ở `ReviewService` (`DELETE` trả `405`). Cập nhật `ReviewServiceTest` & `ReviewControllerTest`.

### 4.3. Đồng bộ Frontend (`fashionstore-ui`)
Sau khi sửa backend, chạy `pnpm build` (`orval && tsc -b && vp build`) để cập nhật `@generated/model` và `@generated/endpoints`.

1. **Điều hướng & Routes (`Account.tsx`, `App.tsx`, `Orders.tsx`, `OrderDetail.tsx`):**
   - Đồng bộ URL search params `?tab=<TAB>&q=<SEARCH>` trên `/account/orders`.
   - Thêm lối tắt *"Đơn mua của tôi"* tại cột trái `Account.tsx` trỏ tới `/account/orders`.
   - Link sản phẩm trỏ đúng route **`/product/${item.productId}`** chỉ khi `item.productAvailable === true` (ngược lại hiện nhãn *"Ngừng kinh doanh"*).
2. **Mapping trạng thái, Lý do & Timeline (`src/lib/orderStatus.ts`, `src/lib/order/orderMonitoring.ts`, `src/lib/seller/sellerPermissions.ts`):**
   - `BUYER_ORDER_TABS`: `ALL` (`Tất cả`), `PENDING` (`Chờ xác nhận`), `CONFIRMED` (`Đang chuẩn bị`), `SHIPPING` (`Đang giao`), `DELIVERED` (`Hoàn thành`), `CANCELLED` (`Đã hủy`), `RETURNS` (`Đổi trả`).
   - `RETURN_STATUS_META`: đồng bộ nhãn & màu với `sellerStatusMeta` (`REQUESTED`, `APPROVED`, `COMPLETED`, `REJECTED`).
   - `RETURN_RESOLUTION_LABELS`: map đủ cả 3 (`REFUND`, `PARTIAL_REFUND`, `EXCHANGE`).
   - `RETURN_REASON_LABELS`: `DAMAGED`, `WRONG_ITEM`, `NOT_AS_DESCRIBED`, `CHANGE_MIND`, `OTHER`.
   - `BUYER_CANCEL_REASON_OPTIONS`: `CHANGE_MIND` (`"Đổi ý"`), `FOUND_CHEAPER` (`"Tìm được giá rẻ hơn"`), `ORDERED_BY_MISTAKE` (`"Đặt nhầm"`), `OTHER` (`"Khác"`).
   - Cập nhật `cancelReasonLabel(reason, cancelledBy?)` trong `orderMonitoring.ts`: giữ `"Lý do khác"` cho Admin (`CANCEL_REASON_OPTIONS`) khi `cancelledBy !== 'BUYER'`, trả `"Khác"` khi `cancelledBy === 'BUYER' && reason === 'OTHER'`, và map đủ 3 mã hủy mới của Buyer để dùng chung ở `Orders`, `OrderDetail`, `SellerOrdersTab`, và `AdminOrderDetailPanel`.
   - Định dạng bước `DELIVERED -> DELIVERED` trong timeline helper (cả `OrderDetail` và `AdminOrderDetailPanel`) hiển thị `"Xác nhận hoàn thành"` (kèm `note` chi tiết từ Buyer hoặc Hệ thống) thay vì `"Đã giao → Đã giao"`.
   - `sellerPermissions.ts`: bổ sung nhãn & badge cho audit action `ORDER_TRACKING_UPDATE` (*"Cập nhật hành trình vận chuyển"*).
3. **Danh sách Đơn hàng (`/account/orders` — `Orders.tsx`):**
   - **7 tab có badge tổng số lượng + ô tìm kiếm** (khớp không phân biệt hoa thường theo `code` bỏ `#`, hoặc `items[].productName`). Riêng tab `Đổi trả` hiển thị 1 thẻ/đơn (`orders.filter(o => o.returnStatus != null)`).
   - **Thẻ đơn hàng:** Tên shop + link shop, mã đơn, ngày đặt, badge `OrderStatus`, badge `ReturnStatus` song song nếu có, mini-badge vận chuyển (`ĐVVC • Mã vận đơn` khi đã có), danh sách item (ảnh/tên/phân loại/số lượng/giá), tổng tiền COD, lý do hủy (nếu hủy), tóm tắt đổi trả trên tab `Đổi trả`.
   - **CTA theo trạng thái:**
     - `PENDING`: `"Hủy đơn hàng"` (modal bắt buộc chọn 1 trong 4 lý do + nhập ghi chú bắt buộc khi chọn `Khác`) + `"Theo dõi / Chi tiết"`.
     - `CONFIRMED` / `SHIPPING`: `"Theo dõi đơn hàng"` + `"Mua lại"`.
     - `DELIVERED`: `"Mua lại"` + (nếu `returnStatus` không thuộc `REQUESTED | APPROVED | COMPLETED`: `"Yêu cầu đổi/trả"`, nếu `receivedAt == null` hiện `"Đã nhận hàng"` kèm ngày tự động hoàn thành theo `autoCompleteAt`, nếu `receivedAt != null` hiện `"Đánh giá"` / `"Sửa đánh giá"` / `"Đã đánh giá"` theo sản phẩm duy nhất trong đơn).
     - `CANCELLED`: `"Mua lại"` + `"Xem chi tiết"`.
4. **Chi tiết & Theo dõi đơn hàng (`/account/orders/:id` — `OrderDetail.tsx`):**
   - **Thanh Progress Bar trực quan:** 5 chặng (`Đặt hàng` → `Chờ xác nhận` → `Đang chuẩn bị` → `Đang giao` → `Hoàn thành`), kèm thanh tiến độ gradient, mốc thời gian từng bước (`createdAt`, `confirmedAt`, `shippedAt`, `deliveredAt`, `receivedAt`). Nhánh riêng khi `CANCELLED` (người hủy + lý do hủy) và dải trạng thái `Đổi trả` đồng bộ Shop khi có `returnStatus`.
   - **Banner & CTA Xác nhận nhận hàng:** Khi `DELIVERED` và `receivedAt == null` (chưa khóa bởi return), hiển thị nút **"Đã nhận hàng"** kèm chú thích *"Đơn sẽ tự động hoàn thành vào ngày DD/MM/YYYY nếu không có yêu cầu đổi/trả"* (`autoCompleteAt`).
   - **Khối Thông tin vận chuyển & Lịch sử di chuyển gói hàng:** Hiển thị ĐVVC, Mã vận đơn (+ nút sao chép), thời gian bàn giao, và timeline các chặng di chuyển gói hàng từ `GET /api/orders/{id}/tracking` bên cạnh/kết hợp `GET /api/orders/{id}/timeline`.
   - **Sản phẩm, Giá, Địa chỉ & Đánh giá 1 + 1:** Hiển thị `subtotal`, `shippingFee`, `total`, người nhận, `order.note` (chỉ khi khác rỗng), gom nhóm đánh giá theo `productId` duy nhất (`maxLength = 2000`, nhắc quy tắc 1 + 1). `ProductTabs.tsx` chỉ đọc và gắn badge `"Đã chỉnh sửa"` khi `edited === true`.
   - **Khối & Modal Đổi trả:** Hiển thị yêu cầu đổi/trả mới nhất + danh sách các lần gửi trước (nếu có) và render **toàn bộ `evidenceKeys`** (`<img>` hoặc `<video controls>` theo định dạng file); modal gửi yêu cầu hỗ trợ upload ảnh/video qua `POST /api/uploads/presign`.
5. **Phía Shop (`SellerOrdersTab.tsx` & `SellerReturnsTab.tsx` & `sellerAdapters.ts`):**
   - `SellerOrdersTab.tsx` & `sellerAdapters.ts`:
     - Dịch `order.cancelReason` qua `cancelReasonLabel(order.cancelReason, order.cancelledBy)`.
     - Hiển thị thêm badge `ReturnStatus` trên thẻ đơn ở tab `DELIVERED` nếu `order.returnStatus != null`.
     - Modal *"Giao cho ĐVVC"* chọn ĐVVC (`GHN`, `GHTK`, `Viettel Post`, `J&T Express`, `SPX Express`, hoặc nhập tay) + nhập mã vận đơn (để trống tự sinh).
     - Action *"Cập nhật hành trình"* cho đơn `SHIPPING`: gọi `GET /api/seller/orders/{id}/tracking` hiển thị lịch sử hiện tại và mở form chọn mốc preset / nhập vị trí & mô tả gọi `POST /api/seller/orders/{id}/tracking`.
   - `SellerReturnsTab.tsx` & `sellerAdapters.ts`:
     - Map & render **toàn bộ `evidenceKeys`** (không chỉ `evidenceKeys[0]`), hỗ trợ cả `<img>` và `<video controls>`.
     - Đọc query param `?orderId=` từ URL để lọc đúng đơn/yêu cầu tương ứng (khớp UUID `orderId` hoặc `orderCode`) kèm nút *"Xem tất cả yêu cầu đổi trả"* để xóa filter.
6. **Đồng bộ Realtime qua WebSocket (`/user/queue/notifications`):**
   - Lắng nghe mọi thông báo `ORDER_*` (bao gồm `ORDER_TRACKING_UPDATED`, `ORDER_COMPLETED`) và `RETURN_*`, tự `invalidateQueries` cho đơn hàng, tracking, timeline, thông tin đổi trả phía Buyer và phía Seller.

---

## 5. Phần để lại cho bước lập kế hoạch chi tiết & triển khai

- Bố cục responsive cụ thể (ngang desktop / dọc mobile cho progress bar 5 chặng, grid tracking + delivery info) tuân thủ `fashionstore-ui/docs/design-system.md`.
- Chi tiết câu SQL trong `OrderMonitoringQueryRepository` / `ReturnRequestRepository` / `OrderTrackingEventRepository` và cấu trúc helper trong `OrderService` / `CartService`.
- Chia tách sub-components (`OrderProgressStepper`, `ShipmentTrackingCard`, `OrderCard`, `CancelOrderModal`, `ReviewModal`, `ReviewProductPickerModal`, `ReturnRequestModal`, `ReorderResultModal`, `SellerShipOrderModal`, `SellerTrackingModal`).

---

## 6. Kế hoạch kiểm thử & xác minh (Verification Strategy)

1. **Backend (`fashionstore`):**
   - Unit + MockMvc + Testcontainers tests cho:
     - Vận chuyển & Tracking (`V35`): `ship(carrier, trackingCode)` & fallback từng field, `GET`/`POST /api/seller/orders/{id}/tracking` (`409` nếu không phải `SHIPPING`, tạo `ORDER_TRACKING_UPDATE` audit + `ORDER_TRACKING_UPDATED` notification), `deliver()` tự ghi mốc `DELIVERED` không vượt độ dài, `GET /api/orders/{id}/tracking` kiểm ownership, `GET /api/orders/{id}/timeline` ẩn thông tin nội bộ nhân viên.
     - Tự động hoàn thành: `OrderAutoCompleteJob` hoàn thành đơn `DELIVERED` quá `order.auto_complete_days` chưa `receivedAt`, bỏ qua đơn có return `REQUESTED | APPROVED | COMPLETED`, xử lý lại nếu `REJECTED`.
     - Hủy đơn (`BuyerCancelReason`): chấp nhận 4 lý do chuẩn + bắt buộc `note` khi `OTHER`, trả `400` khi gửi `{}` thiếu `reason`, vẫn giữ tương thích ngược không body.
     - Đổi trả & Thông báo: chặn trùng `409`, cho gửi lại sau `REJECTED`, `confirmReceipt` chặn `409` khi có return mở, thông báo hai chiều + link chính xác.
     - Đánh giá (`V34`): tạo 1, sửa 1 (`edited=true`), sửa 2 (`409`), xóa (`405`), `GET /api/reviews/mine`.
     - Mua lại: `POST /api/cart/reorder/{orderId}` các kịch bản đủ hàng, thiếu hàng, hết hàng, đạt trần trong giỏ, ngừng bán, shop khóa (`409`), tất cả skipped (`200 OK`).
   - Cập nhật các tests hiện có bị ảnh hưởng DTO/link/reason (`OrderServiceTest`, `OrderControllerTest`, `ReviewServiceTest`, `ReviewControllerTest`, `ReturnRequestServiceUnitTest`).
   - Chạy toàn bộ `./gradlew test` xanh 100%.
2. **Codegen & Frontend (`fashionstore-ui`):**
   - Chạy `pnpm build` (`orval && tsc -b && vp build`) xanh 100%.
   - Viết & cập nhật Vitest + Testing Library tests cho: `orderMonitoring.ts` (`cancelReasonLabel` Buyer/Admin + timeline `DELIVERED -> DELIVERED`), `orderStatus.ts`, `sellerPermissions.ts`, `Orders.tsx`, `OrderDetail.tsx`, `SellerOrdersTab.tsx`, `SellerReturnsTab.tsx`, `ProductTabs.tsx`, `AdminOrderDetailPanel.tsx`, WebSocket sync.
   - Chạy `pnpm test` (`vp test run`) và `pnpm lint` (`vp lint`) xanh 100%.
