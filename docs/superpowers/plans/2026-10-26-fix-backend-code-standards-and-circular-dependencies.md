# Fix Backend Code Standards & Break Service Circular Dependencies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khắc phục triệt để 59 dòng quá 120 ký tự, phá vỡ circular dependency giữa CatalogService ⇄ OrderService/ReviewService bằng việc tách `ProductStockService`, xóa bỏ hoàn toàn 4 annotation `@Lazy` cùng file `lombok.config`, và kiểm chứng gate `./gradlew test` trước khi merge vào master.

**Architecture:** 
- Đóng gói logic truy vấn sản phẩm/biến thể và biến động kho (decrement, increment, movement log) vào `ProductStockService` thuộc domain `features/catalog`.
- Chuyển `OrderService` và `ReviewService` sang inject `ProductStockService`, gỡ bỏ hoàn toàn `@Lazy` ở 4 vị trí và xóa file `lombok.config`, thiết lập đồ thị phụ thuộc 1 chiều DAG tường minh (`ProductStockService` -> `OrderService` -> `ReviewService` -> `CatalogService`).
- Ngắt dòng các record DTO, annotation OpenAPI/Swagger, SQL `@Query`, chuỗi log audit theo đúng quy chuẩn `docs/code-guide.md` (không dòng nào vượt quá 120 ký tự).

**Tech Stack:** Java 21, Spring Boot 4.1.1, Spring Data JDBC, Postgres, JSpecify, NullAway, Gradle 9.

**Spec:** [docs/superpowers/specs/2026-10-26-fix-backend-code-standards-and-circular-dependencies-design.md](file:///d:/fashionstore/fashionstore/docs/superpowers/specs/2026-10-26-fix-backend-code-standards-and-circular-dependencies-design.md)

---

## Global Constraints

- **Cấm ternary (`? :`)**: Mọi logic rẽ nhánh dùng `if/else` hoặc `if` + early `return`.
- **Giới hạn độ dài dòng**: Mọi dòng code, annotation, SQL, comment không vượt quá 120 ký tự (`docs/code-guide.md`).
- **Ranh giới Aggregate**: Service domain A tuyệt đối không inject Repository của domain B (`docs/structure.md`).
- **Explicit over clever**: Không dùng `@Lazy` để vượt vòng phụ thuộc bean. Không inject proxy ngầm.
- **NullAway**: Mọi tham số, trường, return type mặc định non-null (dùng `@NonNull`), chỉ gắn `@Nullable` khi giá trị thật sự optional theo nghiệp vụ.
- **Tiêu chuẩn hoàn thành**: Full suite `./gradlew test` pass 100%, 0 failures, 0 circular dependency warnings.

---

## Review Focus

1. **Trừ kho nguyên tử và rollback khi đặt/hủy đơn**: Đảm bảo `ProductStockService.decrementVariantStock` và `incrementVariantStock` giữ nguyên tính đúng đắn khi `OrderService.confirmOrder` và `cancelOrder` gọi tới.
2. **Kiểm tra quyền và tính hợp lệ sản phẩm khi viết review**: `ProductStockService.requireActiveProduct` phải kiểm tra chặt chẽ `ProductStatus.ACTIVE` và điều kiện shop đã approved.
3. **Không phá vỡ các caller hiện tại của CatalogService**: `CartService` và `WishlistService` vẫn tiếp tục hoạt động trơn tru qua `CatalogService` (hoặc `ProductStockService`).
4. **Không sót bất kỳ dòng nào > 120 ký tự**: Kiểm tra tự động bằng script trên toàn bộ 12+ files bị ảnh hưởng.
5. **Spring Context khởi tạo tất định**: Không còn bất kỳ bean cycle nào lúc context initialization.

---

### Task 1: Tạo `ProductStockService` và Unit Test Cô Lập

**Files:**
- Create: `src/main/java/com/minhnbnt/fashionstore/features/catalog/ProductStockService.java`
- Create: `src/test/java/com/minhnbnt/fashionstore/features/catalog/ProductStockServiceUnitTest.java`

**Interfaces:**
- Consumes:
  - `ProductRepository products`
  - `ProductVariantRepository variants`
  - `StockMovementRepository movements`
  - `ShopService shops`
- Produces:
  ```java
  public Optional<ProductVariant> findVariantById(@NonNull UUID variantId);
  public Optional<Product> findProductById(@NonNull UUID productId);
  public Product requireActiveProduct(@NonNull UUID productId);
  public boolean decrementVariantStock(@NonNull UUID variantId, int quantity);
  public void incrementVariantStock(@NonNull UUID variantId, int quantity);
  public void recordStockMovement(@NonNull UUID variantId, @NonNull UUID shopId, int quantity, @NonNull String type, @NonNull UUID referenceId);
  ```

- [ ] **Step 1: Viết test failing trong `ProductStockServiceUnitTest.java`**

Tạo file `src/test/java/com/minhnbnt/fashionstore/features/catalog/ProductStockServiceUnitTest.java`:
- Test `decrementVariantStock` thành công khi tồn kho đủ (trả về `true`).
- Test `decrementVariantStock` thất bại khi tồn kho thiếu (trả về `false`).
- Test `incrementVariantStock` gọi đúng `variants.incrementStock`.
- Test `requireActiveProduct` ném `404 NOT_FOUND` khi không tìm thấy product.
- Test `requireActiveProduct` ném `400 BAD_REQUEST` khi product không `ACTIVE` hoặc shop chưa approved.
- Test `recordStockMovement` lưu entity `StockMovement` với đúng thông tin.

- [ ] **Step 2: Chạy test để xác nhận fail vì chưa có `ProductStockService`**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.catalog.ProductStockServiceUnitTest"`
Expected: FAIL với lỗi compile "cannot find symbol class ProductStockService".

- [ ] **Step 3: Triển khai `ProductStockService.java`**

Tạo file `src/main/java/com/minhnbnt/fashionstore/features/catalog/ProductStockService.java`:
```java
package com.minhnbnt.fashionstore.features.catalog;

import com.minhnbnt.fashionstore.features.catalog.repository.ProductRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.ProductVariantRepository;
import com.minhnbnt.fashionstore.features.catalog.repository.StockMovementRepository;
import com.minhnbnt.fashionstore.features.shop.ShopService;
import com.minhnbnt.fashionstore.model.Product;
import com.minhnbnt.fashionstore.model.ProductStatus;
import com.minhnbnt.fashionstore.model.ProductVariant;
import com.minhnbnt.fashionstore.model.StockMovement;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final StockMovementRepository movements;
    private final ShopService shops;

    @Transactional(readOnly = true)
    public Optional<ProductVariant> findVariantById(@NonNull UUID variantId) {
        return variants.findById(variantId);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findProductById(@NonNull UUID productId) {
        return products.findById(productId);
    }

    @Transactional(readOnly = true)
    public Product requireActiveProduct(@NonNull UUID productId) {
        var product = products.findById(productId).orElse(null);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is not active");
        }
        var shop = shops.findEntityById(product.getShopId()).orElse(null);
        if (shop == null || !shop.isApproved()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop is not approved");
        }
        return product;
    }

    @Transactional
    public boolean decrementVariantStock(@NonNull UUID variantId, int quantity) {
        return variants.decrementStock(variantId, quantity) > 0;
    }

    @Transactional
    public void incrementVariantStock(@NonNull UUID variantId, int quantity) {
        variants.incrementStock(variantId, quantity);
    }

    @Transactional
    public void recordStockMovement(
        @NonNull UUID variantId,
        @NonNull UUID shopId,
        int quantity,
        @NonNull String type,
        @NonNull UUID referenceId
    ) {
        movements.save(new StockMovement(
            UUID.randomUUID(),
            variantId,
            shopId,
            quantity,
            type,
            referenceId,
            Instant.now(),
            true
        ));
    }
}
```

- [ ] **Step 4: Chạy lại test để xác nhận pass**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.catalog.ProductStockServiceUnitTest"`
Expected: PASS 100%.

---

### Task 2: Chuyển `OrderService` & `ReviewService` sang `ProductStockService` và Gỡ Bỏ `@Lazy`

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java`
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java`
- Delete: `lombok.config`

- [ ] **Step 1: Cập nhật `OrderService.java`**
1. Đổi `import com.minhnbnt.fashionstore.features.catalog.CatalogService;` thành `import com.minhnbnt.fashionstore.features.catalog.ProductStockService;`.
2. Thay trường:
```java
    @Lazy
    private final CatalogService catalog;
```
Bằng:
```java
    private final ProductStockService productStock;
```
3. Cập nhật các vị trí gọi:
- Line 102: `catalog.findVariantById(...)` -> `productStock.findVariantById(...)`
- Line 110: `catalog.findProductById(...)` -> `productStock.findProductById(...)`
- Line 399: `catalog.decrementVariantStock(...)` -> `productStock.decrementVariantStock(...)`
- Line 544: `catalog.incrementVariantStock(...)` -> `productStock.incrementVariantStock(...)`
- Line 545: `catalog.recordStockMovement(...)` -> `productStock.recordStockMovement(...)`

- [ ] **Step 2: Cập nhật `ReviewService.java`**
1. Đổi `import com.minhnbnt.fashionstore.features.catalog.CatalogService;` thành `import com.minhnbnt.fashionstore.features.catalog.ProductStockService;`.
2. Thay trường:
```java
    @Lazy
    private final CatalogService catalog;
```
Bằng:
```java
    private final ProductStockService productStock;
```
3. Cập nhật các vị trí gọi:
- Line 80: `catalog.findProductById(...)` -> `productStock.findProductById(...)`
- Line 167: `catalog.requireActiveProduct(...)` -> `productStock.requireActiveProduct(...)`
- Line 185: `catalog.findProductById(...)` -> `productStock.findProductById(...)`

- [ ] **Step 3: Cập nhật `CatalogService.java`**
1. Xóa bỏ `@Lazy` ở line 106 (`orderService`).
2. Xóa bỏ `@Lazy` ở line 111 (`reviews`).
3. Inject thêm `private final ProductStockService productStock;` (hoặc ủy quyền cho `productStock`).
4. Trong các phương thức `findVariantById`, `findProductById`, `requireActiveProduct`, `decrementVariantStock`, `incrementVariantStock`, `recordStockMovement`, ủy quyền cho `productStock` để giữ nguyên tương thích hoàn toàn cho các caller khác nếu có.

- [ ] **Step 4: Xóa bỏ file `lombok.config`**

Xóa file `lombok.config` khỏi root repo.

- [ ] **Step 5: Chạy unit test của `OrderService` và `ReviewService`**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.catalog.ProductStockServiceUnitTest" --tests "com.minhnbnt.fashionstore.features.review.ReviewControllerTest"`
Expected: PASS.

---

### Task 3: Format Dòng > 120 Ký Tự Trong Repositories và Builder

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsRepository.java` (21 dòng)
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/excel/SellerExcelReportBuilder.java` (10 dòng)

- [ ] **Step 1: Format `ShopStatsRepository.java`**
- Line 28: Ngắt dòng các trường trong record `OrderStatusSummaryRow`.
- Line 54: Ngắt dòng các trường trong record `ProductStatsRow`.
- Line 56: Ngắt dòng các trường trong record `CustomerOverviewRow`.
- Line 58: Ngắt dòng các trường trong record `TopCustomerRow`.
- Lines 65, 66, 67, 68, 98, 302, 303, 306, 310, 341, 369, 373, 404, 408, 440, 475, 479: Xuống dòng các mệnh đề SQL `COALESCE`, `SELECT`, `CASE WHEN`, `LEFT JOIN` trong `@Query`.

- [ ] **Step 2: Format `SellerExcelReportBuilder.java`**
- Line 123: Ngắt dòng chuỗi ngày tháng `"Từ " + ... + " đến " + ...`.
- Lines 146, 147, 153, 154, 155, 156, 157, 160, 163: Xuống dòng các tham số trong `addKpiRow(...)`.

- [ ] **Step 3: Kiểm tra độ dài dòng của 2 file này**

Chạy script python:
```bash
python -X utf8 -c "
for f in ['src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStatsRepository.java', 'src/main/java/com/minhnbnt/fashionstore/features/shop/excel/SellerExcelReportBuilder.java']:
    with open(f, 'r', encoding='utf-8') as fp:
        longs = [i for i, line in enumerate(fp, 1) if len(line.rstrip('\r\n')) > 120]
        print(f'{f}: {len(longs)} lines > 120')
"
```
Expected: `0 lines > 120` cho cả 2 files.

- [ ] **Step 4: Chạy test `SellerExcelReportBuilderTest`**

Run: `.\gradlew.bat test --tests "com.minhnbnt.fashionstore.features.shop.excel.SellerExcelReportBuilderTest"`
Expected: PASS.

---

### Task 4: Format Dòng > 120 Ký Tự Trong Services, Model, Controllers và Tests

**Files:**
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/catalog/CatalogService.java` (6 dòng: 792, 793, 817, 823, 973, 1378)
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopDecorationService.java` (3 dòng: 59, 73, 87)
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/order/OrderService.java` (2 dòng: 408, 443)
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/review/ReviewService.java` (2 dòng: 146, 161)
- Modify: `src/main/java/com/minhnbnt/fashionstore/features/shop/ShopStaffService.java` (2 dòng: 95, 451)
- Modify: `src/main/java/com/minhnbnt/fashionstore/model/Shop.java` (1 dòng: 361)
- Modify 5 Seller Controllers:
  - `src/main/java/com/minhnbnt/fashionstore/features/order/controller/SellerOrderController.java` (lines 36, 43, 109)
  - `src/main/java/com/minhnbnt/fashionstore/features/order/controller/SellerReturnController.java` (lines 37, 70)
  - `src/main/java/com/minhnbnt/fashionstore/features/order/controller/SellerRevenueController.java` (lines 41, 73, 90, 98)
  - `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerShopDecorationController.java` (line 59)
  - `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerShopMembershipController.java` (line 32)
  - `src/main/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStaffController.java` (line 166)
- Modify 4 Test Files:
  - `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopMemberRepositoryIntegrationTest.java` (lines 78, 104)
  - `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStatsServiceTest.java` (lines 132, 157)
  - `src/test/java/com/minhnbnt/fashionstore/features/shop/controller/SellerStatsControllerTest.java` (lines 109, 122)
  - `src/test/java/com/minhnbnt/fashionstore/features/shop/ShopStaffServiceUnitTest.java` (line 174)

- [ ] **Step 1: Format các chuỗi audit log trong `CatalogService`, `ShopDecorationService`, `OrderService`, `ReviewService`, `ShopStaffService`**
Ngắt dòng chuỗi nối chuỗi (`+`) thành nhiều dòng rõ ràng, thụt lề 4 spaces.

- [ ] **Step 2: Format `Shop.java:361`**
Xuống dòng câu lệnh `throw new ResponseStatusException(...)`.

- [ ] **Step 3: Format annotation OpenAPI/Swagger trong các Controller**
Tách `summary` và `description` ra các dòng riêng biệt theo đúng chuẩn `docs/code-guide.md`:
```java
    @Operation(
        summary = "...",
        description = "..."
    )
```

- [ ] **Step 4: Format các dòng dài trong 4 file test**
Xuống dòng các tham số `ShopRole.create(...)`, `when(...).thenReturn(...)`, `.andExpect(...)`.

- [ ] **Step 5: Quét kiểm tra toàn bộ file code nguồn**

Chạy script python kiểm tra toàn diện các file đã sửa:
Expected: 0 dòng > 120 ký tự.

---

### Task 5: Xác Minh Toàn Diện & Gate Kiểm Thử

**Files:** Toàn bộ repository `fashionstore`.

- [ ] **Step 1: Quét toàn bộ repo xác nhận không còn `@Lazy`**

Run: `git grep "@Lazy" src/`
Expected: 0 kết quả.

- [ ] **Step 2: Quét toàn bộ git diff xác nhận không còn dòng > 120 ký tự**

Run:
```bash
python -X utf8 -c "
import subprocess
p = subprocess.run(['git', 'diff', 'master...HEAD'], capture_output=True, text=True, errors='replace')
diff = p.stdout
longs = []
cur = ''
for line in diff.splitlines():
    if line.startswith('+++ b/'):
        cur = line[6:]
    elif line.startswith('+') and not line.startswith('+++'):
        if cur.endswith('.java') and len(line[1:]) > 120:
            longs.append((cur, line[1:]))
print(f'Total java diff lines > 120: {len(longs)}')
for c, l in longs[:10]:
    print(f'{c}: {l[:60]}...')
"
```
Expected: `Total java diff lines > 120: 0`.

- [ ] **Step 3: Chạy toàn bộ test suite dự án**

Run: `.\gradlew.bat test`
Expected: **BUILD SUCCESSFUL, all tests passing, 0 failures, 0 errors**.
