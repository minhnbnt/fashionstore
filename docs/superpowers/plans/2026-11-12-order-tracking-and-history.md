# 1.4.5 Theo Dõi Đơn Hàng & 1.4.6 Lịch Sử Đơn Hàng Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (hoặc subagent-driven-development) để triển khai từng task theo chu kỳ TDD (RED → GREEN → REFACTOR). Khi lệnh test của 1 task đang chạy nền, có thể triển khai song song các file độc lập của task kế tiếp nhưng chỉ đánh dấu hoàn thành task khi đã đọc toàn bộ kết quả test xanh.

**Goal:** Hoàn thiện trọn vẹn trải nghiệm **1.4.5 Theo dõi đơn hàng** (thanh tiến trình trực quan 5 chặng, thông tin vận chuyển ĐVVC/mã vận đơn/lịch sử di chuyển gói hàng đồng bộ hai chiều với Shop, nút *"Đã nhận hàng"* + job tự động hoàn thành sau `order.auto_complete_days` = 7 ngày, nút *"Hủy đơn hàng"* ở trạng thái `PENDING` với 4 lý do chuẩn) và **1.4.6 Lịch sử đơn hàng** (7 tab đồng bộ Shop, tìm kiếm mã đơn/tên sản phẩm, chi tiết sản phẩm kèm ảnh, *"Mua lại"* nguyên tử, *"Đánh giá"* 1 lần tạo + 1 lần sửa duy nhất + không cho xóa, tab *"Đổi trả"* đồng bộ trạng thái Shop & realtime WebSocket).

**Architecture:** Tận dụng kiến trúc vertical-slice hiện tại trong `fashionstore` và domain-grouped React trong `fashionstore-ui`. Migration `V34` bổ sung `edited_at` lên `review`; `V35` bổ sung `shipping_carrier`, `tracking_code`, partial index trên `orders` và bảng `order_tracking_event`. `OrderService` nạp dữ liệu batch qua `ShopService`, `ProductStockService`, `ReturnRequestRepository` và đọc `SystemSettingsService` (tránh vòng phụ thuộc tuyệt đối vì `ReturnRequestService` chỉ dùng `OrderRepository` cùng domain, còn `CartService` & `ReviewService` giữ chiều gọi `-> OrderService`). Phía UI dùng Orval codegen từ `fashionstore-ui/docs/openapi.json` (tự xuất qua `OpenApiExportTest`) và đồng bộ realtime hai chiều qua STOMP `/user/queue/notifications`.

**Tech Stack:**
- Backend (`fashionstore`): Java 21, Spring Boot 4, Spring Data JDBC, PostgreSQL + Flyway, ErrorProne + NullAway, springdoc-openapi, JUnit 5 + MockMvc + Testcontainers.
- Frontend (`fashionstore-ui`): React 19, TypeScript, Vite+ (`vp`), Tailwind CSS v4, `@phosphor-icons/react`, TanStack React Query v5, Orval, Vitest + React Testing Library.

**Spec:** `docs/superpowers/specs/2026-11-12-customer-order-history-design.md`

---

## Global Constraints

- **Java (`fashionstore`):**
  - Cấm tuyệt đối toán tử 3 ngôi (`? :`) — dùng `if` + early `return`.
  - Độ dài dòng tối đa `120` ký tự; mỗi dòng tối đa 1 statement.
  - NullAway bật mức error (`@Nullable` của `org.jspecify.annotations.Nullable` chỉ dùng khi nghiệp vụ thực sự nullable; không dùng `@Data`/`@Setter` trên entity).
  - Mọi HTTP exception dùng thông báo tiếng Anh rõ nghĩa; mọi endpoint & DTO record có đầy đủ `@Operation`, `@ApiResponse`, `@Schema`.
  - **Không circular dependencies:** `ReturnRequestService` không inject `OrderService`; `OrderService` không inject `CatalogService`, `ReviewService`, hay `CartService`.
- **TypeScript / React (`fashionstore-ui`):**
  - Cấm tuyệt đối toán tử 3 ngôi (`? :`) cả trong logic lẫn JSX — dùng `if/return` sớm hoặc `&&`.
  - Chỉ dùng `pnpm` (cấm `npm`).
  - Dùng `Modal.tsx` chung cho mọi dialog; icon từ `@phosphor-icons/react`; tiền tệ qua `formatVnd`, ngày giờ qua `formatDateTime`, media qua `imageUrl`, fetch response qua `takeData`; màu & glass theo `docs/design-system.md`.
  - Không sửa tay file trong `generated/`; mọi thay đổi API backend phải chạy `OpenApiExportTest` rồi `pnpm codegen` / `pnpm build`.

## Review Focus

1. **Vòng phụ thuộc Spring (`BeanCurrentlyInCreationException`):** Xác nhận `ReturnRequestService` chỉ inject `OrderRepository` (không inject `OrderService`), `OrderService` chỉ inject `ProductStockService` (không inject `CatalogService` / `ReviewService` / `CartService`).
2. **Đánh giá 1 + 1 không lách luật:** Không thể sửa lần thứ 2 (`editedAt != null` → `409 Conflict`), không thể xóa review qua `DELETE /api/products/{productId}/reviews` (`405`), và đơn có nhiều biến thể của cùng `productId` gom đúng về 1 trạng thái đánh giá.
3. **Đồng bộ Đổi trả & Auto-Complete:** Đơn có `return_request` trạng thái `REQUESTED | APPROVED | COMPLETED` bị chặn `confirmReceipt` (`409`), không bị `OrderAutoCompleteJob` đóng nhầm, và hiển thị song song cả `OrderStatus` + `ReturnStatus` trên tab `Hoàn thành` & `Đổi trả`; nếu bị `REJECTED` thì mở lại nhận hàng/auto-complete và cho gửi lại yêu cầu mới.
4. **Hành trình vận chuyển & Hủy đơn:** `OrderResponse.shippingCarrier`/`trackingCode` fallback độc lập từng trường khi Shop ship không nhập đủ; `order_tracking_event.location` dùng `TEXT` không tràn địa chỉ 1000 ký tự lúc `deliver()`; `BuyerOrderTimelineResponse` không lộ email/UUID nhân viên; `cancelReasonLabel` giữ nguyên `"Lý do khác"` cho Admin (`CANCEL_REASON_OPTIONS`) và `"Khác"` cho Buyer.
5. **Mua lại nguyên tử & An toàn:** `CartService.reorder` tính tồn kho trừ sẵn số lượng trong giỏ, không gọi `addItem` bỏ qua trần tồn, trả `200 OK` kèm danh sách lý do chi tiết ngay cả khi toàn bộ item bị bỏ qua (`shopApproved == false` mới trả `409`).

---

### Task 1: Backend Database Migrations (`V34`, `V35`), Entities, Enums & Repositories

**Files:**
- Create: `fashionstore/src/main/resources/db/migration/V34__review_edit_once.sql`
- Create: `fashionstore/src/main/resources/db/migration/V35__order_tracking_and_auto_complete.sql`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Review.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Order.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/OrderTrackingEvent.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/BuyerCancelReason.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/OrderTrackingStatus.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderTrackingEventRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/repository/ReturnRequestRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/repository/ReviewRepository.java`
- Modify/Create Tests: `fashionstore/src/test/java/com/minhnbnt/fashionstore/model/OrderTest.java`, `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderTrackingRepositoryTest.java`

**Interfaces:**
- Consumes: PostgreSQL schema (`orders`, `review`, `return_request`).
- Produces: Cột `review.edited_at`, `orders.shipping_carrier`, `orders.tracking_code`, bảng `order_tracking_event`, domain methods `Review.update` (1-edit guard), `Order.ship(carrier, code)`, `Order.cancel(reason, note)`, query methods trên repositories.

- [ ] **Step 1: Viết unit test & repository test failing (RED)**
  - Trong `OrderTest.java`: thêm test cho `order.ship("Viettel Post", "VTP123")` gán đúng `shippingCarrier`/`trackingCode`/`SHIPPING`; `order.ship()` mặc định `"GHN"` và `"VN-" + order.getCode()`; `order.cancel("CHANGE_MIND", "note")` lưu `cancelReason`/`cancelNote`; `Review.update(...)` lần 1 thành công gán `editedAt != null`, lần 2 ném `ResponseStatusException` HTTP `409`; `BuyerCancelReason.parse` & `OrderTrackingStatus.parse` hợp lệ/ném `400`.
  - Tạo `OrderTrackingRepositoryTest.java` (`@DataJdbcTest` hoặc `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` giống pattern repo tests hiện có): test migration `V34`, `V35`, lưu & truy vấn `OrderTrackingEventRepository.findByOrderIdOrderByEventTimeDescCreatedAtDesc`, `ReturnRequestRepository.existsBlockingByOrderId`, `OrderRepository.findDeliveredReadyForAutoComplete`, `ReviewRepository.findByBuyerIdOrderByUpdatedAtDescCreatedAtDesc`.
- [ ] **Step 2: Chạy test xác nhận fail**
  - Run: `cd fashionstore; ./gradlew test --tests "*OrderTest" --tests "*OrderTrackingRepositoryTest"`
  - Expected: FAIL vì chưa có migrations/models/methods mới.
- [ ] **Step 3: Viết `V34__review_edit_once.sql`, `V35__order_tracking_and_auto_complete.sql` & cập nhật Entities/Enums/Repositories (GREEN)**
  - `V34`: `ALTER TABLE review ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;`
  - `V35`: thêm `shipping_carrier VARCHAR(100)`, `tracking_code VARCHAR(100)`, index `idx_orders_delivered_unreceived`, bảng `order_tracking_event (id, order_id, status_code, title, location TEXT, description, event_time, created_by, created_at)` + index.
  - `Review.java`: thêm `@Nullable Instant editedAt`, kiểm tra `if (this.editedAt != null)` ném `409 Conflict` trong `update(...)`, gán `this.editedAt = Instant.now()`.
  - `Order.java`: thêm `@Nullable String shippingCarrier`, `@Nullable String trackingCode`, overload `ship(String, String)` + `ship()` fallback, overload `cancel(@Nullable String reason, @Nullable String note)`.
  - Tạo `OrderTrackingEvent`, `BuyerCancelReason`, `OrderTrackingStatus`, `OrderTrackingEventRepository`, và bổ sung query methods cho `OrderRepository`, `ReturnRequestRepository`, `ReviewRepository`. Tuân thủ nghiêm NullAway & cấm ternary `? :`.
- [ ] **Step 4: Chạy test xác nhận pass**
  - Run: `cd fashionstore; ./gradlew test --tests "*OrderTest" --tests "*OrderTrackingRepositoryTest"`
  - Expected: PASS (0 failures).

---

### Task 2: Backend Reviews — Quy Tắc 1+1, Chặn Xóa & `GET /api/reviews/mine`

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/ReviewResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/review/SellerReviewResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/controller/ReviewController.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/review/controller/MyReviewController.java`
- Modify/Create Tests: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/review/ReviewServiceTest.java`, `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/review/ReviewControllerTest.java` (cập nhật chỗ gọi `new ReviewResponse(...)` hoặc `new SellerReviewResponse(...)` nếu có).

**Interfaces:**
- Consumes: `Review`, `ReviewRepository`, `OrderService` (chỉ từ `ReviewService`).
- Produces: `GET /api/reviews/mine` (`List<ReviewResponse>` sắp theo `updatedAt DESC, createdAt DESC`), `ReviewResponse`/`SellerReviewResponse` có `edited` + `editedAt`, sửa lần 2 trả `409`, `DELETE /api/products/{productId}/reviews` trả `405`.

- [ ] **Step 1: Cập nhật & viết test failing cho Review 1+1 + `GET /api/reviews/mine` (RED)**
  - Trong `ReviewServiceTest`: bỏ case kỳ vọng xóa thành công; thêm test sửa lần 1 thành công (`edited() == true`, `editedAt() != null`), sửa lần 2 ném `ResponseStatusException(CONFLICT)`, `getMyReviews(buyerId)` chỉ trả review của user đó theo `updatedAt DESC`, và tạo/sửa review vẫn thành công kể cả khi sản phẩm bị chuyển sang `INACTIVE` sau khi khách đã `confirmReceipt()`.
  - Trong `ReviewControllerTest`: cập nhật case `DELETE /api/products/{productId}/reviews` mong đợi `405 Method Not Allowed`, thêm test `PUT` lần 2 → `409`, thêm test `GET /api/reviews/mine` → `200` (401 nếu thiếu JWT).
- [ ] **Step 2: Chạy test xác nhận fail**
  - Run: `cd fashionstore; ./gradlew test --tests "*Review*Test"`
  - Expected: FAIL vì `ReviewResponse` chưa có `edited`, `DELETE` vẫn trả 204, `/api/reviews/mine` chưa tồn tại.
- [ ] **Step 3: Cài đặt DTO, Service, `ReviewController` & `MyReviewController` (GREEN)**
  - Cập nhật `ReviewResponse` và `SellerReviewResponse` thêm `@Schema(...) boolean edited` và `@Schema(..., nullable = true) @Nullable Instant editedAt`.
  - Cập nhật `ReviewService`: bỏ `deleteReview`, bổ sung `listMine(UUID buyerId)` đọc `reviews.findByBuyerIdOrderByUpdatedAtDescCreatedAtDesc(buyerId)`, map `edited = review.getEditedAt() != null` (không dùng `? :`), đồng bộ `SellerReviewResponse`.
  - Gỡ `@DeleteMapping` ở `ReviewController`; tạo `MyReviewController` với `@GetMapping("/api/reviews/mine")` có đầy đủ Swagger annotations. Kiểm tra mọi call site tạo `SellerReviewResponse` / `ReviewResponse` trong codebase & tests.
- [ ] **Step 4: Chạy test xác nhận pass**
  - Run: `cd fashionstore; ./gradlew test --tests "*Review*Test"`
  - Expected: PASS. *(Trong lúc đợi có thể chuyển sang soạn phần DTO/helper độc lập của Task 3).*

---

### Task 3: Backend Batch Enrichment, Vận Chuyển/Hành Trình, Hủy Đơn, Timeline Bảo Mật & `OrderAutoCompleteJob`

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/shop/ShopService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/catalog/ProductStockService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/OrderResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/OrderItemResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/ShipOrderRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/AddOrderTrackingEventRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/OrderTrackingEventResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/BuyerOrderTimelineResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/BuyerCancelOrderRequest.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderAutoCompleteJob.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/controller/OrderController.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/controller/SellerOrderController.java`
- Modify/Create Tests: `OrderServiceTest.java`, `OrderControllerTest.java`, `AdminOrderServiceTest.java` (nếu cần), `OrderAutoCompleteJobTest.java`, `SellerOrderTrackingTest.java`

**Interfaces:**
- Consumes: `ShopService`, `ProductStockService`, `SystemSettingsService`, `OrderTrackingEventRepository`, `ReturnRequestRepository`, `OrderMonitoringQueryRepository`.
- Produces: Enriched `OrderResponse`/`OrderItemResponse`, `GET /api/orders/{id}/timeline`, `GET /api/orders/{id}/tracking`, `PATCH /api/orders/{id}/cancel` (4 lý do), `PATCH /api/orders/{id}/received` (return lock + idempotent), `PATCH /api/seller/orders/{id}/ship` (carrier + code), `GET/POST /api/seller/orders/{id}/tracking`, `OrderAutoCompleteJob`.

- [ ] **Step 1: Viết test failing cho `OrderService`, Controllers & `OrderAutoCompleteJob` (RED)**
  - `OrderServiceTest`: test `toResponses` batch nạp `shopName`, `shopSlug`, `imageKey` (ưu tiên ảnh variant trước rồi ảnh chính `sortOrder` nhỏ nhất), `productAvailable`, `returnStatus`, `autoCompleteAt`; `ship(userId, orderId, request)` fallback độc lập từng trường + sinh `PICKED_UP` event + audit + notification; `deliver` sinh `DELIVERED` event (cả địa chỉ dài >200 ký tự không lỗi); `addTrackingEvent` chặn `409` khi không phải `SHIPPING`, tạo audit `ORDER_TRACKING_UPDATE` & notification `ORDER_TRACKING_UPDATED`; `confirmReceipt` trả `409` khi có `ReturnRequest` `REQUESTED`/`APPROVED`/`COMPLETED`, idempotent không ghi đè `receivedAt` khi gọi lại; `cancel` validate 4 lý do + bắt buộc `note` khi `OTHER`; cập nhật các assert link `/account` cũ → `"/account/orders/" + id`.
  - `OrderControllerTest`: test `GET /api/orders/{id}/timeline` (ẩn `changedBy`/`changedByEmail`, chặn user khác), `GET /api/orders/{id}/tracking` (chặn user khác), `PATCH /api/orders/{id}/cancel` (không body OK, `{}` → `400`, `OTHER` thiếu `note` → `400`, `CHANGE_MIND` → `200`).
  - `OrderAutoCompleteJobTest`: mock/integration test các đơn `DELIVERED` quá `order.auto_complete_days` chưa `receivedAt` được tự hoàn thành & gửi notification `ORDER_COMPLETED`; đơn có return `REQUESTED`/`APPROVED`/`COMPLETED` bị bỏ qua; đơn có return `REJECTED` đã quá hạn vẫn tự hoàn thành.
  - `SellerOrderTrackingTest` (MockMvc hoặc service test): `PATCH /api/seller/orders/{id}/ship`, `GET /api/seller/orders/{id}/tracking`, `POST /api/seller/orders/{id}/tracking`.
- [ ] **Step 2: Chạy test xác nhận fail**
  - Run: `cd fashionstore; ./gradlew test --tests "*Order*Test"`
  - Expected: FAIL vì DTO/methods/endpoints/job chưa có.
- [ ] **Step 3: Cài đặt batch helpers (`ShopService`, `ProductStockService`), DTOs, `OrderService`, Controllers & `OrderAutoCompleteJob` (GREEN)**
  - `ShopService`: thêm record `ShopSummary(UUID id, String name, @Nullable String slug, boolean approved)` và `findShopSummariesByIds`.
  - `ProductStockService`: inject `ProductImageRepository`; thêm helper batch lấy map `imageKey` ưu tiên theo `variantId` của item rồi `sortOrder` sản phẩm, cùng cờ `productAvailable` (`ACTIVE && shopApproved`).
  - `OrderService`: inject thêm `SystemSettingsService` và `OrderTrackingEventRepository`; cài đặt batch `toResponse`/`toResponses`, `ship(userId, id, request)`, `deliver`, `getSellerTracking`, `addTrackingEvent`, `getBuyerTimeline`, `getBuyerTracking`, `cancel(buyerId, id, request)`, `confirmReceipt` (guard + history), `findDeliveredReadyForAutoComplete` + `systemAutoCompleteOrder`, đổi cả 6 luồng link thông báo Buyer sang `"/account/orders/" + order.getId()`.
  - `OrderAutoCompleteJob`: `@Scheduled` + `ApplicationReadyEvent` gọi theo từng order transaction độc lập.
  - Cập nhật `OrderController` & `SellerOrderController` với đầy đủ OpenAPI `@Operation`/`@ApiResponse`/`@Schema`.
- [ ] **Step 4: Chạy toàn bộ test order xác nhận pass**
  - Run: `cd fashionstore; ./gradlew test --tests "*Order*Test"`
  - Expected: PASS (0 failures).

---

### Task 4: Backend Đổi Trả Đồng Bộ (`ReturnRequestService`), Mua Lại (`CartService.reorder`) & Xuất OpenAPI

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/ReturnRequestResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/ReturnRequestService.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/cart/ReorderItemIssue.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/cart/ReorderResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/cart/CartService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/cart/controller/CartController.java`
- Modify/Create Tests: `ReturnRequestServiceUnitTest.java`, `CartReorderServiceTest.java`, `CartControllerReorderTest.java`
- Exported Artifact: `fashionstore-ui/docs/openapi.json`

**Interfaces:**
- Consumes: `OrderRepository`, `ReturnRequestRepository`, `NotificationService`, `OrderService` (chỉ trong `CartService`), `CatalogService`, `ShopService`.
- Produces: `ReturnRequestResponse` có `orderCode` & `orderStatus`, thông báo hai chiều `RETURN_*`, `POST /api/cart/reorder/{orderId}`, file `fashionstore-ui/docs/openapi.json` mới nhất.

- [ ] **Step 1: Viết test failing cho `ReturnRequestService` & `CartService.reorder` (RED)**
  - Cập nhật `ReturnRequestServiceUnitTest`: sửa `"DEFECTIVE"` → `"DAMAGED"`; thêm assert validate `reason` (`400` khi invalid), assert chặn `409` nếu đã có `REQUESTED`/`APPROVED`/`COMPLETED`, assert cho phép tạo lại sau `REJECTED`, assert trả đúng `orderCode`/`orderStatus`, và assert `NotificationService.create` được gọi với `RETURN_REQUESTED` (`/shop?shopId=...&tab=returns&orderId=...` chứa `#orderCode`), `RETURN_APPROVED`, `RETURN_REJECTED`, `RETURN_COMPLETED` (`/account/orders/...`).
  - Tạo `CartReorderServiceTest` & `CartControllerReorderTest`: test shop khóa trả `409`; user khác trả `404`; thêm đủ 100% thành công (`addedCount > 0`, giữ nguyên `priceAtAdded` cho món đã có sẵn trong giỏ); thiếu tồn kho một phần (có tính số đang có trong giỏ) thêm phần dư & ghi `adjustedItems`; item xóa/ngừng bán/hết hàng/đạt trần giỏ ghi đúng lý do tiếng Việt vào `skippedItems`; tất cả skipped vẫn trả `200 OK`.
- [ ] **Step 2: Chạy test xác nhận fail**
  - Run: `cd fashionstore; ./gradlew test --tests "*ReturnRequest*Test" --tests "*Reorder*Test"`
  - Expected: FAIL.
- [ ] **Step 3: Cài đặt `ReturnRequestService` & `CartService.reorder` + `CartController` (GREEN)**
  - `ReturnRequestService` inject `NotificationService` (tuyệt đối KHÔNG inject `OrderService`). Validate preset reasons, check trùng open/completed returns, batch enrich `orderCode` & `orderStatus` qua `OrderRepository`, gửi notifications đầy đủ.
  - `CartService.reorder(UUID buyerId, UUID orderId)`: dùng `orders.getDetail(buyerId, orderId)`, kiểm `shops.isShopApproved`, lấy biến thể/sản phẩm/tồn kho hiện tại, cập nhật `CartItem` trực tiếp trong transaction (tôn trọng trần tồn kho), trả `ReorderResponse`.
  - `CartController`: `@PostMapping("/reorder/{orderId}")` với `@Operation`/`@ApiResponse`.
- [ ] **Step 4: Chạy test Task 4, xuất OpenAPI & chạy full suite backend**
  - Run: `cd fashionstore; ./gradlew test --tests "*ReturnRequest*Test" --tests "*Reorder*Test" --tests com.minhnbnt.fashionstore.OpenApiExportTest`
  - Tiếp theo chạy nền full backend suite: `./gradlew test` (trong lúc đợi kết quả full `./gradlew test`, chuyển ngay sang Task 5 để chạy `pnpm codegen` trên `fashionstore-ui/docs/openapi.json` đã được cập nhật!).
  - Expected: PASS 100% và `fashionstore-ui/docs/openapi.json` chứa `"reorder"`, `"myReviews"` / `/api/reviews/mine`, `"tracking"`, `"timeline"`.

---

### Task 5: Frontend Codegen & Domain Helpers (`orderStatus`, `orderMonitoring`, `sellerPermissions`, `sellerAdapters`, `AdminOrderDetailPanel`)

**Files:**
- Generated: `fashionstore-ui/generated/**` (via `pnpm codegen`, không sửa tay)
- Modify: `fashionstore-ui/src/lib/order/orderStatus.ts`
- Modify: `fashionstore-ui/src/lib/order/orderStatus.test.ts`
- Modify: `fashionstore-ui/src/lib/order/orderMonitoring.ts`
- Modify: `fashionstore-ui/src/lib/order/orderMonitoring.test.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerPermissions.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerPermissions.test.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerAdapters.ts`
- Modify: `fashionstore-ui/src/lib/seller/sellerAdapters.test.ts`
- Modify: `fashionstore-ui/src/components/admin/AdminOrderDetailPanel.tsx`
- Modify: `fashionstore-ui/src/types/seller/orders.ts` (bổ sung `shippingCarrier`, `trackingCode`, `returnStatus`, mảng `evidenceKeys` đầy đủ nếu cần)

**Interfaces:**
- Consumes: `fashionstore-ui/docs/openapi.json` → `@generated/model` & `@generated/endpoints`.
- Produces: Pure domain helpers/constants & adapters sạch ternary, đầy đủ unit tests.

- [ ] **Step 1: Chạy `pnpm codegen` kiểm tra OpenAPI hooks mới**
  - Run: `cd fashionstore-ui; pnpm codegen`
  - Kiểm tra có hook cho `GET /api/reviews/mine`, `GET /api/orders/{id}/timeline`, `GET /api/orders/{id}/tracking`, `POST /api/cart/reorder/{orderId}`, `GET/POST /api/seller/orders/{id}/tracking`, và KHÔNG còn `useDelete1` xóa review.
- [ ] **Step 2: Viết unit test failing cho helpers & adapters (RED)**
  - `orderStatus.test.ts`: test đủ 7 `BUYER_ORDER_TABS`, `RETURN_STATUS_META` khớp `sellerStatusMeta`, `RETURN_RESOLUTION_LABELS` đủ 3 giá trị (`REFUND`, `PARTIAL_REFUND`, `EXCHANGE`), `RETURN_REASON_LABELS`, `BUYER_CANCEL_REASON_OPTIONS` (4 lý do chuẩn), `TRACKING_STATUS_LABELS`, helpers `canConfirmReceipt(order)`, `canRequestReturn(order)`, `canCancelOrder(order)`, `isVideoEvidenceKey(key)`, `normalizeOrderSearchQuery(q)`.
  - `orderMonitoring.test.ts`: test `cancelReasonLabel('OTHER', 'ADMIN') === 'Lý do khác'`, `cancelReasonLabel('OTHER', 'BUYER') === 'Khác'`, các mã Buyer (`CHANGE_MIND`, `FOUND_CHEAPER`, `ORDERED_BY_MISTAKE`), và `timelineStepTitle('DELIVERED', 'DELIVERED', note) === 'Xác nhận hoàn thành'`.
  - `sellerPermissions.test.ts` & `sellerAdapters.test.ts`: test `ORDER_TRACKING_UPDATE` badge/label; `sellerAdapters` giữ đủ `shippingCarrier`, `trackingCode`, `returnStatus`, dịch đúng `cancelReason`, và chuyển toàn bộ `evidenceKeys` (cả ảnh & video).
- [ ] **Step 3: Chạy Vitest xác nhận fail**
  - Run: `cd fashionstore-ui; pnpm test -- src/lib/order/orderStatus.test.ts src/lib/order/orderMonitoring.test.ts src/lib/seller/sellerPermissions.test.ts src/lib/seller/sellerAdapters.test.ts`
  - Expected: FAIL.
- [ ] **Step 4: Cài đặt helpers & cập nhật `AdminOrderDetailPanel.tsx` (GREEN)**
  - Cập nhật `orderStatus.ts`, `orderMonitoring.ts`, `sellerPermissions.ts`, `sellerAdapters.ts`, `types/seller/orders.ts`, và `AdminOrderDetailPanel.tsx` (chỉ chỉnh `timelineTitle` gọi helper xử lý `DELIVERED === DELIVERED`, không thêm trường mới vào Admin UI).
  - Bảo đảm 0 toán tử 3 ngôi `? :`.
- [ ] **Step 5: Chạy Vitest xác nhận pass**
  - Run: `cd fashionstore-ui; pnpm test -- src/lib/order/orderStatus.test.ts src/lib/order/orderMonitoring.test.ts src/lib/seller/sellerPermissions.test.ts src/lib/seller/sellerAdapters.test.ts`
  - Expected: PASS.

---

### Task 6: Frontend Customer Order Components, `Orders.tsx` (7 Tabs & Search) & `Account.tsx`

**Files:**
- Create: `fashionstore-ui/src/components/checkout/OrderProgressStepper.tsx`
- Create: `fashionstore-ui/src/components/checkout/ShipmentTrackingCard.tsx`
- Create: `fashionstore-ui/src/components/checkout/CancelOrderModal.tsx`
- Create: `fashionstore-ui/src/components/checkout/ReturnRequestModal.tsx`
- Create: `fashionstore-ui/src/components/checkout/ReviewModal.tsx`
- Create: `fashionstore-ui/src/components/checkout/ReviewProductPickerModal.tsx`
- Create: `fashionstore-ui/src/components/checkout/ReorderResultModal.tsx`
- Modify: `fashionstore-ui/src/pages/checkout/Orders.tsx`
- Modify: `fashionstore-ui/src/pages/account/Account.tsx`
- Create Tests: `fashionstore-ui/src/pages/checkout/Orders.test.tsx`, `fashionstore-ui/src/components/checkout/OrderModals.test.tsx`

**Interfaces:**
- Consumes: Orval hooks (`useMine2`, `useMine1`, `useCancel1`, `useConfirmReceipt`, `useReorder`, `useCreate2`, `useCreate`, `useUpdate3`, `useMineReviews`), helpers từ Task 5, `Modal.tsx`, `uploadImage`/`uploadVideo`/`checkVideoDuration`.
- Produces: Trang `/account/orders` hoàn chỉnh 7 tab, search, modal đầy đủ & lối tắt trên `/account`.

- [ ] **Step 1: Viết component tests failing cho `Orders.tsx` & các modals (RED)**
  - `Orders.test.tsx`: kiểm 7 tab đúng nhãn & badge số lượng, lọc theo `?tab=` và ô search mã đơn (`#ORD-...` / `ord`) + tên sản phẩm; đơn `DELIVERED` có return hiển thị ở CẢ `Hoàn thành` & `Đổi trả` kèm 2 badge song song; tab `PENDING` mở `CancelOrderModal` bắt buộc chọn 1 trong 4 lý do (chọn `Khác` yêu cầu nhập mô tả); tab `DELIVERED` ẩn nút `"Đã nhận hàng"` khi có return mở, hiển thị ngày tự hoàn thành `autoCompleteAt`, mở modal đánh giá 1+1 (hoặc `ReviewProductPickerModal` khi có nhiều sản phẩm khác `productId`), nút `"Mua lại"` 100% điều hướng `/cart`, partial/skipped mở `ReorderResultModal`.
  - `OrderModals.test.tsx`: test upload ảnh + video trong `ReturnRequestModal`, quy tắc 1+1 và `maxLength=2000` trong `ReviewModal`.
- [ ] **Step 2: Chạy Vitest xác nhận fail**
  - Run: `cd fashionstore-ui; pnpm test -- src/pages/checkout/Orders.test.tsx src/components/checkout/OrderModals.test.tsx`
  - Expected: FAIL.
- [ ] **Step 3: Tạo các sub-components trong `src/components/checkout/` & nâng cấp `Orders.tsx` + `Account.tsx` (GREEN)**
  - Viết sạch từng component dùng `Modal.tsx`, tuân thủ `design-system.md` (`rounded-3xl`, `.glass`, `rounded-full` buttons/inputs, dark mode, 0 ternary `? :`).
  - Liên kết tên sản phẩm dùng `/product/${item.productId}` chỉ khi `item.productAvailable` là `true`; trường hợp ngừng bán hiển thị text + badge `"Ngừng kinh doanh"`.
  - Thêm thẻ lối tắt `"Đơn mua của tôi"` trên cột trái `Account.tsx`.
- [ ] **Step 4: Chạy Vitest xác nhận pass**
  - Run: `cd fashionstore-ui; pnpm test -- src/pages/checkout/Orders.test.tsx src/components/checkout/OrderModals.test.tsx`
  - Expected: PASS. *(Trong lúc đợi có thể chuyển sang viết tests cho Task 7).*

---

### Task 7: Frontend `OrderDetail.tsx`, `ProductTabs.tsx`, `SellerOrdersTab.tsx`, `SellerReturnsTab.tsx` & Realtime Sync

**Files:**
- Modify: `fashionstore-ui/src/pages/checkout/OrderDetail.tsx`
- Modify: `fashionstore-ui/src/components/pdp/ProductTabs.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/orders/SellerOrdersTab.tsx`
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/orders/SellerReturnsTab.tsx`
- Create (nếu cần tách gọn): `fashionstore-ui/src/components/seller-dashboard-tab/orders/SellerShipAndTrackingModals.tsx`
- Modify: `fashionstore-ui/src/components/NotificationBell.tsx`
- Create/Modify Tests: `fashionstore-ui/src/pages/checkout/OrderDetail.test.tsx`, `fashionstore-ui/src/components/pdp/ProductTabs.test.tsx`, `fashionstore-ui/src/components/seller-dashboard-tab/orders/SellerOrdersTab.test.tsx`, `fashionstore-ui/src/components/seller-dashboard-tab/orders/SellerReturnsTab.test.tsx`, `fashionstore-ui/src/components/NotificationBell.test.tsx`

**Interfaces:**
- Consumes: `OrderProgressStepper`, `ShipmentTrackingCard`, modals từ Task 6, tracking & timeline & seller tracking endpoints từ Task 5.
- Produces: Chi tiết đơn 1.4.5 + 1.4.6 hoàn chỉnh, Shop giao hàng kèm ĐVVC/mã vận đơn/cập nhật hành trình, Seller/Buyer xem đủ gallery ảnh+video, và WebSocket `/user/queue/notifications` auto-invalidate.

- [ ] **Step 1: Viết tests failing cho `OrderDetail`, `ProductTabs`, `SellerOrdersTab`, `SellerReturnsTab` & `NotificationBell` (RED)**
  - `OrderDetail.test.tsx`: progress bar 5 chặng + nhánh hủy; `ShipmentTrackingCard` hiện ĐVVC, mã vận đơn có nút sao chép, danh sách `order_tracking_event` và timeline ẩn email; countdown `autoCompleteAt` + `"Đã nhận hàng"` bị khóa khi có return `REQUESTED`; 2 biến thể cùng `productId` gom đúng 1 nút/trạng thái đánh giá; khối Đổi trả render cả `<img>` & `<video controls>` và lịch sử các lần gửi trước.
  - `ProductTabs.test.tsx`: hiện badge `"Đã chỉnh sửa"` khi `review.edited === true`.
  - `SellerOrdersTab.test.tsx`: vẫn giữ nhãn `"Đã giao cho ĐVVC"` trên tab `SHIPPING`; bấm *"Giao cho ĐVVC"* mở modal chọn ĐVVC + mã vận đơn trước khi `ship`; đơn `SHIPPING` mở modal xem & thêm mốc di chuyển gói hàng; thẻ `DELIVERED` hiện `returnStatus` badge; `CANCELLED` dịch `cancelReason` qua `cancelReasonLabel`.
  - `SellerReturnsTab.test.tsx`: hỗ trợ `?orderId=` lọc đúng yêu cầu + nút *"Xem tất cả yêu cầu đổi trả"* và render nhiều ảnh + `<video controls>` từ `evidenceKeys`.
  - `NotificationBell.test.tsx`: khi nhận frame `ORDER_SHIPPED` / `ORDER_TRACKING_UPDATED` / `RETURN_APPROVED` / `RETURN_REQUESTED`, tự động invalidate cache đơn hàng, tracking, timeline & đổi trả.
- [ ] **Step 2: Chạy Vitest xác nhận fail**
  - Run: `cd fashionstore-ui; pnpm test -- src/pages/checkout/OrderDetail.test.tsx src/components/pdp/ProductTabs.test.tsx src/components/seller-dashboard-tab/orders/SellerOrdersTab.test.tsx src/components/seller-dashboard-tab/orders/SellerReturnsTab.test.tsx src/components/NotificationBell.test.tsx`
  - Expected: FAIL.
- [ ] **Step 3: Triển khai `OrderDetail.tsx`, `ProductTabs.tsx`, `SellerOrdersTab.tsx`, `SellerReturnsTab.tsx` & `NotificationBell.tsx` (GREEN)**
  - Nâng cấp trọn vẹn `OrderDetail.tsx`, gắn `ProductTabs.tsx` badge chỉnh sửa, thêm modal ship & tracking cho `SellerOrdersTab.tsx`, video + `?orderId=` cho `SellerReturnsTab.tsx`, và WebSocket selective invalidation trong `NotificationBell.tsx`.
  - Kiểm tra kỹ không dùng `? :` trong bất kỳ file nào.
- [ ] **Step 4: Chạy Vitest xác nhận pass**
  - Run: `cd fashionstore-ui; pnpm test -- src/pages/checkout/OrderDetail.test.tsx src/components/pdp/ProductTabs.test.tsx src/components/seller-dashboard-tab/orders/SellerOrdersTab.test.tsx src/components/seller-dashboard-tab/orders/SellerReturnsTab.test.tsx src/components/NotificationBell.test.tsx`
  - Expected: PASS.

---

### Task 8: Verification Toàn Cục, Tuân Thủ Code Guide & Tài Liệu API

**Files:**
- Modify (nếu cần cập nhật checklist/docs): `fashionstore/docs/api-endpoints.md`

- [ ] **Step 1: Kiểm tra quy tắc cấm ternary (`? :`) & code style trên toàn bộ file mới/sửa ở cả 2 repo**
  - Chạy script kiểm tra `? :` trong file đã sửa/tạo ở `fashionstore/src` và `fashionstore-ui/src`.
- [ ] **Step 2: Chạy full backend test suite + OpenAPI consistency**
  - Run: `cd fashionstore; .\gradlew test`
  - Expected: BUILD SUCCESSFUL, 0 failed/skipped tests bất ngờ.
- [ ] **Step 3: Chạy full frontend pre-commit & test pipeline**
  - Run: `cd fashionstore-ui; pnpm format && pnpm lint && pnpm test && pnpm build`
  - Expected: 0 lint errors/warnings, toàn bộ Vitest suite pass, `tsc -b` + `vp build` exit 0.
- [ ] **Step 4: Cập nhật `fashionstore/docs/api-endpoints.md` (nếu có mục theo dõi tiến độ API) & rà soát `git status` / `git diff` tổng thể trước khi báo cáo hoàn thành.**

