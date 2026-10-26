# Design: Fix Backend Code Standards & Break Service Circular Dependencies

- **Date:** 2026-10-26
- **Status:** Proposed
- **Author:** Antigravity & Pair Programming Partner
- **Target Branch:** `feature/seller-staff-2.8.1`

---

## 1. Intent & Context

Nhánh `feature/seller-staff-2.8.1` bổ sung các tính năng Seller Staff Management, Seller Statistics, Export Excel và Audit Logging. Tuy nhiên, trước khi merge vào `master`, cần xử lý triệt để 3 vi phạm:
1. **59 dòng vượt quá 120 ký tự**: Vi phạm quy tắc tại `docs/code-guide.md` ("Dòng code quá 120 ký tự thì xuống dòng").
2. **Circular dependency giữa các service, vá bằng `@Lazy`**:
   - `CatalogService` ⇄ `OrderService` (CatalogService:106 ↔ OrderService:59)
   - `CatalogService` ⇄ `ReviewService` (CatalogService:111 ↔ ReviewService:36)
   - Nhánh `master` không có `@Lazy`. PR trước thêm 4 `@Lazy` và tạo file `lombok.config` mới (`lombok.copyableAnnotations += org.springframework.context.annotation.Lazy`) để Spring Boot vượt qua chu kỳ bean khởi tạo. Điều này đi ngược với nguyên lý "Explicit over clever" và tiềm ẩn lỗi runtime do proxy injection.
3. **Chưa kiểm chứng gate `./gradlew test` toàn diện**: Toàn bộ test suite (~664 tests) cần vượt qua xanh 100% trước khi merge.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- Loại bỏ hoàn toàn 4 annotation `@Lazy` trong `CatalogService`, `OrderService`, `ReviewService`.
- Xóa bỏ file `lombok.config` (vốn chỉ sinh ra để hỗ trợ `@Lazy` trên Lombok `@RequiredArgsConstructor`).
- Đảm bảo đồ thị phụ thuộc (dependency graph) giữa các service là đồ thị có hướng không chu trình (DAG - Directed Acyclic Graph) tường minh.
- Tuân thủ 100% ranh giới aggregate (`docs/structure.md`): Không service nào gọi trực tiếp repository của domain khác.
- Format toàn bộ 59 dòng quá 120 ký tự về chuẩn <= 120 ký tự mà không thay đổi bất kỳ logic nghiệp vụ nào.
- Toàn bộ suite `./gradlew test` pass 100%.

### Non-Goals
- Không thay đổi hành vi nghiệp vụ của Order checkout, Review creation, hay Seller catalog.
- Không tái cấu trúc toàn diện các domain khác ngoài các điểm chạm cần thiết để phá vỡ circular dependency.

### Anti-Goals
- Không giữ lại proxy injection hoặc giải pháp "clever" để che giấu chu trình phụ thuộc.
- Không quay lại việc cho `OrderService` hay `ReviewService` inject trực tiếp `ProductRepository` / `ProductVariantRepository` của domain khác (vi phạm ranh giới aggregate).

---

## 3. Constraints

- **Explicit over clever**: Code rõ ràng, phụ thuộc 1 chiều, khởi tạo Spring bean tất định lúc compile/startup.
- **Cross-domain repository barrier** (`docs/structure.md`): Service domain A tuyệt đối không inject repository của domain B.
- **Line length limit** (`docs/code-guide.md`): Dòng code <= 120 ký tự, annotation dài cũng phải xuống dòng.
- **NullAway**: Mọi parameter, field, return value mặc định non-null trừ khi có lý do nghiệp vụ gắn `@Nullable`.

---

## 4. Approaches & Decision

### Approach A: Tách service nội bộ `ProductStockService` trong domain `catalog` (ĐÃ CHỌN)
- **Cơ chế**:
  - `OrderService` chỉ cần: tra cứu variant/product (`findVariantById`, `findProductById`) và biến động kho (`decrementVariantStock`, `incrementVariantStock`, `recordStockMovement`).
  - `ReviewService` chỉ cần: kiểm tra sản phẩm hợp lệ (`findProductById`, `requireActiveProduct`).
  - Tách các trách nhiệm này vào một service mới `ProductStockService` thuộc `features/catalog`.
  - `OrderService` và `ReviewService` chỉ inject `ProductStockService` (không inject `CatalogService`).
  - `CatalogService` giữ nguyên các lời gọi sang `OrderService` (để lấy `soldCount`, kiểm tra `hasActiveOrdersForProduct`) và `ReviewService` (để lấy `ratingSummary`).
- **Đồ thị phụ thuộc**:
  `ProductStockService` (tầng 1) ← `OrderService` (tầng 2) ← `ReviewService` (tầng 3) ← `CatalogService` (tầng 4).
- **Ưu điểm**:
  - Phá vỡ triệt để chu trình 2 chiều.
  - Xóa sạch 4 `@Lazy` và xóa bỏ `lombok.config`.
  - Giữ vững 100% ranh giới aggregate (không service nào chọc repo chéo).
  - Không phá vỡ các API hiện có của `CatalogService`.
- **Nhược điểm**: Thêm 1 file service mới (`ProductStockService`).

### Approach B: Đảo chiều quan hệ phụ thuộc hoàn toàn (Pure Upstream Catalog)
- **Cơ chế**: `CatalogService` không inject `OrderService` hay `ReviewService`. Thay vào đó, việc tính `soldCount`, `hasActiveOrdersForProduct`, và `ratingSummary` được chuyển lên tầng Controller / Façade hoặc sự kiện.
- **Ưu điểm**: `catalog` trở thành domain độc lập hoàn toàn.
- **Nhược điểm**: Thay đổi quá nhiều chữ ký hàm công khai của `CatalogService`, tác động tới hàng chục controller và test suites hiện có. Refactor quá lớn so với phạm vi cần fix trước merge.

### Approach C: Chấp nhận `@Lazy` và ghi nhận Tech Debt
- **Cơ chế**: Giữ nguyên mã nguồn hiện tại, chỉ thêm tài liệu ghi nhận nợ kỹ thuật.
- **Ưu điểm**: Không cần sửa code injection.
- **Nhược điểm**: Vi phạm quy chuẩn dự án "Explicit over clever", để lại nguy cơ lỗi proxy ngầm lúc runtime.

**Quyết định**: Chọn **Approach A** theo chốt phương án của User.

---

## 5. Detailed Design: Approach A

### 5.1. Tạo mới `ProductStockService`
- **Vị trí**: `src/main/java/com/minhnbnt/fashionstore/features/catalog/ProductStockService.java`
- **Dependencies injected**:
  - `ProductRepository products`
  - `ProductVariantRepository variants`
  - `StockMovementRepository movements`
  - `ShopService shops`
- **Phương thức cung cấp**:
  ```java
  public Optional<ProductVariant> findVariantById(@NonNull UUID variantId);
  public Optional<Product> findProductById(@NonNull UUID productId);
  public Product requireActiveProduct(@NonNull UUID productId);
  public boolean decrementVariantStock(@NonNull UUID variantId, int quantity);
  public void incrementVariantStock(@NonNull UUID variantId, int quantity);
  public void recordStockMovement(@NonNull UUID variantId, @NonNull UUID shopId, int quantity, @NonNull String type, @NonNull UUID referenceId);
  ```

### 5.2. Cập nhật `OrderService`
- Thay `private final CatalogService catalog;` bằng `private final ProductStockService productStock;`.
- Xóa bỏ annotation `@Lazy` trên trường `productStock`.
- Cập nhật các vị trí gọi:
  - `catalog.findVariantById(...)` -> `productStock.findVariantById(...)`
  - `catalog.findProductById(...)` -> `productStock.findProductById(...)`
  - `catalog.decrementVariantStock(...)` -> `productStock.decrementVariantStock(...)`
  - `catalog.incrementVariantStock(...)` -> `productStock.incrementVariantStock(...)`
  - `catalog.recordStockMovement(...)` -> `productStock.recordStockMovement(...)`

### 5.3. Cập nhật `ReviewService`
- Thay `private final CatalogService catalog;` bằng `private final ProductStockService productStock;`.
- Xóa bỏ annotation `@Lazy` trên trường `productStock`.
- Cập nhật các vị trí gọi:
  - `catalog.findProductById(...)` -> `productStock.findProductById(...)`
  - `catalog.requireActiveProduct(...)` -> `productStock.requireActiveProduct(...)`

### 5.4. Cập nhật `CatalogService`
- Xóa bỏ `@Lazy` trên `private final OrderService orderService;` (line 106).
- Xóa bỏ `@Lazy` trên `private final ReviewService reviews;` (line 111).
- Trong `CatalogService`, các phương thức tồn kho/sản phẩm (`findVariantById`, `decrementVariantStock`...) có thể ủy quyền cho `productStock` hoặc giữ nguyên logic để phục vụ nội bộ/Cart/Wishlist.

### 5.5. Xóa bỏ `lombok.config`
- Xóa bỏ file `lombok.config` (do không còn dùng `@Lazy` trên trường được Lombok sinh constructor).

### 5.6. Format 59 dòng quá 120 ký tự
Rà soát và ngắt dòng hợp lý theo đúng `docs/code-guide.md`:
1. **`ShopStatsRepository.java` (21 dòng)**:
   - Ngắt các record definition nhiều trường (`OrderStatusSummaryRow`, `ProductStatsRow`, `CustomerOverviewRow`, `TopCustomerRow`).
   - Ngắt các câu SQL query dài trong `@Query`.
2. **`SellerExcelReportBuilder.java` (10 dòng)**:
   - Xuống dòng các lời gọi hàm `addKpiRow(...)` và chuỗi mô tả tiếng Việt.
3. **`CatalogService.java` (6 dòng)**:
   - Xuống dòng các chuỗi audit log biến động tồn kho và trạng thái sản phẩm.
4. **`ShopDecorationService.java` (3 dòng)**:
   - Xuống dòng các câu gọi `audit.record(...)`.
5. **`OrderService.java` (2 dòng)** & **`ReviewService.java` (2 dòng)** & **`ShopStaffService.java` (2 dòng)**:
   - Xuống dòng các chuỗi audit log.
6. **`Shop.java` (1 dòng)**:
   - Xuống dòng câu `throw new ResponseStatusException(...)`.
7. **5 Seller Controller (6 dòng)**:
   - Xuống dòng các annotation Swagger `@Operation(summary = ..., description = ...)` và `@Parameter(description = ...)`.
8. **4 Test Files (8 dòng)**:
   - Xuống dòng các câu setup stubbing mockito và test fixture.

---

## 6. Verification Plan

1. **Kiểm tra độ dài dòng tự động**: Chạy script python xác nhận **0 dòng nào > 120 ký tự** trên toàn bộ các file đã xử lý.
2. **Kiểm tra Circular Dependency**: Khởi động Spring context (qua unit test & integration test) đảm bảo không có cảnh báo chu trình hay `@Lazy`.
3. **Kiểm tra toàn bộ Test Suite**: Chạy `./gradlew test` (yêu cầu Docker daemon hoạt động cho Testcontainers) đạt **100% green (664 tests pass)**.
