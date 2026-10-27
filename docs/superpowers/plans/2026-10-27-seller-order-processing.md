# Xem & Xử Lý Đơn Hàng Mới (Seller Center 2.3.1) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Hoàn thiện toàn diện chức năng Xem & Xử lý đơn hàng mới tại Kênh Người Bán (Seller Center): 7 tab phân loại, xem chi tiết kèm Order Timeline & ghi chú, xác nhận hàng loạt (Bulk Confirm) với Partial Success, từ chối đơn kèm lý do chuẩn, nhãn đếm ngược 24h & Background Job tự động hủy quá hạn theo chuẩn sàn TMĐT (Shopee/TikTok Shop).

**Architecture:** Chuẩn hóa vòng đời tồn kho (Trừ kho nguyên tử lúc Checkout để triệt tiêu Overselling, hoàn kho khi hủy ở mọi trạng thái). Thêm migration Flyway `V31` hỗ trợ `SYSTEM` trong constraint `orders.cancelled_by`. Dùng Spring `@Scheduled` quét định kỳ hủy đơn quá hạn 24h. API `POST /api/seller/orders/bulk-confirm` xử lý độc lập từng đơn và trả về báo cáo chi tiết. UI React 19/Tailwind v4 hỗ trợ hộp kiểm đa chọn, floating action bar, badge đếm ngược SLA và modal chi tiết toàn diện.

**Tech Stack:** Spring Boot 4 + Spring Data JDBC + Postgres + Flyway (V31) + Clock bean; React 19 + Vite + Tailwind CSS v4 + TanStack Query + Orval codegen + `@phosphor-icons/react` + Vitest.

**Spec:** `docs/superpowers/specs/2026-10-27-seller-order-processing-design.md` (cả plan lẫn executor đọc kèm).

## Global Constraints

- Cấm tuyệt đối toán tử 3 ngôi (`? :`): dùng `if` + early `return`; trong JSX dùng `&&`. Cả 2 repo.
- Backend: indent 4 space, `var` local, `record` DTO, dòng ≤ 120 ký tự, NullAway non-null mặc định (`@Nullable` chỉ khi nghiệp vụ thực sự optional).
- Exception/message lỗi tiếng Anh theo RFC 9457 `ProblemDetail`; nội dung thông báo / lý do hiển thị tiếng Việt nguyên văn.
- Lombok allowlist: `@Getter`, `@RequiredArgsConstructor`, `@Slf4j`; Entity cấm `@Data`/`@Setter`/`@EqualsAndHashCode`.
- Endpoint mới có đầy đủ annotations springdoc OpenAPI: `@Operation`, `@ApiResponse`, `@Parameter`, `@SecurityRequirement(name = "bearerAuth")`.
- Migration Flyway mới: `V31__order_auto_cancel_system.sql`, tuyệt đối không sửa file migration cũ đã merge.
- Testcontainers giữ nguyên image `ghcr.io/minhnbnt/postgresql:latest`.
- UI: chỉ dùng `pnpm` (cấm `npm`), thư mục `generated/` cấm sửa tay (dùng `pnpm codegen`).
- Test component bằng `renderToString` + `vi.mock` (không thêm devDependencies mới).
- `./gradlew test` và `pnpm build` phải xanh 100% trước khi nghiệm thu.

## Review Focus

Các trường hợp biên và failure modes quan trọng cần đảm bảo có test bao phủ:
1. **Overselling race condition:** Hai khách cùng đặt sản phẩm có tồn = 1 → khách thứ 2 phải bị từ chối 409 Conflict ngay lúc Checkout, không để lọt vào trạng thái `PENDING`.
2. **Hoàn kho khi hủy đơn `PENDING`:** Khách tự hủy đơn khi chưa xác nhận, hoặc shop từ chối đơn `PENDING` → kho phải được hoàn trả chính xác và ghi nhận `stock_movement`.
3. **Tự động hủy 24h chính xác:** Đơn tạo cách đây 23h59m không bị hủy; đơn tạo cách đây 24h01m bị hủy ngay, ghi nhận `cancelled_by = 'SYSTEM'`, hoàn kho và gửi thông báo cho cả khách lẫn shop.
4. **Xác nhận hàng loạt lỗi phân quyền hoặc trạng thái:** Khi mẻ chọn có đơn của shop khác hoặc đơn đã bị hủy trước đó → đơn đó nằm trong danh sách `failures`, các đơn hợp lệ khác vẫn được xác nhận thành công.
5. **Đổi trạng thái khóa dòng:** Thao tác xác nhận hoặc hủy phải khóa dòng `SELECT ... FOR UPDATE` tránh xung đột đồng thời.

---

### Task 1: Migration Flyway V31 & Cập Nhật Model `Order`

**Files:**
- Create: `fashionstore/src/main/resources/db/migration/V31__order_auto_cancel_system.sql`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/OrderCanceller.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Order.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/model/OrderModelTest.java`

**Interfaces:**
- Produces: `OrderCanceller.SYSTEM`, `order.cancelBySystem(String reason, @Nullable String note)`.

- [ ] **Step 1: Viết test đỏ cho model Order**
Tạo file `fashionstore/src/test/java/com/minhnbnt/fashionstore/model/OrderModelTest.java`:
```java
package com.minhnbnt.fashionstore.model;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderModelTest {

    @Test
    void cancelBySystemSetsSystemCancellerAndReason() {
        var order = Order.create("ORD-TEST", UUID.randomUUID(), UUID.randomUUID(), 100000, 20000, "A", "090", "Hanoi");
        order.cancelBySystem("Quá thời hạn 24 giờ xác nhận", null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledBy()).isEqualTo(OrderCanceller.SYSTEM);
        assertThat(order.getCancelReason()).isEqualTo("Quá thời hạn 24 giờ xác nhận");
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void cancelBySystemFailsWhenNotPending() {
        var order = Order.create("ORD-TEST", UUID.randomUUID(), UUID.randomUUID(), 100000, 20000, "A", "090", "Hanoi");
        order.confirm();

        assertThatThrownBy(() -> order.cancelBySystem("Expired", null))
            .isInstanceOf(ResponseStatusException.class);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test đỏ**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.model.OrderModelTest"`
Expected: FAIL do chưa có `OrderCanceller.SYSTEM` và phương thức `cancelBySystem`.

- [ ] **Step 3: Tạo migration `V31__order_auto_cancel_system.sql`**
Tạo `fashionstore/src/main/resources/db/migration/V31__order_auto_cancel_system.sql`:
```sql
-- Cho phép SYSTEM trong cột cancelled_by của bảng orders
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_cancelled_by_check;
ALTER TABLE orders ADD CONSTRAINT orders_cancelled_by_check 
    CHECK (cancelled_by IN ('BUYER', 'SELLER', 'ADMIN', 'SYSTEM'));

-- Index hỗ trợ background job quét đơn PENDING theo thời gian tạo
CREATE INDEX IF NOT EXISTS idx_orders_pending_created ON orders (created_at) WHERE status = 'PENDING';
```

- [ ] **Step 4: Cập nhật `OrderCanceller.java` và `Order.java`**
Trong `OrderCanceller.java`:
```java
public enum OrderCanceller {
    BUYER,
    SELLER,
    ADMIN,
    SYSTEM
}
```
Trong `Order.java`, thêm:
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

- [ ] **Step 5: Chạy test để xác nhận test xanh**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.model.OrderModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add fashionstore/src/main/resources/db/migration/V31__order_auto_cancel_system.sql \
        fashionstore/src/main/java/com/minhnbnt/fashionstore/model/OrderCanceller.java \
        fashionstore/src/main/java/com/minhnbnt/fashionstore/model/Order.java \
        fashionstore/src/test/java/com/minhnbnt/fashionstore/model/OrderModelTest.java
git commit -m "feat(order): add SYSTEM order canceller and V31 migration"
```

---

### Task 2: Chuẩn Hóa Vòng Đời Tồn Kho (Trừ Kho Lúc Checkout & Hoàn Kho Khi Hủy)

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderServiceTest.java`

**Interfaces:**
- `OrderService.checkout`: Trừ kho nguyên tử ngay khi tạo đơn; ném 409 nếu không đủ tồn.
- `OrderService.confirm`: Không trừ kho nữa (kho đã giữ từ lúc checkout); chuyển `PENDING` -> `CONFIRMED`.
- `OrderService.cancel` & `sellerCancel`: Hoàn kho nguyên tử cho cả đơn `PENDING` và `CONFIRMED`.

- [ ] **Step 1: Viết test đỏ kiểm tra trừ kho lúc checkout và hoàn kho khi hủy**
Thêm các test case vào `OrderServiceTest.java`:
1. `checkoutDeductsStockImmediately()`: Tạo sản phẩm tồn kho 5. Tạo đơn 2 sản phẩm → tồn kho còn 3 ngay khi đơn ở `PENDING`.
2. `checkoutFailsWhenStockInsufficient()`: Tạo sản phẩm tồn kho 1. Tạo đơn 2 sản phẩm → ném `ResponseStatusException` 409 Conflict.
3. `confirmDoesNotDeductStockAgain()`: Xác nhận đơn đã tạo → tồn kho vẫn giữ nguyên là 3, không bị trừ tiếp.
4. `buyerCancelRestocksPendingOrder()`: Khách hủy đơn `PENDING` → tồn kho được hoàn trả lại thành 5.
5. `sellerCancelRestocksPendingOrder()`: Shop hủy đơn `PENDING` → tồn kho được hoàn trả lại thành 5.

- [ ] **Step 2: Chạy test để xác nhận test đỏ**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.order.OrderServiceTest.checkoutDeductsStockImmediately"`
Expected: FAIL vì hiện tại checkout chưa trừ kho.

- [ ] **Step 3: Triển khai cập nhật trong `OrderService.java`**
1. Trong `checkout(UUID buyerId, CheckoutRequest request)`:
Thêm logic trừ kho nguyên tử trước khi tạo order:
```java
for (var snapshot : snapshots) {
    if (!productStock.decrementVariantStock(snapshot.variantId(), snapshot.quantity())) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "Insufficient stock for product " + snapshot.productName()
        );
    }
}
```
2. Trong `confirm(UUID userId, UUID orderId)`:
Bỏ vòng lặp gọi `productStock.decrementVariantStock`, chỉ giữ kiểm tra quyền, trạng thái `PENDING`, gọi `order.confirm()`, lưu history, audit và notification.
3. Thay thế hàm `restockIfConfirmed` thành hàm tổng quát `restockOrderItems`:
```java
private void restockOrderItems(Order order, List<OrderItem> orderItems) {
    for (var item : orderItems) {
        if (item.getVariantId() == null) {
            continue;
        }
        productStock.incrementVariantStock(item.getVariantId(), item.getQuantity());
    }
}
```
4. Gọi `restockOrderItems` trong cả `cancel(buyerId, orderId)` và `sellerCancel(userId, orderId, request)`.

- [ ] **Step 4: Chạy lại toàn bộ test trong `OrderServiceTest`**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.order.OrderServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java \
        fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderServiceTest.java
git commit -m "feat(order): deduct stock on checkout and restock on pending cancellation"
```

---

### Task 3: Background Job Tự Động Hủy Đơn Quá Hạn 24 Giờ

**Files:**
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderRepository.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJob.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJobTest.java`

**Interfaces:**
- `OrderRepository`: `List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff)`
- `OrderService.systemCancelExpiredOrder(UUID orderId)`
- `OrderAutoCancelJob.scanAndCancelExpiredOrders()`

- [ ] **Step 1: Viết test đỏ cho `OrderAutoCancelJobTest`**
Tạo `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJobTest.java`:
```java
package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.OrderCanceller;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.test.ProductTestFixtures;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderAutoCancelJobTest {

    @Autowired
    private OrderAutoCancelJob job;

    @Autowired
    private OrderService orders;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void autoCancelJobCancelsExpiredPendingOrderAndRestocks() {
        var ownerId = UUID.randomUUID();
        users.upsert(ownerId, new CreateUserRequest("owner-" + ownerId + "@t.local", "Owner", null));
        var adminId = UUID.randomUUID();
        users.upsert(adminId, new CreateUserRequest("admin-" + adminId + "@t.local", "Admin", null));

        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop AutoCancel", "shop-autocancel-" + UUID.randomUUID()));
        shops.approve(adminId, shop.id());

        var categoryId = catalog.listCategories().getFirst().id();
        var product = catalog.createProduct(ownerId, shop.id(), new CreateProductRequest(
            "Product AC", "Desc", categoryId,
            List.of(new CreateVariantRequest("M", "Den", "SKU-AC", 100000L, 10))
        ));
        var variantId = product.variants().getFirst().id();

        var buyerId = UUID.randomUUID();
        users.upsert(buyerId, new CreateUserRequest("buyer-" + buyerId + "@t.local", "Buyer", null));

        var order = orders.checkout(buyerId, new CheckoutRequest(
            shop.id(), List.of(new CheckoutItemRequest(variantId, 2)), 0L, "Buyer", "090", "Hanoi"
        ));

        // Tồn kho sau checkout là 10 - 2 = 8
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(8);

        // Giả lập đơn hàng được tạo cách đây 25 giờ
        var pastTime = Instant.now().minus(Duration.ofHours(25));
        jdbcClient.sql("UPDATE orders SET created_at = :past WHERE id = :id")
            .param("past", pastTime)
            .param("id", order.id())
            .update();

        // Kích hoạt job
        job.scanAndCancelExpiredOrders();

        // Kiểm tra đơn đã bị hủy bởi SYSTEM và tồn kho được hoàn trả
        var updated = orderRepository.findById(order.id()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(updated.getCancelledBy()).isEqualTo(OrderCanceller.SYSTEM);
        assertThat(updated.getCancelReason()).contains("24");

        // Tồn kho được khôi phục về 10
        assertThat(catalog.findVariantById(variantId).orElseThrow().getStockQuantity()).isEqualTo(10);
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test đỏ**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.order.OrderAutoCancelJobTest"`
Expected: FAIL vì `OrderAutoCancelJob` chưa tồn tại.

- [ ] **Step 3: Thêm method vào `OrderRepository` & `OrderService`**
Trong `OrderRepository.java`:
```java
List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant cutoff);
```
Trong `OrderService.java`, thêm `systemCancelExpiredOrder(UUID orderId)`:
```java
@Transactional
public void systemCancelExpiredOrder(@NonNull UUID orderId) {
    var order = requireOrder(orderId);
    if (order.getStatus() != OrderStatus.PENDING) {
        return;
    }
    var from = order.getStatus();
    var orderItems = items.findByOrderId(order.getId());

    order.cancelBySystem("Quá thời hạn 24 giờ xác nhận", null);
    restockOrderItems(order, orderItems);
    histories.save(OrderStatusHistory.record(order.getId(), from, OrderStatus.CANCELLED, null, "Quá thời hạn 24 giờ xác nhận"));

    var saved = orders.save(order);
    audit.record(saved.getShopId(), saved.getShopId(), "ORDER_AUTO_CANCEL_EXPIRED", "ORDER", saved.getId(), "Hệ thống tự động hủy đơn do quá hạn 24 giờ");

    notifications.create(
        Set.of(order.getBuyerId()),
        NotificationCategory.ORDER,
        "ORDER_CANCELLED",
        "Đơn #" + order.getCode() + " đã tự động hủy",
        "Đơn #" + order.getCode() + " đã tự động hủy do người bán không xác nhận trong vòng 24 giờ.",
        "/account",
        order.getId()
    );

    notifications.create(
        shops.findActiveUserIdsByShop(order.getShopId()),
        NotificationCategory.SELLING,
        "ORDER_CANCELLED",
        "Đơn #" + order.getCode() + " tự động hủy quá hạn",
        "Đơn #" + order.getCode() + " đã tự động hủy do quá thời hạn 24 giờ xác nhận.",
        "/shop?shopId=" + order.getShopId() + "&tab=orders&orderId=" + order.getId(),
        order.getId()
    );
}
```

- [ ] **Step 4: Tạo class `OrderAutoCancelJob.java`**
Tạo `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJob.java`:
```java
package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.features.order.repository.OrderRepository;
import com.minhnbnt.fashionstore.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;

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

- [ ] **Step 5: Chạy test để xác nhận test xanh**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.order.OrderAutoCancelJobTest"`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/repository/OrderRepository.java \
        fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java \
        fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJob.java \
        fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/OrderAutoCancelJobTest.java
git commit -m "feat(order): add 24h auto-cancel background job and notifications"
```

---

### Task 4: DTO Mở Rộng, API Xác Nhận Hàng Loạt & API Timeline

**Files:**
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/BulkConfirmOrderRequest.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/BulkConfirmOrderResponse.java`
- Create: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/BulkConfirmFailureItem.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/OrderResponse.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Modify: `fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/controller/SellerOrderController.java`
- Test: `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/SellerOrderBulkConfirmTest.java`

**Interfaces:**
- `POST /api/seller/orders/bulk-confirm`: Nhận `BulkConfirmOrderRequest(List<UUID> orderIds)`, trả về `BulkConfirmOrderResponse(totalRequested, successCount, failureCount, successIds, failures)`.
- `GET /api/seller/orders/{id}/timeline`: Trả về `List<OrderTimelineResponse>`.
- `OrderResponse`: Bổ sung `paymentMethod`, `note`, `cancelReason`, `cancelNote`, `cancelledBy`, `confirmedAt`, `cancelledAt`.

- [ ] **Step 1: Viết test đỏ cho Bulk Confirm và Timeline trong `SellerOrderBulkConfirmTest.java`**
Tạo `fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/SellerOrderBulkConfirmTest.java`:
```java
package com.minhnbnt.fashionstore.features.order;

import com.minhnbnt.fashionstore.TestcontainersConfiguration;
import com.minhnbnt.fashionstore.dto.catalog.CreateProductRequest;
import com.minhnbnt.fashionstore.dto.catalog.CreateVariantRequest;
import com.minhnbnt.fashionstore.dto.identity.CreateUserRequest;
import com.minhnbnt.fashionstore.dto.order.BulkConfirmOrderRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutItemRequest;
import com.minhnbnt.fashionstore.dto.order.CheckoutRequest;
import com.minhnbnt.fashionstore.features.catalog.CatalogService;
import com.minhnbnt.fashionstore.features.identity.UserService;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.OrderStatus;
import com.minhnbnt.fashionstore.test.ShopTestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SellerOrderBulkConfirmTest {

    @Autowired
    private OrderService orders;

    @Autowired
    private CatalogService catalog;

    @Autowired
    private ShopService shops;

    @Autowired
    private UserService users;

    @Test
    void bulkConfirmProcessesValidOrdersAndReportsFailures() {
        var ownerId = UUID.randomUUID();
        users.upsert(ownerId, new CreateUserRequest("bulk-owner@t.local", "Owner", null));
        var adminId = UUID.randomUUID();
        users.upsert(adminId, new CreateUserRequest("bulk-admin@t.local", "Admin", null));

        var shop = shops.register(ownerId, ShopTestFixtures.createRequest("Shop Bulk", "shop-bulk-" + UUID.randomUUID()));
        shops.approve(adminId, shop.id());

        var categoryId = catalog.listCategories().getFirst().id();
        var product = catalog.createProduct(ownerId, shop.id(), new CreateProductRequest(
            "Product Bulk", "Desc", categoryId,
            List.of(new CreateVariantRequest("L", "Trang", "SKU-BLK", 150000L, 50))
        ));
        var variantId = product.variants().getFirst().id();

        var buyerId = UUID.randomUUID();
        users.upsert(buyerId, new CreateUserRequest("bulk-buyer@t.local", "Buyer", null));

        var o1 = orders.checkout(buyerId, new CheckoutRequest(shop.id(), List.of(new CheckoutItemRequest(variantId, 1)), 0L, "B1", "091", "Hanoi"));
        var o2 = orders.checkout(buyerId, new CheckoutRequest(shop.id(), List.of(new CheckoutItemRequest(variantId, 1)), 0L, "B2", "092", "Hanoi"));
        
        // Đã hủy o2 trước
        orders.cancel(buyerId, o2.id());

        var response = orders.bulkConfirm(ownerId, List.of(o1.id(), o2.id()));

        assertThat(response.totalRequested()).isEqualTo(2);
        assertThat(response.successCount()).isEqualTo(1);
        assertThat(response.failureCount()).isEqualTo(1);
        assertThat(response.successIds()).contains(o1.id());
        assertThat(response.failures().getFirst().orderId()).isEqualTo(o2.id());

        assertThat(orders.getDetail(buyerId, o1.id()).status()).isEqualTo("CONFIRMED");
    }
}
```

- [ ] **Step 2: Chạy test để xác nhận test đỏ**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.order.SellerOrderBulkConfirmTest"`
Expected: FAIL vì DTOs và method `bulkConfirm` chưa có.

- [ ] **Step 3: Tạo DTOs và mở rộng `OrderResponse.java`**
1. Tạo `BulkConfirmOrderRequest.java`, `BulkConfirmOrderResponse.java`, `BulkConfirmFailureItem.java`.
2. Mở rộng `OrderResponse.java` thêm:
```java
String paymentMethod,
String note,
@Nullable String cancelReason,
@Nullable String cancelNote,
@Nullable String cancelledBy,
@Nullable Instant confirmedAt,
@Nullable Instant cancelledAt,
```
3. Cập nhật `OrderService.toResponse(...)` truyền các trường này vào.

- [ ] **Step 4: Cập nhật `OrderService.java` & `SellerOrderController.java`**
1. Thêm `bulkConfirm(UUID userId, List<UUID> orderIds)` trong `OrderService.java`:
Lặp qua từng orderId, xử lý độc lập từng đơn trong try/catch (gọi `confirm(userId, orderId)`), nếu thành công add vào `successIds`, nếu `ResponseStatusException` add vào `failures`.
2. Thêm endpoint trong `SellerOrderController.java`:
```java
@PostMapping("/bulk-confirm")
@Operation(summary = "Xác nhận hàng loạt đơn hàng", description = "Xác nhận nhiều đơn cùng lúc. Đơn nào hợp lệ thì thành công, đơn lỗi thì trả về trong danh sách thất bại.")
@ApiResponse(responseCode = "200", description = "Kết quả xác nhận hàng loạt")
@SecurityRequirement(name = "bearerAuth")
public BulkConfirmOrderResponse bulkConfirm(
    @AuthenticationPrincipal Jwt jwt,
    @Valid @RequestBody BulkConfirmOrderRequest request
) {
    var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return orders.bulkConfirm(userId, request.orderIds());
}

@GetMapping("/{id}/timeline")
@Operation(summary = "Xem lịch sử xử lý đơn hàng", description = "Dòng thời gian các bước đặt hàng, xác nhận, giao hàng, hủy.")
@ApiResponse(responseCode = "200", description = "Danh sách mốc thời gian")
@SecurityRequirement(name = "bearerAuth")
public List<OrderTimelineResponse> timeline(
    @AuthenticationPrincipal Jwt jwt,
    @PathVariable UUID id
) {
    var userId = UUID.fromString(Objects.requireNonNull(jwt.getSubject()));
    return orders.getOrderTimeline(userId, id);
}
```

- [ ] **Step 5: Chạy test để xác nhận test xanh**
Run: `./gradlew test --tests "com.minhnbnt.fashionstore.features.order.SellerOrderBulkConfirmTest"`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add fashionstore/src/main/java/com/minhnbnt/fashionstore/dto/order/ \
        fashionstore/src/main/java/com/minhnbnt/fashionstore/features/order/ \
        fashionstore/src/test/java/com/minhnbnt/fashionstore/features/order/SellerOrderBulkConfirmTest.java
git commit -m "feat(order): add bulk confirm endpoint, timeline endpoint and extend order response"
```

---

### Task 5: Xuất OpenAPI Spec & Sinh Code TypeScript (Codegen)

**Files:**
- Modify: `fashionstore/docs/openapi.json`
- Modify: `fashionstore-ui/docs/openapi.json`
- Modify: `fashionstore-ui/src/@generated/` (tự động sinh)

- [ ] **Step 1: Chạy `OpenApiExportTest` để xuất spec mới nhất**
Run:
```bash
./gradlew test --tests "com.minhnbnt.fashionstore.OpenApiExportTest"
```
Expected: PASS, file `docs/openapi.json` ở cả backend và UI được ghi đè phiên bản mới nhất chứa `/bulk-confirm`, `/timeline` và các trường `OrderResponse` mới.

- [ ] **Step 2: Chạy `pnpm codegen` tại `fashionstore-ui`**
Chạy trong thư mục `fashionstore-ui`:
```bash
pnpm codegen
```
Expected: Orval tạo/cập nhật `seller-orders.ts` với `useBulkConfirm`, `useTimeline` và các types trong `src/@generated/model/`.

- [ ] **Step 3: Kiểm tra TypeScript build**
Run:
```bash
pnpm build
```
Expected: Thành công không có lỗi type.

- [ ] **Step 4: Commit**
```bash
git -C D:\fashionstore\fashionstore add docs/openapi.json
git -C D:\fashionstore\fashionstore commit -m "docs: update openapi spec with bulk confirm and timeline"

git -C D:\fashionstore\fashionstore-ui add docs/openapi.json src/@generated/
git -C D:\fashionstore\fashionstore-ui commit -m "chore: regenerate api client from openapi spec"
```

---

### Task 6: Cập Nhật Adapters & Types Phía Frontend

**Files:**
- Modify: `fashionstore-ui/src/types/seller/orders.ts`
- Modify: `fashionstore-ui/src/lib/sellerAdapters.ts`
- Test: `fashionstore-ui/src/lib/sellerAdapters.test.ts` (nếu có, hoặc test case trong component test)

**Interfaces:**
- `OrderItem` mở rộng các trường: `paymentMethod: string`, `paymentStatus: string`, `note: string`, `cancelReason?: string`, `cancelNote?: string`, `cancelledBy?: string`, `confirmedAt?: string`, `cancelledAt?: string`.
- `orderItem(order: OrderResponse): OrderItem` ánh xạ đầy đủ các trường từ response.

- [ ] **Step 1: Cập nhật interface `OrderItem` trong `src/types/seller/orders.ts`**
Thêm các trường:
```typescript
export interface OrderItem {
  id: string
  code: string
  buyerName: string
  buyerPhone: string
  shippingAddress: string
  items: {
    productId: string
    productName: string
    size: string
    color: string
    sku: string
    quantity: number
    price: number
  }[]
  totalAmount: number
  subtotal: number
  shippingFee: number
  status: 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED'
  paymentMethod: string
  paymentStatus: string
  note: string
  cancelReason?: string
  cancelNote?: string
  cancelledBy?: string
  confirmedAt?: string
  cancelledAt?: string
  createdAt: string
  rawCreatedAt: string
}
```

- [ ] **Step 2: Cập nhật adapter `orderItem` trong `src/lib/sellerAdapters.ts`**
Cập nhật hàm `orderItem`:
- Map `sku` từ `item.sku ?? ''`.
- Map `paymentMethod`: `safeText(order.paymentMethod)`.
- Map `paymentStatus`: `safeText(order.paymentStatus)`.
- Map `note`: `safeText(order.note)`.
- Map `cancelReason`: `order.cancelReason`.
- Map `cancelNote`: `order.cancelNote`.
- Map `cancelledBy`: `order.cancelledBy`.
- Map `confirmedAt`: `order.confirmedAt ? formatDateTime(order.confirmedAt) : undefined` (theo convention không ternary, dùng if).
- Map `rawCreatedAt`: `order.createdAt ?? ''` để phục vụ tính toán đếm ngược SLA.

- [ ] **Step 3: Kiểm tra định dạng code & lint**
Run:
```bash
pnpm format && pnpm lint
```
Expected: 0 error, 0 warning.

- [ ] **Step 4: Commit**
```bash
git -C D:\fashionstore\fashionstore-ui add src/types/seller/orders.ts src/lib/sellerAdapters.ts
git -C D:\fashionstore\fashionstore-ui commit -m "feat(seller): update order types and adapter with payment, note and cancel metadata"
```

---

### Task 7: Nâng Cấp Giao Diện `SellerOrdersTab.tsx`

**Files:**
- Modify: `fashionstore-ui/src/components/seller-dashboard-tab/SellerOrdersTab.tsx`
- Test: `fashionstore-ui/src/components/seller-dashboard-tab/SellerOrdersTab.test.tsx`

**Interfaces:**
- 7 Tab phân loại: `ALL`, `PENDING`, `CONFIRMED`, `SHIPPING`, `DELIVERED`, `CANCELLED`, `RETURNS`.
- Hộp kiểm chọn đơn + "Chọn tất cả" + Floating Action Bar `Xác nhận tất cả (N đơn)`.
- Modal báo cáo kết quả Partial Success.
- Badge đếm ngược hạn 24 giờ trên đơn `PENDING` (màu cam khi < 6h, đỏ khi < 2h).
- Modal chi tiết đơn hàng: Người nhận, COD, ghi chú từ khách, sản phẩm kèm size/màu/SKU, và Order Timeline dọc.
- Modal từ chối đơn: Radio lý do chuẩn + textarea ghi chú bổ sung.

- [ ] **Step 1: Viết test mở rộng trong `SellerOrdersTab.test.tsx`**
Thêm các bài test kiểm tra:
1. Render đầy đủ 7 tab: *Tất cả*, *Chờ xác nhận*, *Đang chuẩn bị*, *Đã giao cho ĐVVC*, *Hoàn thành*, *Đã hủy*, *Đổi trả*.
2. Hiển thị nhãn đếm ngược 24h trên đơn có status `PENDING`.
3. Hiển thị thông tin ghi chú khách hàng và phương thức thanh toán.
4. Hiển thị checkbox chọn đơn và nút xác nhận hàng loạt khi chọn đơn.

- [ ] **Step 2: Chạy test để xác nhận test đỏ**
Run:
```bash
pnpm test --run SellerOrdersTab.test.tsx
```
Expected: FAIL vì các phần tử mới chưa có trong component.

- [ ] **Step 3: Triển khai các tính năng mới trong `SellerOrdersTab.tsx`**
1. Cấu hình mảng 7 tab:
```typescript
const ORDER_TABS = [
  { id: 'ALL', label: 'Tất cả' },
  { id: 'PENDING', label: 'Chờ xác nhận' },
  { id: 'CONFIRMED', label: 'Đang chuẩn bị' },
  { id: 'SHIPPING', label: 'Đã giao cho ĐVVC' },
  { id: 'DELIVERED', label: 'Hoàn thành' },
  { id: 'CANCELLED', label: 'Đã hủy' },
  { id: 'RETURNS', label: 'Đổi trả' },
]
```
2. Thêm hàm tính toán thời gian SLA 24h:
```typescript
function getSlaCountdown(createdAtStr: string) {
  const createdTime = new Date(createdAtStr).getTime()
  const deadline = createdTime + 24 * 3600 * 1000
  const now = Date.now()
  const diffMs = deadline - now
  // trả về { isExpired, hours, minutes, colorClass, text }
}
```
3. State quản lý chọn hàng loạt:
- `selectedOrderIds: string[]`
- Hàm toggle chọn 1 đơn, hàm toggle chọn tất cả đơn `PENDING`.
- Floating action bar cố định góc dưới màn hình khi `selectedOrderIds.length > 0`.
- Tích hợp `useBulkConfirm` mutation: khi xong, hiển thị kết quả. Nếu có thất bại, mở Modal chi tiết lỗi.
4. Modal Xem Chi Tiết Đơn Hàng:
- Nút "Xem chi tiết" mở Modal.
- Hiển thị đầy đủ thông tin: Người nhận, phương thức thanh toán COD, ghi chú từ khách, danh sách món kèm size/màu/SKU/thành tiền.
- Nhúng `useTimeline(selectedOrder.id)` để hiển thị dòng thời gian các bước (Đặt hàng → Xác nhận → Giao ĐVVC → Hoàn thành / Hủy).
5. Modal Từ Chối Đơn:
- Cung cấp danh sách lý do chuẩn: `Hết hàng trong kho`, `Sai giá hoặc thông tin sản phẩm`, `Không liên lạc được với khách hàng`, `Người mua yêu cầu hủy đơn`, `Lý do khác`.
- Textarea nhập ghi chú gửi đến khách hàng.

- [ ] **Step 4: Chạy test xác nhận xanh**
Run:
```bash
pnpm test --run SellerOrdersTab.test.tsx
```
Expected: PASS.

- [ ] **Step 5: Chạy kiểm tra lint & build frontend**
Run:
```bash
pnpm format && pnpm lint && pnpm build
```
Expected: 0 error, 0 warning, build thành công.

- [ ] **Step 6: Commit**
```bash
git -C D:\fashionstore\fashionstore-ui add src/components/seller-dashboard-tab/SellerOrdersTab.tsx \
        src/components/seller-dashboard-tab/SellerOrdersTab.test.tsx
git -C D:\fashionstore\fashionstore-ui commit -m "feat(seller): implement 7 tabs, 24h SLA countdown, bulk confirm and order timeline"
```

---

### Task 8: Kiểm Thử Toàn Diện & Nghiệm Thu Hệ Thống

**Files:** Toàn bộ dự án

- [ ] **Step 1: Chạy toàn bộ test suite Backend**
Run:
```bash
./gradlew test
```
Expected: BUILD SUCCESSFUL, tất cả unit & integration tests đều xanh.

- [ ] **Step 2: Chạy toàn bộ test suite Frontend**
Run:
```bash
pnpm test --run
```
Expected: Tất cả bài test Vitest đều xanh.

- [ ] **Step 3: Chạy pre-commit checks Frontend**
Run:
```bash
pnpm format:check && pnpm lint && pnpm build
```
Expected: Không có cảnh báo hay lỗi cú pháp, build bundle thành công.

- [ ] **Step 4: Commit hoàn tất và cập nhật tài liệu API**
Cập nhật checkbox tiến độ trong `fashionstore/docs/api-endpoints.md`.
```bash
git -C D:\fashionstore\fashionstore add docs/api-endpoints.md
git -C D:\fashionstore\fashionstore commit -m "docs: update seller order endpoints progress"
```
