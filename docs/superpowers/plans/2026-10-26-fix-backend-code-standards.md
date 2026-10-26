# Kế hoạch Chi tiết Sửa lỗi Tuân thủ Chuẩn AGENTS.md (Backend)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khắc phục triệt để các nội dung CHƯA ĐẠT và CẦN DỌN DẸP theo quy chuẩn tại `AGENTS.md`, `docs/code-guide.md`, và `docs/structure.md`: loại bỏ toán tử ternary, dọn dẹp import Lombok/dead entity, và thiết lập triệt để hàng rào ranh giới aggregate (Cross-domain repository barrier) trên toàn bộ 8 services.

**Architecture:** 
- Tuân thủ nguyên tắc Explicit over clever: thay thế ternary bằng `if/else` tường minh hoặc early `return`.
- Tuân thủ nguyên tắc Vertical Slice & Aggregates (`docs/structure.md`): Service domain này cấm gọi Repository của domain khác. Bổ sung các API nghiệp vụ vào `ShopService`, `CatalogService`, và `OrderService` để các domain tiêu thụ (`Cart`, `Wishlist`, `Chat`, `Review`, `Order`, `ReturnRequest`, `Revenue`) gọi qua Service ranh giới thay vì chọc thẳng vào Repository nội bộ của domain bạn.
- Dọn dẹp dead entity `ProductReview.java` (đã bị drop table từ V11) và gỡ bỏ phụ thuộc thừa `spring-boot-starter-data-jpa-test`.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JDBC, Postgres (Testcontainers), JSpecify `@Nullable`, NullAway, Gradle 9.

**Spec:** Đối chiếu báo cáo review mã nguồn backend theo quy chuẩn tại `AGENTS.md`, `docs/code-guide.md`, `docs/structure.md`, `docs/data.md`.

---

## Global Constraints

- **Cấm ternary (`? :`)**: Mọi biểu thức ternary phải chuyển thành `if/else` hoặc `if` + early `return`.
- **Entity Immutability & Allowlist**: Cấm `@Data`, `@Setter`, `@EqualsAndHashCode`, `@Builder` trên Entity. Mọi entity implement `Persistable<UUID>` với factory `create(...)`.
- **Cross-Domain Repository Barrier**: Service domain A tuyệt đối không inject `Repository` của domain B. Mọi trao đổi dữ liệu xuyên domain phải đi qua `Service` của domain B.
- **NullAway mức ERROR**: Mọi trường, tham số, return type mặc định non-null. Chỉ dùng `@Nullable` từ `org.jspecify.annotations.Nullable` khi nghiệp vụ cho phép vắng mặt.
- **Tiêu chuẩn hoàn thành**: Toàn bộ suite `./gradlew test` (664 tests) phải xanh 100% sau mỗi task.

---

## Review Focus

1. **Tính nguyên tử của trừ kho khi Order Checkout / Confirm**: `CatalogService` phải bảo toàn logic cập nhật kho nguyên tử (`variants.decrementStock(...)`) và ghi nhận `StockMovement` khi chuyển từ `OrderService`.
2. **Khả năng hiển thị và validation của Variant/Product trong giỏ hàng và đơn hàng**: Đảm bảo trạng thái `ProductStatus.ACTIVE` và điều kiện shop đã approved được kiểm tra chặt chẽ như logic hiện hành.
3. **Logic kiểm tra điều kiện viết review (`hasDeliveredAndReceivedProduct`)**: Chuyển vào `OrderService` phải đảm bảo chỉ đơn hàng đã `DELIVERED` và có `receivedAt != null` chứa đúng `productId` mới trả về `true`.
4. **Không phát sinh circular dependency (phụ thuộc vòng)**: Phân tầng phụ thuộc 1 chiều: `Shop` -> `Catalog` -> `Order` -> `Cart`/`Review`/`Wishlist`/`Chat`.
5. **Cập nhật Mock trong Unit Test**: Các test unit cô lập (`ChatServiceUnitTest`, `RevenueServiceUnitTest`, `ReturnRequestServiceUnitTest`) phải mock đúng Service mới thay vì Repository cũ, loại bỏ các trường mock thừa không sử dụng.

---

### Task 1: Xóa bỏ 3 Toán tử Ternary, Dọn dẹp Entity và Phụ thuộc Thừa

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java:97`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java:127-129`
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/OrderStatusHistory.java:53`
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/WishlistItem.java:6`
- Modify: `build.gradle.kts:48`
- Delete: `src/main/java/com/minhnbnt/fashionstore/model/ProductReview.java`

- [ ] **Step 1: Sửa ternary trong `OrderService.java:97`**

Thay thế:
```java
var shippingFee = request.shippingFee() == null ? 0L : request.shippingFee();
```
Bằng:
```java
var shippingFee = 0L;
if (request.shippingFee() != null) {
    shippingFee = request.shippingFee();
}
```

- [ ] **Step 2: Sửa ternary trong `ReviewService.java:127-129`**

Thay thế:
```java
var list = onlyUnreplied
    ? reviews.findUnrepliedByShopIdOrderByCreatedAtDesc(shopId)
    : reviews.findByShopIdOrderByCreatedAtDesc(shopId);
```
Bằng:
```java
List<Review> list;
if (onlyUnreplied) {
    list = reviews.findUnrepliedByShopIdOrderByCreatedAtDesc(shopId);
} else {
    list = reviews.findByShopIdOrderByCreatedAtDesc(shopId);
}
```

- [ ] **Step 3: Sửa ternary trong `OrderStatusHistory.java:53`**

Tách biến `fromName`:
```java
String fromName = null;
if (from != null) {
    fromName = from.name();
}

return new OrderStatusHistory(
    UUID.randomUUID(),
    orderId,
    fromName,
    to.name(),
    changedBy,
    note,
    Instant.now(),
    true
);
```

- [ ] **Step 4: Dọn dẹp Entity & Dependencies thừa**

1. Trong `WishlistItem.java`: Xóa dòng `import lombok.Setter;`.
2. Xóa file dead-code `src/main/java/com/minhnbnt/fashionstore/model/ProductReview.java`.
3. Trong `build.gradle.kts`: Xóa dòng phụ thuộc `testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")`.

- [ ] **Step 5: Chạy test kiểm chứng Task 1**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.model.OrderStatusHistoryTest" --tests "com.minhnbnt.fashionstore.model.WishlistItemTest"`
Expected: PASS

---

### Task 2: Mở rộng API Service trong `ShopService` để Đóng gói Truy cập Shop & Thành viên

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopService.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopServiceTest.java`

**Interfaces:**
- Produces:
  ```java
  public Optional<Shop> findEntityById(@NonNull UUID id)
  public Shop requireShop(@NonNull UUID id)
  public String getShopName(@NonNull UUID id)
  public List<UUID> findShopIdsByUser(@NonNull UUID userId)
  public List<UUID> findActiveUserIdsByShop(@NonNull UUID shopId)
  ```

- [ ] **Step 1: Viết test kiểm tra các method mới trong `ShopServiceTest.java`**

Bổ sung test cho `findEntityById`, `requireShop`, `getShopName`, `findShopIdsByUser`, `findActiveUserIdsByShop`.

- [ ] **Step 2: Triển khai các method trong `ShopService.java`**

```java
@Transactional(readOnly = true)
public Optional<Shop> findEntityById(@NonNull UUID id) {
    return shops.findById(id);
}

@Transactional(readOnly = true)
public Shop requireShop(@NonNull UUID id) {
    var shop = shops.findById(id).orElse(null);
    if (shop == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found");
    }
    return shop;
}

@Transactional(readOnly = true)
public String getShopName(@NonNull UUID id) {
    var shop = shops.findById(id).orElse(null);
    if (shop == null) {
        return "Cửa hàng";
    }
    return shop.getName();
}

@Transactional(readOnly = true)
public List<UUID> findShopIdsByUser(@NonNull UUID userId) {
    return members.findShopIdsByUser(userId);
}

@Transactional(readOnly = true)
public List<UUID> findActiveUserIdsByShop(@NonNull UUID shopId) {
    return members.findActiveUserIdsByShop(shopId);
}
```

- [ ] **Step 3: Chạy test xác nhận `ShopServiceTest` pass**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.ShopServiceTest"`
Expected: PASS

---

### Task 3: Refactor các Service Gọi `ShopRepository` & `ShopMemberRepository` (`CatalogService`, `RevenueService`, `ReturnRequestService`, `ChatService`)

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/RevenueService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/ReturnRequestService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/chat/ChatService.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/RevenueServiceUnitTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/ReturnRequestServiceUnitTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/chat/ChatServiceUnitTest.java`

- [ ] **Step 1: Refactor `CatalogService.java`**

- Xóa `import com.minhnbnt.fashionstore.features.shop.ShopRepository;`
- Thay trường `private final ShopRepository shopRepository;` bằng `private final ShopService shops;`
- Tại line 164: Thay `var shop = shopRepository.findById(shopId)...` bằng `var shop = shops.requireShop(shopId);`

- [ ] **Step 2: Refactor `RevenueService.java` và cập nhật `RevenueServiceUnitTest.java`**

- Trong `RevenueService.java`:
  - Thay `private final ShopRepository shops;` bằng `private final ShopService shops;`.
  - Line 155: Thay `shops.findById(request.shopId()).orElse(null)` bằng `shops.findEntityById(request.shopId()).orElse(null)`.
- Trong `RevenueServiceUnitTest.java`:
  - Thay `@Mock private ShopRepository shops;` bằng `@Mock private ShopService shops;`.
  - Xóa trường mock không dùng: `private ShopAuditService audit;`.

- [ ] **Step 3: Refactor `ReturnRequestService.java` và cập nhật `ReturnRequestServiceUnitTest.java`**

- Trong `ReturnRequestService.java`:
  - Xóa `import com.minhnbnt.fashionstore.features.shop.ShopMemberRepository;`.
  - Thêm `private final ShopService shops;`.
  - Line 217: Thay `members.findShopIdsByUser(userId)` bằng `shops.findShopIdsByUser(userId)`.
- Trong `ReturnRequestServiceUnitTest.java`:
  - Xóa trường mock không dùng: `private ShopAuditService audit;`.
  - Mock `ShopService` nếu test cần duyệt qua danh sách shop.

- [ ] **Step 4: Refactor `ChatService.java` và cập nhật `ChatServiceUnitTest.java`**

- Trong `ChatService.java`:
  - Xóa import `ShopRepository` và `ShopMemberRepository`.
  - Thay thế 2 repository này bằng `private final ShopService shops;`.
  - Thay `shops.findById(...)` bằng `shops.findEntityById(...)` hoặc `shops.getShopName(...)`.
  - Thay `members.findRole(...)` bằng `shops.isMember(...)`.
  - Thay `members.findActiveUserIdsByShop(...)` bằng `shops.findActiveUserIdsByShop(...)`.
  - Thay `members.findOwner(...)` bằng `shops.ownerOf(...)`.
- Trong `ChatServiceUnitTest.java`:
  - Thay mock `ShopRepository` và `ShopMemberRepository` bằng mock `ShopService`.
  - Xóa trường mock thừa `private ShopAccessChecker access;`.
  - Cập nhật các stub `when(shops.findEntityById(...))` và `when(shops.isMember(...))` tương ứng.

- [ ] **Step 5: Chạy test xác nhận các service này đã pass**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.order.RevenueServiceUnitTest" --tests "com.minhnbnt.fashionstore.features.order.ReturnRequestServiceUnitTest" --tests "com.minhnbnt.fashionstore.features.chat.ChatServiceUnitTest"`
Expected: PASS

---

### Task 4: Mở rộng API Service trong `CatalogService` & `OrderService` cho Đơn hàng, Giỏ hàng và Review

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/dto/catalog/WishlistProductCard.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/catalog/CatalogServiceTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/OrderServiceTest.java`

**Interfaces:**
- `CatalogService` Produces:
  ```java
  public Optional<ProductVariant> findVariantById(@NonNull UUID variantId)
  public Optional<Product> findProductById(@NonNull UUID productId)
  public List<ProductImage> findImagesByProductId(@NonNull UUID productId)
  public Product requireActiveProduct(@NonNull UUID productId)
  public ProductVariant requireVariant(@NonNull UUID variantId)
  public boolean decrementVariantStock(@NonNull UUID variantId, int quantity)
  public void incrementVariantStock(@NonNull UUID variantId, int quantity)
  public void recordStockMovement(@NonNull UUID variantId, @NonNull UUID shopId, int quantity, @NonNull String type, @NonNull UUID referenceId)
  public WishlistProductCard getWishlistProductCard(@NonNull UUID productId)
  ```
- `OrderService` Produces:
  ```java
  public boolean hasDeliveredAndReceivedProduct(@NonNull UUID buyerId, @NonNull UUID productId)
  ```

- [ ] **Step 1: Tạo DTO `WishlistProductCard.java`**

Tạo file `src/main/java/com/minhnbnt/fashionstore/dto/catalog/WishlistProductCard.java`:
```java
package com.minhnbnt.fashionstore.dto.catalog;

import org.jspecify.annotations.Nullable;
import java.util.UUID;

public record WishlistProductCard(
    UUID productId,
    String name,
    String slug,
    @Nullable Long minPrice,
    @Nullable String primaryImageKey,
    boolean inStock,
    @Nullable String unavailableReason
) {}
```

- [ ] **Step 2: Triển khai các phương thức truy vấn và kho trong `CatalogService.java`**

- `findVariantById`, `findProductById`, `findImagesByProductId`.
- `requireActiveProduct`: ném `404` nếu không tìm thấy hoặc `status != ACTIVE` hoặc shop chưa approved.
- `decrementVariantStock`, `incrementVariantStock`, `recordStockMovement`.
- `getWishlistProductCard`: tổng hợp giá min, ảnh chính, trạng thái tồn kho và lý do không khả dụng.

- [ ] **Step 3: Triển khai `hasDeliveredAndReceivedProduct` trong `OrderService.java`**

```java
@Transactional(readOnly = true)
public boolean hasDeliveredAndReceivedProduct(@NonNull UUID buyerId, @NonNull UUID productId) {
    for (var order : orders.findByBuyerIdOrderByCreatedAtDesc(buyerId)) {
        if (order.getStatus() != OrderStatus.DELIVERED) {
            continue;
        }
        if (order.getReceivedAt() == null) {
            continue;
        }
        for (var item : items.findByOrderId(order.getId())) {
            if (productId.equals(item.getProductId())) {
                return true;
            }
        }
    }
    return false;
}
```

- [ ] **Step 4: Chạy test xác nhận các method mới**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.catalog.CatalogServiceTest" --tests "com.minhnbnt.fashionstore.features.order.OrderServiceTest"`
Expected: PASS

---

### Task 5: Refactor `ReviewService` và `WishlistService` Không còn Truy cập Repo Khác

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/wishlist/WishlistService.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/review/ReviewServiceTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/wishlist/WishlistServiceTest.java`

- [ ] **Step 1: Refactor `ReviewService.java`**

- Xóa các imports: `ProductRepository`, `OrderRepository`, `OrderItemRepository`.
- Thay bằng inject:
  ```java
  private final CatalogService catalog;
  private final OrderService orderService;
  ```
- Thay `requireVisible(productId)` bằng `catalog.requireActiveProduct(productId)`.
- Thay `products.findById(productId)` bằng `catalog.findProductById(productId)`.
- Thay phương thức private `hasDeliveredAndReceived(buyerId, productId)` bằng gọi `orderService.hasDeliveredAndReceivedProduct(buyerId, productId)`.

- [ ] **Step 2: Refactor `WishlistService.java`**

- Xóa các imports: `ProductRepository`, `ProductVariantRepository`, `ProductImageRepository`.
- Thay bằng inject `private final CatalogService catalog;`.
- Trong `toggle`: gọi `catalog.requireActiveProduct(productId)` thay vì private `requireViewable`.
- Trong `toResponse`: gọi `catalog.getWishlistProductCard(productId)` và map sang `WishlistItemResponse`.

- [ ] **Step 3: Chạy test xác nhận `ReviewServiceTest` và `WishlistServiceTest`**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.review.ReviewServiceTest" --tests "com.minhnbnt.fashionstore.features.wishlist.WishlistServiceTest"`
Expected: PASS

---

### Task 6: Refactor `CartService` và `OrderService` Không còn Truy cập Repo Khác

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/cart/CartService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/cart/CartServiceTest.java`
- Test: `src/test/java/com/minhnbnt/fashionstore/features/order/OrderServiceTest.java`

- [ ] **Step 1: Refactor `CartService.java`**

- Xóa các imports: `ProductRepository`, `ProductVariantRepository`, `ProductImageRepository`, `ShopRepository`.
- Inject `CatalogService catalog` (đã có sẵn `ShopService shops`).
- Thay thế các lời gọi `variants.findById(...)`, `products.findById(...)`, `images.findByProductId...` bằng `catalog.findVariantById(...)`, `catalog.findProductById(...)`, `catalog.findImagesByProductId(...)`.
- Thay thế `shopRepo.findById(id).map(Shop::getName)...` bằng `shops.getShopName(id)`.

- [ ] **Step 2: Refactor `OrderService.java`**

- Xóa các imports: `ProductRepository`, `ProductVariantRepository`, `StockMovementRepository`, `ShopMemberRepository`.
- Inject `CatalogService catalog` và `ShopService shops`.
- Trong `checkout`: dùng `catalog.findVariantById(...)` và `catalog.findProductById(...)` để kiểm tra và tạo `OrderItemSnapshot`.
- Trong `confirmOrder`: thay `variants.decrementStock(...)` và `movements.save(...)` bằng:
  ```java
  if (!catalog.decrementVariantStock(item.getVariantId(), item.getQuantity())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient stock for " + item.getProductName());
  }
  catalog.recordStockMovement(item.getVariantId(), order.getShopId(), item.getQuantity(), "ORDER_CHECKOUT", order.getId());
  ```
- Trong `restockIfConfirmed`: thay `variants.incrementStock(...)` và `movements.save(...)` bằng:
  ```java
  catalog.incrementVariantStock(item.getVariantId(), item.getQuantity());
  catalog.recordStockMovement(item.getVariantId(), order.getShopId(), item.getQuantity(), "ORDER_CANCEL", order.getId());
  ```
- Trong `orderReadableShopIds`: thay `members.findShopIdsByUser(userId)` bằng `shops.findShopIdsByUser(userId)`.

- [ ] **Step 3: Chạy test xác nhận `CartServiceTest` và `OrderServiceTest`**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.cart.CartServiceTest" --tests "com.minhnbnt.fashionstore.features.order.OrderServiceTest"`
Expected: PASS

---

### Task 7: Xác minh Toàn diện & Kiểm tra Hàng rào Kiến trúc (Full Verification)

**Files:** Toàn bộ repository `fashionstore`.

- [ ] **Step 1: Quét lại toàn bộ codebase kiểm tra toán tử ternary**

Chạy script Python kiểm tra ternary trên toàn bộ `.java`:
Expected output: 0 ternary operators.

- [ ] **Step 2: Quét lại kiểm tra import chéo Repository**

Chạy script kiểm tra để xác nhận không còn bất kỳ Service nào import `Repository` thuộc domain khác.
Expected output: 0 cross-domain repository access.

- [ ] **Step 3: Chạy toàn bộ test suite dự án**

Run: `.\gradlew.bat test`
Expected: **BUILD SUCCESSFUL, 664/664 tests passed, 0 failures, 0 errors, 0 skipped**.
Log sạch, không còn JPA auto-configuration scanning warnings.
